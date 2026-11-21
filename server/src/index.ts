import { DurableObject } from "cloudflare:workers";

export interface Env {
  ROOM: DurableObjectNamespace<Room>;
  BOARD: DurableObjectNamespace<Board>;
  VAULT: DurableObjectNamespace<Vault>;
  GROUPS: DurableObjectNamespace<Groups>;
  /** Bearer token the replay verifier (a scheduled GitHub Action) presents; unset means no verification. */
  VERIFY_SECRET?: string;
  /** Overrides MIN_LEVEL_MS, in milliseconds. Set short in .dev.vars so the tests need not wait. */
  MIN_LEVEL_MS?: string;
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

/** Whoever opened a group is seated first, and the first seat is the lowest. */
const HOST_SLOT = 1;

/** A `mode.Galaxy` or `mode.Level` name, as the client's enum spells it. */
const PLACE = /^[A-Z0-9_]{1,32}$/;

/** A `prefs.Difficulty` name. */
const DIFFICULTY = /^[A-Z]{1,16}$/;

/** How often a listed room tells the board it is still there. */
const HEARTBEAT_MS = 60_000;

/** A listing nobody has refreshed for this long is a room that died without saying so. */
const LISTING_STALE_MS = 150_000;

/** Listings returned by GET /groups. More than a screen can scroll through comfortably already. */
const LISTED_GROUPS = 50;

/**
 * The shortest a level can be for clearing it to count as help.
 *
 * The relay cannot tell a level that was flown from one that was only announced, so this is the
 * whole of the check: a host and a helper who hold their sockets open and take turns saying
 * "started" and "cleared" earn one assist per half minute and no faster. That is slower than
 * playing the game, which is the bar for a scoreboard among friends.
 */
const MIN_LEVEL_MS = 30_000;

/** What one level cleared for somebody else is worth on the helpers' table. Flat, so the relay needs no table of the game's levels. */
const HELP_POINTS = 100;

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
  | { type: "roster"; peers: number[] }
  | { type: "names"; names: [number, string][] }
  | { type: "group"; group: Group }
  | { type: "left"; slot: number }
  | { type: "closed" };

/** How long a departed peer's slot is held for them, so a dropped socket can come back as itself. */
const RECLAIM_MS = 60_000;

/**
 * Control frames go out as comma-separated fields, not JSON.
 *
 *   welcome,<your slot>,<room>,<slot>,<slot>...
 *   token,<reconnect token>
 *   roster,<slot>,<slot>...
 *   names,<slot>,<name>,<slot>,<name>...
 *   group,<visibility>,<max>,<galaxy>,<level>,<difficulty>,<status>
 *   left,<slot>
 *   closed
 *
 * The last four arrived with groups. A client older than them skips any line it does not know,
 * which is what lets them be added without a version handshake.
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
    case "names": return ["names", ...control.names.flat()].join(",");
    case "group": {
      const group = control.group;
      return ["group", group.visibility, group.max, group.galaxy, group.level, group.difficulty, group.status].join(",");
    }
    case "left": return ["left", control.slot].join(",");
    case "closed": return "closed";
  }
}

/**
 * What a room was opened for, when it was opened as a group rather than as a bare code.
 *
 * The one place the relay knows anything about a match, and it is all hearsay: the host says which
 * level and how hard, and the relay repeats it on the board without understanding a word. Nothing
 * here feeds back into what is forwarded.
 */
interface Group {
  code: string;
  /** PRIVATE is a room found only by its code, which is what every room was before groups. */
  visibility: "PUBLIC" | "PRIVATE";
  max: number;
  galaxy: string;
  level: string;
  difficulty: string;
  /** The host's pilot name, for the board. */
  host: string;
  status: "LOBBY" | "PLAYING";
  /** When the host said the level under way began, or 0 while none is. */
  playingSince: number;
}

interface Session {
  /** The player number this connection flies, one to MAX_PEERS. Stable for the connection's life. */
  slot: number;
  /** Proof of the slot: a peer that drops and comes back with it inside RECLAIM_MS gets the slot again. */
  token: string;
  /** The pilot's name as the lobby shows it, or "" from a client that does not send one. */
  name: string;
  /** The pilot's public board id, or "" -- in which case helping earns them nothing here. */
  player: string;
  /** Whether the host named this seat in the level under way. Only those can be credited for it. */
  flying: boolean;
  /** Set as the relay closes the socket itself, so a wake between that and the close event does not seat a ghost. */
  gone?: boolean;
}

/** A slot held for a departed peer, keyed by slot in storage under "reserved". */
interface Reservation {
  token: string;
  until: number;
  name?: string;
  player?: string;
  flying?: boolean;
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
      if (attachment && !attachment.gone) {
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
      // A body means a group: the Worker has already checked every field, so this only files it.
      const fields = (await request.text()).trim().split(",");
      if (fields.length === 7) {
        const [code, visibility, max, galaxy, level, difficulty, host] = fields;
        const group: Group = {
          code, visibility: visibility === "PUBLIC" ? "PUBLIC" : "PRIVATE", max: Number(max),
          galaxy, level, difficulty, host, status: "LOBBY", playingSince: 0,
        };
        await this.ctx.storage.put("group", group);
        // If the host never arrives, the alarm finds an empty group and clears it away.
        await this.ctx.storage.setAlarm(Date.now() + HEARTBEAT_MS);
      }
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
    const group = await this.ctx.storage.get<Group>("group");
    const capacity = group?.max ?? MAX_PEERS;
    const saidName = url.searchParams.get("name");
    const saidPlayer = url.searchParams.get("player") ?? "";

    // A returning peer: the slot and token it was given, inside the window, is its seat.
    const wantedSlot = Number(url.searchParams.get("slot"));
    const offered = url.searchParams.get("token");
    const held = reserved.get(wantedSlot);
    let slot: number;
    let token: string;
    let name = saidName === null ? "" : clean(saidName);
    let player = PLAYER_ID.test(saidPlayer) ? saidPlayer : "";
    let flying = false;
    if (held && offered && held.token === offered) {
      slot = wantedSlot;
      token = held.token;
      // A pilot who dropped mid-level comes back as who they were, still part of that level.
      name = name || (held.name ?? "");
      player = player || (held.player ?? "");
      flying = held.flying ?? false;
      reserved.delete(slot);
    } else {
      if (this.sessions.size + reserved.size >= capacity) {
        // Refused before the upgrade, so a full room is an ordinary HTTP failure the client can
        // read rather than a socket that opens and immediately closes for reasons it has to guess at.
        return new Response("full", { status: 409 });
      }
      slot = this.lowestFreeSlot(reserved, capacity);
      token = newToken();
    }
    await this.ctx.storage.put("reserved", Object.fromEntries(reserved));

    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);

