import { DurableObject } from "cloudflare:workers";

export interface Env {
  ROOM: DurableObjectNamespace<Room>;
  BOARD: DurableObjectNamespace<Board>;
  VAULT: DurableObjectNamespace<Vault>;
  /** Bearer token the replay verifier (a scheduled GitHub Action) presents; unset means no verification. */
  VERIFY_SECRET?: string;
}
/** A recorded run, gzipped. A twenty-minute run is well under this. */
const MAX_REPLAY_BYTES = 1024 * 1024;

/** Two to four, matching the ship slots the game can field. */
const MAX_PEERS = 4;

/** Room codes people read to each other, so no 0/O or 1/I, and no accidental words. */
const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

/**
 * Six characters, not four.
 *
 * Four was twenty bits -- about a million codes -- and a room is addressed by nothing else, so
 * anybody who could spend a million connections could walk into a stranger's match. Six is thirty
 * bits, a thousand times the work, and is still two groups of three to read down a phone. This is
 * the whole of the answer to room hijacking: the code *is* the room, so making the code expensive
 * to guess is making the room expensive to find.
 */
const CODE_LENGTH = 6;

/**
 * A sync code, which is the only thing standing between a stranger and somebody's saved campaign.
 *
 * Twelve characters of the same alphabet is sixty bits. The client picks its own -- there is no
 * registration step and nothing to collide with at that width -- so all this end does is refuse
 * anything that is not one, which keeps the shape of a vault key from being whatever was typed.
 */
const SYNC_CODE = /^[A-HJ-NP-Z2-9]{12}$/;

/** A player's public identity on the board. Never the sync code: that one is a credential. */
const PLAYER_ID = /^[a-f0-9-]{8,40}$/;

/** How much profile a vault will hold. An export of the whole preference tree is a few kilobytes. */
const MAX_PROFILE_BYTES = 64 * 1024;

/**
 * Above any reachable score, and the only sanity check there can be on a submitted one.
 *
 * Worth being plain about: the board is client-reported and unverifiable by construction. Every
 * score in this game is computed on the player's own machine, so a determined player can post any
 * number they like, and no login anywhere would change that -- an authenticated liar is still a
 * liar. What this ceiling buys is that a bored one cannot park `Number.MAX_SAFE_INTEGER` at the
 * top of the table forever. Treat the board as a scoreboard among friends, which is what it is.
 */
const MAX_SCORE = 100_000_000;

/** Rows returned by GET /top. Ten is what the screen shows. */
const TOP_ROWS = 10;

/**
 * What the relay itself says. Everything else it forwards without looking at it.
 *
 * Sent as text so the split is enforced by the protocol rather than by a convention: text is the
 * relay talking, binary is the game talking, and the relay never parses binary. That is what keeps
 * this file from slowly acquiring opinions about the game -- there is nowhere to put them.
 */
type Control =
  | { type: "welcome"; slot: number; peers: number[]; room: string }
  | { type: "token"; token: string }
  | { type: "roster"; peers: number[] };

/** How long a departed peer's slot is held for them, so a dropped socket can come back as itself. */
const RECLAIM_MS = 60_000;

/**
 * Control frames go out as comma-separated fields, not JSON.
 *
 *   welcome,<your slot>,<room>,<slot>,<slot>...
 *   token,<reconnect token>
 *   roster,<slot>,<slot>...
 *
 * The game is a Java client with no JSON library in it, and this is already the encoding it uses
 * for save slots and loadouts. One `split(",")` on the other side beats a dependency and a parser
 * for four fields that will never grow past a handful.
 */
function encode(control: Control): string {
  switch (control.type) {
    case "welcome": return ["welcome", control.slot, control.room, ...control.peers].join(",");
    case "token": return ["token", control.token].join(",");
    case "roster": return ["roster", ...control.peers].join(",");
  }
}

