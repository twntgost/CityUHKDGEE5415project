#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成 UI 材质素材与配套音效（无外部版权素材，全部程序化合成）。

产物（app/src/main/assets/）：
  textures/kraft.png      牛皮纸（可平铺 512）
  textures/tyvek.png      杜邦纸（可平铺 512）
  textures/grain.png      细颗粒叠加层（RGBA）
  ink/circle_1..5.png     5 种手绘红笔圈
  stamps/stamp_pushed.png 做旧「推送成功」方章
  audio/dossier_open.wav  解开牛皮纸档案袋
  audio/page_flip.wav     翻页
  audio/pen_stroke.wav    笔在纸上划动
  audio/stamp.wav         盖章
"""
import math, os, struct, wave
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = "app/src/main/assets"
SR = 22050
rng = np.random.default_rng(20261111)

def out(*p):
    path = os.path.join(ROOT, *p)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    return path

# ---------------------------------------------------------------- 纸纹理
def fbm(shape, octaves=5, seed=0):
    r = np.random.default_rng(seed)
    acc = np.zeros(shape); amp = 0.5; total = 0
    for o in range(octaves):
        h = max(2, shape[0] >> (octaves - o - 1))
        w = max(2, shape[1] >> (octaves - o - 1))
        base = r.random((h, w))
        img = np.array(Image.fromarray((base*255).astype(np.uint8)).resize((shape[1], shape[0]), Image.BICUBIC))/255.0
        acc += img*amp; total += amp; amp *= 0.5
    return acc/total

def kraft(size=512):
    n = fbm((size, size), 5, 11)
    fine = np.random.default_rng(12).random((size, size))
    # 纸纤维：横向拉长的细纹
    fib = np.array(Image.fromarray((fine*255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.6)))/255.0
    h = 24 + 10*fbm((size, size), 3, 13)
    rgb = np.zeros((size, size, 3))
    base = np.array([196, 164, 116]) / 255.0     # 牛皮纸底色
    grain = (n - 0.5) * 0.10 + (fib - 0.5) * 0.05
    for i in range(3):
        rgb[..., i] = base[i] + grain * (1.0 if i == 0 else 0.95 if i == 1 else 0.85)
    # 零星深色纤维点
    speck = np.random.default_rng(14).random((size, size))
    rgb[speck > 0.996] *= 0.72
    img = Image.fromarray(np.clip(rgb*255, 0, 255).astype(np.uint8))
    img = img.filter(ImageFilter.GaussianBlur(0.35))
    img.save(out("textures", "kraft.png"))

def tyvek(size=512):
    n = fbm((size, size), 5, 21)
    fine = np.random.default_rng(22).random((size, size))
    # 杜邦纸：细密随机纤维，比牛皮纸更白更细
    rgb = np.zeros((size, size, 3))
    base = np.array([238, 233, 222]) / 255.0
    grain = (n - 0.5) * 0.055 + (fine - 0.5) * 0.035
    for i in range(3):
        rgb[..., i] = base[i] + grain
    rgb[fine > 0.995] *= 0.90
    img = Image.fromarray(np.clip(rgb*255, 0, 255).astype(np.uint8))
    img = img.filter(ImageFilter.GaussianBlur(0.45))
    img.save(out("textures", "tyvek.png"))

def grain(size=256):
    n = np.random.default_rng(31).random((size, size))
    a = (n*26).astype(np.uint8)
    rgba = np.zeros((size, size, 4), np.uint8)
    rgba[..., 0:3] = 40; rgba[..., 3] = a
    Image.fromarray(rgba).save(out("textures", "grain.png"))

# ---------------------------------------------------------------- 手绘红笔圈
INK = (176, 42, 32)

def wobble_ellipse(seed, W=360, H=210, cx=None, cy=None, rx=None, ry=None,
                   thick=(7, 13), turns=1.12, jitter=0.035):
    r = np.random.default_rng(seed)
    cx = cx or W/2 + r.uniform(-8, 8); cy = cy or H/2 + r.uniform(-8, 8)
    rx = rx or W*0.40 + r.uniform(-6, 8); ry = ry or H*0.33 + r.uniform(-5, 6)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    n = 260
    start = r.uniform(-0.25, 0.05)
    end = turns*2*math.pi + r.uniform(-0.15, 0.35)
    pts = []
    for i in range(n):
        t = start + (end-start)*i/(n-1)
        w = 1 + jitter*math.sin(3.1*t + r.uniform(0, 2)) + jitter*0.7*math.sin(7.3*t)
        x = cx + rx*w*math.cos(t) + r.uniform(-1.2, 1.2)
        y = cy + ry*w*math.sin(t) + r.uniform(-1.2, 1.2)
        # 起笔/收笔变细
        f = i/(n-1)
        taper = min(1.0, 0.35 + 3.2*min(f, 1-f))
        pts.append((x, y, max(2.0, (thick[0] + (thick[1]-thick[0])*r.random()) * taper)))
    for x, y, w in pts:
        d.ellipse([x-w/2, y-w/2, x+w/2, y+w/2], fill=INK+(235,))
    # 轻微墨迹不均
    arr = np.array(img).astype(np.int16)
    noise = np.random.default_rng(seed+100).random(arr.shape[:2])
    arr[..., 3] = np.clip(arr[..., 3] * (0.78 + 0.30*noise), 0, 255)
    img = Image.fromarray(arr.astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.4))
    return img

def circles():
    for i in range(1, 6):
        wobble_ellipse(seed=100+i, thick=(6+i % 3, 11+i % 4), turns=1.05+0.06*i).save(out("ink", f"circle_{i}.png"))

# ---------------------------------------------------------------- 做旧方章
def stamp(size=440):
    S = size
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    m = int(S*0.09)
    # 双线方框，抖动手绘感
    r = np.random.default_rng(77)
    def rough_rect(pad, width):
        pts = []
        for side in range(4):
            for k in range(40):
                f = k/39
                if side == 0:   x, y = pad + (S-2*pad)*f, pad
                elif side == 1: x, y = S-pad, pad + (S-2*pad)*f
                elif side == 2: x, y = S-pad - (S-2*pad)*f, S-pad
                else:           x, y = pad, S-pad - (S-2*pad)*f
                pts.append((x + r.uniform(-1.6, 1.6), y + r.uniform(-1.6, 1.6)))
        for k in range(len(pts)):
            x, y = pts[k]
            d.ellipse([x-width/2, y-width/2, x+width/2, y+width/2], fill=INK+(238,))
    rough_rect(m, S*0.030)
    rough_rect(m + S*0.055, S*0.014)

    # 2×2 四字
    try:
        font = ImageFont.truetype("/System/Library/Fonts/Songti.ttc", int(S*0.27), index=0)
    except Exception:
        font = ImageFont.truetype("/System/Library/Fonts/STHeiti Medium.ttc", int(S*0.27), index=0)
    chars = "推送成功"
    cell = (S - 2*(m + S*0.10)) / 2
    x0 = m + S*0.10; y0 = m + S*0.10
    for i, ch in enumerate(chars):
        cx = x0 + cell*((i % 2) + 0.5); cy = y0 + cell*((i // 2) + 0.5)
        bb = d.textbbox((0, 0), ch, font=font)
        d.text((cx-(bb[2]-bb[0])/2 - bb[0], cy-(bb[3]-bb[1])/2 - bb[1]), ch, font=font, fill=INK+(240,))

    # 做旧：噪点腐蚀 + 缺墨斑
    arr = np.array(img).astype(np.int16)
    pad_n = 34
    noise = fbm((S+2*pad_n, S+2*pad_n), 5, 91)
    noise = noise[pad_n:pad_n+S, pad_n:pad_n+S]
    mask = np.clip((noise - 0.34) * 2.6, 0, 1)          # 局部缺墨
    speck = np.random.default_rng(92).random((S, S))
    mask *= (speck > 0.06)                              # 细碎白点
    arr[..., 3] = np.clip(arr[..., 3] * (0.35 + 0.75*mask), 0, 255)
    # 边缘磨损
    edge = np.zeros((S, S)); edge[:6, :] = 1; edge[-6:, :] = 1; edge[:, :6] = 1; edge[:, -6:] = 1
    for _ in range(3):
        edge = np.maximum(edge, np.roll(edge, 1, 0)*0.7)
    arr[..., 3] = np.clip(arr[..., 3] * (1 - 0.55*edge), 0, 255)
    Image.fromarray(arr.astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.5)).save(out("stamps", "stamp_pushed.png"))

# ---------------------------------------------------------------- 音效
def save_wav(rel, x, sr=SR):
    x = np.asarray(x, dtype=np.float64)
    k = max(1, int(sr*0.004))
    x = x.copy(); x[:k] *= np.linspace(0, 1, k); x[-k:] *= np.linspace(1, 0, k)
    pcm = (np.clip(x, -1, 1)*32767).astype("<i2")
    path = out(*rel)
    with wave.open(path, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr)
        w.writeframes(pcm.tobytes())

def bandnoise(n, lo, hi, seed):
    r = np.random.default_rng(seed)
    spec = np.fft.rfft(r.normal(0, 1, n))
    freqs = np.fft.rfftfreq(n, 1/SR)
    spec[(freqs < lo) | (freqs > hi)] = 0
    y = np.fft.irfft(spec, n)
    return y / (np.abs(y).max()+1e-9)

def dossier_open():
    """解开牛皮纸档案袋：纸的窸窣 + 绳扣轻响 + 抽出纸的摩擦"""
    dur = 0.95; n = int(SR*dur); t = np.arange(n)/SR
    crinkle = bandnoise(n, 900, 6500, 3) * (0.5 + 0.5*np.sin(2*np.pi*11*t)) * np.exp(-3.0*t)
    crinkle *= (np.random.default_rng(4).random(n) > 0.55)
    crinkle = np.convolve(crinkle, np.ones(9)/9, mode="same")
    slide = bandnoise(n, 400, 2600, 5) * np.exp(-0.9*(t-0.28).clip(0)**2*40) * 0.35
    untie = 0.5*np.sin(2*np.pi*520*t)*np.exp(-22*(t-0.06).clip(0)**2*20)*(t > 0.06)
    mix = 0.42*crinkle + slide + 0.18*untie
    save_wav(("audio", "dossier_open.wav"), mix)

def page_flip():
    """翻页：极短（~160ms）的纸面掠过"""
    dur = 0.16; n = int(SR*dur); t = np.arange(n)/SR
    y = bandnoise(n, 1500, 9000, 7)
    env = np.exp(-9*t) * np.minimum(1, t/0.012)
    y = y*env*0.55
    y += 0.12*np.sin(2*np.pi*(1800-9000*t)*t)*np.exp(-26*t)
    save_wav(("audio", "page_flip.wav"), y)

def pen_stroke():
    """笔在纸上划动：带颗粒的摩擦声，随时间起伏"""
    dur = 0.62; n = int(SR*dur); t = np.arange(n)/SR
    grain = bandnoise(n, 1800, 8000, 9)
    am = 0.35 + 0.65*np.abs(np.sin(2*np.pi*3.4*t + 0.6*np.sin(2*np.pi*1.7*t)))
    y = grain*am*np.exp(-2.4*t)*0.6
    y += 0.06*np.sin(2*np.pi*140*t)*np.exp(-6*t)
    save_wav(("audio", "pen_stroke.wav"), y)

def stamp_sound():
    """盖章：低频闷响 + 纸张冲击"""
    dur = 0.38; n = int(SR*dur); t = np.arange(n)/SR
    thump = 0.75*np.sin(2*np.pi*(120*np.exp(-9*t)+58)*t)*np.exp(-16*t)
    slap = bandnoise(n, 300, 5200, 11)*np.exp(-40*t)*0.5
    tail = bandnoise(n, 200, 1400, 12)*np.exp(-5*t)*0.08
    save_wav(("audio", "stamp.wav"), thump+slap+tail)

if __name__ == "__main__":
    kraft(); tyvek(); grain(); circles(); stamp()
    dossier_open(); page_flip(); pen_stroke(); stamp_sound()
    import pathlib
    for p in ["textures","ink","stamps"]:
        files = list(pathlib.Path(ROOT, p).glob("*"))
        print(f"{p}: {len(files)} 个  " + ", ".join(f"{f.name}({f.stat().st_size//1024}KB)" for f in files))
    for f in ["dossier_open","page_flip","pen_stroke","stamp"]:
        fp = pathlib.Path(ROOT, "audio", f+".wav")
        print(f"audio/{f}.wav {fp.stat().st_size//1024}KB")
