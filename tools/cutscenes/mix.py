#!/usr/bin/env python3
"""Builds each cutscene's audio and timeline from its script, the voice takes, the effects and the score.

    python3 tools/cutscenes/mix.py [scene...]          # build; default every scene in script/
    python3 tools/cutscenes/mix.py --shots [scene...]  # print the shot list with each shot's length
    python3 tools/cutscenes/mix.py --talk [scene...]   # write lip-sync audio for the talk shots

Inputs (raw takes are not committed; see voices.json for who speaks them):
    script/<scene>.txt        the screenplay, see below
    out/<scene>-NN.mp3        one ElevenLabs take per line, NN counting lines in script order
    out/sfx/<name>.mp3        effects named by @sfx
    out/score/<scene>.wav     the scene's Suno score

Script:
    @shot N action: <prompt>          a moving shot, image2video from N.jpg at the shot's length
    @shot N talk: SPEAKER [portrait=x]  a lip-synced close-up of SPEAKER, who says every line in it
    @shot N still                     the still alone, drifting
    @pause S                          S seconds of silence; a shot cut before any line opens on it
    @sfx name [+/-offset] [gain]      an effect, placed relative to the next line's start
    SPEAKER[ (radio)]: line

Outputs, read by scene.Cutscene:
    src/main/resources/cutscenes/<scene>/scene.mp3   dialogue and effects over the ducked score
    src/main/resources/cutscenes/<scene>/timeline.txt
        S <t> <shot>                  shot <shot> starts at t seconds
        L <t> <dur> <SPEAKER> <text>  a line starts at t and is spoken for dur seconds
        E <t>                         the scene ends
    out/talk/<scene>-<shot>.wav       (--talk) the shot's lines on the shot's own clock, for lip sync

The treatments are offline so the game plays one finished file per scene: radio lines get a comms
band-pass, Aeon is pitched down under a slow echo, and every line is loudness-matched first.
Levels: dialogue at -16 LUFS, effects about -22, the score normalized to -23 and ducked under each line.
"""
import os, re, subprocess, sys, tempfile
from dataclasses import dataclass, field

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(ROOT, "src/main/resources/cutscenes")
RATE = 44100

LEAD_IN = 1.6      # score alone before the first line
GAP = 0.45         # between consecutive lines
SHOT_LEAD = 0.5    # a shot cuts in this long before its first line; a talk clip's audio starts with this much silence
TAIL = 3.0         # score after the last line

def ff(*args):
    p = subprocess.run(["ffmpeg", "-v", "error", "-y", *args], capture_output=True, text=True)
    if p.returncode:
        raise SystemExit(p.stderr[-800:])

def duration(path):
    p = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path],
                       capture_output=True, text=True)
    return float(p.stdout.strip())

@dataclass
class Shot:
    number: int
    kind: str                 # action | talk | still
    detail: str = ""          # the motion prompt, or "SPEAKER portrait=x"
    start: float = None
    lines: list = field(default_factory=list)

@dataclass
class Line:
    index: int
    speaker: str
    radio: bool
    text: str
    shot: Shot
    start: float = 0.0
    length: float = 0.0
    wav: str = ""

def parse(scene):
    """[items] in script order: Shot, ("pause", s), ("sfx", name, offset, gain), Line."""
    items, n = [], 0
    for raw in open(os.path.join(HERE, "script", scene + ".txt")):
        line = raw.strip()
        if m := re.match(r"@shot (\d+)\s*(action|talk|still)?:?\s*(.*)$", line):
            items.append(Shot(int(m.group(1)), m.group(2) or "still", m.group(3)))
        elif m := re.match(r"@pause ([\d.]+)", line):
            items.append(("pause", float(m.group(1))))
        elif m := re.match(r"@sfx (\S+)(?:\s+([+-][\d.]+))?(?:\s+([\d.]+))?", line):
            items.append(("sfx", m.group(1), float(m.group(2) or 0), float(m.group(3) or 1)))
        elif m := re.match(r"^([A-Z]+)( \(radio\))?: (.*)$", line):
            n += 1
            shot = next(i for i in reversed(items) if isinstance(i, Shot))
            items.append(Line(n, m.group(1), bool(m.group(2)), m.group(3), shot))
    return items