interface Session {
  /** The player number this connection flies, one to MAX_PEERS. Stable for the connection's life. */
  slot: number;
  /** Proof of the slot: a peer that drops and comes back with it inside RECLAIM_MS gets the slot again. */
  token: string;
}

/** A slot held for a departed peer, keyed by slot in storage under "reserved". */
interface Reservation {
  token: string;
  until: number;
}

/**
 * One room. Forwards opaque packets between the peers in it and knows nothing about the game.
 *
 * The whole netcode design rests on this staying dumb: every peer simulates the whole game and the
 * only thing crossing the wire is input, so there is no authoritative state here to get out of step
 * with anyone, nothing to validate, and no reason for this file to change when the game does.
 *
 * Addressed by room code through `getByName`, so the same four characters always reach the same
 * object, and the object is placed near whoever created it -- which for a group of friends is the
 * right guess about where they all are.
 */
export class Room extends DurableObject<Env> {
  /**
   * Rebuilt from the sockets themselves on every wake, never from storage.
   *
   * A hibernating room drops this map, so the slot assignment lives in each socket's attachment and
   * is read back here. Keeping it in storage instead would leave rows behind for every room anyone
   * ever opened, to describe connections that no longer exist.
   */
  private sessions = new Map<WebSocket, Session>();

  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    for (const ws of this.ctx.getWebSockets()) {
      const attachment = ws.deserializeAttachment() as Session | null;
      if (attachment) {
        this.sessions.set(ws, attachment);
      }
    }
  }

  override async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    const parts = url.pathname.split("/");
    // POST /new marks the code issued through this object, so only codes the relay handed out
    // open a room: a guessed six characters finds nothing, not an empty room to wait in.
    if (parts.at(-1) === "issue") {
      await this.ctx.storage.put("issued", true);
      return new Response("issued");
    }
    if (!(await this.ctx.storage.get<boolean>("issued"))) {
      return new Response("no such room", { status: 404 });
    }
    if (parts.at(-1) === "probe") {
      return new Response("ok");
    }
    const room = parts.pop() ?? "";
    const reserved = await this.reservations();

    // A returning peer: the slot and token it was given, inside the window, is its seat.
    const wantedSlot = Number(url.searchParams.get("slot"));
    const offered = url.searchParams.get("token");
    const held = reserved.get(wantedSlot);
    let slot: number;
    let token: string;
    if (held && offered && held.token === offered) {
      slot = wantedSlot;
      token = held.token;
      reserved.delete(slot);
    } else {
      if (this.sessions.size + reserved.size >= MAX_PEERS) {
        // Refused before the upgrade, so a full room is an ordinary HTTP failure the client can
        // read rather than a socket that opens and immediately closes for reasons it has to guess at.
        return new Response("full", { status: 409 });
      }
      slot = this.lowestFreeSlot(reserved);
      token = newToken();
    }
    await this.ctx.storage.put("reserved", Object.fromEntries(reserved));

    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);

    // acceptWebSocket rather than server.accept(): this one lets the room hibernate between
    // matches, which is most of a room's life.
    this.ctx.acceptWebSocket(server);
    const session: Session = { slot, token };
    server.serializeAttachment(session);
    this.sessions.set(server, session);

    this.send(server, { type: "welcome", slot, peers: this.slots(), room });
    this.send(server, { type: "token", token });
    this.broadcastControl({ type: "roster", peers: this.slots() }, server);

    return new Response(null, { status: 101, webSocket: client });
  }

  /** Slots held for departed peers, with expired holds dropped. */
  private async reservations(): Promise<Map<number, Reservation>> {
    const stored = (await this.ctx.storage.get<Record<string, Reservation>>("reserved")) ?? {};
    const now = Date.now();
    const live = new Map<number, Reservation>();
    for (const [slot, hold] of Object.entries(stored)) {
      if (hold.until > now) {
        live.set(Number(slot), hold);
      }
    }
    return live;
  }

  /**
   * Forwards one packet to everyone else in the room, verbatim.
   *
   * Binary only. A peer that sends text is talking to nobody: the relay's own vocabulary is text,
   * and echoing a client's text back out would let one peer impersonate the relay to the others.
   */
  override async webSocketMessage(ws: WebSocket, message: string | ArrayBuffer): Promise<void> {
    if (typeof message === "string") {
      return;
    }
    for (const peer of this.sessions.keys()) {
      if (peer !== ws) {
        // A socket can die between the roster being read and the send landing, and one dead peer
        // must not stop the packet reaching the others.
        try {
          peer.send(message);
        } catch {
          this.forget(peer);
        }
      }
    }
  }

  override async webSocketClose(ws: WebSocket): Promise<void> {
    await this.forget(ws);
  }

  override async webSocketError(ws: WebSocket): Promise<void> {
    await this.forget(ws);
  }

  private async forget(ws: WebSocket): Promise<void> {
    const session = this.sessions.get(ws);
    if (!this.sessions.delete(ws) || !session) {
      return;
    }
    // Held for the window, so the match the others are now pausing can take this peer back as
    // the same player rather than as a newcomer in a freed seat.
    const reserved = await this.reservations();
    reserved.set(session.slot, { token: session.token, until: Date.now() + RECLAIM_MS });
    await this.ctx.storage.put("reserved", Object.fromEntries(reserved));
    // The peers work out what a departure means for the match themselves; all they are told is
    // that it happened.
    this.broadcastControl({ type: "roster", peers: this.slots() });
  }

  /**
   * The lowest slot nobody holds, so a player who drops out of a three-player game frees their
   * number for whoever joins next rather than pushing the room past four.
   */
  private lowestFreeSlot(reserved: Map<number, Reservation>): number {
    const taken = new Set([...this.slots(), ...reserved.keys()]);
    for (let slot = 1; slot <= MAX_PEERS; slot++) {
      if (!taken.has(slot)) {
        return slot;
      }
    }
    throw new Error("no free slot in a room that passed the capacity check");
  }

  private slots(): number[] {
    return [...this.sessions.values()].map((session) => session.slot).sort((a, b) => a - b);
  }

  private broadcastControl(control: Control, except?: WebSocket): void {
    for (const peer of this.sessions.keys()) {
      if (peer !== except) {
        this.send(peer, control);
      }
    }
  }

  private send(ws: WebSocket, control: Control): void {
    try {
      ws.send(encode(control));
    } catch {
      this.sessions.delete(ws);
    }
  }
}

