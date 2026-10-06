# -*- coding: utf-8 -*-
"""从 Wikimedia Commons 抓 17 种目标鱼的图片，做成 App 里用的缩略图。

为什么用学名搜而不是中文名：Commons 是国际项目，中文名经常搜不到或者搜到
无关的花鸟鱼虫；学名能精确命中那个物种的实物照片。

图片来源是 Commons（CC 授权 / 公有领域），会同时写一份 CREDITS.txt 记录出处。
"""
import io
import json
import os
import time
import urllib.parse
import urllib.request

from PIL import Image

BASE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(BASE), "app", "src", "main", "assets", "fish")

API = "https://commons.wikimedia.org/w/api.php"
UA = "bjlure-fishpic/1.0 (personal fishing app; contact: local)"

# fish.json 里的 id -> (中文名, 学名)
FISH = [
    ("qiaozui",  "翘嘴",   "Culter alburnus"),
    ("makou",    "马口",   "Opsariichthys bidens"),
    ("heiyu",    "黑鱼",   "Channa argus"),
    ("nianyu",   "鲶鱼",   "Silurus asotus"),
    ("qingshao", "青稍",   "Culter mongolicus"),
    ("baitiao",  "白条",   "Hemiculter leucisculus"),
    ("luyu",     "鲈鱼",   "Micropterus salmoides"),
    ("guiyu",    "鳜鱼",   "Siniperca chuatsi"),
    ("hongzun",  "虹鳟",   "Oncorhynchus mykiss"),
    ("jiyu",     "鲫鱼",   "Carassius auratus"),
    ("liyu",     "鲤鱼",   "Cyprinus carpio"),
    ("caoyu",    "草鱼",   "Ctenopharyngodon idella"),
    ("lianyu",   "鲢鱼",   "Hypophthalmichthys molitrix"),
    ("yongyu",   "鳙鱼",   "Hypophthalmichthys nobilis"),
    ("huangsang", "黄颡鱼", "Tachysurus fulvidraco"),
    ("xige",     "溪哥",   "Zacco platypus"),
    ("liugen",   "柳根",   "Rhynchocypris lagowskii"),
]

# 缩略图最长边。卡片上显示的只有 88dp，480 足够清楚，再大纯浪费体积
THUMB = 480
# 点开放大用的图
LARGE = 900


def api(params, retries=4):
    last = None
    for i in range(retries):
        try:
            url = API + "?" + urllib.parse.urlencode(params)
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=25) as r:
                return json.loads(r.read().decode("utf-8"))
        except Exception as e:
            last = e
            time.sleep(1.5 * (i + 1))
    raise last


def find_image(sci, width):
    """搜索学名，返回若干候选图。优先鱼体完整的横构图照片。"""
    d = api({
        "action": "query", "format": "json", "generator": "search",
        "gsrsearch": sci, "gsrnamespace": "6", "gsrlimit": "8",
        "prop": "imageinfo", "iiprop": "url|size|mime", "iiurlwidth": str(width),
    })
    out = []
    for _, p in (d.get("query", {}).get("pages", {}) or {}).items():
        ii = (p.get("imageinfo") or [{}])[0]
        if not ii.get("thumburl"):
            continue
        if ii.get("mime") not in ("image/jpeg", "image/png"):
            continue
        w, h = ii.get("width") or 0, ii.get("height") or 0
        # 只要横构图：鱼是长的，竖图多半是标本照或书页扫描
        if w < 300 or h < 150 or w <= h:
            continue
        out.append((p.get("title", ""), ii["thumburl"], w, h))
    # 越接近 4:3 / 16:9 越像正经的鱼体照
    out.sort(key=lambda x: -abs((x[2] / max(1, x[3])) - 1.6))
    return out


def download(url, retries=4):
    last = None
    for i in range(retries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=30) as r:
                return r.read()
        except Exception as e:
            last = e
            time.sleep(1.5 * (i + 1))
    raise last


def main():
    os.makedirs(OUT, exist_ok=True)
    credits = []
    ok = 0
    fail = []

    for fid, cn, sci in FISH:
        try:
            cands = find_image(sci, LARGE)
            if not cands:
                fail.append((fid, cn, "没搜到合适图片"))
                print("  %-10s 没搜到" % cn)
                continue

            title, url, w, h = cands[0]
            raw = download(url)
            img = Image.open(io.BytesIO(raw)).convert("RGB")

            # 大图
            big = img.copy()
            big.thumbnail((LARGE, LARGE), Image.LANCZOS)
            big.save(os.path.join(OUT, "%s.jpg" % fid), "JPEG", quality=86, optimize=True)

            # 缩略图
            sm = img.copy()
            sm.thumbnail((THUMB, THUMB), Image.LANCZOS)
            sm.save(os.path.join(OUT, "%s_s.jpg" % fid), "JPEG", quality=84, optimize=True)

            credits.append("%s（%s）\t%s\t%s" % (cn, sci, title, url))
            ok += 1
            print("  %-10s OK  %s  %dx%d -> 原图 %dx%d" % (cn, title[:44], w, h, img.width, img.height))
            time.sleep(0.4)
        except Exception as e:
            fail.append((fid, cn, str(e)[:60]))
            print("  %-10s 失败 %s" % (cn, str(e)[:60]))

    with open(os.path.join(OUT, "CREDITS.txt"), "w", encoding="utf-8") as f:
        f.write("鱼类图片出处（均来自 Wikimedia Commons，CC 授权或公有领域）\n")
        f.write("=" * 70 + "\n")
        f.write("\n".join(credits))
        f.write("\n")

    size = sum(os.path.getsize(os.path.join(OUT, n)) for n in os.listdir(OUT) if n.endswith(".jpg"))
    print("\n成功 %d / %d，合计 %.2f MB -> %s" % (ok, len(FISH), size / 1024 / 1024, OUT))
    if fail:
        print("失败清单：")
        for fid, cn, msg in fail:
            print("  %s %s : %s" % (fid, cn, msg))
    return 0 if not fail else 1


if __name__ == "__main__":
    raise SystemExit(main())
