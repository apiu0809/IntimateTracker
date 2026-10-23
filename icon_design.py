"""生成 Intimate Tracker 的几款应用图标候选（512x512）与对比图。"""

import math
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = "icon-design"
os.makedirs(OUT, exist_ok=True)
S = 1024  # 超采样尺寸，最后缩小到 512

BG = (18, 18, 18, 255)
GREEN = (133, 184, 152, 255)
GREEN_HI = (178, 219, 194, 255)
GOLD = (232, 183, 90, 255)
PINK = (214, 120, 142, 255)
WHITE = (245, 247, 245, 255)


def heart_points(cx, cy, scale, steps=240):
    pts = []
    for i in range(steps):
        t = 2 * math.pi * i / steps
        x = 16 * math.sin(t) ** 3
        y = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
        pts.append((cx + x * scale, cy - y * scale))
    return pts


def rounded_mask(size, radius):
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    return mask


def base_canvas(bg=BG, radius=230):
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    bg_layer = Image.new("RGBA", (S, S), bg)
    img.paste(bg_layer, (0, 0), rounded_mask(S, radius))
    return img


def subtle_grid(img, color=(255, 255, 255, 14), step=128, inset=150):
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for x in range(inset, S - inset, step):
        d.line([(x, 0), (x, S)], fill=color, width=2)
    for y in range(inset, S - inset, step):
        d.line([(0, y), (S, y)], fill=color, width=2)
    img.alpha_composite(Image.composite(layer, Image.new("RGBA", (S, S), (0, 0, 0, 0)), rounded_mask(S, 230)))


def glow(img, shape_points, color, blur=60, alpha=110):
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.polygon(shape_points, fill=(color[0], color[1], color[2], alpha))
    layer = layer.filter(ImageFilter.GaussianBlur(blur))
    img.alpha_composite(layer)


# ---------- 1. 爱心 + 日历点阵 ----------
def icon_heart_calendar():
    img = base_canvas()
    subtle_grid(img)
    pts = heart_points(S / 2, S / 2 + 40, 26)
    glow(img, pts, GREEN, blur=70, alpha=90)
    d = ImageDraw.Draw(img)
    d.polygon(pts, fill=GREEN)
    # 心形内部 3x2 日期格（日历意象）
    for row in range(2):
        for col in range(3):
            cx = S / 2 + (col - 1) * 150
            cy = S / 2 + 80 + row * 130
            d.rounded_rectangle([cx - 52, cy - 46, cx + 52, cy + 46], radius=16, fill=BG)
    return img


# ---------- 2. 心形描边 + 星 ----------
def icon_heart_outline():
    img = base_canvas()
    pts = heart_points(S / 2, S / 2 + 30, 27)
    glow(img, pts, GREEN, blur=55, alpha=70)
    d = ImageDraw.Draw(img)
    d.line(pts + [pts[0]], fill=GREEN_HI, width=46, joint="curve")
    # 中心小星
    cx, cy, r = S / 2, S / 2 + 60, 86
    star = []
    for i in range(10):
        ang = -math.pi / 2 + i * math.pi / 5
        rr = r if i % 2 == 0 else r * 0.45
        star.append((cx + rr * math.cos(ang), cy + rr * math.sin(ang)))
    d.polygon(star, fill=GOLD)
    return img


# ---------- 3. 绿底深色心 ----------
def icon_green_heart():
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    bg = Image.new("RGBA", (S, S), GREEN)
    img.paste(bg, (0, 0), rounded_mask(S, 230))
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.ellipse([-200, -520, S + 200, 620], fill=(255, 255, 255, 26))
    img.alpha_composite(layer)
    d = ImageDraw.Draw(img)
    pts = heart_points(S / 2, S / 2 + 30, 26)
    d.polygon(pts, fill=BG)
    return img