function newToken(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(12));
  return [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/**
 * The high score table, one object for the whole world.
 *
 * A single Durable Object rather than a shard per mode, because a table has to be ordered to be a
 * table and ordering across shards means reading all of them. One object also means the count that
 * produces a rank is one indexed query rather than a fan-out.
 *
 * ponytail: one object serialises every submission in the game. That is the right shape at this
 * size -- a submission happens once per run, per player -- and the upgrade if it ever isn't is a
 * shard per mode, since nothing here compares across modes.
 */
export class Board extends DurableObject<Env> {

  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    // Synchronous, so there is no window where a request arrives before the table exists.
    this.ctx.storage.sql.exec(
      `CREATE TABLE IF NOT EXISTS scores (
         player TEXT NOT NULL,
         mode   TEXT NOT NULL,
         name   TEXT NOT NULL,
         score  INTEGER NOT NULL,
         PRIMARY KEY (player, mode)
       )`);
    // The board is only ever read one way: best first, within a mode.
    this.ctx.storage.sql.exec(
      `CREATE INDEX IF NOT EXISTS scores_by_rank ON scores (mode, score DESC)`);
    // Added after the table shipped, so ALTER rather than a wider CREATE; each one fails harmlessly
    // once it has run. verified: 0 waiting, 1 the replay reproduced the score, -1 it did not.
    for (const column of ["verified INTEGER NOT NULL DEFAULT 0", "replay BLOB", "replay_score INTEGER"]) {
      try {
        this.ctx.storage.sql.exec(`ALTER TABLE scores ADD COLUMN ${column}`);
      } catch {
        // already there
      }
    }
  }

  /**
   * Records a run and answers with where it stands, counting from one.
   *
   * One row per player per mode, and it only ever moves up: a bad run does not cost somebody the
   * good one they already posted, which is the same bargain `prefs.HighScores` makes locally.
   */
  submit(player: string, mode: string, name: string, score: number): number {
    // A new best is unverified until its own recording arrives and is replayed; the old recording
    // backed the old score and is dropped with it.
    this.ctx.storage.sql.exec(
      `INSERT INTO scores (player, mode, name, score, verified, replay, replay_score) VALUES (?, ?, ?, ?, 0, NULL, NULL)
         ON CONFLICT (player, mode) DO UPDATE
         SET name = excluded.name,
             verified = CASE WHEN excluded.score > scores.score THEN 0 ELSE scores.verified END,
             replay = CASE WHEN excluded.score > scores.score THEN NULL ELSE scores.replay END,
             replay_score = CASE WHEN excluded.score > scores.score THEN NULL ELSE scores.replay_score END,
             score = max(scores.score, excluded.score)`,
      player, mode, name, score);
    const [ahead] = [...this.ctx.storage.sql.exec<{ ahead: number }>(
      `SELECT COUNT(*) AS ahead FROM scores WHERE mode = ? AND score > ?`, mode, score)];
    return (ahead?.ahead ?? 0) + 1;
  }

  /** The top of one mode's table as `name,score,verified` lines -- the same comma idiom as everything else. */
  top(mode: string): string {
    const rows = [...this.ctx.storage.sql.exec<{ name: string; score: number; verified: number }>(
      `SELECT name, score, verified FROM scores WHERE mode = ? ORDER BY score DESC, name ASC LIMIT ?`,
      mode, TOP_ROWS)];
    return rows.map((row) => `${row.name},${row.score},${row.verified}`).join("\n");
  }

  /** Attaches a recording to the row it backs. Refused unless the score is the row's current best. */
  attach(player: string, mode: string, score: number, replay: ArrayBuffer): boolean {
    const [row] = [...this.ctx.storage.sql.exec<{ score: number }>(
      `SELECT score FROM scores WHERE player = ? AND mode = ?`, player, mode)];
    if (!row || row.score !== score) {
      return false;
    }
    this.ctx.storage.sql.exec(
      `UPDATE scores SET replay = ?, replay_score = ?, verified = 0 WHERE player = ? AND mode = ?`,
      replay, score, player, mode);
    return true;
  }

  /** Rows waiting for the verifier, as `player,mode,score` lines. */
  pending(): string {
    const rows = [...this.ctx.storage.sql.exec<{ player: string; mode: string; score: number }>(
      `SELECT player, mode, score FROM scores WHERE replay IS NOT NULL AND verified = 0 LIMIT 20`)];
    return rows.map((row) => `${row.player},${row.mode},${row.score}`).join("\n");
  }

  replay(player: string, mode: string): ArrayBuffer | null {
    const [row] = [...this.ctx.storage.sql.exec<{ replay: ArrayBuffer | null }>(
      `SELECT replay FROM scores WHERE player = ? AND mode = ?`, player, mode)];
    return row?.replay ?? null;
  }

  /** The verifier's verdict. The recording has done its work either way and is dropped. */
  verdict(player: string, mode: string, ok: boolean): void {
    this.ctx.storage.sql.exec(
      `UPDATE scores SET verified = ?, replay = NULL WHERE player = ? AND mode = ? AND replay IS NOT NULL`,
      ok ? 1 : -1, player, mode);
  }
}

