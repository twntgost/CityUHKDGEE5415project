#!/usr/bin/env python3
"""合成 48 个占位音效（WAV / 16-bit 单声道 / 22050Hz）。

项目没有音效素材，也没有 ffmpeg/oggenc 可用。这里用 numpy 合成一组**克制、
不刺耳、彼此可区分**的短音效，作为正式素材到位前的占位实现：
  - 简历确认池：male/female 用不同音区，accept 明亮、refuse 低沉；
  - 竞猜：开注＝筹码轻响，赢＝上行琶音，输＝下行闷响；
  - 结局：Good＝大调解决，Health＝高频悬停，Bad＝低音下行。

换成真实素材时：把 .ogg 放进同一路径，并改 SoundCatalog 的 EXT 常量即可。
"""
import math, os, struct, wave
import numpy as np

SR = 22050
OUT = "app/src/main/assets/audio"

def env(n, attack=0.01, release=0.12):
    a = int(n * attack) if attack < 1 else int(attack)
    r = int(n * release) if release < 1 else int(release)
    e = np.ones(n)
    if a > 0: e[:a] = np.linspace(0, 1, a)
    if r > 0: e[-r:] = np.linspace(1, 0, r)
    return e

def fade(x, ms=6):
    k = int(SR * ms / 1000)
    if len(x) > 2 * k:
        x[:k] *= np.linspace(0, 1, k)
        x[-k:] *= np.linspace(1, 0, k)
    return x

def pluck(freq, dur, amp=0.5, bright=0.35, decay=9.0):
    n = int(SR * dur); t = np.arange(n) / SR
    x = np.sin(2*np.pi*freq*t) + bright*np.sin(2*np.pi*freq*2*t) + 0.15*bright*np.sin(2*np.pi*freq*3*t)
    x *= np.exp(-decay*t) * env(n, 0.004, 0.25)
    return amp * x

def thud(freq=140, dur=0.20, amp=0.55):
    n = int(SR*dur); t = np.arange(n)/SR
    f = freq * np.linspace(1.0, 0.55, n)
    x = np.sin(2*np.pi*np.cumsum(f)/SR) * np.exp(-22*t)
    noise = np.random.default_rng(7).normal(0, 0.25, n) * np.exp(-40*t)
    return amp * fade(x + noise)

def coin(t0=0.0, f=2600, dur=0.09, amp=0.30):
    n = int(SR*dur); t = np.arange(n)/SR
    x = np.sin(2*np.pi*f*t) + 0.6*np.sin(2*np.pi*f*1.5*t)
    return amp * x * np.exp(-30*t)

def seq(parts, total=None):
    """parts: [(offset_sec, signal), ...]"""
    end = max(o + len(s) for o, s in parts)
    buf = np.zeros(int(SR*end)+1)
    for o, s in parts:
        i = int(SR*o); buf[i:i+len(s)] += s
    if total: buf = buf[:int(SR*total)]
    return buf

def pad(freqs, dur, amp=0.28, vib=0.0):
    n = int(SR*dur); t = np.arange(n)/SR
    x = np.zeros(n)
    for i, f in enumerate(freqs):
        detune = 1 + 0.0015*i
        mod = 1 + (vib*np.sin(2*np.pi*5*t) if vib else 0)
        x += np.sin(2*np.pi*f*detune*mod*t)
    x /= max(1, len(freqs))
    return amp * x * env(n, 0.18, 0.45)

def save(rel, x):
    path = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    x = np.clip(fade(np.asarray(x)), -1, 1)
    pcm = (x * 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    return path

# 五声音阶，保证变体之间和谐、不刺耳
PENTA = [261.63, 293.66, 329.63, 392.00, 440.00, 523.25, 587.33, 659.25]
MALE, FEMALE = 0.86, 1.18          # 音区偏移：男声偏低、女声偏高
rng = np.random.default_rng(20261102)

made = []
# ---------- 简历确认池：accept 明亮 / refuse 低沉 ----------
for i in range(1, 13):
    root = PENTA[i % len(PENTA)] * MALE
    made.append(save(f"resume_sound/male_accept{i}.wav",
                     seq([(0, pluck(root, 0.30, 0.42, bright=0.5)),
                          (0.055, pluck(root*1.5, 0.26, 0.22, bright=0.4))], 0.40)))
for i in range(1, 7):
    root = PENTA[(i+2) % len(PENTA)] * FEMALE
    made.append(save(f"resume_sound/female_accept{i}.wav",
                     seq([(0, pluck(root, 0.30, 0.40, bright=0.55)),
                          (0.05, pluck(root*1.5, 0.26, 0.20, bright=0.45))], 0.40)))
for i in range(1, 12):
    made.append(save(f"resume_sound/male_refuse{i}.wav",
                     thud(120 + (i % 4)*18, 0.22, 0.50)))
for i in range(1, 7):
    made.append(save(f"resume_sound/female_refuse{i}.wav",
                     thud(168 + (i % 4)*16, 0.20, 0.44)))

# ---------- 竞猜 ----------
for i, name in enumerate(["gambol_start", "gambol_start2", "gambol_start3", "gambol_start4", "gambol_start5"]):
    f = 2400 + i*160
    made.append(save(f"gambol_sound/{name}.wav",
                     seq([(0, coin(0, f)), (0.07, coin(0, f*1.12, 0.07, 0.24))], 0.30)))
for i, name in enumerate(["gambol_win", "gambol_win2", "gambol_win3"]):
    base = 261.63 * (1 + 0.06*i)
    parts = [(k*0.075, pluck(base*[1, 1.25, 1.5, 2][k], 0.32, 0.34, bright=0.5)) for k in range(4)]
    made.append(save(f"gambol_sound/{name}.wav", seq(parts, 0.62)))
for i, name in enumerate(["gambol_lose", "gambol_lose2"]):
    base = 196.0 * (1 - 0.05*i)
    parts = [(0, thud(150, 0.28, 0.45)),
             (0.16, pluck(base*0.75, 0.40, 0.30, bright=0.2, decay=6)),
             (0.20, pluck(base*0.56, 0.44, 0.26, bright=0.15, decay=5))]
    made.append(save(f"gambol_sound/{name}.wav", seq(parts, 0.70)))

# ---------- 结局 ----------
# Good：C 大调解决，温暖上行
made.append(save("game_GoodEnding.wav",
                 seq([(0, pad([261.63, 329.63, 392.00], 1.9, 0.22)),
                      (0.18, pad([329.63, 415.30, 493.88], 1.7, 0.18)),
                      (0.40, pad([523.25, 659.25, 783.99], 1.5, 0.16))], 2.4)))
# Health：高频悬停 + 轻微拍频，诡异
made.append(save("game_HealthEnding.wav",
                 seq([(0, pad([880.0, 883.0], 2.3, 0.16, vib=0.004)),
                      (0.6, pad([1318.5, 1322.0], 1.6, 0.08))], 2.4)))
# Bad：低音下行，压抑
made.append(save("game_BadEnding.wav",
                 seq([(0, pad([130.81, 155.56, 196.00], 1.2, 0.22)),
                      (0.7, pad([110.00, 130.81, 164.81], 1.4, 0.20)),
                      (1.5, pad([87.31, 103.83], 1.1, 0.18))], 2.6)))

print(f"生成 {len(made)} 个文件")
total = sum(os.path.getsize(p) for p in made)
print(f"总体积 {total/1024:.0f} KB")
