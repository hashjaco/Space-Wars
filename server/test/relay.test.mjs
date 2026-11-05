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
const settle = () => new Promise((r) => setTimeout(r, 250));
const parseControl = (line) => {
  const f = line.split(",");
  return f[0] === "welcome"
    ? { type: "welcome", slot: +f[1], room: f[2], peers: f.slice(3).map(Number) }
    : f[0] === "token"
      ? { type: "token", token: f[1] }
      : { type: "roster", peers: f.slice(1).map(Number) };
};

check("GET /new is refused", (await fetch(`${BASE}/new`)).status === 405);
const room = (await (await fetch(`${BASE}/new`, { method: "POST" })).text()).trim();
check("POST /new returns a 6-char room code", /^[A-HJ-NP-Z2-9]{6}$/.test(room), room);
check("a code nobody was issued finds no room", (await fetch(`${BASE}/room/ZZZZZZ`)).status === 404);

const a = await open(room); await settle();
const b = await open(room); await settle();

check("first peer is told slot 1", a.control[0]?.type === "welcome" && a.control[0].slot === 1, String(a.control[0]?.slot));
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
check("departure updates the roster", a.control.at(-1)?.type === "roster" && a.control.at(-1).peers.join() === "1,3,4", JSON.stringify(a.control.at(-1)));

// The departed seat is held for its owner for a minute: with three present and one held, the room
// is full to a stranger, even one guessing at the seat, while the owner walks back in.
const strangerOpened = await open(room, "?slot=2&token=notthetoken").then(() => true, () => false);
check("a stranger cannot take the held seat", strangerOpened === false, "the stranger got in");
const back = await open(room, `?slot=2&token=${tokenB}`); await settle();
check("the owner reclaims the held seat with the token", back.control[0]?.slot === 2, String(back.control[0]?.slot));
check("reclaiming keeps the token", back.control.find((c) => c.type === "token")?.token === tokenB);
check("the roster shows the owner back", a.control.at(-1)?.type === "roster" && a.control.at(-1).peers.join() === "1,2,3,4", JSON.stringify(a.control.at(-1)));
back.close(); await settle();

const eOpened = await open(room).then(() => true, () => false);
check("a seat is held for its owner rather than reused at once", eOpened === false, "a newcomer took the held seat");
const e = { close() {} };

check("no upgrade header gets 426", (await fetch(`${BASE}/room/${room}`)).status === 426);

for (const ws of [a, c, d, e]) ws.close();

// ---- The board -------------------------------------------------------------------------------
//
// Unlike a room, a board row outlives the run that wrote it -- that is the point of it -- and
// `wrangler dev` keeps its storage between invocations. So every identifier below is fresh per
// run: a suite that only passes against an empty table is a suite that passes once.

const pick = (n, from) => Array.from({ length: n }, () => from[(Math.random() * from.length) | 0]).join("");
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

console.log(failures ? `\n${failures} FAILED` : "\nall relay checks passed");
process.exit(failures ? 1 : 0);

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

console.log(failures ? `${failures} FAILED` : "all relay checks passed");
process.exit(failures ? 1 : 0);
