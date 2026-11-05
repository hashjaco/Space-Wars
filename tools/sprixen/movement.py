#!/usr/bin/env python3
"""Does a music track move, or does it sit there? Scores an audio file three ways and says pass/fail.

    python3 tools/sprixen/movement.py <file> [<file>...]

Decodes with ffmpeg to 22.05 kHz mono, then:
  cov      coefficient of variation of the RMS envelope (250 ms windows): loudness that changes
  sections energy sections, counted where the smoothed envelope moves more than 3 dB: builds and drops
  onsets   spectral-flux peaks per second: rhythmic activity
Pass = cov >= 0.25 and sections >= 3 and onsets >= 1.5. Thresholds are the ones the plan set; the
numbers are relative, so compare tracks against each other and against the originals, not in a vacuum.
"""
import subprocess, sys
import numpy as np

SR = 22050

def decode(path):
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-ac", "1", "-ar", str(SR), "-f", "s16le", "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype=np.int16).astype(np.float32) / 32768.0

def score(x):
    win = SR // 4
    n = len(x) // win
    rms = np.sqrt(np.mean(x[: n * win].reshape(n, win) ** 2, axis=1)) + 1e-6
    cov = float(np.std(rms) / np.mean(rms))
    # Sections: smooth over 2 s, count sign changes of a >3 dB move.
    k = 8
    smooth = np.convolve(rms, np.ones(k) / k, mode="same")
    db = 20 * np.log10(smooth + 1e-6)
    sections, last = 1, db[0]
    for v in db:
        if abs(v - last) > 3:
            sections += 1; last = v
    # Onsets: spectral flux on 46 ms frames, peaks above the local median.
    frame, hop = 1024, 512
    frames = np.lib.stride_tricks.sliding_window_view(x, frame)[::hop] * np.hanning(frame)
    mag = np.abs(np.fft.rfft(frames, axis=1))
    flux = np.sum(np.maximum(mag[1:] - mag[:-1], 0), axis=1)
    med = np.convolve(flux, np.ones(16) / 16, mode="same")
    peaks = (flux[1:-1] > flux[:-2]) & (flux[1:-1] > flux[2:]) & (flux[1:-1] > 1.5 * med[1:-1] + 1e-6)
    onsets = float(np.sum(peaks) / (len(x) / SR))
    return cov, sections, onsets

if __name__ == "__main__":
    print(f"{'file':44} {'secs':>5} {'cov':>5} {'sect':>4} {'onset/s':>7}  verdict")
    for path in sys.argv[1:]:
        x = decode(path)
        cov, sections, onsets = score(x)
        ok = cov >= 0.25 and sections >= 3 and onsets >= 1.5
        print(f"{path.split('/')[-1]:44} {len(x)/SR:5.0f} {cov:5.2f} {sections:4d} {onsets:7.2f}  {'PASS' if ok else 'fail'}")
