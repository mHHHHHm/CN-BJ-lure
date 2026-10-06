# -*- coding: utf-8 -*-
"""生成「蓝色大肥鱼」App 图标。

图标用的是社区表情包 403「你还能有 DeepSeek 聪明？」里的 Q 版鲸鱼娘：
蓝色长发、女仆裙、头上一圈褶边，举着一块印着 DeepSeek 鲸鱼 logo 的牌子 ——
和 App 里那只大肥鱼是同一个形象，够正统。

底部那行台词会被裁掉：图标最大才 192px，一行字缩下去只会糊成一团黑。

有个坑要注意：角色身上的女仆裙和手里的牌子**本身就是白色**，
所以不能无脑「把白色变透明」—— 那样裙子会被一起抠掉。
正确做法是从四个角漫水填充，只抠掉与画布边缘连通的那片背景白；
裙子、牌子都被黑色轮廓包着，跟外部不连通，自然就保住了。
"""
import os

from PIL import Image, ImageDraw, ImageFont

BASE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(os.path.dirname(BASE), "app", "src", "main", "res")

# 表情包图源放在 tools/emoji/ 下，用相对路径 ——
# 之前这里写的是本机桌面的绝对路径，别人 clone 下来根本跑不了这个脚本。
# 换图只要把新图丢进 emoji/ 并改这两个文件名即可。
EMOJI_DIR = os.path.join(BASE, "emoji")
SRC = os.path.join(EMOJI_DIR, "403_tagline.webp")        # 说明页：举牌子 + 台词
SPLASH_SRC = os.path.join(EMOJI_DIR, "501_moyu.webp")    # 图标 & 启动画面：抱鲸鱼抱枕

SS = 4  # 超采样倍数

# 与 App 主题一致的底色
BG_TOP = (232, 243, 254, 255)
BG_BOT = (188, 219, 247, 255)

# 漫水填充用的哨兵色，抠完立刻转透明
SENTINEL = (0, 254, 0)


def strip_white(img):
    """从四角漫水填充，把与画布边缘连通的背景白抠成透明，然后裁紧。

    只抠「连得出去」的白：角色身上的白裙子、手里的白牌子、气泡内部
    都被深色轮廓包着，跟外部不连通，所以不会被误伤。
    """
    work = img.convert("RGBA").copy()
    w, h = work.size
    for corner in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        try:
            ImageDraw.floodfill(work, corner, SENTINEL, thresh=42)
        except Exception:
            pass

    data = list(work.getdata())
    work.putdata([
        (0, 0, 0, 0) if (r, g, b) == SENTINEL else (r, g, b, a)
        for r, g, b, a in data
    ])

    bbox = work.getbbox()
    return work.crop(bbox) if bbox else work


def load_character():
    """App 图标用的图。

    哥哥要求图标和启动动画用**同一张**（501 摸鱼），所以这里直接取摸鱼那张。
    （403「你还能有 DeepSeek 聪明？」留给说明页顶部用。）
    """
    return strip_white(Image.open(SPLASH_SRC))


CHARACTER = load_character()
# 启动画面的那张「摸鱼」：抱着鲸鱼抱枕睡觉
SPLASH = strip_white(Image.open(SPLASH_SRC))
# 说明页顶部那只：举牌子那张，带台词
INFO_FISH = strip_white(Image.open(SRC))


def patch_tagline(img, text, font_path=r"C:\Windows\Fonts\msyhbd.ttc"):
    """把表情包底部那句台词改掉。

    台词是**印在图里的**，不是文字层，所以要擦掉重写：
    先用白色盖住原来那行，再用系统字体写新的一行。

    字号不写死 —— 新台词比原来长（多了 "MMH & "），
    从 46 往下试，直到宽度塞得进 470px 为止，不然会顶出画布。
    描边用白、字用深色，跟原图的「黑字白边」风格保持一致。
    """
    if not os.path.exists(font_path):
        print("  !! 找不到字体 %s，台词没改" % font_path)
        return img

    img = img.convert("RGBA").copy()
    W, H = img.size
    d = ImageDraw.Draw(img)
    # 底部台词区（这张图实测 y=442~503 是文字，往上多留一点余量）
    # 关键：抹成**透明**而不是白色 —— 图是贴在淡蓝卡片上的，
    # 填白会在浅色背景上糊出一块突兀的白板。
    d.rectangle([0, int(H * 0.845), W, H], fill=(255, 255, 255, 0))

    size = 50
    limit = int(W * 0.965)   # 尽量用满画布宽度，字号才能大
    while size > 16:
        f = ImageFont.truetype(font_path, size)
        bb = d.textbbox((0, 0), text, font=f, stroke_width=2)
        if bb[2] - bb[0] <= limit:
            break
        size -= 1

    f = ImageFont.truetype(font_path, size)
    bb = d.textbbox((0, 0), text, font=f, stroke_width=2)
    w, h = bb[2] - bb[0], bb[3] - bb[1]
    x = (W - w) / 2 - bb[0]
    band_top = int(H * 0.845)
    y = band_top + ((H - band_top) - h) / 2 - bb[1]
    d.text((x, y), text, font=f, fill=(28, 32, 48, 255),
           stroke_width=3, stroke_fill=(255, 255, 255, 255))
    print("  台词已改写：字号 %d，宽 %dpx（上限 %d）" % (size, w, limit))
    return img


