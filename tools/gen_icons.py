#!/usr/bin/env python3
"""由 aquarius_logo.png 生成全套 Android 图标资源。

- 提取蓝色波浪图形的 alpha 遮罩（原图背景为近白 #FAFAF8）
- 生成 adaptive icon 前景（432×432，图形占画布约 60%，落在 72dp 安全区内）
- 生成 monochrome 单色图层（白色剪影，Android 13 主题图标用）
- 生成 legacy mipmap（mdpi..xxxhdpi 方形 + 圆形）
"""
from PIL import Image, ImageDraw

SRC = "aquarius_logo.png"
BRAND = (6, 100, 222)          # 图形主色，取自原图中心
BG = (255, 255, 255)

src = Image.open(SRC).convert("RGBA")
W, H = src.size
px = src.load()

# 1) 用红通道反推 alpha（背景近白，图形近 0）
mask = Image.new("L", (W, H), 0)
mp = mask.load()
for y in range(H):
    for x in range(W):
        r, g, b, _ = px[x, y]
        a = (250 - r) / 238.0
        if g > 200 and b > 200 and r > 200:   # 背景杂色直接归零
            a = 0.0
        mp[x, y] = max(0, min(255, int(a * 255)))

bbox = mask.getbbox()
mask = mask.crop(bbox)
gw, gh = mask.size
print("glyph bbox", bbox, "size", mask.size)


def glyph(color, canvas, ratio, bg=None, circle=False, radius_ratio=0.22):
    """把字形按 ratio 缩放到 canvas 画布中央，返回 RGBA 图。"""
    img = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    if bg is not None:
        d = ImageDraw.Draw(img)
        if circle:
            d.ellipse([0, 0, canvas - 1, canvas - 1], fill=bg)
        else:
            r = int(canvas * radius_ratio)
            d.rounded_rectangle([0, 0, canvas - 1, canvas - 1], radius=r, fill=bg)
    side = int(canvas * ratio)
    scale = side / max(gw, gh)
    nw, nh = max(1, int(gw * scale)), max(1, int(gh * scale))
    g = Image.new("RGBA", (nw, nh), color + (0,))
    g.putalpha(mask.resize((nw, nh), Image.LANCZOS))
    img.alpha_composite(g, ((canvas - nw) // 2, (canvas - nh) // 2))
    return img


def save(img, path):
    img.save(path, "PNG", optimize=True)
    print("wrote", path, img.size)


# 2) adaptive foreground：108dp 画布，安全区 72dp → 图形取 60%
save(glyph(BRAND, 432, 0.60), "app/src/main/res/drawable/ic_launcher_foreground.png")
save(glyph(BRAND, 432, 0.60), "feature/settings/src/main/res/drawable/ic_launcher_foreground.png")

# 3) monochrome：白色剪影
save(glyph((255, 255, 255), 432, 0.60), "app/src/main/res/drawable/ic_launcher_monochrome.png")
save(glyph((255, 255, 255), 432, 0.60), "feature/settings/src/main/res/drawable/ic_launcher_monochrome.png")

# 4) legacy mipmap：方形（白底 + 圆角）与圆形（白底圆）
DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}
for density, size in DENSITIES.items():
    save(glyph(BRAND, size, 0.66, bg=BG), f"app/src/main/res/mipmap-{density}/ic_launcher.png")
    save(glyph(BRAND, size, 0.60, bg=BG, circle=True), f"app/src/main/res/mipmap-{density}/ic_launcher_round.png")

# 5) 应用内使用的大图（关于页/欢迎页可直接引用）
img = Image.new("RGBA", (432, 432), BG + (0,))
img.alpha_composite(glyph(BRAND, 432, 0.78))
save(img, "app/src/main/res/drawable/aquarius_brand_logo.png")
save(img, "feature/settings/src/main/res/drawable/aquarius_brand_logo.png")