/**
 * One player's profile, addressed by their sync code.
 *
 * An object per code rather than a table of them, for the reason `getByName` is used for rooms:
 * the code is the address, so there is no index to keep and no scan to do, and one player's
 * profile is stored near that player. It holds one opaque string and has no opinion about what is
 * in it -- the game exports its own preference tree and reads it back, so the format is the
 * client's business, exactly as the relay has no opinion about a packet.
 */
export class Vault extends DurableObject<Env> {

  async put(profile: string): Promise<void> {
    await this.ctx.storage.put("profile", profile);
  }

  async get(): Promise<string | undefined> {
    return this.ctx.storage.get<string>("profile");
  }
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    // ---- The board -------------------------------------------------------------------------
    //
    // Plain text in and plain text out, like everything else here: the client is a Java game with
    // no JSON parser in it, and these bodies are four fields and ten lines.

    if (url.pathname === "/score" && request.method === "POST") {
      // player,mode,name,score
      const fields = (await request.text()).trim().split(",");
      if (fields.length !== 4) {
        return new Response("expected player,mode,name,score", { status: 400 });
      }
      const [player, mode, name, score] = fields;
      if (!PLAYER_ID.test(player) || !isMode(mode)) {
        return new Response("bad player or mode", { status: 400 });
      }
      const points = Number(score);
      if (!Number.isInteger(points) || points < 0 || points > MAX_SCORE) {
        return new Response("bad score", { status: 400 });
      }
      const rank = await env.BOARD.getByName("global").submit(player, mode, clean(name), points);
      return text(String(rank));
    }

