# -*- coding: utf-8 -*-
"""校验 assets/seed 下的 JSON 是否符合 SeedImporter 的解析预期。

App 首次启动会把这几份 JSON 灌进 Room。种子数据格式一旦对不上，
用户看到的就是一个空列表 —— 而这种错在开发机上不一定复现得出来。
所以在这里按 SeedImporter 的读取顺序做一遍模拟解析。
"""
import json
import os
import sys

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

BASE = os.path.dirname(os.path.abspath(__file__))
SEED = os.path.join(os.path.dirname(BASE), "app", "src", "main", "assets", "seed")

problems = []
notes = []


def need(obj, key, kind, where):
    if key not in obj:
        problems.append("%s 缺少必需字段 %s" % (where, key))
        return None
    v = obj[key]
    if kind == "str" and not isinstance(v, str):
        problems.append("%s.%s 应为 string，实际 %s" % (where, key, type(v).__name__))
    elif kind == "num" and not isinstance(v, (int, float)):
        problems.append("%s.%s 应为 number，实际 %s" % (where, key, type(v).__name__))
    elif kind == "bool" and not isinstance(v, bool):
        problems.append("%s.%s 应为 boolean，实际 %s" % (where, key, type(v).__name__))
    elif kind == "arr" and not isinstance(v, list):
        problems.append("%s.%s 应为 array，实际 %s" % (where, key, type(v).__name__))
    return v


def optional(obj, key, kind, where):
    if key not in obj or obj[key] is None:
        return None
    return need(obj, key, kind, where)


