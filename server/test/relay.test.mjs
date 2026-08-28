const BASE = "http://127.0.0.1:8787";
let failures = 0;
const check = (name, ok, detail = "") => {
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}${ok ? "" : "  <- " + detail}`);
  if (!ok) failures++;
};
const open = (room) => new Promise((res, rej) => {
  const ws = new WebSocket(`ws://127.0.0.1:8787/room/${room}`);
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
    : { type: "roster", peers: f.slice(1).map(Number) };
};

const room = (await (await fetch(`${BASE}/new`)).text()).trim();
check("POST /new returns a 6-char room code", /^[A-HJ-NP-Z2-9]{6}$/.test(room), room);

const a = await open(room); await settle();
const b = await open(room); await settle();

check("first peer is told slot 1", a.control[0]?.type === "welcome" && a.control[0].slot === 1, String(a.control[0]?.slot));
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

b.close(); await settle();
check("departure updates the roster", a.control.at(-1)?.type === "roster" && a.control.at(-1).peers.join() === "1,3,4", JSON.stringify(a.control.at(-1)));

const e = await open(room); await settle();
check("the freed slot is reused", e.control[0]?.slot === 2, String(e.control[0]?.slot));

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
check("the board comes back best first", rows[0] === "NOVA,9000", JSON.stringify(rows));
check("one row per player, not per run", rows.length === 3, JSON.stringify(rows));

await post(`${players[1]},${MODE},NOVA,10`);
check("a bad run does not cost a good one",
  (await board(MODE)).split("\n")[0] === "NOVA,9000", await board(MODE));

check("a mode keeps its own table", (await board(EMPTY_MODE)) === "");
check("an absurd score is refused",
  (await post(`${players[3]},${MODE},CHEAT,999999999999`)).status === 400);
check("a negative score is refused", (await post(`${players[3]},${MODE},CHEAT,-5`)).status === 400);
check("a sync code cannot be posted as a player id",
  (await post(`ABCDEFGHJKLM,${MODE},X,10`)).status === 400);

await post(`${players[4]},${MODE},AC,ME,42`);
check("a name carrying the separator is refused rather than splitting a row",
  (await board(MODE)).split("\n").every((line) => line.split(",").length === 2), await board(MODE));

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