def treat(src, dst, speaker, radio):
    # loudnorm resamples to 192 kHz internally; back to RATE first, or asetrate below reads the wrong rate.
    chain = ["loudnorm=I=-16:TP=-1.5:LRA=11", f"aresample={RATE}"]
    if speaker == "AEON":
        chain += [f"asetrate={RATE}*0.84", f"aresample={RATE}", "atempo=1.19",
                  "aecho=0.8:0.6:90|170:0.35|0.22", "lowpass=f=6000"]
    elif speaker == "EMPRESS":
        chain += ["aecho=0.8:0.5:60:0.18"]
    if radio:
        chain += ["highpass=f=320", "lowpass=f=3300", "acompressor=threshold=0.1:ratio=4", "volume=1.4"]
    ff("-i", src, "-af", ",".join(chain) + f",aresample={RATE}", "-ac", "1", dst)

def lay_out(scene, items, tmp):
    """Runs the script's clock: every shot's cut, every line's start, every effect's start. Returns the end."""
    t, first, pending, sfx_wait, sfx = LEAD_IN, True, None, [], []
    for item in items:
        if isinstance(item, Shot):
            pending = item
        elif isinstance(item, tuple) and item[0] == "pause":
            if pending is not None:          # a shot that opens before anyone speaks
                pending.start, pending = (0.0 if first else t), None
            t += item[1]
        elif isinstance(item, tuple) and item[0] == "sfx":
            sfx_wait.append(item)
        else:
            line = item
            take = os.path.join(HERE, "out", f"{scene}-{line.index:02d}.mp3")
            line.wav = os.path.join(tmp, f"{line.index:02d}.wav")
            treat(take, line.wav, line.speaker, line.radio)
            line.length = duration(line.wav)
            t += 0 if first else GAP
            if pending is not None:
                pending.start, pending = (0.0 if first else max(0.0, t - SHOT_LEAD)), None
            line.start = t
            line.shot.lines.append(line)
            for _, name, offset, gain in sfx_wait:
                sfx.append((name, max(0.0, t + offset), gain))
            sfx_wait, first = [], False
            t += line.length
    for _, name, offset, gain in sfx_wait:
        sfx.append((name, max(0.0, t + offset), gain))
    return t + TAIL, sfx

def shots_of(items):
    return [i for i in items if isinstance(i, Shot)]

def lines_of(items):
    return [i for i in items if isinstance(i, Line)]

def mix(scene):
    items = parse(scene)
    tmp = tempfile.mkdtemp(prefix="mix-")
    end, sfx = lay_out(scene, items, tmp)
    shots, lines = shots_of(items), lines_of(items)
    rows = []
    for s in shots:
        rows.append((s.start, f"S {s.start:.2f} {s.number}"))
    for l in lines:
        rows.append((l.start + 0.001, f"L {l.start:.2f} {l.length:.2f} {l.speaker} {l.text}"))
    rows = [r for _, r in sorted(rows)] + [f"E {end:.2f}"]

    inputs, pads, k = [], [], 0
    for l in lines:
        inputs += ["-i", l.wav]
        ms = int(l.start * 1000)
        pads.append(f"[{k}:a]adelay={ms}|{ms},apad[d{k}]")
        k += 1
    voice = "".join(f"[d{i}]" for i in range(k)) + f"amix=inputs={k}:normalize=0:duration=longest,atrim=0:{end:.2f}[voice]"
    nodes = pads + [voice]
    fx_labels = []
    for name, at, gain in sfx:
        inputs += ["-i", os.path.join(HERE, "out", "sfx", name + ".mp3")]
        ms = int(at * 1000)
        nodes.append(f"[{k}:a]loudnorm=I=-22:TP=-3,aresample={RATE},volume={gain},adelay={ms}|{ms},apad[f{k}]")
        fx_labels.append(f"[f{k}]")
        k += 1
    score = os.path.join(HERE, "out", "score", scene + ".wav")
    inputs += ["-stream_loop", "-1", "-i", score]
    nodes.append(f"[{k}:a]atrim=0:{end:.2f},asetpts=PTS-STARTPTS,loudnorm=I=-23:TP=-2:LRA=11,aresample={RATE},"
                 f"afade=t=in:d=1.2,afade=t=out:st={end - 2.6:.2f}:d=2.6[music]")
    nodes += ["[voice]asplit=2[vkey][vmix]",
              "[music][vkey]sidechaincompress=threshold=0.02:ratio=8:attack=30:release=500:makeup=1[ducked]"]
    if fx_labels:
        nodes.append("".join(fx_labels) + f"amix=inputs={len(fx_labels)}:normalize=0:duration=longest,"
                     f"atrim=0:{end:.2f}[fx]")
        nodes.append("[ducked][fx][vmix]amix=inputs=3:normalize=0:duration=first,alimiter=limit=0.95[out]")
    else:
        nodes.append("[ducked][vmix]amix=inputs=2:normalize=0:duration=first,alimiter=limit=0.95[out]")
    dest = os.path.join(OUT, scene)
    ff(*inputs, "-filter_complex", ";".join(nodes), "-map", "[out]", "-ac", "2", "-c:a", "libmp3lame",
       "-b:a", "96k", os.path.join(dest, "scene.mp3"))
    open(os.path.join(dest, "timeline.txt"), "w").write("\n".join(rows) + "\n")
    print(f"{scene}: {len(shots)} shots, {len(lines)} lines, {len(sfx)} effects, {end:.1f}s")
    check_clips(scene, shots, end)

