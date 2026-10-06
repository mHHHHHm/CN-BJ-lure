# -*- coding: utf-8 -*-
"""从 Wikimedia Commons 拉候选鱼图，拼成联系表供人工挑选。

为什么要人工挑：Commons 的搜索结果里混杂着标本照、玻璃缸反光照、
黑白插图、甚至书页扫描 —— 光靠宽高比过滤不掉。先小图拼成一张大图
一次看完，挑出每种鱼最像「活鱼侧身照」的那一张，再回下载高清版。

只用到 thumb.wikimedia.org 这个域名（实测国内可直连）。
"""
import io
import json
import os
import time
import urllib.parse
import urllib.request

from PIL import Image, ImageDraw

BASE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(BASE), "out", "fish_candidates")

API = "https://commons.wikimedia.org/w/api.php"
UA = {"User-Agent": "bjlure-fishpic/1.0 (personal fishing app)"}

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

PER_FISH = 6          # 每种鱼取几个候选
THUMB_W = 300         # 候选缩略图宽度
BATCH = 6             # 每批几种鱼（一批一张联系表）


def api(params, retries=4):
    last = None
    for i in range(retries):
        try:
            url = API + "?" + urllib.parse.urlencode(params)
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
                return json.loads(r.read().decode("utf-8"))
        except Exception as e:
            last = e
            time.sleep(2 * (i + 1))
    raise last


def fetch(url, retries=4):
    last = None
    for i in range(retries):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
                return r.read()
        except Exception as e:
            last = e
            time.sleep(2 * (i + 1))
    raise last


def candidates(sci, n=PER_FISH):
    d = api({
        "action": "query", "format": "json", "generator": "search",
        "gsrsearch": sci, "gsrnamespace": "6", "gsrlimit": "20",
        "prop": "imageinfo", "iiprop": "url|size|mime", "iiurlwidth": str(THUMB_W),
    })
    out = []
    for _, p in (d.get("query", {}).get("pages", {}) or {}).items():
        ii = (p.get("imageinfo") or [{}])[0]
        if not ii.get("thumburl") or ii.get("mime") not in ("image/jpeg", "image/png"):
            continue
        w, h = ii.get("width") or 0, ii.get("height") or 0
        if w < 320 or h < 180:
            continue
        out.append({
            "title": p.get("title", ""),
            "thumb": ii["thumburl"],
            "full": ii.get("url"),
            "w": w, "h": h,
            "ratio": w / max(1, h),
        })
    # 横构图优先，比例接近 1.5 的最像正经侧身照
    out.sort(key=lambda x: (x["w"] <= x["h"], -abs(x["ratio"] - 1.5)))
    return out[:n]


def main():
    os.makedirs(OUT, exist_ok=True)
    index = {}

    for b in range(0, len(FISH), BATCH):
        batch = FISH[b:b + BATCH]
        rows = []
        for fid, cn, sci in batch:
            try:
                cands = candidates(sci)
            except Exception as e:
                print("  %-8s 搜索失败 %s" % (cn, str(e)[:50]))
                continue
            index[fid] = {"cn": cn, "sci": sci, "cands": []}
            imgs = []
            for i, c in enumerate(cands):
                try:
                    raw = fetch(c["thumb"])
                    im = Image.open(io.BytesIO(raw)).convert("RGB")
                    im.thumbnail((THUMB_W, 220), Image.LANCZOS)
                    imgs.append((cn, i, im, c))
                    index[fid]["cands"].append(c)
                except Exception:
                    continue
            rows.append((cn, imgs))
            print("  %-8s %d 个候选" % (cn, len(imgs)))
            time.sleep(0.3)

        if not rows:
            continue

        cols = max(len(r[1]) for r in rows)
        cw, ch = THUMB_W + 8, 220 + 30
        sheet = Image.new("RGB", (cols * cw, len(rows) * ch), (255, 255, 255))
        dr = ImageDraw.Draw(sheet)
        for r, (cn, imgs) in enumerate(rows):
            for c, (_, i, im, _c) in enumerate(imgs):
                x = c * cw + 4
                y = r * ch + 24
                sheet.paste(im, (x, y))
                dr.text((x + 2, r * ch + 6), "%s  #%d" % (cn, i), fill=(0, 0, 0))
        path = os.path.join(OUT, "batch%d.jpg" % (b // BATCH + 1))
        sheet.save(path, "JPEG", quality=85)
        print("  -> %s  (%dx%d)" % (path, sheet.width, sheet.height))

    with open(os.path.join(OUT, "index.json"), "w", encoding="utf-8") as f:
        json.dump(index, f, ensure_ascii=False, indent=1)
    print("\n索引已写:", os.path.join(OUT, "index.json"))


if __name__ == "__main__":
    main()