    const board = url.pathname.match(/^\/top\/([A-Z]{1,16})$/);
    if (board) {
      return text(await env.BOARD.getByName("global").top(board[1]));
    }

    // ---- Profiles --------------------------------------------------------------------------

    const vault = url.pathname.match(/^\/save\/(.+)$/);
    if (vault) {
      const code = vault[1];
      if (!SYNC_CODE.test(code)) {
        return new Response("that is not a sync code", { status: 400 });
      }
      const store = env.VAULT.getByName(code);
      if (request.method === "PUT") {
        const profile = await request.text();
        if (profile.length > MAX_PROFILE_BYTES) {
          return new Response("profile too large", { status: 413 });
        }
        await store.put(profile);
        return text("ok");
      }
      const held = await store.get();
      // 404 rather than an empty body, so "nothing has ever been uploaded" is distinguishable from
      // "an upload happened and was empty" without the client having to guess.
      return held === undefined ? new Response("no profile", { status: 404 }) : text(held);
    }

    // ---- Recordings and the verifier --------------------------------------------------------
    //
    // A run's recording rides in after its score. Verification happens off this Worker, where the
    // real Java simulation can run: a scheduled job presents VERIFY_SECRET, takes what is pending,
    // replays it and posts a verdict. Until then the row is on the board but unmarked.

