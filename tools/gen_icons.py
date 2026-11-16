#!/usr/bin/env python3
"""由 aquarius_logo_smaller.png 生成全套 Android 图标资源。

2.4.0 起图标源图从 `aquarius_logo.png` 换成 `aquarius_logo_smaller.png`：
同一套蓝色波浪字形，但画布更大、四周留白更多（字形宽度只占画布 60.4%），
缩到桌面图标 / 关于页品牌区时观感更轻、更干净。

- 提取蓝色波浪图形的 alpha 遮罩（原图背景为近白）
- adaptive icon 前景：432×432，字形宽度占画布 60.4%（与源图同构图）
- monochrome 单色图层（白色剪影，Android 13 主题图标用）
- legacy mipmap（mdpi..xxxhdpi 方形 + 圆形）
- 应用内品牌图 aquarius_brand_logo.png（关于页头部 / 主页品牌区）

用法（仓库根目录）：python3 tools/gen_icons.py
"""
from PIL import Image, ImageDraw

SRC = "aquarius_logo_smaller.png"
BRAND = (6, 100, 222)          # 图形主色，取自原图
BG = (255, 255, 255)
# 字形宽度占画布比例：
# - 图标（adaptive / 圆形）取 0.50，字形完整落在 72dp 安全区内，不再贴边；
# - 方形 legacy 图标略大一点（0.54），贴近旧版方形图标的观感；
# - 应用内品牌图沿用 smaller 源图自己的构图（0.604）。
RATIO_ICON = 0.50
RATIO_LEGACY_SQUARE = 0.54
RATIO_BRAND = 0.604

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
    """把字形按 ratio（宽度占画布）缩放到画布中央，返回 RGBA 图。"""
    img = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    if bg is not None:
        d = ImageDraw.Draw(img)
        if circle:
            d.ellipse([0, 0, canvas - 1, canvas - 1], fill=bg)
        else:
            r = int(canvas * radius_ratio)
            d.rounded_rectangle([0, 0, canvas - 1, canvas - 1], radius=r, fill=bg)
    target_w = max(1, int(canvas * ratio))
    target_h = max(1, int(gh * target_w / gw))
    g = Image.new("RGBA", (target_w, target_h), color + (0,))
    g.putalpha(mask.resize((target_w, target_h), Image.LANCZOS))
    img.alpha_composite(g, ((canvas - target_w) // 2, (canvas - target_h) // 2))
    return img


def save(img, path):
    img.save(path, "PNG", optimize=True)
    print("wrote", path, img.size)


# 2) adaptive foreground（108dp 画布）+ 单色层
save(glyph(BRAND, 432, RATIO_ICON), "app/src/main/res/drawable/ic_launcher_foreground.png")
save(glyph(BRAND, 432, RATIO_ICON), "feature/settings/src/main/res/drawable/ic_launcher_foreground.png")
save(glyph((255, 255, 255), 432, RATIO_ICON), "app/src/main/res/drawable/ic_launcher_monochrome.png")
save(glyph((255, 255, 255), 432, RATIO_ICON), "feature/settings/src/main/res/drawable/ic_launcher_monochrome.png")

# 3) legacy mipmap：方形（白底圆角）与圆形（白底圆）
DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}
for density, size in DENSITIES.items():
    save(glyph(BRAND, size, RATIO_LEGACY_SQUARE, bg=BG), f"app/src/main/res/mipmap-{density}/ic_launcher.png")
    save(glyph(BRAND, size, RATIO_ICON, bg=BG, circle=True), f"app/src/main/res/mipmap-{density}/ic_launcher_round.png")

# 4) 应用内品牌图：与 smaller 源图同一构图
img = Image.new("RGBA", (512, 512), BG + (0,))
img.alpha_composite(glyph(BRAND, 512, RATIO_BRAND))
save(img, "app/src/main/res/drawable/aquarius_brand_logo.png")
save(img, "feature/settings/src/main/res/drawable/aquarius_brand_logo.png")
img.convert("RGB").save("logo.png", "PNG", optimize=True)
print("wrote logo.png", img.size)