# ---------- 4. 双环 ----------
def icon_rings():
    img = base_canvas()
    d = ImageDraw.Draw(img)
    r = 240
    offset = 145
    left = [S / 2 - r - offset, S / 2 - r, S / 2 + r - offset, S / 2 + r]
    right = [S / 2 - r + offset, S / 2 - r, S / 2 + r + offset, S / 2 + r]
    d.ellipse(left, outline=GREEN, width=50)
    d.ellipse(right, outline=GOLD, width=50)
    # 交集透镜区域内放一颗心（先铺底色再画心，形成负空间）
    lens = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ld = ImageDraw.Draw(lens)
    ld.ellipse(left, fill=(255, 255, 255, 255))
    ld.ellipse(right, fill=(255, 255, 255, 255))
    # 仅保留两圆交集
    inter = Image.new("L", (S, S), 0)
    idd = ImageDraw.Draw(inter)
    idd.ellipse(left, fill=255)
    inner = Image.new("L", (S, S), 0)
    ImageDraw.Draw(inner).ellipse(right, fill=255)
    from PIL import ImageChops
    inter = ImageChops.multiply(inter, inner)
    bg_layer = Image.new("RGBA", (S, S), BG)
    img.paste(bg_layer, (0, 0), inter)
    d = ImageDraw.Draw(img)
    pts = heart_points(S / 2, S / 2 + 20, 9)
    d.polygon(pts, fill=PINK)
    return img


# ---------- 5. 心 + 锁孔 ----------
def icon_lock_heart():
    img = base_canvas()
    pts = heart_points(S / 2, S / 2 + 20, 26)
    glow(img, pts, GREEN, blur=60, alpha=80)
    d = ImageDraw.Draw(img)
    d.polygon(pts, fill=GREEN)
    cx, cy = S / 2, S / 2 + 30
    d.ellipse([cx - 78, cy - 150, cx + 78, cy + 6], fill=BG)
    d.polygon([(cx - 32, cy - 10), (cx + 32, cy - 10), (cx + 52, cy + 150), (cx - 52, cy + 150)], fill=BG)
    return img



# ---------- 7. 日历 + 心 ----------
def icon_calendar_heart():
    img = base_canvas()
    d = ImageDraw.Draw(img)
    frame = [S / 2 - 330, S / 2 - 330, S / 2 + 330, S / 2 + 330]
    d.rounded_rectangle(frame, radius=90, outline=GREEN_HI, width=34)
    # 顶部装订线
    d.line([S / 2 - 330, S / 2 - 200, S / 2 + 330, S / 2 - 200], fill=GREEN_HI, width=30)
    d.line([S / 2 - 170, S / 2 - 390, S / 2 - 170, S / 2 - 250], fill=GREEN_HI, width=34)
    d.line([S / 2 + 170, S / 2 - 390, S / 2 + 170, S / 2 - 250], fill=GREEN_HI, width=34)
    pts = heart_points(S / 2, S / 2 + 110, 15)
    glow(img, pts, PINK, blur=50, alpha=90)
    d.polygon(pts, fill=PINK)
    return img
# ---------- 6. 心跳线 ----------
def icon_pulse():
    img = base_canvas()
    pts = heart_points(S / 2, S / 2 + 20, 26)
    glow(img, pts, (214, 120, 142), blur=70, alpha=70)
    d = ImageDraw.Draw(img)
    d.polygon(pts, fill=(214, 120, 142))
    line = [
        (250, 540), (400, 540), (450, 440), (520, 660), (590, 380), (650, 540), (780, 540),
    ]
    d.line(line, fill=WHITE, width=34, joint="curve")
    return img


DESIGNS = [
    ("1-heart-calendar", icon_heart_calendar),
    ("2-heart-outline", icon_heart_outline),
    ("3-green-heart", icon_green_heart),
    ("4-rings", icon_rings),
    ("5-lock-heart", icon_lock_heart),
    ("6-pulse", icon_pulse),
    ("7-calendar-heart", icon_calendar_heart),
]

made = []
for name, fn in DESIGNS:
    img = fn().resize((512, 512), Image.LANCZOS)
    path = os.path.join(OUT, f"{name}.png")
    img.save(path)
    made.append((name, path))
    print("saved", path)

# 对比图
try:
    font = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 26)
except Exception:
    font = ImageFont.load_default()

cell = 300
pad = 34
cols = 4
rows = (len(made) + cols - 1) // cols
sheet = Image.new("RGB", (cols * (cell + pad) + pad, rows * (cell + pad + 46) + pad), (24, 24, 24))
sd = ImageDraw.Draw(sheet)
for idx, (name, path) in enumerate(made):
    r, c = divmod(idx, cols)
    x = pad + c * (cell + pad)
    y = pad + r * (cell + pad + 46)
    icon = Image.open(path).convert("RGBA").resize((cell, cell), Image.LANCZOS)
    sheet.paste(icon, (x, y), icon)
    sd.text((x, y + cell + 10), name, fill=(210, 210, 210), font=font)

sheet_path = os.path.join(OUT, "preview.png")
sheet.save(sheet_path)
print("saved", sheet_path)
