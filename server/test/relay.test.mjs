// Localhost by default; `RELAY_BASE=https://... npm test` runs the same checks against production.
const BASE = process.env.RELAY_BASE ?? "http://127.0.0.1:8787";
const SOCKET = BASE.replace(/^http/, "ws");
let failures = 0;
const check = (name, ok, detail = "") => {
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}${ok ? "" : "  <- " + detail}`);
  if (!ok) failures++;
};
const open = (room, query = "") => new Promise((res, rej) => {
  const ws = new WebSocket(`${SOCKET}/room/${room}${query}`);
  ws.binaryType = "arraybuffer";
  ws.inbox = []; ws.control = [];
  ws.onmessage = (e) => (typeof e.data === "string" ? ws.control.push(parseControl(e.data)) : ws.inbox.push(new Uint8Array(e.data)));
  ws.onopen = () => res(ws);
  ws.onerror = (e) => rej(new Error("ws error " + room));
  setTimeout(() => rej(new Error("timeout opening " + room)), 5000);
});
const pick = (n, from) => Array.from({ length: n }, () => from[(Math.random() * from.length) | 0]).join("");
const settle = () => new Promise((r) => setTimeout(r, 250));
const parseControl = (line) => {
  const f = line.split(",");
  switch (f[0]) {
    case "welcome": return { type: "welcome", slot: +f[1], room: f[2], peers: f.slice(3).map(Number) };
    case "token": return { type: "token", token: f[1] };
    case "roster": return { type: "roster", peers: f.slice(1).map(Number) };
    case "names": {
      const names = {};
      for (let i = 1; i + 1 < f.length; i += 2) names[f[i]] = f[i + 1];
      return { type: "names", names };
    }
    case "group": return { type: "group", visibility: f[1], max: +f[2], galaxy: f[3], level: f[4], difficulty: f[5], status: f[6] };
    case "left": return { type: "left", slot: +f[1] };
    default: return { type: f[0] };
  }
};
/** The newest line of one kind, since a roster is now followed by the names that go with it. */
const last = (ws, type) => ws.control.findLast((c) => c.type === type);

check("GET /new is refused", (await fetch(`${BASE}/new`)).status === 405);
const room = (await (await fetch(`${BASE}/new`, { method: "POST" })).text()).trim();
check("POST /new returns a 6-char room code", /^[A-HJ-NP-Z2-9]{6}$/.test(room), room);
check("a code nobody was issued finds no room", (await fetch(`${BASE}/room/ZZZZZZ`)).status === 404);

const a = await open(room); await settle();
const b = await open(room); await settle();

check("first peer is told slot 1", a.control[0]?.type === "welcome" && a.control[0].slot === 1, String(a.control[0]?.slot));
check("a bare room code is not a group", !a.control.some((c) => c.type === "group"), JSON.stringify(a.control));
const tokenA = a.control.find((c) => c.type === "token")?.token;
check("a peer is handed a reconnect token", /^[0-9a-f]{24}$/.test(tokenA ?? ""), String(tokenA));
check("second peer is told slot 2", b.control[0]?.type === "welcome" && b.control[0].slot === 2, String(b.control[0]?.slot));
check("first peer sees the roster grow", a.control.some((c) => c.type === "roster" && c.peers.join() === "1,2"), JSON.stringify(a.control));

const packet = new Uint8Array([0, 1, 0, 0, 0, 42, 255, 128, 7]);
a.send(packet); await settle();
check("binary reaches the other peer verbatim", b.inbox.length === 1 && b.inbox[0].join() === packet.join(), JSON.stringify(b.inbox));
check("binary is not echoed to the sender", a.inbox.length === 0, JSON.stringify(a.inbox));

const controlBefore = b.control.length;
a.send("i am the relay"); await settle();
check("text from a peer is not forwarded", b.control.length === controlBefore, JSON.stringify(b.control.slice(controlBefore)));

const c = await open(room); const d = await open(room); await settle();
check("four peers fit", d.control[0]?.slot === 4, String(d.control[0]?.slot));
// Not via fetch(): undici refuses to send an Upgrade header, so the only way to ask for a
// fifth seat is to actually ask for one.
const fifthOpened = await open(room).then(() => true, () => false);
check("a fifth peer is refused", fifthOpened === false, "the fifth connection opened");

const tokenB = b.control.find((c) => c.type === "token")?.token;
b.close(); await settle();
check("departure updates the roster", last(a, "roster")?.peers.join() === "1,3,4", JSON.stringify(last(a, "roster")));

// The departed seat is held for its owner for a minute: with three present and one held, the room
// is full to a stranger, even one guessing at the seat, while the owner walks back in.
const strangerOpened = await open(room, "?slot=2&token=notthetoken").then(() => true, () => false);
check("a stranger cannot take the held seat", strangerOpened === false, "the stranger got in");
const back = await open(room, `?slot=2&token=${tokenB}`); await settle();
check("the owner reclaims the held seat with the token", back.control[0]?.slot === 2, String(back.control[0]?.slot));
check("reclaiming keeps the token", back.control.find((c) => c.type === "token")?.token === tokenB);
check("the roster shows the owner back", last(a, "roster")?.peers.join() === "1,2,3,4", JSON.stringify(last(a, "roster")));
back.close(); await settle();

const eOpened = await open(room).then(() => true, () => false);
check("a seat is held for its owner rather than reused at once", eOpened === false, "a newcomer took the held seat");
const e = { close() {} };

check("no upgrade header gets 426", (await fetch(`${BASE}/room/${room}`)).status === 426);

for (const ws of [a, c, d, e]) ws.close();

// ---- Groups ----------------------------------------------------------------------------------
//
// Galaxy and level names are random per run, so a listing left by an earlier run -- or by real
// players, against production -- cannot be mistaken for this one's.

const place = () => pick(12, "ABCDEFGHIJKLMNOPQRSTUVWXYZ");
const GALAXY = place(); const LEVEL = place(); const NEXT_LEVEL = place();
const group = (body) => fetch(`${BASE}/group`, { method: "POST", body });
const listed = async (query = "") => (await (await fetch(`${BASE}/groups${query}`)).text()).trim().split("\n").filter(Boolean);
const rowFor = async (code, query = "") => (await listed(query)).find((row) => row.startsWith(code + ","));
const helpers = async () => (await (await fetch(`${BASE}/helpers`)).text()).trim().split("\n");

check("GET /group is refused", (await fetch(`${BASE}/group`)).status === 405);
check("a group needs all six fields", (await group(`PUBLIC,3,${GALAXY},${LEVEL}`)).status === 400);
check("a group for one is refused", (await group(`PUBLIC,1,${GALAXY},${LEVEL},HARD,ACE`)).status === 400);
check("a group for five is refused", (await group(`PUBLIC,5,${GALAXY},${LEVEL},HARD,ACE`)).status === 400);
check("a visibility that is neither is refused", (await group(`SECRET,2,${GALAXY},${LEVEL},HARD,ACE`)).status === 400);
check("a level that is not an enum name is refused", (await group(`PUBLIC,2,${GALAXY},lower case,HARD,ACE`)).status === 400);

const publicCode = (await (await group(`PUBLIC,2,${GALAXY},${LEVEL},HARD,ace!`)).text()).trim();
check("POST /group returns a room code", /^[A-HJ-NP-Z2-9]{6}$/.test(publicCode), publicCode);
check("a group is not listed until its host is in it", (await rowFor(publicCode)) === undefined, String(await rowFor(publicCode)));

const hostId = crypto.randomUUID(); const helperId = crypto.randomUUID();
const HELPER_NAME = pick(8, "ABCDEFGHIJKLMNOPQRSTUVWXYZ");
const host = await open(publicCode, `?name=ace&player=${hostId}`); await settle();
check("the host is told what the group is before being seated",
  host.control[0]?.type === "group" && host.control[0].max === 2 && host.control[0].status === "LOBBY", JSON.stringify(host.control[0]));
check("the host of a group is slot 1", host.control.find((c) => c.type === "welcome")?.slot === 1);
check("a public group is listed once its host arrives",
  (await rowFor(publicCode)) === `${publicCode},ACE,1,2,${GALAXY},${LEVEL},HARD,LOBBY`, String(await rowFor(publicCode)));
check("the galaxy filter finds it", (await rowFor(publicCode, `?galaxy=${GALAXY}`)) !== undefined);
check("another galaxy's filter does not", (await rowFor(publicCode, `?galaxy=${place()}`)) === undefined);
check("the difficulty filter finds it", (await rowFor(publicCode, `?galaxy=${GALAXY}&difficulty=HARD`)) !== undefined);
check("another difficulty's filter does not", (await rowFor(publicCode, `?galaxy=${GALAXY}&difficulty=EASY`)) === undefined);
check("a filter that is not a name is refused", (await fetch(`${BASE}/groups?galaxy=no%20such`)).status === 400);

const helper = await open(publicCode, `?name=${HELPER_NAME}&player=${helperId}`); await settle();
check("everyone is told everyone's name", last(host, "names")?.names[2] === HELPER_NAME && last(helper, "names")?.names[1] === "ACE",
  JSON.stringify([last(host, "names"), last(helper, "names")]));
check("the listing counts the newcomer", (await rowFor(publicCode))?.split(",")[2] === "2", String(await rowFor(publicCode)));
const thirdOpened = await open(publicCode).then(() => true, () => false);
check("a group for two refuses a third", thirdOpened === false, "the third connection opened");

// Only the host describes the match. A guest saying the same words changes nothing.
helper.send(`state,PLAYING,${NEXT_LEVEL},1,2`); await settle();
check("a guest cannot say the match has started", (await rowFor(publicCode))?.endsWith(",LOBBY"), String(await rowFor(publicCode)));
host.send(`state,PLAYING,${NEXT_LEVEL},1,2`); await settle();
check("the host can, and the board shows the level being flown",
  (await rowFor(publicCode)) === `${publicCode},ACE,2,2,${GALAXY},${NEXT_LEVEL},HARD,PLAYING`, String(await rowFor(publicCode)));
check("everyone in the room is told", last(helper, "group")?.status === "PLAYING" && last(helper, "group")?.level === NEXT_LEVEL,
  JSON.stringify(last(helper, "group")));

// The relay runs with MIN_LEVEL_MS=1000 for these; say MIN_LEVEL_MS=30000 against production.
const LEVEL_MS = Number(process.env.MIN_LEVEL_MS ?? 1000);
const credited = async () => (await helpers()).some((row) => row === `${HELPER_NAME},100`);
host.send("cleared"); await settle();
check("a level cleared the moment it began earns nothing", !(await credited()), JSON.stringify(await helpers()));
await new Promise((r) => setTimeout(r, LEVEL_MS + 200));
helper.send("cleared"); await settle();
check("a guest cannot call the level cleared", !(await credited()), JSON.stringify(await helpers()));
host.send("cleared"); await settle();
check("helping clear a level puts the helper on the table", await credited(), JSON.stringify(await helpers()));
host.send("cleared"); await settle();
check("one level is not cleared twice", await credited(), JSON.stringify(await helpers()));

// Leaving on purpose frees the seat now, where a dropped socket holds it for a minute.
helper.send("leave"); await settle();
check("the others are told a pilot left on purpose", last(host, "left")?.slot === 2, JSON.stringify(host.control.slice(-3)));
check("the listing shows the free seat at once", (await rowFor(publicCode))?.split(",")[2] === "1", String(await rowFor(publicCode)));
const newcomer = await open(publicCode, "?name=late"); await settle();
check("a newcomer takes the seat with no token", newcomer.control.find((c) => c.type === "welcome")?.slot === 2);
check("a newcomer to a match under way is told so", newcomer.control[0]?.status === "PLAYING", JSON.stringify(newcomer.control[0]));

host.send("leave"); await settle();
check("when the host leaves, everyone is told the group is closed", newcomer.control.some((c) => c.type === "closed"), JSON.stringify(newcomer.control.slice(-3)));
check("and the listing comes down", (await rowFor(publicCode)) === undefined, String(await rowFor(publicCode)));
check("and its code finds no room", (await fetch(`${BASE}/room/${publicCode}`)).status === 404);

const privateCode = (await (await group(`PRIVATE,4,${GALAXY},${LEVEL},EASY,ACE`)).text()).trim();
const quiet = await open(privateCode, "?name=ace"); await settle();
check("a private group is joinable by its code", quiet.control.find((c) => c.type === "welcome")?.slot === 1);
check("a private group is never listed", (await rowFor(privateCode)) === undefined, String(await rowFor(privateCode)));
quiet.send("leave"); await settle();

// ---- The board -------------------------------------------------------------------------------
//
// Unlike a room, a board row outlives the run that wrote it -- that is the point of it -- and
// `wrangler dev` keeps its storage between invocations. So every identifier below is fresh per
// run: a suite that only passes against an empty table is a suite that passes once.

const MODE = pick(10, "ABCDEFGHIJKLMNOPQRSTUVWXYZ");
const EMPTY_MODE = pick(10, "ABCDEFGHIJKLMNOPQRSTUVWXYZ");
const post = (body) => fetch(`${BASE}/score`, { method: "POST", body });
const board = async (mode) => (await (await fetch(`${BASE}/top/${mode}`)).text()).trim();
const players = Array.from({ length: 5 }, () => crypto.randomUUID());

check("a first score comes back ranked first",
  (await (await post(`${players[0]},${MODE},ACE,5000`)).text()) === "1");
check("a better score outranks it",
  (await (await post(`${players[1]},${MODE},NOVA,9000`)).text()) === "1");
check("a worse score is ranked behind both",
  (await (await post(`${players[2]},${MODE},PIP,100`)).text()) === "3");

const rows = (await board(MODE)).split("\n");
check("the board comes back best first", rows[0] === "NOVA,9000,0", JSON.stringify(rows));
check("one row per player, not per run", rows.length === 3, JSON.stringify(rows));

await post(`${players[1]},${MODE},NOVA,10`);
check("a bad run does not cost a good one",
  (await board(MODE)).split("\n")[0] === "NOVA,9000,0", await board(MODE));

check("a mode keeps its own table", (await board(EMPTY_MODE)) === "");

const boardV2 = async (mode) => (await (await fetch(`${BASE}/top/${mode}?v=2`)).text()).trim();
await post(`${players[4]},${MODE},DIFF,7000,SUFFER`);
check("v2 rows carry the difficulty, blank for rows posted without one",
  (await boardV2(MODE)).split("\n").slice(0, 2).join("|") === "NOVA,9000,0,|DIFF,7000,0,SUFFER", await boardV2(MODE));
check("the old board shape is unchanged for old clients",
  (await board(MODE)).split("\n")[1] === "DIFF,7000,0", await board(MODE));
await post(`${players[4]},${MODE},DIFF,50,EASY`);
check("a worse run does not relabel the best one",
  (await boardV2(MODE)).split("\n")[1] === "DIFF,7000,0,SUFFER", await boardV2(MODE));
check("a difficulty that is not a preset name is refused",
  (await post(`${players[4]},${MODE},DIFF,8000,<b>`)).status === 400);
check("an absurd score is refused",
  (await post(`${players[3]},${MODE},CHEAT,999999999999`)).status === 400);
check("a negative score is refused", (await post(`${players[3]},${MODE},CHEAT,-5`)).status === 400);
check("a sync code cannot be posted as a player id",
  (await post(`ABCDEFGHJKLM,${MODE},X,10`)).status === 400);

await post(`${players[4]},${MODE},AC,ME,42`);
check("a name carrying the separator is refused rather than splitting a row",
  (await board(MODE)).split("\n").every((line) => line.split(",").length === 3), await board(MODE));

// ---- Profiles --------------------------------------------------------------------------------

const CODE = pick(12, "ABCDEFGHJKLMNPQRSTUVWXYZ23456789");
check("an unknown sync code has no profile", (await fetch(`${BASE}/save/${CODE}`)).status === 404);

const vault = (body) => fetch(`${BASE}/save/${CODE}`, { method: "PUT", body });
const held = async () => (await fetch(`${BASE}/save/${CODE}`)).text();

const profile = "1\nsaves|checkpoint|2,SOLO,UNDERCITY,3,1\npilots|player1|ACE";
check("a profile uploads", (await vault(profile)).status === 200);
check("and comes back verbatim", (await held()) === profile);

const replacement = "1\npilots|player1|NOVA";
await vault(replacement);
check("a second upload replaces the first", (await held()) === replacement);

check("a short code is refused", (await fetch(`${BASE}/save/ABCD`)).status === 400);
check("a room code is not a sync code", (await fetch(`${BASE}/save/${room}`)).status === 400);
check("an oversized profile is refused", (await vault("x".repeat(70000))).status === 413);
check("the profile survived the refused upload", (await held()) === replacement);

// ---- Recordings ------------------------------------------------------------------------------
//
// Runs against `wrangler dev` with VERIFY_SECRET=dev-secret in server/.dev.vars.

const VERIFY = { Authorization: "Bearer dev-secret" };
const runner = pick(16, "abcdef0123456789");
const REPLAY_MODE = pick(10, "ABCDEFGHIJKLMNOPQRSTUVWXYZ");
await fetch(`${BASE}/score`, { method: "POST", body: `${runner},${REPLAY_MODE},REC,5000` });
const bytes = new Uint8Array([31, 139, 8, 0, 1, 2, 3, 4]);
check("a recording for a score that is not the row's best is refused",
  (await fetch(`${BASE}/replay/${runner}/${REPLAY_MODE}?score=4000`, { method: "PUT", body: bytes })).status === 409);
check("a recording for the row's best is attached",
  (await fetch(`${BASE}/replay/${runner}/${REPLAY_MODE}?score=5000`, { method: "PUT", body: bytes })).status === 200);
check("the board marks the row unverified", (await (await fetch(`${BASE}/top/${REPLAY_MODE}`)).text()).trim() === "REC,5000,0");
check("the verifier's routes need the secret", (await fetch(`${BASE}/replays/pending`)).status === 401);
const pending = (await (await fetch(`${BASE}/replays/pending`, { headers: VERIFY })).text()).split("\n");
check("the recording is pending for the verifier", pending.includes(`${runner},${REPLAY_MODE},5000`), JSON.stringify(pending));
const fetched = new Uint8Array(await (await fetch(`${BASE}/replay/${runner}/${REPLAY_MODE}`, { headers: VERIFY })).arrayBuffer());
check("the verifier gets the bytes back verbatim", fetched.join() === bytes.join(), fetched.join());
check("a verdict is recorded", (await fetch(`${BASE}/verify/${runner}/${REPLAY_MODE}`, { method: "POST", headers: VERIFY, body: "ok" })).status === 200);
check("the board now shows the tick", (await (await fetch(`${BASE}/top/${REPLAY_MODE}`)).text()).trim() === "REC,5000,1");
check("a verified row is no longer pending",
  !(await (await fetch(`${BASE}/replays/pending`, { headers: VERIFY })).text()).includes(runner));
await fetch(`${BASE}/score`, { method: "POST", body: `${runner},${REPLAY_MODE},REC,6000` });
check("a new best drops the old verdict", (await (await fetch(`${BASE}/top/${REPLAY_MODE}`)).text()).trim() === "REC,6000,0");

const replayBoard = async () => (await (await fetch(`${BASE}/top/${REPLAY_MODE}`)).text()).trim();
const judge = (body) => fetch(`${BASE}/verify/${runner}/${REPLAY_MODE}`, { method: "POST", headers: VERIFY, body });
await fetch(`${BASE}/replay/${runner}/${REPLAY_MODE}?score=6000`, { method: "PUT", body: bytes });
await judge("skip");
check("an unverifiable recording leaves the row unmarked", (await replayBoard()) === "REC,6000,0", await replayBoard());
check("and is no longer pending",
  !(await (await fetch(`${BASE}/replays/pending`, { headers: VERIFY })).text()).includes(runner));
await fetch(`${BASE}/replay/${runner}/${REPLAY_MODE}?score=6000`, { method: "PUT", body: bytes });
await judge("bad");
await judge("skip");
check("skip lifts a cross", (await replayBoard()) === "REC,6000,0", await replayBoard());
await fetch(`${BASE}/replay/${runner}/${REPLAY_MODE}?score=6000`, { method: "PUT", body: bytes });
await judge("ok");
await judge("skip");
check("skip never takes a tick away", (await replayBoard()) === "REC,6000,1", await replayBoard());

console.log(failures ? `${failures} FAILED` : "all relay checks passed");
process.exit(failures ? 1 : 0);