# 台词尽量紧凑：空格能省就省，省下来的宽度全都让给字号
TAGLINE = "你还能有 MMH&DeepSeek 聪明？"


def on_gradient(px, char_scale):
    """淡蓝渐变圆角方底 + 角色。用于传统图标。"""
    big = px * SS
    bg = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(bg, "RGBA")
    for i in range(big):
        t = i / max(1, big - 1)
        d.line(
            [(0, i), (big, i)],
            fill=tuple(int(round(BG_TOP[k] + (BG_BOT[k] - BG_TOP[k]) * t)) for k in range(4)),
        )
    mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [0, 0, big - 1, big - 1], radius=int(big * 0.23), fill=255
    )
    bg.putalpha(mask)

    ch = fit(CHARACTER, int(big * char_scale))
    bg.alpha_composite(ch, ((big - ch.width) // 2, (big - ch.height) // 2 - int(big * 0.01)))
    return bg.resize((px, px), Image.LANCZOS)


def transparent(px, char_scale):
    """透明底 + 角色。用于自适应图标前景（会被裁成圆形，所以只占安全区）。"""
    big = px * SS
    canvas = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    ch = fit(CHARACTER, int(big * char_scale))
    canvas.alpha_composite(ch, ((big - ch.width) // 2, (big - ch.height) // 2))
    return canvas.resize((px, px), Image.LANCZOS)


def fit(img, box):
    """等比缩放到最长边不超过 box。"""
    r = min(box / img.width, box / img.height)
    return img.resize((max(1, int(img.width * r)), max(1, int(img.height * r))), Image.LANCZOS)


def save(img, folder, name):
    path = os.path.join(RES, folder, name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, "PNG")
    return path


def main():
    densities = [
        ("mipmap-mdpi", 48),
        ("mipmap-hdpi", 72),
        ("mipmap-xhdpi", 96),
        ("mipmap-xxhdpi", 144),
        ("mipmap-xxxhdpi", 192),
    ]

    # 传统图标：图占 84%
    for folder, px in densities:
        save(on_gradient(px, 0.84), folder, "ic_launcher.png")

    # 自适应图标前景：透明底，角色只占中间 66%（安全区，圆形遮罩切不到）
    for folder, px in densities:
        save(transparent(px, 0.66), folder, "ic_launcher_foreground.png")
    save(transparent(432, 0.66), "mipmap-xxxhdpi", "ic_launcher_foreground.png")

    # 说明页顶部那张：举牌子 + 台词（台词改成带 MMH 的版本）
    info = patch_tagline(INFO_FISH.copy(), TAGLINE)
    r2 = 512.0 / max(info.width, info.height)
    info = info.resize((int(info.width * r2), int(info.height * r2)), Image.LANCZOS)
    canvas_info = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    canvas_info.alpha_composite(info, ((512 - info.width) // 2, (512 - info.height) // 2))
    save(canvas_info, "drawable-nodpi", "fat_fish.png")

    # 启动画面那张「摸鱼」（透明底，铺在浅蓝背景上）
    # 800px 够大屏手机用了，再大只是徒增 APK 体积
    splash = SPLASH.copy()
    r = 800.0 / max(splash.width, splash.height)
    splash = splash.resize((int(splash.width * r), int(splash.height * r)), Image.LANCZOS)
    path = os.path.join(RES, "drawable-nodpi", "splash_fish.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    splash.save(path, "PNG", optimize=True)

    preview = on_gradient(512, 0.84)
    preview.save(os.path.join(BASE, "icon_preview.png"), "PNG")
    # 说明页效果预览
    transparent(512, 1.0).save(os.path.join(BASE, "icon_preview_nodpi.png"), "PNG")

    print("图标角色裁紧后:", CHARACTER.size, " 启动图:", SPLASH.size)
    print("图标已写入", RES)
    print("预览图:", os.path.join(BASE, "icon_preview.png"))


if __name__ == "__main__":
    main()
