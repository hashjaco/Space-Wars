#!/usr/bin/env python3
"""Tiny client for the Sprixen MCP server (https://api.sprixen.com/v1/mcp), stdlib only.

Reads the bearer key from ~/.claude.json (the same entry Claude Code's MCP config uses) and never
prints it. Usage:

    sprixen.py tools                         # list tool names
    sprixen.py credits                       # remaining credits
    sprixen.py call <tool> '<json-args>'     # call one tool, print its text result
    sprixen.py download <url> <out-file>     # authenticated GET
    sprixen.py wait <generation-id>...       # poll get_generation until every job is done, print results
    sprixen.py rest <METHOD> <path> ['<json>']  # plain REST call (no MCP session), e.g. rest GET /v1/generations/<id>

The MCP session id is cached in ~/.cache/sprixen-session and reused, because the server rate-limits
session creation; a rejected session is re-initialised once. 429s back off and retry.
"""
import json, os, sys, time, urllib.error, urllib.request

MCP = "https://api.sprixen.com/v1/mcp"
CACHE = os.path.expanduser("~/.cache/sprixen-session")


def key():
    cfg = json.load(open(os.path.expanduser("~/.claude.json")))
    for proj in cfg.get("projects", {}).values():
        auth = proj.get("mcpServers", {}).get("sprixen", {}).get("headers", {}).get("Authorization")
        if auth:
            return auth
    sys.exit("no sprixen key in ~/.claude.json")


def post(body, session=None):
    req = urllib.request.Request(MCP, data=json.dumps(body).encode(), method="POST", headers={
        "Authorization": key(), "Content-Type": "application/json", "Accept": "application/json, text/event-stream",
        **({"Mcp-Session-Id": session} if session else {}),
    })
    for attempt in range(6):
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
                sid = r.headers.get("Mcp-Session-Id")
                raw = r.read().decode()
            break
        except urllib.error.HTTPError as e:
            if e.code == 429 and attempt < 5:
                time.sleep(5 * (attempt + 1))
                continue
            if e.code in (400, 404) and session:
                raise StaleSession()
            sys.exit(f"HTTP {e.code}: {e.read().decode()[:300]}")
    data = None
    for line in raw.splitlines():  # SSE or plain JSON
        if line.startswith("data:"):
            data = line[5:].strip()
    return json.loads(data or raw or "{}"), sid


class StaleSession(Exception):
    pass


def session(fresh=False):
    if not fresh and os.path.exists(CACHE) and time.time() - os.path.getmtime(CACHE) < 3600:
        return open(CACHE).read().strip()
    _, sid = post({"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {
        "protocolVersion": "2025-03-26", "capabilities": {}, "clientInfo": {"name": "space-case-tools", "version": "1"}}})
    post({"jsonrpc": "2.0", "method": "notifications/initialized"}, sid)
    os.makedirs(os.path.dirname(CACHE), exist_ok=True)
    open(CACHE, "w").write(sid or "")
    return sid


def rpc(body):
    try:
        res, _ = post(body, session())
    except StaleSession:
        res, _ = post(body, session(fresh=True))
    return res


def call(tool, args):
    res = rpc({"jsonrpc": "2.0", "id": 2, "method": "tools/call", "params": {"name": tool, "arguments": args}})
    if "error" in res:
        sys.exit(f"error: {res['error']}")
    out = res["result"]
    texts = [c.get("text", "") for c in out.get("content", []) if c.get("type") == "text"]
    text = "\n".join(texts)
    if out.get("isError"):
        sys.exit(f"tool error: {text}")
    return text


def rest(method, path, body=None):
    req = urllib.request.Request("https://api.sprixen.com" + path, data=json.dumps(body).encode() if body is not None else None,
                                 method=method, headers={"Authorization": key(), "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            return r.read().decode()
    except urllib.error.HTTPError as e:
        sys.exit(f"HTTP {e.code}: {e.read().decode()[:400]}")


def main(argv):
    cmd = argv[1] if len(argv) > 1 else "help"
    if cmd == "tools":
        res = rpc({"jsonrpc": "2.0", "id": 3, "method": "tools/list"})
        print("\n".join(t["name"] for t in res["result"]["tools"]))
    elif cmd == "wait":
        pending = list(argv[2:])
        for _ in range(60):
            for gid in list(pending):
                text = rest("GET", "/v1/generations/" + gid)
                status = json.loads(text).get("status", "?")
                if status in ("completed", "failed", "error"):
                    print(gid, status); print(text); pending.remove(gid)
            if not pending:
                break
            time.sleep(15)
        if pending:
            sys.exit(f"still pending: {pending}")
    elif cmd == "credits":
        print(call("get_credits", {}))
    elif cmd == "call":
        print(call(argv[2], json.loads(argv[3]) if len(argv) > 3 else {}))
    elif cmd == "rest":
        print(rest(argv[2], argv[3], json.loads(argv[4]) if len(argv) > 4 else None))
    elif cmd == "download":
        req = urllib.request.Request(argv[2], headers={"Authorization": key()})
        with urllib.request.urlopen(req, timeout=300) as r, open(argv[3], "wb") as f:
            f.write(r.read())
        print(argv[3], os.path.getsize(argv[3]), "bytes")
    else:
        print(__doc__)


if __name__ == "__main__":
    main(sys.argv)