    // acceptWebSocket rather than server.accept(): this one lets the room hibernate between
    // matches, which is most of a room's life.
    this.ctx.acceptWebSocket(server);
    const session: Session = { slot, token, name, player, flying };
    server.serializeAttachment(session);
    this.sessions.set(server, session);

    // Before the welcome, so a client knows what kind of room this is by the time it is seated.
    if (group) {
      this.send(server, { type: "group", group });
    }
    this.send(server, { type: "welcome", slot, peers: this.slots(), room });
    this.send(server, { type: "token", token });
    this.broadcastControl({ type: "roster", peers: this.slots() }, server);
    this.broadcastControl({ type: "names", names: this.names() });
    await this.list(group);

    return new Response(null, { status: 101, webSocket: client });
  }

  /**
   * What a peer says to the relay rather than to the other peers.
   *
   *   leave                                  this seat is free now, not in a minute
   *   state,PLAYING,<level>,<slot>,<slot>... the host: a level has begun with these seats flying
   *   state,LOBBY                            the host: nobody is flying
   *   cleared                                the host: that level is won
   *
   * Never forwarded, which keeps the old rule whole: a peer's text reaches the relay and nobody
   * else, so no peer can speak to another in the relay's voice.
   */
  private async command(ws: WebSocket, line: string): Promise<void> {
    const session = this.sessions.get(ws);
    if (!session) {
      return;
    }
    const fields = line.split(",");
    const group = await this.ctx.storage.get<Group>("group");
    if (fields[0] === "leave") {
      await this.leave(ws, session, group);
      return;
    }
    // Everything else is the host describing the match, and only a group has a host.
    if (!group || session.slot !== HOST_SLOT) {
      return;
    }
    if (fields[0] === "state" && fields[1] === "PLAYING" && PLACE.test(fields[2] ?? "")) {
      const flying = new Set(fields.slice(3).map(Number));
      this.markFlying((slot) => flying.has(slot));
      group.status = "PLAYING";
      group.level = fields[2];
      group.playingSince = Date.now();
    } else if (fields[0] === "state" && fields[1] === "LOBBY") {
      this.markFlying(() => false);
      group.status = "LOBBY";
      group.playingSince = 0;
    } else if (fields[0] === "cleared") {
      await this.creditHelpers(group);
      return;
    } else {
      return;
    }
    await this.ctx.storage.put("group", group);
    this.broadcastControl({ type: "group", group });
    await this.list(group);
  }

