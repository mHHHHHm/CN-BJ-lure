# -*- coding: utf-8 -*-
"""下载北京全域的低缩放级别地图瓦片，打包进 APK。

用途：保证 App 在完全离线的情况下也能看到北京整体地图轮廓与主要路网。
更高缩放级别（z>=11）由 App 运行时在线加载并缓存到本地。

瓦片源用高德的路网图（中文标注、国内访问快、无需 Key）。
注意高德用的是 GCJ-02 坐标系，App 侧会把所有要素统一转到 GCJ-02 再绘制，
所以这里按标准 Web Mercator 编号取瓦片即可，两边一致。
"""
import math
import os
import time
import urllib.request

BASE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(BASE), "app", "src", "main", "assets", "tiles")

# 北京范围，稍微留点余量（GCJ-02 相对 WGS-84 有几百米偏移）
LON_MIN, LAT_MIN, LON_MAX, LAT_MAX = 115.30, 39.35, 117.65, 41.15
ZOOMS = [7, 8, 9, 10]

# 高德瓦片有 1-4 四个子域，轮着用来分摊压力
SUBDOMAINS = ["01", "02", "03", "04"]
URL = "https://webrd{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}"


def deg2tile(lat, lon, z):
    n = 2 ** z
    x = int((lon + 180.0) / 360.0 * n)
    lat_rad = math.radians(lat)
    y = int((1.0 - math.asinh(math.tan(lat_rad)) / math.pi) / 2.0 * n)
    return max(0, min(n - 1, x)), max(0, min(n - 1, y))


def main():
    total = 0
    ok = 0
    skip = 0
    fail = []

    for z in ZOOMS:
        x0, y1 = deg2tile(LAT_MIN, LON_MIN, z)
        x1, y0 = deg2tile(LAT_MAX, LON_MAX, z)
        xs = range(min(x0, x1), max(x0, x1) + 1)
        ys = range(min(y0, y1), max(y0, y1) + 1)
        count = len(list(xs)) * len(list(ys))
        print("z=%d  x=%d..%d  y=%d..%d  共 %d 张" % (z, min(xs), max(xs), min(ys), max(ys), count))

        folder = os.path.join(OUT, str(z))
        os.makedirs(folder, exist_ok=True)

        for x in xs:
            for y in ys:
                total += 1
                path = os.path.join(folder, "%d_%d.png" % (x, y))
                if os.path.exists(path) and os.path.getsize(path) > 1000:
                    skip += 1
                    continue
                sub = SUBDOMAINS[(x + y) % 4]
                url = URL.format(s=sub, x=x, y=y, z=z)
                try:
                    req = urllib.request.Request(url, headers={
                        "User-Agent": "Mozilla/5.0 (bjlure tile packer)",
                        "Referer": "https://www.amap.com/",
                    })
                    with urllib.request.urlopen(req, timeout=25) as r:
                        data = r.read()
                    if len(data) < 500:
                        raise ValueError("瓦片过小 %d 字节" % len(data))
                    with open(path, "wb") as f:
                        f.write(data)
                    ok += 1
                    time.sleep(0.05)  # 别把人家服务器打急了
                except Exception as e:
                    fail.append((z, x, y, str(e)))

    size = 0
    files = 0
    for root, _dirs, names in os.walk(OUT):
        for n in names:
            size += os.path.getsize(os.path.join(root, n))
            files += 1

    print("\n完成：新下载 %d 张，已存在 %d 张，失败 %d 张" % (ok, skip, len(fail)))
    print("瓦片总数 %d，合计 %.2f MB -> %s" % (files, size / 1024 / 1024, OUT))
    if fail:
        print("失败明细（前 10）：")
        for z, x, y, msg in fail[:10]:
            print("  z=%d x=%d y=%d : %s" % (z, x, y, msg))
    return 1 if fail else 0


if __name__ == "__main__":
    raise SystemExit(main())
