# -*- coding: utf-8 -*-
"""把已核实的真实数据转成 App 的 assets 种子数据。

数据来源不重新编造，直接复用 bj-fishing 工程里已经交叉核实过的那一份：
  - 钓点：bj-fishing/build.py 的 SPOTS
  - 禁钓区几何：bj-fishing/out/geo.json（OpenStreetMap 水体边界）
本脚本只做「格式转换 + 按 App 领域模型补字段 + 生成自检报告」。
"""
import importlib.util
import json
import math
import os

BASE = os.path.dirname(os.path.abspath(__file__))
WORK = os.path.dirname(BASE)
ASSETS = os.path.join(WORK, "app", "src", "main", "assets", "seed")
BJ_FISHING = os.path.join(os.path.dirname(WORK), "bj-fishing")


def load_bj_spots():
    """复用 bj-fishing/build.py 里的 SPOTS，避免两份数据漂移"""
    spec = importlib.util.spec_from_file_location("bjbuild", os.path.join(BJ_FISHING, "build.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod.SPOTS


def load_geo():
    with open(os.path.join(BJ_FISHING, "out", "geo.json"), encoding="utf-8") as f:
        return json.load(f)


# ---------------------------------------------------------------------------
# 鱼种
# ---------------------------------------------------------------------------
FISH = [
    dict(id="qiaozui", name="翘嘴", aliases=["翘嘴鲌", "白鱼", "翘嘴白"],
         category="SURFACE", lureActiveSeason="5-6 月、9-10 月两波最好", activeMonths=[5, 6, 9, 10],
         bestTimeOfDay="清晨与傍晚，水面炸水时最好", habitatPreference="开阔水面中上层，追小鱼群",
         abundance=85),
    dict(id="makou", name="马口", aliases=["马口鱼", "桃花鱼"],
         category="SURFACE", lureActiveSeason="3-10 月，春末到秋最好", activeMonths=[3, 4, 5, 6, 7, 8, 9, 10],
         bestTimeOfDay="白天，阴天更好", habitatPreference="溪流浅滩、急缓交界、清水区",
         abundance=80),
    dict(id="heiyu", name="黑鱼", aliases=["乌鳢", "乌鱼", "财鱼"],
         category="SURFACE", lureActiveSeason="6-9 月最猛", activeMonths=[5, 6, 7, 8, 9],
         bestTimeOfDay="清晨与傍晚，正午晒背时也能打", habitatPreference="水草区、浮萍区、草洞",
         abundance=70),
    dict(id="nianyu", name="鲶鱼", aliases=["鲇鱼", "胡子鲶"],
         category="BOTTOM", lureActiveSeason="5-9 月", activeMonths=[5, 6, 7, 8, 9],
         bestTimeOfDay="夜间", habitatPreference="深潭、桥下、闸口底层",
         abundance=60),
    dict(id="qingshao", name="青稍", aliases=["蒙古鲌", "达氏鲌", "青梢"],
         category="MID", lureActiveSeason="全年可钓，春夏秋最活跃", activeMonths=[4, 5, 6, 7, 8, 9, 10],
         bestTimeOfDay="清晨与傍晚", habitatPreference="闸口、桥墩缓流区，中上层",
         abundance=75),
    dict(id="baitiao", name="白条", aliases=["䱗条", "参鱼", "白鲦"],
         category="SURFACE", lureActiveSeason="全年，夏季最疯", activeMonths=[4, 5, 6, 7, 8, 9, 10],
         bestTimeOfDay="全天，下午水面成群", habitatPreference="表层、走水区",
         abundance=95),
    dict(id="luyu", name="鲈鱼", aliases=["大口黑鲈", "加州鲈"],
         category="MID", lureActiveSeason="5-10 月", activeMonths=[5, 6, 7, 8, 9, 10],
         bestTimeOfDay="清晨与傍晚", habitatPreference="结构区、桥墩、倒树；北京多为放生个体，建议放流",
         abundance=35),
    dict(id="guiyu", name="鳜鱼", aliases=["桂鱼", "花鲫鱼"],
         category="BOTTOM", lureActiveSeason="6-9 月，夜钓最好", activeMonths=[6, 7, 8, 9],
         bestTimeOfDay="夜间 21:00-1:00", habitatPreference="闸口、乱石、倒树等结构区底层",
         abundance=40),
    dict(id="hongzun", name="虹鳟", aliases=["虹鳟鱼"],
         category="MID", lureActiveSeason="秋冬放流期与春季", activeMonths=[3, 4, 10, 11],
         bestTimeOfDay="白天", habitatPreference="怀柔山区冷水溪流",
         abundance=25),

    # ---- 下面这些主要靠台钓，路亚只是偶尔碰上 ----
    # 加进来是因为用户打开钓点详情最想知道的恰恰是「这儿有什么鱼」，
    # 只列路亚鱼种反而不如实。拟饵库不给它们配饵，推荐区自然就不显示。
    dict(id="jiyu", name="鲫鱼", aliases=["鲫瓜子", "月鲫"],
         category="BOTTOM", lureActiveSeason="全年可钓，春秋最好", activeMonths=[3, 4, 5, 9, 10, 11],
         bestTimeOfDay="清晨与傍晚", habitatPreference="几乎所有静水和缓流都有，北京最普遍的鱼",
         abundance=92),
    dict(id="liyu", name="鲤鱼", aliases=["鲤拐子", "红鱼"],
         category="BOTTOM", lureActiveSeason="4-10 月", activeMonths=[4, 5, 6, 7, 8, 9, 10],
         bestTimeOfDay="清晨与傍晚", habitatPreference="库湾、河床深坑、有障碍的底层",
         abundance=68),
    dict(id="caoyu", name="草鱼", aliases=["鲩鱼", "草鲩"],
         category="MID", lureActiveSeason="5-9 月", activeMonths=[5, 6, 7, 8, 9],
         bestTimeOfDay="上午与傍晚", habitatPreference="水草丰茂的库湾与河道中上层",
         abundance=50),
    dict(id="lianyu", name="鲢鱼", aliases=["白鲢", "鲢子"],
         category="MID", lureActiveSeason="6-9 月", activeMonths=[6, 7, 8, 9],
         bestTimeOfDay="白天", habitatPreference="开阔水面中上层，成群活动",
         abundance=55),
    dict(id="yongyu", name="鳙鱼", aliases=["胖头鱼", "花鲢"],
         category="MID", lureActiveSeason="6-9 月", activeMonths=[6, 7, 8, 9],
         bestTimeOfDay="白天", habitatPreference="深水区中上层，比鲢鱼更靠深",
         abundance=45),
    dict(id="huangsang", name="黄颡鱼", aliases=["嘎鱼", "昂刺鱼", "黄辣丁"],
         category="BOTTOM", lureActiveSeason="5-9 月，夜钓最稳", activeMonths=[5, 6, 7, 8, 9],
         bestTimeOfDay="夜间", habitatPreference="石缝、桥墩、乱石底，软虫很容易骗到",
         abundance=58),
    dict(id="xige", name="溪哥", aliases=["宽鳍鱲", "长鳍鱲", "桃花鱼", "溪石斑"],
         category="SURFACE", lureActiveSeason="4-10 月，春夏最活跃", activeMonths=[4, 5, 6, 7, 8, 9, 10],
         bestTimeOfDay="白天，晴天中午也咬", habitatPreference="山涧溪流的缓流区和浅滩，成群活动",
         abundance=72),
    dict(id="liugen", name="柳根", aliases=["拉氏鱥", "柳根子", "柳根鱼"],
         category="BOTTOM", lureActiveSeason="4-10 月", activeMonths=[4, 5, 6, 7, 8, 9, 10],
         bestTimeOfDay="白天", habitatPreference="冷水溪流的深潭与石缝，怀柔密云房山山区多见",
         abundance=45),
]

# ---------------------------------------------------------------------------
# 拟饵（gearClass：MICRO 微物 / GENERAL 泛用 / HEAVY 雷强 / DISTANCE 远投）
# ---------------------------------------------------------------------------
LURES = [
    dict(id="minnow-float", name="浮水米诺", category="MINNOW", gearClass="GENERAL",
         targetFishIds=["qiaozui", "luyu", "qingshao"], waterLayer="SURFACE", weightRange="7-12g",
         actionStyle="抽停（抽两下停一秒），靠水花声诱鱼", sceneNote="水面有炸水时优先用，翘嘴和鲈鱼对水花声特别敏感",
         goodTerrain=["SHALLOW_FLAT", "GENTLE_SLOPE"]),
    dict(id="minnow-sinking", name="沉水米诺", category="MINNOW", gearClass="GENERAL",
         targetFishIds=["qiaozui", "qingshao", "luyu"], waterLayer="SUBSURFACE", weightRange="7-14g",
         actionStyle="匀收或变速收，可数秒下沉到指定水层", sceneNote="鱼在中层不上水面时用，能精确控制泳层",
         goodTerrain=["GENTLE_SLOPE", "DEEP_POOL"]),
    dict(id="metal-jig", name="远投铁板", category="METAL_JIG", gearClass="DISTANCE",
         targetFishIds=["qiaozui", "qingshao"], waterLayer="MID", weightRange="15-25g",
         actionStyle="远投后匀速收线，或让它飘落下沉", sceneNote="重心靠后、入水平飘，在缓坡水深变化的野钓点表现稳定",
         goodTerrain=["DEEP_POOL", "GENTLE_SLOPE"]),
    dict(id="metal-jig-fall", name="飘落铁板", category="METAL_JIG", gearClass="DISTANCE",
         targetFishIds=["qingshao", "qiaozui"], waterLayer="MID", weightRange="10-20g",
         actionStyle="抽起后放松线让饵自然飘落，咬口多发生在飘落瞬间", sceneNote="闸口深水区钓青稍的利器",
         goodTerrain=["STRUCTURE", "DEEP_POOL"]),
    dict(id="vib", name="VIB 震动饵", category="VIB", gearClass="GENERAL",
         targetFishIds=["qiaozui", "qingshao", "luyu"], waterLayer="MID", weightRange="7-14g",
         actionStyle="匀速收线，靠高频振动找鱼", sceneNote="搜索陌生水域效率高，缺点是挂底",
         goodTerrain=["GENTLE_SLOPE", "DEEP_POOL"]),
    dict(id="spoon-rotating", name="旋转亮片", category="SPOON", gearClass="MICRO",
         targetFishIds=["makou", "qiaozui", "heiyu", "baitiao"], waterLayer="SURFACE", weightRange="3-7g",
         actionStyle="匀速收线，偶尔小抽", sceneNote="溪流和清水区最通用的饵，新手第一枚就该买它",
         goodTerrain=["SHALLOW_FLAT", "FLOWING"]),
    dict(id="spoon-micro", name="瓜子亮片", category="SPOON", gearClass="MICRO",
         targetFishIds=["baitiao", "makou", "xige"], waterLayer="SURFACE", weightRange="1-3g",
         actionStyle="快速匀收", sceneNote="钓白条和溪哥专用，几乎不会空军，适合练手和救场",
         goodTerrain=["SHALLOW_FLAT", "FLOWING"]),
    dict(id="spoon-small-stream", name="溪流小亮片", category="SPOON", gearClass="MICRO",
         targetFishIds=["makou", "hongzun", "xige", "liugen"], waterLayer="SUBSURFACE", weightRange="2-5g",
         actionStyle="逆流匀收，或顺流让它自然漂", sceneNote="山区溪流打马口、溪哥、柳根和虹鳟，注意别惊到鱼",
         goodTerrain=["FLOWING", "SHALLOW_FLAT"]),
    dict(id="frog", name="雷蛙", category="FROG", gearClass="HEAVY",
         targetFishIds=["heiyu"], waterLayer="SURFACE", weightRange="10-18g",
         actionStyle="慢拖加停顿，走 Z 字，停顿那一下最容易被咬", sceneNote="重草区打黑鱼的标准配置，防挂极好",
         goodTerrain=["WEED_BED"]),
    dict(id="prop-bait", name="螺旋桨饵（拖拉机）", category="FROG", gearClass="HEAVY",
         targetFishIds=["heiyu"], waterLayer="SURFACE", weightRange="10-20g",
         actionStyle="匀速收线，螺旋桨持续打水花", sceneNote="亮水区和稀疏草区比雷蛙更好用，声效更持续",
         goodTerrain=["WEED_BED", "SHALLOW_FLAT"]),
    dict(id="soft-texas", name="软虫德州钓组", category="SOFT_PLASTIC", gearClass="GENERAL",
         targetFishIds=["heiyu", "nianyu", "guiyu", "luyu"], waterLayer="BOTTOM", weightRange="7-14g",
         actionStyle="跳底、慢拖，让软虫在底部一跳一停", sceneNote="防挂性能最好，结构区必备",
         goodTerrain=["ROCK_PILE", "STRUCTURE", "DEEP_POOL"]),
    dict(id="soft-jighead", name="铅头钩软虫", category="JIG_HEAD", gearClass="GENERAL",
         targetFishIds=["guiyu", "luyu", "nianyu"], waterLayer="BOTTOM", weightRange="5-15g",
         actionStyle="跳底，跳起后让饵自然下落，鳜鱼多在落底瞬间咬", sceneNote="钓鳜鱼的主力，挂底极凶，一次要带够",
         goodTerrain=["STRUCTURE", "ROCK_PILE"]),
    dict(id="pencil", name="铅笔（走狗）", category="PENCIL", gearClass="GENERAL",
         targetFishIds=["qiaozui", "luyu"], waterLayer="SURFACE", weightRange="9-15g",
         actionStyle="抽停走出 Z 字水痕", sceneNote="清晨水面平静时钓翘嘴，视觉冲击最强",
         goodTerrain=["SHALLOW_FLAT", "GENTLE_SLOPE"]),
    dict(id="crank", name="摇滚（Crank）", category="CRANK", gearClass="GENERAL",
         targetFishIds=["luyu", "qingshao"], waterLayer="SUBSURFACE", weightRange="7-12g",
         actionStyle="匀速收线，靠唇板下潜撞底", sceneNote="硬底缓坡搜索效率高，撞底那下会引来攻击",
         goodTerrain=["GENTLE_SLOPE", "ROCK_PILE"]),
    dict(id="soft-worm-ned", name="微物软虫", category="SOFT_PLASTIC", gearClass="MICRO",
         targetFishIds=["baitiao", "makou", "qingshao", "jiyu", "xige", "liugen"], waterLayer="SUBSURFACE", weightRange="1-4g",
         actionStyle="轻抖竿尖，让软虫在水中抽动", sceneNote="鱼口极轻时用；城市河道玩白条小翘嘴，溪流里溪哥柳根也吃",
         goodTerrain=["SHALLOW_FLAT", "FLOWING", "STRUCTURE"]),
    dict(id="worm-micro-jig", name="微物软虫（小铅头）", category="SOFT_PLASTIC", gearClass="MICRO",
         targetFishIds=["jiyu", "huangsang", "baitiao"], waterLayer="BOTTOM", weightRange="1-3g",
         actionStyle="贴底慢拖，偶尔轻挑让尾巴摆动", sceneNote="钓鲫鱼和黄颡鱼的万金油，挂底少、入口率高",
         goodTerrain=["GENTLE_SLOPE", "STRUCTURE", "SHALLOW_FLAT"]),
    dict(id="worm-jighead-big", name="铅头软虫（大号）", category="SOFT_PLASTIC", gearClass="GENERAL",
         targetFishIds=["huangsang", "nianyu", "guiyu", "luyu"], waterLayer="BOTTOM", weightRange="5-10g",
         actionStyle="跳底：抬竿一次让饵跳起再落底，咬口多在落底瞬间",
         sceneNote="夜钓嘎鱼和鲶鱼最有效，鳜鱼也吃",
         goodTerrain=["STRUCTURE", "DEEP_POOL"]),
]

# 钓点地形 / 水域类型 / 坐标可信度的人工核定表
# 依据是各钓点的实际描述（官方表里的水深岸别、民间分享里的结构描述）
SPOT_META = {
    "cb-henancun":     dict(terrain=["WEED_BED", "SHALLOW_FLAT"], waterType="RIVER", acc="APPROXIMATE", parking=True),
    "cb-suzhuang":     dict(terrain=["STRUCTURE", "SHALLOW_FLAT"], waterType="RIVER", acc="UNVERIFIED", parking=None),
    "cb-gengxin":      dict(terrain=["WEED_BED", "SHALLOW_FLAT"], waterType="RIVER", acc="EXACT", parking=None),
    "cb-shijiakou":    dict(terrain=["STRUCTURE", "ROCK_PILE", "FLOWING"], waterType="RIVER", acc="EXACT", parking=None),
    "cb-liugezhuang":  dict(terrain=["WEED_BED"], waterType="RIVER", acc="APPROXIMATE", parking=None),
    "cb-shunyi-park":  dict(terrain=["STRUCTURE", "SHALLOW_FLAT"], waterType="RIVER", acc="APPROXIMATE", parking=True),
    "wy-lafeite":      dict(terrain=["WEED_BED"], waterType="RIVER", acc="APPROXIMATE", parking=None),
    "wy-tugou":        dict(terrain=["GENTLE_SLOPE"], waterType="RIVER", acc="UNVERIFIED", parking=None),
    "wy-jichang":      dict(terrain=["GENTLE_SLOPE", "SHALLOW_FLAT"], waterType="RIVER", acc="UNVERIFIED", parking=None),
    "yd-lianshihu":    dict(terrain=["GENTLE_SLOPE", "SHALLOW_FLAT"], waterType="RIVER", acc="EXACT", parking=True),
    "hd-shangzhuang":  dict(terrain=["GENTLE_SLOPE", "WEED_BED"], waterType="RESERVOIR", acc="APPROXIMATE", parking=None),
    "cp-nanshahe":     dict(terrain=["WEED_BED", "SHALLOW_FLAT", "FLOWING"], waterType="RIVER", acc="APPROXIMATE", parking=None),
    "cp-beishahe":     dict(terrain=["GENTLE_SLOPE", "FLOWING"], waterType="RIVER", acc="APPROXIMATE", parking=None),
    "cp-shaheshuiku":  dict(terrain=["WEED_BED", "GENTLE_SLOPE", "SHALLOW_FLAT"], waterType="RESERVOIR", acc="EXACT", parking=True),
    "dc-nanhuchenghe": dict(terrain=["GENTLE_SLOPE"], waterType="RIVER", acc="APPROXIMATE", parking=None),
    "dx-xinfenghe":    dict(terrain=["SHALLOW_FLAT", "WEED_BED"], waterType="RIVER", acc="APPROXIMATE", parking=None),
    "dx-niantan":      dict(terrain=["GENTLE_SLOPE", "WEED_BED"], waterType="POND", acc="APPROXIMATE", parking=True),
    "pg-juhe":         dict(terrain=["FLOWING", "SHALLOW_FLAT"], waterType="RIVER", acc="APPROXIMATE", parking=None),
    "pg-jinhaihu":     dict(terrain=["GENTLE_SLOPE", "DEEP_POOL", "WEED_BED"], waterType="RESERVOIR", acc="EXACT", parking=True),
    "pg-huangsongyu":  dict(terrain=["GENTLE_SLOPE", "DEEP_POOL"], waterType="RESERVOIR", acc="APPROXIMATE", parking=None),
    "pg-xiyu":         dict(terrain=["GENTLE_SLOPE", "DEEP_POOL"], waterType="RESERVOIR", acc="APPROXIMATE", parking=None),
    "my-baihe":        dict(terrain=["FLOWING", "SHALLOW_FLAT"], waterType="RIVER", acc="APPROXIMATE", parking=None),
    "my-chaohe":       dict(terrain=["FLOWING", "SHALLOW_FLAT"], waterType="RIVER", acc="APPROXIMATE", parking=None),
    "my-shachang":     dict(terrain=["DEEP_POOL", "GENTLE_SLOPE", "WEED_BED"], waterType="RESERVOIR", acc="EXACT", parking=None),
    "cy-liangmahe":    dict(terrain=["STRUCTURE", "GENTLE_SLOPE"], waterType="CANAL", acc="APPROXIMATE", parking=None),
    "ft-xiaoyuehu":    dict(terrain=["GENTLE_SLOPE", "SHALLOW_FLAT"], waterType="POND", acc="APPROXIMATE", parking=True),
    "hd-nanshahe-xi":  dict(terrain=["WEED_BED", "SHALLOW_FLAT", "FLOWING"], waterType="RIVER", acc="APPROXIMATE", parking=None),
    "cy-bahe":         dict(terrain=["GENTLE_SLOPE", "WEED_BED"], waterType="RIVER", acc="APPROXIMATE", parking=True),
    "ft-nanyuan":      dict(terrain=["SHALLOW_FLAT", "WEED_BED"], waterType="WETLAND", acc="APPROXIMATE", parking=True),
    "tz-caifu":        dict(terrain=["STRUCTURE", "GENTLE_SLOPE", "FLOWING"], waterType="RIVER", acc="APPROXIMATE", parking=True),
    "fs-liangjianfang": dict(terrain=["STRUCTURE", "WEED_BED", "FLOWING"], waterType="RIVER", acc="APPROXIMATE", parking=None),
    "xc-jishuitan":    dict(terrain=["GENTLE_SLOPE", "STRUCTURE"], waterType="RIVER", acc="APPROXIMATE", parking=None),
    # ---- 从公开资料补充的点位 ----
    "sy-luomahu":      dict(terrain=["WEED_BED", "GENTLE_SLOPE"], waterType="POND", acc="APPROXIMATE", parking=True),
    "yq-yeyahu":       dict(terrain=["WEED_BED", "SHALLOW_FLAT"], waterType="WETLAND", acc="APPROXIMATE", parking=True),
    "ft-qinglonghu-park": dict(terrain=["GENTLE_SLOPE", "WEED_BED"], waterType="RESERVOIR", acc="APPROXIMATE", parking=True),
    "yq-houhe":        dict(terrain=["FLOWING", "ROCK_PILE"], waterType="RIVER", acc="APPROXIMATE", radius=300),
    "hr-yanqihu":      dict(terrain=["GENTLE_SLOPE", "DEEP_POOL"], waterType="RESERVOIR", acc="APPROXIMATE", parking=True),
    "yq-baihepu":      dict(terrain=["GENTLE_SLOPE", "DEEP_POOL"], waterType="RESERVOIR", acc="APPROXIMATE"),
    "cp-nanzhuang":    dict(terrain=["WEED_BED", "SHALLOW_FLAT"], waterType="RESERVOIR", acc="APPROXIMATE"),
    "hr-shayukou":     dict(terrain=["GENTLE_SLOPE", "DEEP_POOL"], waterType="RESERVOIR", acc="APPROXIMATE"),
    "fs-qinglonghu":   dict(terrain=["WEED_BED", "SHALLOW_FLAT"], waterType="RESERVOIR", acc="APPROXIMATE"),
    "fs-doudian":      dict(terrain=["DEEP_POOL", "GENTLE_SLOPE"], waterType="POND", acc="APPROXIMATE", radius=150),
    "fs-shiwudu":      dict(terrain=["FLOWING", "STRUCTURE", "SHALLOW_FLAT"], waterType="RIVER", acc="APPROXIMATE"),
    # ---- 钓友整理的 2025 标点清单 ----
    "cb-xiji":         dict(terrain=["FLOWING", "WEED_BED"], waterType="RIVER", acc="APPROXIMATE"),
    "yd-jinglianglu":  dict(terrain=["STRUCTURE", "SHALLOW_FLAT"], waterType="RIVER", acc="APPROXIMATE"),
    "yd-zigu":         dict(terrain=["WEED_BED", "GENTLE_SLOPE"], waterType="POND", acc="APPROXIMATE"),
    "yd-wangping":     dict(terrain=["SHALLOW_FLAT", "FLOWING", "ROCK_PILE"], waterType="RIVER", acc="APPROXIMATE", radius=300),
    "wy-weilai":       dict(terrain=["GENTLE_SLOPE", "SHALLOW_FLAT"], waterType="RIVER", acc="APPROXIMATE", parking=True),
    "wy-huosha":       dict(terrain=["GENTLE_SLOPE", "DEEP_POOL"], waterType="RIVER", acc="APPROXIMATE"),
    "qh-shucunzha":    dict(terrain=["STRUCTURE", "GENTLE_SLOPE"], waterType="RIVER", acc="APPROXIMATE"),
    "qh-shenjiafen":   dict(terrain=["STRUCTURE", "WEED_BED"], waterType="RIVER", acc="APPROXIMATE", parking=True),
    "ls-yizhuang":     dict(terrain=["FLOWING", "SHALLOW_FLAT", "ROCK_PILE"], waterType="RIVER", acc="APPROXIMATE", radius=250),
    "ls-jiuzhonglu":   dict(terrain=["WEED_BED", "SHALLOW_FLAT"], waterType="RIVER", acc="APPROXIMATE", radius=200),
    "hh-xishuxing":    dict(terrain=["FLOWING", "STRUCTURE"], waterType="RIVER", acc="APPROXIMATE"),
    "by-fuzhongxin":   dict(terrain=["GENTLE_SLOPE", "DEEP_POOL"], waterType="RIVER", acc="EXACT"),
    "hr-lihuaxigu":    dict(terrain=["FLOWING", "SHALLOW_FLAT"], waterType="RIVER", acc="APPROXIMATE", parking=True),
    "mtg-109guodao":   dict(terrain=["FLOWING", "ROCK_PILE"], waterType="RIVER", acc="APPROXIMATE", radius=800),
    "fs-dashihe":      dict(terrain=["FLOWING", "SHALLOW_FLAT"], waterType="RIVER", acc="APPROXIMATE"),
    "hr-baihewan":     dict(terrain=["FLOWING", "SHALLOW_FLAT", "ROCK_PILE"], waterType="RIVER", acc="APPROXIMATE", radius=600),
    "fs-dingjiawa":    dict(terrain=["GENTLE_SLOPE", "WEED_BED"], waterType="RESERVOIR", acc="APPROXIMATE"),
    # ---- 钓友补充清单（按区整理） ----
    "hd-xiaojiache":   dict(terrain=["GENTLE_SLOPE", "STRUCTURE"], waterType="RIVER", acc="APPROXIMATE"),
    "cy-machang":      dict(terrain=["GENTLE_SLOPE", "SHALLOW_FLAT"], waterType="RIVER", acc="APPROXIMATE", parking=True),
    "ft-lvdi":         dict(terrain=["GENTLE_SLOPE", "SHALLOW_FLAT"], waterType="RIVER", acc="APPROXIMATE", parking=True),
    "mtg-wolonggang":  dict(terrain=["SHALLOW_FLAT", "ROCK_PILE"], waterType="RIVER", acc="APPROXIMATE"),
    "fs-heilongguan":  dict(terrain=["FLOWING", "ROCK_PILE"], waterType="RIVER", acc="APPROXIMATE", parking=True, radius=300),
    "fs-changcao":     dict(terrain=["FLOWING", "ROCK_PILE"], waterType="RIVER", acc="APPROXIMATE", radius=250),
    "fs-xiaoqinghe":   dict(terrain=["FLOWING", "SHALLOW_FLAT"], waterType="RIVER", acc="APPROXIMATE", radius=200),
    "tz-youdaqiao":    dict(terrain=["GENTLE_SLOPE", "SHALLOW_FLAT"], waterType="RIVER", acc="APPROXIMATE"),
    "tz-taihu":        dict(terrain=["FLOWING", "SHALLOW_FLAT"], waterType="RIVER", acc="EXACT", radius=250),
    "dx-yanshouying":  dict(terrain=["SHALLOW_FLAT", "WEED_BED"], waterType="RIVER", acc="APPROXIMATE", radius=200),
    "sy-gaobai":       dict(terrain=["WEED_BED", "GENTLE_SLOPE"], waterType="RIVER", acc="APPROXIMATE", parking=True),
    "cp-wenyu-shahe":  dict(terrain=["GENTLE_SLOPE", "SHALLOW_FLAT"], waterType="RIVER", acc="EXACT", parking=True),
    "cp-ansilu":       dict(terrain=["FLOWING", "ROCK_PILE"], waterType="RIVER", acc="APPROXIMATE", radius=300),
    "hr-nianziwan":    dict(terrain=["FLOWING", "ROCK_PILE"], waterType="RIVER", acc="APPROXIMATE", radius=250),
    "my-zhuangtou":    dict(terrain=["FLOWING", "ROCK_PILE", "SHALLOW_FLAT"], waterType="RIVER", acc="APPROXIMATE", radius=300),
    "my-gubeikou":     dict(terrain=["FLOWING", "ROCK_PILE"], waterType="RIVER", acc="APPROXIMATE", radius=250),
    "yq-qianjiadian":  dict(terrain=["FLOWING", "SHALLOW_FLAT"], waterType="RIVER", acc="APPROXIMATE", radius=350),
    "yd-heishuihe":    dict(terrain=["FLOWING", "SHALLOW_FLAT"], waterType="RIVER", acc="UNVERIFIED", parking=None),
    "yd-sandian":      dict(terrain=["STRUCTURE", "FLOWING"], waterType="RIVER", acc="APPROXIMATE", parking=None),
    "yd-shiji":        dict(terrain=["STRUCTURE", "GENTLE_SLOPE"], waterType="RIVER", acc="APPROXIMATE", parking=None),
    "yd-luodaozhuang": dict(terrain=["GENTLE_SLOPE"], waterType="CANAL", acc="APPROXIMATE", parking=None),
    "yd-bayihu":       dict(terrain=["GENTLE_SLOPE"], waterType="CANAL", acc="APPROXIMATE", parking=None),
    "by-xinbeiguan":   dict(terrain=["STRUCTURE", "FLOWING"], waterType="RIVER", acc="EXACT", parking=None),
    "by-yudaihe":      dict(terrain=["GENTLE_SLOPE"], waterType="RIVER", acc="APPROXIMATE", parking=None),
    "by-majiawan":     dict(terrain=["WEED_BED", "DEEP_POOL"], waterType="WETLAND", acc="EXACT", parking=True),
    "by-tianjiafu":    dict(terrain=["GENTLE_SLOPE"], waterType="RIVER", acc="EXACT", parking=None),
    "bj-gaobeidian":   dict(terrain=["GENTLE_SLOPE"], waterType="RIVER", acc="APPROXIMATE", parking=None),
    "bj-tonghuiqiao":  dict(terrain=["GENTLE_SLOPE"], waterType="RIVER", acc="UNVERIFIED", parking=None),
    "bj-qinghe":       dict(terrain=["SHALLOW_FLAT"], waterType="RIVER", acc="APPROXIMATE", parking=None),
    "bj-huchenghe":    dict(terrain=["GENTLE_SLOPE"], waterType="RIVER", acc="APPROXIMATE", parking=None),
    "bj-maizhong":     dict(terrain=["GENTLE_SLOPE"], waterType="RIVER", acc="EXACT", parking=None),
    "hr-siduhe":       dict(terrain=["FLOWING", "SHALLOW_FLAT"], waterType="RIVER", acc="APPROXIMATE", parking=None),
    "hr-liuduhe":      dict(terrain=["FLOWING", "SHALLOW_FLAT"], waterType="RIVER", acc="APPROXIMATE", parking=None),
    "hr-jiuduhe":      dict(terrain=["FLOWING", "SHALLOW_FLAT"], waterType="RIVER", acc="APPROXIMATE", parking=None),
    "fs-shidu":        dict(terrain=["STRUCTURE", "FLOWING", "ROCK_PILE"], waterType="RIVER", acc="EXACT", parking=True),
    "fs-liulihe":      dict(terrain=["STRUCTURE", "WEED_BED"], waterType="RIVER", acc="EXACT", parking=None),
    "fs-changgou":     dict(terrain=["WEED_BED", "SHALLOW_FLAT"], waterType="WETLAND", acc="EXACT", parking=None),
    "yq-guishui":      dict(terrain=["FLOWING", "SHALLOW_FLAT"], waterType="RIVER", acc="APPROXIMATE", parking=None),
    "mtg-zhenzhu":     dict(terrain=["DEEP_POOL", "GENTLE_SLOPE"], waterType="RESERVOIR", acc="EXACT", parking=True),
    "cp-shisanling":   dict(terrain=["WEED_BED", "GENTLE_SLOPE"], waterType="RESERVOIR", acc="EXACT", parking=True),
    "yq-guanting":     dict(terrain=["WEED_BED", "FLOWING"], waterType="RIVER", acc="UNVERIFIED", parking=None),
    "sy-hanshiqiao":   dict(terrain=["WEED_BED"], waterType="WETLAND", acc="APPROXIMATE", parking=None),
}

# 中文鱼名 -> 鱼种 id
FISH_NAME_TO_ID = {}
for f in FISH:
    FISH_NAME_TO_ID[f["name"]] = f["id"]
    for a in f["aliases"]:
        FISH_NAME_TO_ID[a] = f["id"]

# 钓点里写的鱼名带括号备注，做一次清洗
FISH_ALIAS_FIX = {
    "黄颡（嘎鱼）": "nianyu_huang",  # 黄颡鱼不在路亚主目标里，单独处理
    "黄颡": "nianyu_huang",
    "嘎鱼": "nianyu_huang",
    "小翘嘴": "qiaozui",
    "鲤鱼": None,
    "鲫鱼": None,
    "草鱼": None,
    "鲢鱼": None,
    "鳙鱼": None,
    "鳑鲏": None,
}

# 各水域类型默认的「钓点覆盖半径」（米）。
# 这不是精确边界，是「这一片范围内都能下杆」的示意 —— 地图上按这个画圈，
# 让用户一眼看出这是个水面而不是一个针尖。
# 想让某个点单独大一点/小一点，在 SPOT_META 里加 "radius": 数字 覆盖。
RADIUS_BY_WATER = {
    "RESERVOIR": 600,   # 水库是一大片水面
    "WETLAND": 450,
    "RIVER": 220,       # 河道就一段，别画成一条长龙
    "CANAL": 180,
    "POND": 200,
}


def clean_fish(name):
    base = name.split("（")[0].split("(")[0].strip()
    return base


# ---------------------------------------------------------------------------
# 禁钓区
# ---------------------------------------------------------------------------
ZONE_META = {
    "密云水库": dict(
        type="PROPAGATION_RELEASE",
        rule="密云水库是增殖放流水域（京农发〔2008〕180 号），同时属市级河湖禁游区，全年禁止垂钓。",
        basis="关于划定禁止垂钓的增殖放流水域的通告（京农发〔2008〕180 号）；北京市水务局市级河湖禁游区清单",
    ),
    "怀柔水库": dict(
        type="PROPAGATION_RELEASE",
        rule="怀柔水库是增殖放流水域且属市级河湖禁游区，全年禁止垂钓，禁渔期内同样不得使用任何渔具。",
        basis="京农发〔2008〕180 号；北京市水务局市级河湖禁游区清单",
    ),
    "官厅水库": dict(
        type="PROPAGATION_RELEASE",
        rule="官厅水库（北京界内，含妫水西湖）是增殖放流水域，禁止垂钓。",
        basis="京农发〔2008〕180 号",
    ),
    "斋堂水库": dict(
        type="WATER_SOURCE_PROTECTION",
        rule="斋堂水库库区属市级河湖禁游区，禁止垂钓。",
        basis="北京市水务局市级河湖禁游区清单",
    ),
    "大宁水库": dict(
        type="WATER_SOURCE_PROTECTION",
        rule="大宁水库库区属市级河湖禁游区，禁止垂钓。",
        basis="北京市水务局市级河湖禁游区清单",
    ),
    "永定河滞洪水库": dict(
        type="WATER_SOURCE_PROTECTION",
        rule="永定河滞洪水库（大宁水库库区）属市级河湖禁游区，禁止垂钓。",
        basis="北京市水务局市级河湖禁游区清单",
    ),
    "团城湖": dict(
        type="WATER_SOURCE_PROTECTION",
        rule="团城湖属京密引水渠渠首至颐和园段，是饮用水水源一级保护区，禁止垂钓。",
        basis="北京市水务局市级河湖禁游区清单；饮用水水源保护区相关规定",
    ),
    "十三陵水库": dict(
        type="SEASONAL_FISHING_BAN",
        rule="十三陵水库在季节性禁渔区名录（二）内，每年 4 月 1 日 0 时至 9 月 24 日 24 时禁渔；"
             "注意禁渔只限制作业方式，钓具垂钓不受此条限制。",
        basis="京政农发〔2019〕63 号 附件 2《北京市禁渔区名录（二）》",
    ),
}

SEASONAL_MONTHS = [4, 5, 6, 7, 8, 9]


def project(lat, lon, lat0, lon0):
    """局部平面投影：返回米为单位的 (x, y)"""
    kx = 111_320.0 * math.cos(math.radians(lat0))
    ky = 110_540.0
    return (lon - lon0) * kx, (lat - lat0) * ky


def unproject(x, y, lat0, lon0):
    kx = 111_320.0 * math.cos(math.radians(lat0))
    ky = 110_540.0
    return (lat0 + y / ky, lon0 + x / kx)


def buffer_line(line, half_width_m):
    """把折线（[lon, lat] 序列）扩成带状多边形，用于渠道型禁钓区。"""
    if len(line) < 2:
        return []
    lat0 = sum(p[1] for p in line) / len(line)
    lon0 = sum(p[0] for p in line) / len(line)
    pts = [project(p[1], p[0], lat0, lon0) for p in line]

    left, right = [], []
    n = len(pts)
    for i in range(n):
        if i == 0:
            dx, dy = pts[1][0] - pts[0][0], pts[1][1] - pts[0][1]
        elif i == n - 1:
            dx, dy = pts[-1][0] - pts[-2][0], pts[-1][1] - pts[-2][1]
        else:
            dx, dy = pts[i + 1][0] - pts[i - 1][0], pts[i + 1][1] - pts[i - 1][1]
        length = math.hypot(dx, dy)
        if length < 1e-6:
            continue
        nx, ny = -dy / length, dx / length
        left.append((pts[i][0] + nx * half_width_m, pts[i][1] + ny * half_width_m))
        right.append((pts[i][0] - nx * half_width_m, pts[i][1] - ny * half_width_m))

    ring = left + right[::-1]
    out = []
    for x, y in ring:
        lat, lon = unproject(x, y, lat0, lon0)
        out.append([round(lat, 6), round(lon, 6)])
    if out and out[0] != out[-1]:
        out.append(out[0])
    return out


def build_zones(geo):
    zones = []
    for area in geo["banAreas"]:
        meta = ZONE_META.get(area["name"])
        if not meta:
            print("  !! 禁钓区缺少元数据:", area["name"])
            continue
        # geo.json 的 ring 是 [lon, lat]，转成 [lat, lon]
        polygons = [[[p[1], p[0]] for p in ring] for ring in area["rings"]]
        polygons = [p for p in polygons if len(p) >= 4]
        zones.append(dict(
            id="zone-" + str(len(zones) + 1),
            name=area["name"],
            type=meta["type"],
            boundaryType="POLYGON",
            polygons=polygons,
            activeMonths=SEASONAL_MONTHS if meta["type"] == "SEASONAL_FISHING_BAN" else [],
            ruleDescription=meta["rule"],
            legalBasis=meta["basis"],
        ))

    # 京密引水渠渠首至颐和园段：线状水体，扩成 120m 半宽的带状多边形
    canal_count = 0
    canal_polys = []
    for line in geo["canals"]:
        ring = buffer_line(line, 120.0)
        if len(ring) >= 4:
            canal_polys.append(ring)
            canal_count += 1
    if canal_polys:
        zones.append(dict(
            id="zone-jingmi-canal",
            name="京密引水渠（渠首至颐和园段）",
            type="WATER_SOURCE_PROTECTION",
            boundaryType="POLYGON",
            polygons=canal_polys,
            activeMonths=[],
            ruleDescription="京密引水渠渠首至颐和园段（含团城湖）是饮用水水源一级保护区，"
                           "同时属市级河湖禁游区，全年禁止垂钓。",
            legalBasis="北京市水务局市级河湖禁游区清单；饮用水水源保护区相关规定",
        ))
    print("  京密引水渠禁钓带：%d 段" % canal_count)

    # ---- 增殖放流水域里的两条河道 ----
    # 《关于划定禁止垂钓的增殖放流水域的通告》（京农发〔2008〕180 号）点名的
    # 禁钓水域里有「潮白河顺义段（牛栏山闸至河南村闸）」和「减河」。
    # 这两段 OSM 没有现成的面数据，用河道中心线扩带宽生成。
    # 不画的话，「顺义城区滨河段」那个钓点会被误判成可以钓 —— 那条正好落在禁钓段内。
    ban_river_file = os.path.join(BASE, "tools", "data", "ban_rivers.json")
    if not os.path.exists(ban_river_file):
        ban_river_file = os.path.join(BASE, "data", "ban_rivers.json")
    if os.path.exists(ban_river_file):
        with open(ban_river_file, encoding="utf-8") as f:
            ban_rivers = json.load(f)

        rules = {
            "chaoBaiShunyi": (
                "潮白河顺义段（牛栏山闸至河南村闸）是增殖放流水域，全年禁止垂钓。"
                "注意：这一段**不是**「禁渔区可以钓」的那种 —— 增殖放流水域属于真禁钓，"
                "别和上游下游的普通河道搞混。",
                "关于划定禁止垂钓的增殖放流水域的通告（京农发〔2008〕180 号）",
                "zone-chaobai-shunyi",
            ),
            "jianHe": (
                "减河（顺义城北减河）是增殖放流水域，全年禁止垂钓。",
                "关于划定禁止垂钓的增殖放流水域的通告（京农发〔2008〕180 号）",
                "zone-jianhe",
            ),
        }

        for key, (rule, basis, zid) in rules.items():
            info = ban_rivers.get(key)
            if not info or len(info.get("line") or []) < 2:
                print("  !! 禁钓河道缺数据:", key)
                continue
            ring = buffer_line(info["line"], float(info.get("halfWidthM", 120.0)))
            if len(ring) < 4:
                continue
            zones.append(dict(
                id=zid,
                name=info["name"],
                type="PROPAGATION_RELEASE",
                boundaryType="POLYGON",
                polygons=[ring],
                activeMonths=[],
                ruleDescription=rule,
                legalBasis=basis,
            ))
            print("  %s 禁钓带：%d 点河道" % (info["name"], len(info["line"])))
    else:
        print("  !! 找不到 ban_rivers.json，潮白河顺义段与减河的禁钓带没生成")

    return zones


def build_districts(geo):
    """北京各区轮廓，给地图页当底图。geo.json 里是 [lon, lat]，这里转成 [lat, lon]。"""
    out = []
    for d in geo["districts"]:
        rings = []
        for ring in d["rings"]:
            converted = [[p[1], p[0]] for p in ring]
            if len(converted) >= 4:
                rings.append(converted)
        if rings:
            out.append(dict(name=d["name"], rings=rings))
    return out


def build_spots(bj_spots):
    out = []
    for s in bj_spots:
        meta = SPOT_META.get(s["id"])
        if not meta:
            print("  !! 钓点缺少核定元数据:", s["id"], s["name"])
            continue

        fish_ids = []
        for fname in s["fish"]:
            base = clean_fish(fname)
            fid = FISH_NAME_TO_ID.get(base)
            if fid is None and base in ("黄颡", "嘎鱼"):
                continue  # 黄颡鱼不属于路亚主目标
            if fid and fid not in fish_ids:
                fish_ids.append(fid)

        lat = s["lat"]
        lon = s["lon"]
        acc = meta["acc"]
        if lat is None or lon is None:
            acc = "UNVERIFIED"

        methods = ["路亚"]
        if s.get("taiwan") and "不适合" not in s["taiwan"]:
            methods.append("台钓")

        out.append(dict(
            id=s["id"],
            name=s["name"],
            district=s["district"],
            address=s.get("access", ""),
            latitude=lat,
            longitude=lon,
            coordAccuracy=acc,
            radiusMeters=meta.get("radius", RADIUS_BY_WATER.get(meta["waterType"], 250)),
            waterType=meta["waterType"],
            waterName=s["water"],
            accessType="FREE",
            sourceType={"official": "OFFICIAL", "wild": "WILD", "risky": "RISKY"}.get(s["type"], "WILD"),
            fishingMethods=methods,
            targetFishIds=fish_ids,
            terrain=meta["terrain"],
            parking=meta.get("parking"),
            notes=s.get("tip", ""),
            bestSeason=s.get("season", ""),
            bestTimeOfDay=s.get("time", "清晨与傍晚"),
            lures=s.get("lures", []),
            isActive=True,
            source=s.get("source", ""),
            sourceYear=s.get("sourceYear", 0),
            legalWarning=s.get("ban"),
        ))
    return out


def main():
    os.makedirs(ASSETS, exist_ok=True)
    bj_spots = load_bj_spots()
    geo = load_geo()

    print("转换禁钓区 ...")
    zones = build_zones(geo)
    print("转换钓点 ...")
    spots = build_spots(bj_spots)

    # ---------------- 自检 ----------------
    problems = []
    fish_ids = {f["id"] for f in FISH}
    lure_ids = {l["id"] for l in LURES}
    for s in spots:
        if not s["targetFishIds"]:
            problems.append("钓点 %s 没有关联到任何鱼种" % s["id"])
        for fid in s["targetFishIds"]:
            if fid not in fish_ids:
                problems.append("钓点 %s 引用了不存在的鱼种 %s" % (s["id"], fid))
        if s["accessType"] != "FREE":
            problems.append("钓点 %s 不是免费，不该进列表" % s["id"])
        if s["latitude"] is not None and not (39.3 < s["latitude"] < 41.2):
            problems.append("钓点 %s 纬度越界" % s["id"])
        if s["longitude"] is not None and not (115.3 < s["longitude"] < 117.6):
            problems.append("钓点 %s 经度越界" % s["id"])
    for l in LURES:
        for fid in l["targetFishIds"]:
            if fid not in fish_ids:
                problems.append("拟饵 %s 引用了不存在的鱼种 %s" % (l["id"], fid))
    for z in zones:
        if z["boundaryType"] == "POLYGON" and not z["polygons"]:
            problems.append("禁钓区 %s 没有几何数据" % z["name"])

    # 用真实几何做一次「钓点是否落在禁钓区里」的体检
    print("\n禁钓区体检（钓点坐标是否落在禁钓区多边形内）：")

    def in_poly(lat, lon, ring):
        inside = False
        n = len(ring)
        j = n - 1
        for i in range(n):
            yi, xi = ring[i][0], ring[i][1]
            yj, xj = ring[j][0], ring[j][1]
            if (yi > lat) != (yj > lat):
                if lon < xi + (lat - yi) / (yj - yi) * (xj - xi):
                    inside = not inside
            j = i
        return inside

    for s in spots:
        if s["latitude"] is None:
            continue
        for z in zones:
            for ring in z["polygons"]:
                if in_poly(s["latitude"], s["longitude"], ring):
                    msg = "钓点「%s」落在禁钓区「%s」内（%s）" % (s["name"], z["name"], z["type"])
                    if z["type"] == "SEASONAL_FISHING_BAN":
                        msg += "  ← 这是季节性禁渔区，钓具可钓，属预期"
                        print("  · " + msg)
                    elif "已列入禁钓区" in s["name"]:
                        # 有意保留的「禁钓提示点」：放在列表里就是为了让人看见这儿不能钓，
                        # 好过让人白跑一趟或者挨罚
                        print("  · " + msg + "  ← 有意保留的禁钓提示点，属预期")
                    else:
                        problems.append(msg)
                    break

    files = {
        "fish.json": FISH,
        "lures.json": LURES,
        "zones.json": zones,
        "spots.json": spots,
        "districts.json": build_districts(geo),
    }
    for name, data in files.items():
        path = os.path.join(ASSETS, name)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
        print("  %-12s %6.1f KB" % (name, os.path.getsize(path) / 1024))

    print("\n鱼种 %d / 拟饵 %d / 禁钓区 %d / 钓点 %d" % (len(FISH), len(LURES), len(zones), len(spots)))
    counts = {}
    for s in spots:
        counts[s["coordAccuracy"]] = counts.get(s["coordAccuracy"], 0) + 1
    print("坐标可信度分布：", counts)

    if problems:
        print("\n!! 自检发现 %d 个问题：" % len(problems))
        for p in problems:
            print("   - " + p)
        return 1
    print("\n自检通过")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