  private markFlying(flying: (slot: number) => boolean): void {
    for (const [peer, session] of this.sessions) {
      session.flying = flying(session.slot);
      peer.serializeAttachment(session);
    }
  }

  /**
   * Writes down everyone who helped clear the level the host just called won.
   *
   * The room reports this rather than the helper, which is the difference between a table of
   * people who say they helped and a table of people who were seated in somebody else's room, not
   * as its host, named by that host in a level that lasted long enough to have been flown. None of
   * it is proof -- see MIN_LEVEL_MS -- but each of those is one more thing a liar has to arrange.
   */
  private async creditHelpers(group: Group): Promise<void> {
    const least = Number(this.env.MIN_LEVEL_MS ?? MIN_LEVEL_MS);
    if (!group.playingSince || Date.now() - group.playingSince < least) {
      return;
    }
    // Spent, so one level cannot be cleared twice: the next needs its own "state,PLAYING".
    group.playingSince = 0;
    await this.ctx.storage.put("group", group);
    const board = this.env.BOARD.getByName("global");
    for (const session of this.sessions.values()) {
      if (session.flying && session.slot !== HOST_SLOT && session.player) {
        await board.assist(session.player, session.name || "PILOT", HELP_POINTS);
      }
    }
  }

  /**
   * A peer leaving on purpose. Unlike a dropped socket there is nobody to hold the seat for, so it
   * is free at once and the others are told it was a decision rather than an outage.
   */
  private async leave(ws: WebSocket, session: Session, group: Group | undefined): Promise<void> {
    this.sessions.delete(ws);
    this.dismiss(ws, session, 1000, "left");
    this.broadcastControl({ type: "left", slot: session.slot });
    this.broadcastControl({ type: "roster", peers: this.slots() });
    this.broadcastControl({ type: "names", names: this.names() });
    if (group && session.slot === HOST_SLOT) {
      await this.closeGroup(group);
      return;
    }
    await this.list(group);
  }

  /** Closes a socket from this end, marked so that no later wake mistakes it for a seated peer. */
  private dismiss(ws: WebSocket, session: Session, code: number, reason: string): void {
    try {
      ws.serializeAttachment({ ...session, gone: true });
      ws.close(code, reason);
    } catch {
      // already closed
    }
  }

  /**
   * The host is gone for good, and a group is its host's: they chose the level, and theirs is the
   * only machine that names each level's terms. Everyone is told and the room stops existing, so
   * its code finds nothing and its listing is taken down.
   */
  private async closeGroup(group: Group): Promise<void> {
    const peers = [...this.sessions];
    this.sessions.clear();
    for (const [peer, session] of peers) {
      try {
        peer.send(encode({ type: "closed" }));
      } catch {
        // already closed
      }
      this.dismiss(peer, session, 4000, "the host left");
    }
    if (group.visibility === "PUBLIC") {
      await this.env.GROUPS.getByName("global").remove(group.code);
    }
    await this.ctx.storage.deleteAlarm();
    await this.ctx.storage.deleteAll();
  }

  /**
   * Puts a public group on the board, or refreshes it there, and keeps the alarm running.
   *
   * Seats held for a dropped peer count as taken, because they are: a stranger who tried one would
   * be refused. The alarm does two jobs for any group, listed or not -- it is the heartbeat that
   * keeps a listing from going stale, and it is what notices the host's held seat running out.
   */
  private async list(group: Group | undefined): Promise<void> {
    if (!group) {
      return;
    }
    const reserved = await this.reservations();
    const hostPresent = [...this.sessions.values()].some((session) => session.slot === HOST_SLOT);
    if (group.visibility === "PUBLIC") {
      const groups = this.env.GROUPS.getByName("global");
      if (hostPresent) {
        await groups.put(group.code, group.host, this.sessions.size + reserved.size, group.max,
          group.galaxy, group.level, group.difficulty, group.status);
      } else {
        // Not while the host's seat is only held: joining a room whose host may not come back is
        // a wait for nothing.
        await groups.remove(group.code);
      }
    }
    const hostHold = reserved.get(HOST_SLOT);
    await this.ctx.storage.setAlarm(Math.min(Date.now() + HEARTBEAT_MS, hostHold ? hostHold.until + 50 : Infinity));
  }

