# Space Case relay

Three Durable Objects, all of them dumb.

| class | one per | holds |
|---|---|---|
| `Room` | room code | nothing; forwards opaque packets between two and four peers |
| `Board` | the world | one high score row per player per mode |
| `Vault` | sync code | one player's profile, as an opaque string |

`Room` holds **no game logic** — every device simulates the whole game and only input crosses the
wire, so there is no authoritative state here to get out of step with anyone. `Board` and `Vault`
are the opposite: storage is the whole point of both, which is why they are separate classes rather
than more routes on the room, whose disposability is load-bearing.

Live at `https://space-case-relay.hashimjacobs.workers.dev`.

## Protocol

Plain text in, plain text out, everywhere. The Java client has no JSON parser and does not need one.

### Rooms

| | |
|---|---|
| `POST /new` | a fresh six-character room code, as plain text |
| `GET /room/{code}` with `Upgrade: websocket` | join; `409` when the room already holds four |

### The board

| | |
|---|---|
| `POST /score` body `player,mode,name,score` | records it; answers with the place, counting from one |
| `GET /top/{mode}` | up to ten `name,score` lines, best first |

`player` is the account's **public id**, never its sync code. One row per player per mode, and it
only ever moves up: a bad run does not cost somebody the good one they already posted.

**The board is client-reported and unverifiable by construction.** Every score in this game is
computed on the machine that flew the run, so a determined player can post any number they like. No
login would change that — an authenticated liar is still a liar; closing it for real means the
server simulating the game, which is the host-authoritative design the netcode deliberately is not.
The `MAX_SCORE` ceiling only stops a bored one parking `Number.MAX_SAFE_INTEGER` at the top forever.

### Profiles

| | |
|---|---|
| `PUT /save/{sync code}` | stores the body, replacing what was there; `413` over 64 KiB |
| `GET /save/{sync code}` | the profile, or `404` if nothing was ever uploaded |

A sync code is twelve characters of the room alphabet — sixty bits — and the client picks its own.
It is a **capability, not an identity**: whoever holds it holds the profile, which is the right
shape for something read off your own screen and typed into your own laptop, and the wrong shape
for anything to be read out loud. The body is opaque here; its format is `prefs.Profile`'s business.

**Text frames are the relay talking. Binary frames are the game talking.** The relay never forwards
a client's text, so no peer can impersonate it, and it never parses binary, so it cannot grow
opinions about the game.

Control frames follow the same comma-separated idiom as `SaveSlot.encode()` — the Java client has no
JSON parser and does not need one:

```
welcome,<your slot>,<room>,<slot>,<slot>...
roster,<slot>,<slot>...
```

## Working on it

```sh
npm install
npm run dev      # local relay on :8787
npm test         # 31 checks against whatever is on :8787
npm run deploy
```

`npm test` also runs against production: point it at the deployed URL instead of localhost.

The Java side has its own integration test, which **skips** when nothing answers so CI stays green
without wrangler:

```sh
cd .. && ./mvnw test -Dtest=RelayIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Drelay.base=https://space-case-relay.hashimjacobs.workers.dev
```

## Measured

Round trip peer → relay → peer, 60 samples from one machine in one place, so treat it as the shape
rather than as what two players in two cities will see:

| median | p90 | p99 |
|---|---|---|
| 19.8 ms | 22.9 ms | 41.7 ms |

`Lockstep.INPUT_DELAY_TICKS` is 3, which is 50 ms of budget.

## Deliberately not here

**No authentication provider.** The game has no users to manage: a run is played by whoever is at
the keyboard, and the two things that genuinely needed identity — keeping one player to one board
row, and letting a player reach their own saves from a second machine — are a random UUID and a
random capability code, both minted locally on first run. Clerk and the like solve a problem this
game does not have, at the price of an OAuth device flow and refresh-token storage in a desktop
JavaFX app with no browser in it.

**No rate limit.** A room code is six characters from a 32-character alphabet, a room holds four
people, and one packet in becomes at most three out — so there is no amplification and little to
gain by finding one. The board is capped at one row per player per mode, so flooding it costs a
distinct account per row. If any of it ever attracts attention, Cloudflare's own rate limiting on
`/new` and `/score` is the first move, not application code.

**No paid content and no licence check.** That was considered and belongs to a storefront — Steam,
itch — rather than here. A DRM check in a client whose source ships with it is a speed bump, and
adding accounts to enforce one would make every offline player log in to fly a single-player game.