def load(name):
    path = os.path.join(SEED, name)
    if not os.path.exists(path):
        problems.append("缺少文件 %s" % name)
        return None
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def main():
    fish = load("fish.json")
    lures = load("lures.json")
    zones = load("zones.json")
    spots = load("spots.json")
    districts = load("districts.json")
    if None in (fish, lures, zones, spots, districts):
        return report()

    fish_ids = set()
    for f in fish:
        w = "fish[%s]" % f.get("id", "?")
        fid = need(f, "id", "str", w)
        need(f, "name", "str", w)
        need(f, "aliases", "arr", w)
        need(f, "category", "str", w)
        need(f, "lureActiveSeason", "str", w)
        need(f, "activeMonths", "arr", w)
        need(f, "bestTimeOfDay", "str", w)
        need(f, "habitatPreference", "str", w)
        need(f, "abundance", "num", w)
        if fid:
            fish_ids.add(fid)
        for m in f.get("activeMonths", []):
            if not isinstance(m, int) or not (1 <= m <= 12):
                problems.append("%s.activeMonths 含非法月份 %r" % (w, m))

    lure_ids = set()
    gear_counts = {}
    for l in lures:
        w = "lures[%s]" % l.get("id", "?")
        lid = need(l, "id", "str", w)
        need(l, "name", "str", w)
        need(l, "category", "str", w)
        gc = need(l, "gearClass", "str", w)
        need(l, "targetFishIds", "arr", w)
        need(l, "waterLayer", "str", w)
        need(l, "weightRange", "str", w)
        need(l, "actionStyle", "str", w)
        need(l, "sceneNote", "str", w)
        need(l, "goodTerrain", "arr", w)
        if gc not in ("MICRO", "GENERAL", "HEAVY", "DISTANCE"):
            problems.append("%s.gearClass 非法值 %s" % (w, gc))
        else:
            gear_counts[gc] = gear_counts.get(gc, 0) + 1
        if lid:
            lure_ids.add(lid)
        for fid in l.get("targetFishIds", []):
            if fid not in fish_ids:
                problems.append("%s 引用了不存在的鱼种 %s" % (w, fid))
    notes.append("拟饵档位分布：%s" % gear_counts)

    for z in zones:
        w = "zones[%s]" % z.get("id", "?")
        need(z, "id", "str", w)
        need(z, "name", "str", w)
        need(z, "type", "str", w)
        need(z, "boundaryType", "str", w)
        need(z, "polygons", "arr", w)
        need(z, "activeMonths", "arr", w)
        need(z, "ruleDescription", "str", w)
        need(z, "legalBasis", "str", w)
        poly = z.get("polygons", [])
        total_pts = 0
        for ri, ring in enumerate(poly):
            if not isinstance(ring, list) or len(ring) < 3:
                problems.append("%s.polygons[%d] 顶点不足 3 个" % (w, ri))
                continue
            for pi, pt in enumerate(ring):
                if not isinstance(pt, list) or len(pt) != 2:
                    problems.append("%s.polygons[%d][%d] 不是 [lat, lon]" % (w, ri, pi))
                    break
                lat, lon = pt
                if not (39.0 < lat < 41.5 and 115.0 < lon < 118.0):
                    problems.append("%s.polygons[%d][%d] 坐标越界 %.5f,%.5f" % (w, ri, pi, lat, lon))
                    break
            total_pts += len(ring)
        notes.append("%-28s 环 %2d，顶点 %5d" % (z["name"], len(poly), total_pts))

    exact = approx = unverified = 0
    source_counts = {}
    for s in spots:
        w = "spots[%s]" % s.get("id", "?")
        need(s, "id", "str", w)
        need(s, "name", "str", w)
        need(s, "district", "str", w)
        need(s, "address", "str", w)
        acc = need(s, "coordAccuracy", "str", w)
        need(s, "waterType", "str", w)
        need(s, "waterName", "str", w)
        at = need(s, "accessType", "str", w)
        st = need(s, "sourceType", "str", w)
        if st not in ("OFFICIAL", "WILD", "RISKY"):
            problems.append("%s.sourceType 非法值 %s" % (w, st))
        else:
            source_counts[st] = source_counts.get(st, 0) + 1
        need(s, "fishingMethods", "arr", w)
        need(s, "targetFishIds", "arr", w)
        need(s, "terrain", "arr", w)
        need(s, "lures", "arr", w)
        need(s, "isActive", "bool", w)
        need(s, "source", "str", w)
        need(s, "sourceYear", "num", w)
        optional(s, "latitude", "num", w)
        optional(s, "longitude", "num", w)
        optional(s, "parking", "bool", w)
        optional(s, "legalWarning", "str", w)

        if at != "FREE":
            problems.append("%s accessType=%s，收费点不该进种子数据" % (w, at))
        for fid in s.get("targetFishIds", []):
            if fid not in fish_ids:
                problems.append("%s 引用了不存在的鱼种 %s" % (w, fid))
        if "路亚" not in s.get("fishingMethods", []):
            problems.append("%s 不含路亚钓法" % w)
        if acc == "EXACT":
            exact += 1
        elif acc == "APPROXIMATE":
            approx += 1
        else:
            unverified += 1
        if acc == "UNVERIFIED" and s.get("latitude") is not None:
            problems.append("%s 标为 UNVERIFIED 却带坐标" % w)

    for d in districts:
        w = "districts[%s]" % d.get("name", "?")
        need(d, "name", "str", w)
        rings = need(d, "rings", "arr", w) or []
        for ri, ring in enumerate(rings):
            if len(ring) < 3:
                problems.append("%s.rings[%d] 顶点不足" % (w, ri))

    print("鱼种 %d / 拟饵 %d / 禁钓区 %d / 钓点 %d / 区县 %d"
          % (len(fish), len(lures), len(zones), len(spots), len(districts)))
    print("坐标可信度：EXACT %d / APPROXIMATE %d / UNVERIFIED %d" % (exact, approx, unverified))
    print("钓点来源：%s\n" % source_counts)
    for n in notes:
        print("  " + n)
    return report()


def report():
    print()
    if problems:
        print("!! 发现 %d 个问题：" % len(problems))
        for p in problems[:60]:
            print("   - " + p)
        return 1
    print("种子数据校验通过，与 SeedImporter 的解析预期一致 ✓")
    return 0


if __name__ == "__main__":
    sys.exit(main())
