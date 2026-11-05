#!/usr/bin/env python3
"""Runs the slow half of the CG pipeline unattended: submits premium loops for every picked still
that lacks one (backing off on Sprixen's rate limit), and imports each loop when it completes.

    nohup python3 tools/sprixen/cg_driver.py > tools/sprixen/out/cg/driver.log 2>&1 &

Picking stays a human (or reviewing) step: the driver never chooses a variant. It exits when every
picked slug is imported, or after 18 hours.
"""
import importlib.util, json, os, sys, time

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("cg", os.path.join(HERE, "cg.py"))
cg = importlib.util.module_from_spec(spec); sys.argv = ["cg"]; spec.loader.exec_module(cg)

def log(*a):
    print(time.strftime("%H:%M:%S"), *a, flush=True)

deadline = time.time() + 18 * 3600
while time.time() < deadline:
    state = cg.load()
    picked = [s for s, v in state.items() if "pick" in v and "sprites" in v]  # variants downloaded
    for slug in picked:
        v = state[slug]
        if "loop" not in v:
            try:
                cg.cmd_loop([slug]); log("loop submitted", slug)
            except SystemExit as refused:
                log("loop deferred", slug, str(refused)[:120]); break
        elif not v.get("imported"):
            time.sleep(3)  # Sprixen allows 60 requests a minute across everything on this key
            try:
                a = cg.rest("GET", f"/v1/animations/{v['loop']}")
            except SystemExit as e:
                log("poll failed", slug, str(e)[:120]); continue
            if a.get("status") == "completed":
                cg.cmd_import([slug]); log("imported", slug)
            elif a.get("status") in ("failed", "error"):
                log("loop FAILED", slug, a.get("error")); state = cg.load(); state[slug].pop("loop", None); cg.save(state)
    state = cg.load()
    if picked and all(state[s].get("imported") for s in picked) and not any("pick" not in v and "sprites" in v for v in state.values()):
        log("all picked slugs imported"); break
    time.sleep(180)
log("driver exit")