    const replayMatch = url.pathname.match(/^\/replay\/([a-f0-9-]{8,40})\/([A-Z]{1,16})$/);
    if (replayMatch && request.method === "PUT") {
      const score = Number(url.searchParams.get("score"));
      const body = await request.arrayBuffer();
      if (!Number.isInteger(score) || body.byteLength === 0 || body.byteLength > MAX_REPLAY_BYTES) {
        return new Response("bad recording", { status: 400 });
      }
      const ok = await env.BOARD.getByName("global").attach(replayMatch[1], replayMatch[2], score, body);
      return ok ? text("attached") : new Response("not the row's best", { status: 409 });
    }
    const verifier = env.VERIFY_SECRET && request.headers.get("Authorization") === `Bearer ${env.VERIFY_SECRET}`;
    if (url.pathname === "/replays/pending" || replayMatch || url.pathname.startsWith("/verify/")) {
      if (!env.VERIFY_SECRET) {
        return new Response("no verifier configured", { status: 503 });
      }
      if (!verifier) {
        return new Response("verifier only", { status: 401 });
      }
      if (url.pathname === "/replays/pending") {
        return text(await env.BOARD.getByName("global").pending());
      }
      if (replayMatch) {
        const blob = await env.BOARD.getByName("global").replay(replayMatch[1], replayMatch[2]);
        return blob ? new Response(blob, { headers: { "Content-Type": "application/octet-stream" } })
          : new Response("no recording", { status: 404 });
      }
      const verdictMatch = url.pathname.match(/^\/verify\/([a-f0-9-]{8,40})\/([A-Z]{1,16})$/);
      if (verdictMatch && request.method === "POST") {
        const verdict = (await request.text()).trim();
        if (verdict !== "ok" && verdict !== "bad") {
          return new Response("ok or bad", { status: 400 });
        }
        await env.BOARD.getByName("global").verdict(verdictMatch[1], verdictMatch[2], verdict === "ok");
        return text(verdict);
      }
      return new Response("not found", { status: 404 });
    }

    // ---- Rooms -----------------------------------------------------------------------------

    // Handing out codes here rather than letting clients pick keeps two groups off the same six
    // characters, and keeps a guessable code from being the way into somebody else's game.
    if (url.pathname === "/new") {
      if (request.method !== "POST") {
        return new Response("POST", { status: 405, headers: { Allow: "POST" } });
      }
      // Plain text, for the same reason the control frames are: the client has no JSON parser and
      // this response is one word. Marked issued on its room first, so the code works the moment
      // it is read out.
      const code = newRoomCode();
      await env.ROOM.getByName(code).fetch(new Request(`${url.origin}/room/${code}/issue`, { method: "POST" }));
      return new Response(code, { headers: { "Content-Type": "text/plain" } });
    }

    const match = url.pathname.match(/^\/room\/([A-Z0-9]{1,16})$/);
    if (!match) {
      return new Response(
        "POST /new for a room code, then GET /room/{code} with Upgrade: websocket\n"
        + "POST /score with player,mode,name,score  |  GET /top/{mode}\n"
        + "PUT or GET /save/{sync code}\n",
        { status: 404 });
    }
    // The room answers 404 for a code that was never issued, before the upgrade is considered.
    const room = env.ROOM.getByName(match[1]);
    if (request.headers.get("Upgrade") !== "websocket") {
      const probe = await room.fetch(new Request(`${url.origin}/room/${match[1]}/probe`));
      return probe.status === 404 ? probe : new Response("expected Upgrade: websocket", { status: 426 });
    }

    // getByName, so the same code always reaches the same room from anywhere in the world.
    return room.fetch(request);
  },
};

/** Plain text, for the same reason the control frames are: there is no parser at the other end. */
function text(body: string): Response {
  return new Response(body, { headers: { "Content-Type": "text/plain" } });
}

/** A `mode.GameMode` name. Anything else is a client this build does not have a table for. */
function isMode(mode: string): boolean {
  return /^[A-Z]{1,16}$/.test(mode);
}

/**
 * A pilot name reduced to what the board can print.
 *
 * Commas are the field separator on the way back out, so a name containing one would split a row
 * into two and shift every score by a column. The rest of the filter is the same clip
 * `prefs.Pilots.setName` already applies, applied again here because this end cannot assume the
 * thing that posted was the game.
 */
function clean(name: string): string {
  const kept = name.toUpperCase().replace(/[^A-Z0-9 ]/g, "").trim().slice(0, 10);
  return kept.length === 0 ? "PILOT" : kept;
}

function newRoomCode(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(CODE_LENGTH));
  let code = "";
  for (const byte of bytes) {
    // Modulo over a 32-character alphabet divides 256 evenly, so no character is likelier than
    // another and the code carries its full thirty bits.
    code += CODE_ALPHABET[byte % CODE_ALPHABET.length];
  }
  return code;
}