  override async alarm(): Promise<void> {
    const group = await this.ctx.storage.get<Group>("group");
    if (!group) {
      return;
    }
    const reserved = await this.reservations();
    const hostPresent = [...this.sessions.values()].some((session) => session.slot === HOST_SLOT);
    if (!hostPresent && !reserved.has(HOST_SLOT)) {
      await this.closeGroup(group);
      return;
    }
    await this.list(group);
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
      await this.command(ws, message);
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
    // A group its host closed has no storage left, and its sockets are closing because of that.
    if (!(await this.ctx.storage.get<boolean>("issued"))) {
      return;
    }
    const reserved = await this.reservations();
    reserved.set(session.slot, {
      token: session.token, until: Date.now() + RECLAIM_MS,
      name: session.name, player: session.player, flying: session.flying,
    });
    await this.ctx.storage.put("reserved", Object.fromEntries(reserved));
    // The peers work out what a departure means for the match themselves; all they are told is
    // that it happened.
    this.broadcastControl({ type: "roster", peers: this.slots() });
    this.broadcastControl({ type: "names", names: this.names() });
    await this.list(await this.ctx.storage.get<Group>("group"));
  }

  /**
   * The lowest slot nobody holds, so a player who drops out of a three-player game frees their
   * number for whoever joins next rather than pushing the room past four.
   */
  private lowestFreeSlot(reserved: Map<number, Reservation>, capacity: number): number {
    const taken = new Set([...this.slots(), ...reserved.keys()]);
    // Up to MAX_PEERS rather than to the capacity: a three-seat group whose second pilot left has
    // seats one and three taken, and the free one is still a real ship slot.
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

  /** Every seated pilot's name by slot. A client that sent none is "" and names itself locally. */
  private names(): [number, string][] {
    return [...this.sessions.values()]
      .sort((a, b) => a.slot - b.slot)
      .map((session) => [session.slot, session.name]);
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
    for (const column of ["verified INTEGER NOT NULL DEFAULT 0", "replay BLOB", "replay_score INTEGER",
      // The preset the row's best was set on, as the client's enum name; '' for rows older than this.
      "difficulty TEXT NOT NULL DEFAULT ''"]) {
      try {
        this.ctx.storage.sql.exec(`ALTER TABLE scores ADD COLUMN ${column}`);
      } catch {
        // already there
      }
    }
    // A second table rather than more columns: a helper has no mode, and a score has no assists.
    this.ctx.storage.sql.exec(
      `CREATE TABLE IF NOT EXISTS helpers (
         player  TEXT PRIMARY KEY,
         name    TEXT NOT NULL,
         points  INTEGER NOT NULL,
         assists INTEGER NOT NULL
       )`);
    this.ctx.storage.sql.exec(
      `CREATE INDEX IF NOT EXISTS helpers_by_rank ON helpers (points DESC)`);
  }

  /**
   * One level cleared for somebody else. Only a room calls this -- there is no route to it -- so
   * the only way onto the helpers' table is to have been seated in another pilot's group.
   */
  assist(player: string, name: string, points: number): void {
    this.ctx.storage.sql.exec(
      `INSERT INTO helpers (player, name, points, assists) VALUES (?, ?, ?, 1)
         ON CONFLICT (player) DO UPDATE
         SET name = excluded.name, points = helpers.points + excluded.points, assists = helpers.assists + 1`,
      player, name, points);
  }

  /** The top of the helpers' table as `name,points` lines, the shape the score board already reads. */
  helpers(): string {
    const rows = [...this.ctx.storage.sql.exec<{ name: string; points: number }>(
      `SELECT name, points FROM helpers ORDER BY points DESC, name ASC LIMIT ?`, TOP_ROWS)];
    return rows.map((row) => `${row.name},${row.points}`).join("\n");
  }

  /**
   * Records a run and answers with where it stands, counting from one.
   *
   * One row per player per mode, and it only ever moves up: a bad run does not cost somebody the
   * good one they already posted, which is the same bargain `prefs.HighScores` makes locally.
   */
  submit(player: string, mode: string, name: string, score: number, difficulty: string): number {
    // A new best is unverified until its own recording arrives and is replayed; the old recording
    // backed the old score and is dropped with it.
    this.ctx.storage.sql.exec(
      `INSERT INTO scores (player, mode, name, score, difficulty, verified, replay, replay_score) VALUES (?, ?, ?, ?, ?, 0, NULL, NULL)
         ON CONFLICT (player, mode) DO UPDATE
         SET name = excluded.name,
             difficulty = CASE WHEN excluded.score > scores.score THEN excluded.difficulty ELSE scores.difficulty END,
             verified = CASE WHEN excluded.score > scores.score THEN 0 ELSE scores.verified END,
             replay = CASE WHEN excluded.score > scores.score THEN NULL ELSE scores.replay END,
             replay_score = CASE WHEN excluded.score > scores.score THEN NULL ELSE scores.replay_score END,
             score = max(scores.score, excluded.score)`,
      player, mode, name, score, difficulty);
    const [ahead] = [...this.ctx.storage.sql.exec<{ ahead: number }>(
      `SELECT COUNT(*) AS ahead FROM scores WHERE mode = ? AND score > ?`, mode, score)];
    return (ahead?.ahead ?? 0) + 1;
  }

  /**
   * The top of one mode's table as `name,score,verified` lines -- the same comma idiom as everything else.
   *
   * `withDifficulty` adds a fourth field. Opt-in, because clients up to 2.4 skip any row that is
   * not two or three fields and would show an empty board.
   */
  top(mode: string, withDifficulty: boolean): string {
    const rows = [...this.ctx.storage.sql.exec<{ name: string; score: number; verified: number; difficulty: string }>(
      `SELECT name, score, verified, difficulty FROM scores WHERE mode = ? ORDER BY score DESC, name ASC LIMIT ?`,
      mode, TOP_ROWS)];
    return rows.map((row) => `${row.name},${row.score},${row.verified}` + (withDifficulty ? `,${row.difficulty}` : "")).join("\n");
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

  /**
   * A recording that cannot be judged -- a run resumed from a checkpoint has no seed to replay from.
   * Unmarked, not rejected. Also lifts a cross a row was given for that reason before this existed;
   * it never takes a tick away.
   */
  unverifiable(player: string, mode: string): void {
    this.ctx.storage.sql.exec(
      `UPDATE scores SET verified = 0, replay = NULL WHERE player = ? AND mode = ? AND verified <= 0`,
      player, mode);
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

/**
 * The game board: every public group that is open right now, one object for the whole world.
 *
 * Apart from the score board on purpose. This is read by everyone who has the board open, every
 * few seconds, and written by every listed room once a minute; the score board is written once per
 * run and carries recordings a megabyte at a time. One object serialises its callers, so sharing
 * would put a pilot browsing for a game in a queue behind somebody's replay upload.
 *
 * Nothing here is the truth about a room -- the room is. A row is what the room last said, and a
 * room that dies without saying goodbye is noticed by its silence rather than by a callback.
 */
export class Groups extends DurableObject<Env> {

  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    this.ctx.storage.sql.exec(
      `CREATE TABLE IF NOT EXISTS groups (
         code       TEXT PRIMARY KEY,
         host       TEXT NOT NULL,
         players    INTEGER NOT NULL,
         max        INTEGER NOT NULL,
         galaxy     TEXT NOT NULL,
         level      TEXT NOT NULL,
         difficulty TEXT NOT NULL,
         status     TEXT NOT NULL,
         seen       INTEGER NOT NULL
       )`);
  }

  put(code: string, host: string, players: number, max: number, galaxy: string, level: string,
    difficulty: string, status: string): void {
    this.ctx.storage.sql.exec(
      `INSERT OR REPLACE INTO groups (code, host, players, max, galaxy, level, difficulty, status, seen)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      code, host, players, max, galaxy, level, difficulty, status, Date.now());
  }

  remove(code: string): void {
    this.ctx.storage.sql.exec(`DELETE FROM groups WHERE code = ?`, code);
  }

  /**
   * Open groups as `code,host,players,max,galaxy,level,difficulty,status` lines, those with a free
   * seat first and the most recently heard from first within that. An empty filter matches all.
   */
  list(galaxy: string, difficulty: string): string {
    // Swept on read rather than on a timer: a stale row matters only when somebody is looking.
    this.ctx.storage.sql.exec(`DELETE FROM groups WHERE seen <= ?`, Date.now() - LISTING_STALE_MS);
    const rows = [...this.ctx.storage.sql.exec<{
      code: string; host: string; players: number; max: number;
      galaxy: string; level: string; difficulty: string; status: string;
    }>(
      `SELECT code, host, players, max, galaxy, level, difficulty, status FROM groups
         WHERE (? = '' OR galaxy = ?) AND (? = '' OR difficulty = ?)
         ORDER BY (players < max) DESC, seen DESC LIMIT ?`,
      galaxy, galaxy, difficulty, difficulty, LISTED_GROUPS)];
    return rows.map((row) =>
      [row.code, row.host, row.players, row.max, row.galaxy, row.level, row.difficulty, row.status].join(",")).join("\n");
  }
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    // ---- Groups ----------------------------------------------------------------------------
    //
    // A group is a room with a note on the door: what is being flown, how hard, and how many
    // may come. A public one is also pinned to the board, where anyone can find it.

    if (url.pathname === "/group") {
      if (request.method !== "POST") {
        return new Response("POST", { status: 405, headers: { Allow: "POST" } });
      }
      const fields = (await request.text()).trim().split(",");
      if (fields.length !== 6) {
        return new Response("expected visibility,max,galaxy,level,difficulty,name", { status: 400 });
      }
      const [visibility, max, galaxy, level, difficulty, name] = fields;
      const seats = Number(max);
      if ((visibility !== "PUBLIC" && visibility !== "PRIVATE")
        || !Number.isInteger(seats) || seats < 2 || seats > MAX_PEERS
        || !PLACE.test(galaxy) || !PLACE.test(level) || !DIFFICULTY.test(difficulty)) {
        return new Response("bad group", { status: 400 });
      }
      const code = newRoomCode();
      await env.ROOM.getByName(code).fetch(new Request(`${url.origin}/room/${code}/issue`, {
        method: "POST",
        body: [code, visibility, seats, galaxy, level, difficulty, clean(name)].join(","),
      }));
      return text(code);
    }

    if (url.pathname === "/groups") {
      const galaxy = url.searchParams.get("galaxy") ?? "";
      const difficulty = url.searchParams.get("difficulty") ?? "";
      if ((galaxy !== "" && !PLACE.test(galaxy)) || (difficulty !== "" && !DIFFICULTY.test(difficulty))) {
        return new Response("bad filter", { status: 400 });
      }
      return text(await env.GROUPS.getByName("global").list(galaxy, difficulty));
    }

    if (url.pathname === "/helpers") {
      return text(await env.BOARD.getByName("global").helpers());
    }

    // ---- The board -------------------------------------------------------------------------
    //
    // Plain text in and plain text out, like everything else here: the client is a Java game with
    // no JSON parser in it, and these bodies are four fields and ten lines.

    if (url.pathname === "/score" && request.method === "POST") {
      // player,mode,name,score[,difficulty] -- the fifth field is absent from clients up to 2.4.
      const fields = (await request.text()).trim().split(",");
      if (fields.length !== 4 && fields.length !== 5) {
        return new Response("expected player,mode,name,score,difficulty", { status: 400 });
      }
      const [player, mode, name, score, difficulty = ""] = fields;
      if (!PLAYER_ID.test(player) || !isMode(mode)) {
        return new Response("bad player or mode", { status: 400 });
      }
      if (!/^[A-Z]{0,16}$/.test(difficulty)) {
        return new Response("bad difficulty", { status: 400 });
      }
      const points = Number(score);
      if (!Number.isInteger(points) || points < 0 || points > MAX_SCORE) {
        return new Response("bad score", { status: 400 });
      }
      const rank = await env.BOARD.getByName("global").submit(player, mode, clean(name), points, difficulty);
      return text(String(rank));
    }

    const board = url.pathname.match(/^\/top\/([A-Z]{1,16})$/);
    if (board) {
      return text(await env.BOARD.getByName("global").top(board[1], url.searchParams.get("v") === "2"));
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
        if (verdict !== "ok" && verdict !== "bad" && verdict !== "skip") {
          return new Response("ok, bad or skip", { status: 400 });
        }
        if (verdict === "skip") {
          await env.BOARD.getByName("global").unverifiable(verdictMatch[1], verdictMatch[2]);
          return text(verdict);
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
        + "POST /group with visibility,max,galaxy,level,difficulty,name  |  GET /groups  |  GET /helpers\n"
        + "POST /score with player,mode,name,score,difficulty  |  GET /top/{mode}?v=2\n"
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