def lengths(shots, end):
    ordered = sorted(shots, key=lambda s: s.start)
    return {s.number: (ordered[i + 1].start if i + 1 < len(ordered) else end) - s.start
            for i, s in enumerate(ordered)}

def check_clips(scene, shots, end):
    """Flags any clip that would run out before its shot does -- the cause of the old 'looping'."""
    for number, length in sorted(lengths(shots, end).items()):
        clip = os.path.join(OUT, scene, f"{number}.mp4")
        if os.path.exists(clip):
            have = duration(clip)
            if have < length - 0.3:
                print(f"  SHORT  {scene} shot {number}: clip {have:.1f}s for a {length:.1f}s shot")

def show_shots(scene):
    items = parse(scene)
    end, _ = lay_out(scene, items, tempfile.mkdtemp(prefix="mix-"))
    span = lengths(shots_of(items), end)
    for s in shots_of(items):
        clip = os.path.join(OUT, scene, f"{s.number}.mp4")
        have = f"{duration(clip):.1f}s clip" if os.path.exists(clip) else "no clip"
        print(f"{scene}\t{s.number}\t{s.kind}\t{span[s.number]:.2f}\t{have}\t{s.detail}")

def talk(scene):
    """Each talk shot's own lines, on the shot's clock: SHOT_LEAD of silence, then the voice, gaps kept."""
    items = parse(scene)
    tmp = tempfile.mkdtemp(prefix="talk-")
    end, _ = lay_out(scene, items, tmp)
    span = lengths(shots_of(items), end)
    os.makedirs(os.path.join(HERE, "out", "talk"), exist_ok=True)
    for s in shots_of(items):
        if s.kind != "talk":
            continue
        who = s.detail.split()[0]
        mine = [l for l in s.lines if l.speaker == who]
        if len(mine) != len(s.lines):
            print(f"  NOTE  {scene} shot {s.number}: lines by others are left out of the lip sync")
        inputs, pads = [], []
        for k, l in enumerate(mine):
            # The clean take, not the radio-treated one: lip sync reads phonemes, and the comms
            # filter only goes on the scene mix.
            clean = os.path.join(tmp, f"clean-{l.index}.wav")
            ff("-i", os.path.join(HERE, "out", f"{scene}-{l.index:02d}.mp3"), "-af",
               f"loudnorm=I=-16:TP=-1.5,aresample={RATE}", "-ac", "1", clean)
            inputs += ["-i", clean]
            ms = int((l.start - s.start) * 1000)
            pads.append(f"[{k}:a]adelay={ms}|{ms},apad[d{k}]")
        n = len(mine)
        graph = ";".join(pads + ["".join(f"[d{k}]" for k in range(n)) +
                                 f"amix=inputs={n}:normalize=0,atrim=0:{span[s.number]:.2f}[out]"])
        dest = os.path.join(HERE, "out", "talk", f"{scene}-{s.number}.wav")
        ff(*inputs, "-filter_complex", graph, "-map", "[out]", "-ac", "1", dest)
        print(f"{dest}  {span[s.number]:.2f}s  {who}")

if __name__ == "__main__":
    args = sys.argv[1:]
    mode = args.pop(0) if args and args[0].startswith("--") else "--build"
    scenes = args or sorted(f[:-4] for f in os.listdir(os.path.join(HERE, "script")))
    for scene in scenes:
        {"--build": mix, "--shots": show_shots, "--talk": talk}[mode](scene)
