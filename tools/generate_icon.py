"""生成「亲密记录」应用图标（用户选定的黑色日历 + 心形）。

素材：icon-design/app-icon-source.png（取自图标合集第一排第二个）。
从素材中提取白色图形（含抗锯齿），重新合成到精确尺寸的圆角底上：
  - mipmap-*/ic_launcher(.round).png    启动图标（各密度）
  - drawable-xxxhdpi/ic_launcher_foreground.png  自适应图标前景（108dp 画布，内容占可见区 55%）
  - drawable-nodpi/ic_brand_mark.png    关于页品牌标记
  - icon-design/preview-v4.png          预览

运行： python tools/generate_icon.py
"""

import os

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
SRC = os.path.join(ROOT, "icon-design", "app-icon-source.png")
CORNER_RADIUS = 0.225


def load_source():
    return Image.open(SRC).convert("RGB")


def extract(tile):
    """返回 (背景色, 内容 bbox, 白形 alpha 数组)。"""
    arr = np.asarray(tile).astype(np.float32)
    lum = arr.mean(axis=2)
    h, w = lum.shape

    dark = lum < 80
    bg = np.median(arr[dark], axis=0)
    bg_lum = float(bg.mean())

    # 从四角泛洪，标记圆角方块之外的白色背景（fromarray 只读，先 copy）
    flood = Image.fromarray(((lum > 200) * 255).astype("uint8")).copy()
    for seed in ((0, 0), (flood.width - 1, 0), (0, flood.height - 1), (flood.width - 1, flood.height - 1)):
        ImageDraw.floodfill(flood, seed, 128, thresh=12)
    outside = np.array(flood) == 128

    alpha = np.clip((lum - bg_lum) / (248.0 - bg_lum), 0, 1)
    alpha[outside] = 0

    # 素材方块本身有一条很淡的内描边，会被当成白色图形；只保留强白（日历）周边区域
    strong = (lum > 235) & ~outside
    ys, xs = np.where(strong)
    pad = 8
    x0 = max(0, int(xs.min()) - pad)
    x1 = min(w, int(xs.max()) + 1 + pad)
    y0 = max(0, int(ys.min()) - pad)
    y1 = min(h, int(ys.max()) + 1 + pad)
    keep = np.zeros_like(alpha, dtype=bool)
    keep[y0:y1, x0:x1] = True
    alpha[~keep] = 0
    alpha[alpha < 0.04] = 0

    ys, xs = np.where(alpha > 0.5)
    bbox = (int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1)
    return bg, bbox, alpha


def content_layer(tile, bbox, alpha):
    """白色图形 RGBA（裁到内容 bbox）。"""
    x0, y0, x1, y1 = bbox
    a = (alpha[y0:y1, x0:x1] * 255).astype("uint8")
    white = np.full((y1 - y0, x1 - x0, 3), 248, dtype="uint8")
    return Image.fromarray(np.dstack([white, a]), "RGBA")


def render_icon(size, bg, bbox, layer, tile_size):
    """实心圆角底 + 按原比例放置的白色图形。"""
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(canvas)
    d.rounded_rectangle(
        [0, 0, size - 1, size - 1],
        radius=size * CORNER_RADIUS,
        fill=tuple(int(v) for v in bg) + (255,),
    )

    tw, th = tile_size
    content_w = (bbox[2] - bbox[0]) / tw * size
    content_h = (bbox[3] - bbox[1]) / th * size
    cx = (bbox[0] + bbox[2]) / 2 / tw * size
    cy = (bbox[1] + bbox[3]) / 2 / th * size

    scaled = layer.resize((max(1, round(content_w)), max(1, round(content_h))), Image.LANCZOS)
    canvas.alpha_composite(scaled, (round(cx - content_w / 2), round(cy - content_h / 2)))
    return canvas


def render_foreground(size, bbox, layer, tile_size):
    """自适应图标前景：108dp 画布中，内容宽度占可见区（72dp）的 55%。"""
    tw, th = tile_size
    content_frac = (bbox[2] - bbox[0]) / tw  # ≈0.55
    target_w = content_frac * (72.0 / 108.0) * size
    target_h = target_w * (bbox[3] - bbox[1]) / (bbox[2] - bbox[0])

    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    scaled = layer.resize((round(target_w), round(target_h)), Image.LANCZOS)
    cx = size / 2
    cy = size / 2 + ((bbox[1] + bbox[3]) / 2 / th - 0.5) * (72.0 / 108.0) * size
    canvas.alpha_composite(scaled, (round(cx - target_w / 2), round(cy - target_h / 2)))
    return canvas


def save(img, rel_path):
    path = os.path.join(RES, rel_path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def main():
    tile = load_source()
    tile_size = tile.size
    bg, bbox, alpha = extract(tile)
    layer = content_layer(tile, bbox, alpha)

    for density, px in {
        "mdpi": 48,
        "hdpi": 72,
        "xhdpi": 96,
        "xxhdpi": 144,
        "xxxhdpi": 192,
    }.items():
        icon = render_icon(px, bg, bbox, layer, tile_size)
        save(icon, os.path.join("mipmap-" + density, "ic_launcher.png"))
        save(icon, os.path.join("mipmap-" + density, "ic_launcher_round.png"))

    save(render_foreground(432, bbox, layer, tile_size), os.path.join("drawable-xxxhdpi", "ic_launcher_foreground.png"))
    save(render_icon(355, bg, bbox, layer, tile_size), os.path.join("drawable-nodpi", "ic_brand_mark.png"))

    preview = os.path.join(ROOT, "icon-design", "preview-v4.png")
    render_icon(512, bg, bbox, layer, tile_size).save(preview)
    print("generated:", preview, "bg=", tuple(int(v) for v in bg))


if __name__ == "__main__":
    main()
