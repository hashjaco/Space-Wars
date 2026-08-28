import { DurableObject } from "cloudflare:workers";

export interface Env {
  ROOM: DurableObjectNamespace<Room>;
  BOARD: DurableObjectNamespace<Board>;
  VAULT: DurableObjectNamespace<Vault>;
}

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
  | { type: "roster"; peers: number[] };

/**
 * Control frames go out as comma-separated fields, not JSON.
 *
 *   welcome,<your slot>,<room>,<slot>,<slot>...
 *   roster,<slot>,<slot>...
 *
 * The game is a Java client with no JSON library in it, and this is already the encoding it uses
 * for save slots and loadouts. One `split(",")` on the other side beats a dependency and a parser
 * for four fields that will never grow past a handful.
 */
function encode(control: Control): string {
  return control.type === "welcome"
    ? ["welcome", control.slot, control.room, ...control.peers].join(",")
    : ["roster", ...control.peers].join(",");
}

interface Session {
  /** The player number this connection flies, one to MAX_PEERS. Stable for the connection's life. */
  slot: number;
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
    const room = new URL(request.url).pathname.split("/").pop() ?? "";

    if (this.sessions.size >= MAX_PEERS) {
      // Refused before the upgrade, so a full room is an ordinary HTTP failure the client can read
      // rather than a socket that opens and immediately closes for reasons it has to guess at.
      return new Response("full", { status: 409 });
    }

    const slot = this.lowestFreeSlot();
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);

    // acceptWebSocket rather than server.accept(): this one lets the room hibernate between
    // matches, which is most of a room's life.
    this.ctx.acceptWebSocket(server);
    const session: Session = { slot };
    server.serializeAttachment(session);
    this.sessions.set(server, session);

    this.send(server, { type: "welcome", slot, peers: this.slots(), room });
    this.broadcastControl({ type: "roster", peers: this.slots() }, server);

    return new Response(null, { status: 101, webSocket: client });
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
    this.forget(ws);
  }

  override async webSocketError(ws: WebSocket): Promise<void> {
    this.forget(ws);
  }

  private forget(ws: WebSocket): void {
    if (this.sessions.delete(ws)) {
      // The peers work out what a departure means for the match themselves; all they are told is
      // that it happened.
      this.broadcastControl({ type: "roster", peers: this.slots() });
    }
  }

  /**
   * The lowest slot nobody holds, so a player who drops out of a three-player game frees their
   * number for whoever joins next rather than pushing the room past four.
   */
  private lowestFreeSlot(): number {
    const taken = new Set(this.slots());
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
  }

  /**
   * Records a run and answers with where it stands, counting from one.
   *
   * One row per player per mode, and it only ever moves up: a bad run does not cost somebody the
   * good one they already posted, which is the same bargain `prefs.HighScores` makes locally.
   */
  submit(player: string, mode: string, name: string, score: number): number {
    this.ctx.storage.sql.exec(
      `INSERT INTO scores (player, mode, name, score) VALUES (?, ?, ?, ?)
         ON CONFLICT (player, mode) DO UPDATE
         SET name = excluded.name, score = max(scores.score, excluded.score)`,
      player, mode, name, score);
    const [ahead] = [...this.ctx.storage.sql.exec<{ ahead: number }>(
      `SELECT COUNT(*) AS ahead FROM scores WHERE mode = ? AND score > ?`, mode, score)];
    return (ahead?.ahead ?? 0) + 1;
  }

  /** The top of one mode's table as `name,score` lines -- the same comma idiom as everything else. */
  top(mode: string): string {
    const rows = [...this.ctx.storage.sql.exec<{ name: string; score: number }>(
      `SELECT name, score FROM scores WHERE mode = ? ORDER BY score DESC, name ASC LIMIT ?`,
      mode, TOP_ROWS)];
    return rows.map((row) => `${row.name},${row.score}`).join("\n");
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

    // ---- Rooms -----------------------------------------------------------------------------

    // Handing out codes here rather than letting clients pick keeps two groups off the same six
    // characters, and keeps a guessable code from being the way into somebody else's game.
    if (url.pathname === "/new") {
      // Plain text, for the same reason the control frames are: the client has no JSON parser and
      // this response is one word.
      return new Response(newRoomCode(), { headers: { "Content-Type": "text/plain" } });
    }

    const match = url.pathname.match(/^\/room\/([A-Z0-9]{1,16})$/);
    if (!match) {
      return new Response(
        "POST /new for a room code, then GET /room/{code} with Upgrade: websocket\n"
        + "POST /score with player,mode,name,score  |  GET /top/{mode}\n"
        + "PUT or GET /save/{sync code}\n",
        { status: 404 });
    }
    if (request.headers.get("Upgrade") !== "websocket") {
      return new Response("expected Upgrade: websocket", { status: 426 });
    }

    // getByName, so the same code always reaches the same room from anywhere in the world.
    return env.ROOM.getByName(match[1]).fetch(request);
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
