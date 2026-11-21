# Space Case relay

Four Durable Objects, all of them dumb.

| class | one per | holds |
|---|---|---|
| `Room` | room code | what the group was opened for, if it is one; forwards opaque packets between two and four peers |
| `Groups` | the world | one row per public group that is open right now |
| `Board` | the world | one high score row per player per mode, and one helper row per player |
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
| `POST /new` | a fresh six-character room code, as plain text; other methods get `405` |
| `GET /room/{code}` with `Upgrade: websocket` | join; `409` when the room already holds four, `404` for a code `/new` never issued |
| `GET /room/{code}?slot=N&token=T` | rejoin a held seat inside its sixty-second window |
| `PUT /replay/{player}/{mode}?score=N` | attach a run's recording (gzip, under 1 MB) to the row it backs; `409` unless `N` is that row's best |
| `GET /replays/pending`, `GET /replay/{player}/{mode}`, `POST /verify/{player}/{mode}` | the verifier's routes, `Authorization: Bearer $VERIFY_SECRET`; `503` until that secret is set |

### Groups

A group is a room with a note on the door: what is being flown, how hard, and how many may come.

| | |
|---|---|
| `POST /group` body `visibility,max,galaxy,level,difficulty,name` | opens one and answers with its room code; `visibility` is `PUBLIC` or `PRIVATE`, `max` two to four, the rest are the client's enum names |
| `GET /groups?galaxy=&difficulty=` | up to fifty `code,host,players,max,galaxy,level,difficulty,status` lines, those with a free seat first; both filters optional; `status` is `LOBBY` or `PLAYING` |
| `GET /room/{code}?name=N&player=P` | join saying who you are, so the others see a name and the relay knows whose help to write down; both optional |
| `GET /helpers` | up to ten `name,points` lines: the pilots who have helped most |

A **private** group is never listed and is found only by its code, which is what every room was
before groups. A **public** one is listed from the moment its host connects until they are gone.
A listing is whatever the room last said, refreshed once a minute; a room that dies silently drops
off after two and a half.

Whoever opens a group is seated first, in slot one, and is its host. A group is its host's: when
they leave, or their held seat runs out, everyone is told `closed`, the listing comes down and the
code stops working. A room from `POST /new` has no host and behaves as it always did.

A peer talks to the relay itself in text frames, which are never forwarded:

```
leave                                    free this seat now, with no sixty-second hold
state,PLAYING,<level>,<slot>,<slot>...   host only: a level has begun with these seats flying
state,LOBBY                              host only: nobody is flying
cleared                                  host only: that level is won
```

**Help is reported by the room, not by the helper.** On `cleared`, the room adds 100 points to the
helpers' table for every connected peer the host named as flying, other than the host. There is no
route a client can post its own help to. It is still hearsay: the relay cannot tell a level that
was flown from one that was only announced, so the check is that the level lasted thirty seconds
(`MIN_LEVEL_MS`) and that each level can be cleared once. A host and a helper colluding earn an
assist every half minute, which is slower than playing.

### The board

| | |
|---|---|
| `POST /score` body `player,mode,name,score[,difficulty]` | records it; answers with the place, counting from one |
| `GET /top/{mode}` | up to ten `name,score,verified` lines, best first; `?v=2` adds the difficulty the score was set on as a fourth field |

`player` is the account's **public id**, never its sync code. One row per player per mode, and it
only ever moves up: a bad run does not cost somebody the good one they already posted.

**Scores are client-reported, then verified by replay.** Every score is computed on the machine
that flew the run, so the relay cannot know a number is honest when it arrives. What it can hold is
the run's recording: the seed, each level's terms and every tick's input, posted right after the
score. A scheduled job ([`.github/workflows/verify.yml`](../.github/workflows/verify.yml)) fetches
what is pending, replays it through the game's own simulation (`engine.Replayer`, the same tick order
the fight ran), and posts a verdict; the board shows a tick beside a score it reproduced and a cross
beside one it could not. A row without a recording stays unmarked. Runs resumed from a checkpoint
cannot be replayed from their seed and stay unmarked too. The `MAX_SCORE` ceiling remains as the
last line against nonsense.

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
token,<reconnect token>
roster,<slot>,<slot>...
names,<slot>,<name>,<slot>,<name>...
group,<visibility>,<max>,<galaxy>,<level>,<difficulty>,<status>
left,<slot>
closed
```

The last four arrived with groups. A `group` line is sent before `welcome`, so a client knows what
kind of room it is in by the time it is seated. A client older than them skips any line it does not
know, which is what let them be added without a version handshake.

A departed peer's seat is held for sixty seconds. Joining again as `/room/{code}?slot=N&token=T`
inside that window gives the same seat back, with the same token; the game uses it to rejoin a match
that is paused waiting for it (`net.RelayClient.reconnect`, `net.Lockstep.resync`). While a seat is
held it still counts toward the four, so a stranger cannot take it. Only codes handed out by
`POST /new` open a room: any other six characters answer `404`.

## Working on it

```sh
npm install
npm run dev      # local relay on :8787
npm test         # 92 checks against whatever is on :8787
npm run deploy
```

The tests want two settings on the local relay: the verifier's secret, and a short minimum level
so the helper checks need not wait half a minute. Either put `VERIFY_SECRET=dev-secret` and
`MIN_LEVEL_MS=1000` in `.dev.vars`, or start it with
`npx wrangler dev --var VERIFY_SECRET:dev-secret --var MIN_LEVEL_MS:1000`.

`npm test` also runs against production, sockets and all (it then waits out the real thirty
seconds once):

```sh
RELAY_BASE=https://space-case-relay.hashimjacobs.workers.dev MIN_LEVEL_MS=30000 npm test
```

Every identifier it uses is fresh per run -- a room code from `/new`, a UUID per board row, a random
sync code, a random galaxy name for its groups -- so running it against the live worker leaves
nothing behind that anybody will meet, apart from one row of 100 on the helpers' table.

The Java side has its own integration tests, which **skip** when nothing answers so CI stays green
without wrangler. `OnlineGroupTest` is the whole story over real sockets: open a group, find it on
the board, join mid-run, be seated at the next level, fly it in step, be credited, leave.

```sh
cd .. && ./mvnw test -Dtest='RelayIntegrationTest,OnlineGroupTest' -Dsurefire.failIfNoSpecifiedTests=false \
  -Drelay.base=https://space-case-relay.hashimjacobs.workers.dev -Drelay.minLevelMs=30000
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
gain by finding one. A listing on the board lasts only as long as somebody holds a socket open in
its room, and shows nothing of theirs but a pilot name cut down to ten letters and digits. The board is capped at one row per player per mode, so flooding it costs a
distinct account per row. If any of it ever attracts attention, Cloudflare's own rate limiting on
`/new` and `/score` is the first move, not application code.

**No paid content and no licence check.** That was considered and belongs to a storefront — Steam,
itch — rather than here. A DRM check in a client whose source ships with it is a speed bump, and
adding accounts to enforce one would make every offline player log in to fly a single-player game.
