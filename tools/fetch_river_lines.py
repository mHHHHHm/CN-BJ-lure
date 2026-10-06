# -*- coding: utf-8 -*-
"""抓「潮白河顺义段」和「减河」的河道折线，用于生成禁钓区多边形。

这两段是《关于划定禁止垂钓的增殖放流水域的通告》（京农发〔2008〕180 号）
点名的禁钓水域，但 OSM 里没有现成的面数据，只能自己按河道中心线扩带宽。
不画的话，顺义城区滨河段那个钓点就会被误判成「可以钓」。
"""
import json
import time
import urllib.parse
import urllib.request

OUT = r"D:\deepseek-cache\dsh-workdata\bjlure\out\rivers_raw.json"

QUERY = """
[out:json][timeout:60];
(
  way["waterway"~"river|canal|stream"]["name"~"减河"](40.08,116.55,40.22,116.80);
  way["waterway"~"river|canal|stream"]["name"~"潮白河"](40.10,116.58,40.24,116.74);
);
out geom;
"""


def main():
    req = urllib.request.Request(
        "https://overpass-api.de/api/interpreter",
        data=urllib.parse.urlencode({"data": QUERY}).encode(),
        headers={"User-Agent": "bjlure-zones/1.0"},
    )
    raw = None
    for i in range(4):
        try:
            raw = urllib.request.urlopen(req, timeout=90).read()
            break
        except Exception as e:
            print("第 %d 次失败: %s" % (i + 1, str(e)[:70]))
            time.sleep(4)
    if raw is None:
        print("Overpass 一直连不上")
        return 1

    d = json.loads(raw.decode("utf-8"))
    out = []
    for e in d.get("elements", []):
        geom = e.get("geometry") or []
        if len(geom) < 2:
            continue
        name = (e.get("tags") or {}).get("name", "?")
        line = [[round(p["lon"], 6), round(p["lat"], 6)] for p in geom]
        out.append({"name": name, "id": e.get("id"), "points": len(line), "line": line})

    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False)

    lines = ["抓到 %d 条河道：" % len(out)]
    for o in out:
        first, last = o["line"][0], o["line"][-1]
        lines.append("  %-10s id=%-12s %3d 点  起(%.4f,%.4f) 终(%.4f,%.4f)"
                     % (o["name"], o["id"], o["points"], first[1], first[0], last[1], last[0]))
    txt = "\n".join(lines)
    print(txt)
    with open(r"D:\deepseek-cache\dsh-workdata\bjlure\out\rivers_summary.txt", "w", encoding="utf-8") as f:
        f.write(txt)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
