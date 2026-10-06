package com.bjlure.verify

import com.bjlure.app.domain.FishingIndex
import com.bjlure.app.domain.Geo
import com.bjlure.app.domain.LureMatcher
import com.bjlure.app.domain.NoFishingGuard
import com.bjlure.app.domain.SpotFilter
import com.bjlure.app.domain.model.AccessType
import com.bjlure.app.domain.model.BoundaryType
import com.bjlure.app.domain.model.CoordAccuracy
import com.bjlure.app.domain.model.FishCategory
import com.bjlure.app.domain.model.FishSpecies
import com.bjlure.app.domain.model.FishingSpot
import com.bjlure.app.domain.model.GuardLevel
import com.bjlure.app.domain.model.Lure
import com.bjlure.app.domain.model.LureCategory
import com.bjlure.app.domain.model.NoFishingZone
import com.bjlure.app.domain.model.Terrain
import com.bjlure.app.domain.model.WaterLayer
import com.bjlure.app.domain.model.WaterType
import com.bjlure.app.domain.model.WeatherDay
import com.bjlure.app.domain.model.WeatherPoint
import com.bjlure.app.domain.model.WeatherSnapshot
import com.bjlure.app.domain.model.ZoneType
import kotlin.math.abs

/**
 * 离线验证套件。
 *
 * 不依赖 Android、不依赖测试框架，用 kotlinc 直接编译成 jar 跑，
 * 目的只有一个：让「禁钓校验」和「饵料匹配」这两块业务规则在被装进 APK 之前，
 * 就已经在真实编译器下验证过。
 */

private var passed = 0
private var failed = 0
private val failures = ArrayList<String>()

private fun check(name: String, cond: Boolean, extra: String = "") {
    if (cond) {
        passed++
        println("  \u2713 $name")
    } else {
        failed++
        failures += "$name ${if (extra.isNotEmpty()) "-> $extra" else ""}"
        println("  \u2717 $name ${if (extra.isNotEmpty()) "-> $extra" else ""}")
    }
}

private fun section(t: String) {
    println("\n$t")
}

// ---------------------------------------------------------------------------
// 测试夹具
// ---------------------------------------------------------------------------

/** 一个边长约 2km 的方形「禁钓区」，中心在密云水库附近 */
private val squareBan = NoFishingZone(
    id = "test-square",
    name = "测试禁钓方块",
    type = ZoneType.WATER_SOURCE_PROTECTION,
    boundaryType = BoundaryType.POLYGON,
    polygons = listOf(
        listOf(
            40.500 to 116.890,
            40.500 to 116.910,
            40.520 to 116.910,
            40.520 to 116.890,
            40.500 to 116.890,
        )
    ),
    ruleDescription = "测试用",
    legalBasis = "测试依据",
)

/** 双环水体：模拟密云水库那种互不相连的多块水面 */
private val multiRingBan = NoFishingZone(
    id = "test-multi",
    name = "测试双环水库",
    type = ZoneType.PROPAGATION_RELEASE,
    boundaryType = BoundaryType.POLYGON,
    polygons = listOf(
        listOf(40.500 to 116.890, 40.500 to 116.900, 40.510 to 116.900, 40.510 to 116.890),
        // 第二块水面离第一块约 5km
        listOf(40.550 to 116.940, 40.550 to 116.950, 40.560 to 116.950, 40.560 to 116.940),
    ),
    ruleDescription = "测试用多环",
    legalBasis = "测试依据",
)

/** 一个「只禁作业方式」的禁渔区，同一片位置 */
private val squareFishingBan = squareBan.copy(
    id = "test-ban",
    name = "测试禁渔河道",
    type = ZoneType.PERMANENT_FISHING_BAN,
)

/** 季节性禁渔区，只在 4-9 月生效 */
private val seasonalZone = NoFishingZone(
    id = "test-seasonal",
    name = "测试季节性禁渔水库",
    type = ZoneType.SEASONAL_FISHING_BAN,
    boundaryType = BoundaryType.POINT_RADIUS,
    centerLat = 40.300,
    centerLng = 116.600,
    radiusMeters = 1000,
    activeMonths = listOf(4, 5, 6, 7, 8, 9),
    ruleDescription = "每年 4 月 1 日至 9 月 24 日禁渔",
    legalBasis = "测试依据",
)

private val fishList = listOf(
    FishSpecies("qiaozui", "翘嘴", listOf("翘嘴鲌"), FishCategory.SURFACE, "5-6 月、9-10 月", listOf(5, 6, 9, 10), "清晨/傍晚", "开阔水面中上层", 85),
    FishSpecies("makou", "马口", listOf("马口鱼"), FishCategory.SURFACE, "3-10 月", listOf(3, 4, 5, 6, 7, 8, 9, 10), "白天", "溪流浅滩", 80),
    FishSpecies("heiyu", "黑鱼", listOf("乌鳢"), FishCategory.SURFACE, "6-9 月", listOf(6, 7, 8, 9), "清晨/傍晚", "水草区", 70),
    FishSpecies("nianyu", "鲶鱼", listOf("鲇鱼"), FishCategory.BOTTOM, "5-9 月", listOf(5, 6, 7, 8, 9), "夜间", "深潭底层", 60),
)

private val lureList = listOf(
    Lure("spoon-small", "2-5g 旋转亮片", LureCategory.SPOON, listOf("makou", "qiaozui", "heiyu"),
        WaterLayer.SURFACE, "2-5g", "匀速收线、偶尔小抽", "溪流浅滩和清水区的通用饵",
        listOf(Terrain.SHALLOW_FLAT, Terrain.FLOWING)),
    Lure("metal-jig", "10-20g 远投铁板", LureCategory.METAL_JIG, listOf("qiaozui"),
        WaterLayer.MID, "10-20g", "远投后快速匀收或飘落", "开阔水面找翘嘴，靠飘落触发咬口",
        listOf(Terrain.DEEP_POOL, Terrain.GENTLE_SLOPE)),
    Lure("frog", "雷蛙", LureCategory.FROG, listOf("heiyu"),
        WaterLayer.SURFACE, "10-18g", "慢拖加停顿", "重草区打黑鱼，注意别惊窝",
        listOf(Terrain.WEED_BED)),
    Lure("soft-plastic", "软虫德州钓组", LureCategory.SOFT_PLASTIC, listOf("heiyu", "nianyu"),
        WaterLayer.BOTTOM, "7-14g", "跳底、慢拖", "结构区防挂，跳底找鲶鱼",
        listOf(Terrain.ROCK_PILE, Terrain.STRUCTURE, Terrain.DEEP_POOL)),
    Lure("minnow-float", "浮水米诺", LureCategory.MINNOW, listOf("qiaozui"),
        WaterLayer.SURFACE, "7-12g", "抽停", "水面有炸水时优先",
        listOf(Terrain.SHALLOW_FLAT, Terrain.GENTLE_SLOPE)),
)

// ---------------------------------------------------------------------------

fun main() {
    println("=".repeat(64))
    println("北京路亚 · 领域逻辑离线验证")
    println("=".repeat(64))

    testGeoDistance()
    testPointInPolygon()
    testMultiRingZone()
    testZoneDistance()
    testGuardInBan()
    testGuardNearBan()
    testGuardFishingBanIsNotBlocked()
    testGuardSeasonal()
    testGuardUnknownCoordinate()
    testLureMatcher()
    testLureMatcherSeason()
    testRealWorldCoordinates()
    testSpotFilter()
    testSpotFilterBanOrdering()
    testFishingIndexCalibration()

    println("\n" + "=".repeat(64))
    println("通过 $passed 项，失败 $failed 项")
    if (failures.isNotEmpty()) {
        println("\n失败明细：")
        failures.forEach { println("  - $it") }
        kotlin.system.exitProcess(1)
    }
    println("全部通过")
}

private fun testGeoDistance() {
    section("[1] 距离计算")
    // 天安门 -> 密云水库大坝附近，直线约 75km（允许 3% 误差）
    val d = Geo.distanceMeters(39.9087, 116.3975, 40.4790, 116.8450)
    check("天安门到密云水库约 75km", abs(d - 75_000) / 75_000 < 0.03, "实际 %.1f km".format(d / 1000))

    // 1 个纬度约 111km
    val oneDeg = Geo.distanceMeters(40.0, 116.0, 41.0, 116.0)
    check("1 度纬度约 111km", abs(oneDeg - 111_195) < 1500, "实际 %.0f m".format(oneDeg))

    check("同一点距离为 0", Geo.distanceMeters(40.0, 116.0, 40.0, 116.0) < 0.001)
}

private fun testPointInPolygon() {
    section("[2] 多边形包含判定")
    val ring = squareBan.polygons.first()
    check("中心点在内部", Geo.pointInPolygon(40.510, 116.900, ring))
    check("明显在外部（西）", !Geo.pointInPolygon(40.510, 116.800, ring))
    check("明显在外部（北）", !Geo.pointInPolygon(40.600, 116.900, ring))
    check("边界外的角点", !Geo.pointInPolygon(40.490, 116.880, ring))
    check("空多边形返回 false", !Geo.pointInPolygon(40.5, 116.9, emptyList()))
    check("点数不足返回 false", !Geo.pointInPolygon(40.5, 116.9, listOf(40.5 to 116.9, 40.6 to 116.9)))
}

private fun testMultiRingZone() {
    section("[2b] 多环水体（密云水库那种碎片化水面）")
    check("第一环内部命中", Geo.isInsideZone(40.505, 116.895, multiRingBan))
    check("第二环内部也命中", Geo.isInsideZone(40.555, 116.945, multiRingBan))
    check("两环之间不命中", !Geo.isInsideZone(40.530, 116.920, multiRingBan))
    check("两环之间到水体的距离不是 0",
        Geo.distanceToZoneMeters(40.530, 116.920, multiRingBan) > 0)
    check("落在第二环时距离为 0",
        Geo.distanceToZoneMeters(40.555, 116.945, multiRingBan) == 0.0)

    val g = NoFishingGuard.check(40.555, 116.945, listOf(multiRingBan), month = 7)
    check("多环水体同样会判 IN_BAN", g.level == GuardLevel.IN_BAN, g.level.name)
}

private fun testZoneDistance() {
    section("[3] 到禁钓区的距离")
    val inside = Geo.distanceToZoneMeters(40.510, 116.900, squareBan)
    check("区域内距离为 0", inside == 0.0, "实际 $inside")

    // 方块南边界在 40.500，往南 0.01 度约 1.1km
    val south = Geo.distanceToZoneMeters(40.490, 116.900, squareBan)
    check("南侧约 1.1km", south > 900 && south < 1300, "实际 %.0f m".format(south))

    val circle = Geo.distanceToZoneMeters(40.300, 116.600, seasonalZone)
    check("圆形区域内为 0", circle == 0.0)

    val circleOut = Geo.distanceToZoneMeters(40.320, 116.600, seasonalZone)
    // 0.02 度纬度约 2.2km，减去半径 1km
    check("圆形区外约 1.2km", circleOut > 900 && circleOut < 1600, "实际 %.0f m".format(circleOut))
}

private fun testGuardInBan() {
    section("[4] 禁钓校验：落在真禁钓区里")
    val r = NoFishingGuard.check(40.510, 116.900, listOf(squareBan), month = 7)
    check("等级为 IN_BAN", r.level == GuardLevel.IN_BAN, r.level.name)
    check("blocked 为 true", r.blocked)
    check("提示里点名了禁钓区", r.detail.contains("测试禁钓方块"))
}

private fun testGuardNearBan() {
    section("[5] 禁钓校验：靠近真禁钓区")
    // 南侧约 1.1km，超出 500m 预警线
    val far = NoFishingGuard.check(40.490, 116.900, listOf(squareBan), month = 7)
    check("1.1km 外不报警", far.level == GuardLevel.OK, far.level.name)

    // 紧贴南边界外约 300m
    val nearLat = 40.500 - 300.0 / 110_540.0
    val near = NoFishingGuard.check(nearLat, 116.900, listOf(squareBan), month = 7)
    check("300m 内触发 NEAR_BAN", near.level == GuardLevel.NEAR_BAN, near.level.name)
    check("提示里有距离", near.detail.contains("米"))
    check("NEAR_BAN 不算 blocked", !near.blocked)
}

private fun testGuardFishingBanIsNotBlocked() {
    section("[6] 关键规则：禁渔区 ≠ 禁钓区")
    // 同一个位置，只把类型换成「全年禁渔区（钓具除外）」
    val r = NoFishingGuard.check(40.510, 116.900, listOf(squareFishingBan), month = 7)
    check("等级是 INFO 而不是 IN_BAN", r.level == GuardLevel.INFO, r.level.name)
    check("不拦截用户", !r.blocked)
    check("提示说明钓具不受限", r.detail.contains("钓具"))

    // 混合场景：真禁钓区 + 禁渔区同时命中，必须以真禁钓为准
    val mixed = NoFishingGuard.check(40.510, 116.900, listOf(squareFishingBan, squareBan), month = 7)
    check("混合场景仍判为 IN_BAN", mixed.level == GuardLevel.IN_BAN, mixed.level.name)
}

private fun testGuardSeasonal() {
    section("[7] 季节性禁渔的月份判定")
    val inSeason = NoFishingGuard.check(40.300, 116.600, listOf(seasonalZone), month = 6)
    check("6 月在禁渔期内 -> INFO", inSeason.level == GuardLevel.INFO, inSeason.level.name)
    check("提示里写明禁渔期", inSeason.detail.contains("4 月 1 日") || inSeason.detail.contains("禁渔期"))

    val offSeason = NoFishingGuard.check(40.300, 116.600, listOf(seasonalZone), month = 12)
    check("12 月不在禁渔期 -> 不提示", offSeason.level == GuardLevel.OK, offSeason.level.name)

    check("月份边界：3 月未生效", !NoFishingGuard.isActiveInMonth(seasonalZone, 3))
    check("月份边界：4 月生效", NoFishingGuard.isActiveInMonth(seasonalZone, 4))
    check("月份边界：9 月生效", NoFishingGuard.isActiveInMonth(seasonalZone, 9))
    check("月份边界：10 月未生效", !NoFishingGuard.isActiveInMonth(seasonalZone, 10))

    // 全年生效的 zone，activeMonths 为空
    check("空月份表示全年生效", NoFishingGuard.isActiveInMonth(squareBan, 1))
    check("空月份表示全年生效（12 月）", NoFishingGuard.isActiveInMonth(squareBan, 12))
}

private fun testGuardUnknownCoordinate() {
    section("[8] 坐标缺失时不能装作没事")
    val r = NoFishingGuard.check(null, null, listOf(squareBan), month = 7)
    check("等级为 INFO 而非 OK", r.level == GuardLevel.INFO, r.level.name)
    check("提示说明无法校验", r.detail.contains("现场标识"))
}

private fun testLureMatcher() {
    section("[9] 饵料匹配：基础规则")
    val recs = LureMatcher.recommend(
        fishIds = listOf("heiyu"),
        terrain = listOf(Terrain.WEED_BED),
        lures = lureList, fish = fishList, month = 7,
    )
    check("只返回适配黑鱼的饵", recs.all { it.lure.targetFishIds.contains("heiyu") })
    check("不含只适配翘嘴的铁板", recs.none { it.lure.id == "metal-jig" })
    check("雷蛙排第一（地形完全对味 + 旺季）", recs.first().lure.id == "frog",
        "实际第一是 ${recs.first().lure.id}")
    check("推荐条数受限", recs.size <= 6)

    val frog = recs.first { it.lure.id == "frog" }
    check("雷蛙命中了水草地形", frog.matchedTerrain.contains(Terrain.WEED_BED))
    check("推荐度在合法区间", frog.confidence in 5..99, "${frog.confidence}")

    // 地形不对味时，同一条鱼的排序会变化
    val deepRecs = LureMatcher.recommend(
        fishIds = listOf("heiyu"),
        terrain = listOf(Terrain.ROCK_PILE, Terrain.STRUCTURE),
        lures = lureList, fish = fishList, month = 7,
    )
    check("换成石头堆地形后软虫反超雷蛙",
        deepRecs.first().lure.id == "soft-plastic", "实际第一是 ${deepRecs.first().lure.id}")
}

private fun testLureMatcherSeason() {
    section("[10] 饵料匹配：季节影响")
    val summer = LureMatcher.recommend(listOf("heiyu"), listOf(Terrain.WEED_BED), lureList, fishList, 7)
    val winter = LureMatcher.recommend(listOf("heiyu"), listOf(Terrain.WEED_BED), lureList, fishList, 1)
    check("冬季推荐度低于夏季",
        winter.first().confidence < summer.first().confidence,
        "夏 ${summer.first().confidence} / 冬 ${winter.first().confidence}")
    check("淡季提示里会点明", winter.first().tip.contains("淡季"))

    val empty = LureMatcher.recommend(emptyList(), listOf(Terrain.WEED_BED), lureList, fishList, 7)
    check("没有目标鱼时返回空", empty.isEmpty())

    val unknown = LureMatcher.recommend(listOf("no-such-fish"), emptyList(), lureList, fishList, 7)
    check("未知鱼种返回空", unknown.isEmpty())
}

private fun testRealWorldCoordinates() {
    section("[11] 真实坐标几何回归（用北京实际地标）")
    // 用密云水库简化边界的一个小环，验证真实点位判定
    val miyunRing = listOf(
        40.4800 to 116.8450, 40.4700 to 116.9600, 40.4500 to 117.0300,
        40.5000 to 117.0700, 40.5700 to 117.0500, 40.5850 to 116.9200,
        40.5500 to 116.8200, 40.5000 to 116.8000, 40.4800 to 116.8450,
    )
    check("密云水库中心点在环内", Geo.pointInPolygon(40.5100, 116.9500, miyunRing))
    check("顺义潮白河不在密云水库环内", !Geo.pointInPolygon(40.1158, 116.6932, miyunRing))
    check("天安门不在密云水库环内", !Geo.pointInPolygon(39.9087, 116.3975, miyunRing))

    val area = Geo.polygonAreaKm2(miyunRing)
    check("密云水库示意环面积在 50-400 km² 之间", area in 50.0..400.0, "实际 %.0f km²".format(area))

    // 潮白河河南村桥到密云水库大坝的直线距离，量级应该在 50km 上下
    val d = Geo.distanceMeters(40.1158, 116.6932, 40.4790, 116.8450)
    check("河南村桥到密云水库约 40-50km", d in 38_000.0..52_000.0, "实际 %.1f km".format(d / 1000))
}

// ---------------------------------------------------------------------------
// 列表筛选
// ---------------------------------------------------------------------------

private fun spot(
    id: String,
    name: String,
    district: String,
    lat: Double?,
    lon: Double?,
    fish: List<String>,
    access: AccessType = AccessType.FREE,
    methods: List<String> = listOf("路亚"),
    active: Boolean = true,
    accuracy: CoordAccuracy = CoordAccuracy.EXACT,
) = FishingSpot(
    id = id, name = name, district = district, address = "",
    latitude = lat, longitude = lon, coordAccuracy = accuracy,
    waterType = WaterType.RIVER, waterName = "测试河", accessType = access,
    fishingMethods = methods, targetFishIds = fish, terrain = listOf(Terrain.GENTLE_SLOPE),
    parking = null, notes = "", bestSeason = "", bestTimeOfDay = "", lures = emptyList(),
    isActive = active, source = "测试", sourceYear = 2026,
)

private fun testSpotFilter() {
    section("[12] 钓点列表筛选与排序")
    val spots = listOf(
        spot("a-near", "近点", "顺义区", 40.1100, 116.6900, listOf("qiaozui", "baitiao")),
        spot("b-far", "远点", "通州区", 40.3000, 116.9000, listOf("heiyu")),
        spot("c-paid", "收费点", "顺义区", 40.1150, 116.6950, listOf("qiaozui"), access = AccessType.PAID),
        spot("d-off", "已下架点", "顺义区", 40.1150, 116.6950, listOf("qiaozui"), active = false),
        spot("e-nocoord", "无坐标点", "怀柔区", null, null, listOf("makou"), accuracy = CoordAccuracy.UNVERIFIED),
        spot("f-taidao", "只适合台钓的点", "房山区", 39.7000, 115.9000, listOf("qiaozui"), methods = listOf("台钓")),
    )
    val userLat = 40.1000
    val userLon = 116.6800

    val all = SpotFilter.build(spots, fishList, emptyList(), month = 7, userLat = userLat, userLon = userLon)
    check("收费点被剔除", all.none { it.spot.id == "c-paid" })
    check("下架点被剔除", all.none { it.spot.id == "d-off" })
    check("不含路亚的台钓点被剔除", all.none { it.spot.id == "f-taidao" })
    check("其余 3 个点都在", all.size == 3, "实际 ${all.size}")
    check("最近的点排第一", all.first().spot.id == "a-near", all.first().spot.id)
    check("有坐标的排在无坐标的前面", all.last().spot.id == "e-nocoord", all.last().spot.id)
    check("距离算出来了", all.first().distanceKm != null && all.first().distanceKm!! < 2.0,
        "${all.first().distanceKm}")

    val byFish = SpotFilter.build(spots, fishList, emptyList(), month = 7, selectedFishIds = setOf("heiyu"))
    check("按鱼种筛选只剩黑鱼点", byFish.size == 1 && byFish.first().spot.id == "b-far", "实际 ${byFish.size}")

    val byDistrict = SpotFilter.build(spots, fishList, emptyList(), month = 7, district = "怀柔区")
    check("按区域筛选只剩怀柔", byDistrict.size == 1 && byDistrict.first().spot.id == "e-nocoord")

    val byRadius = SpotFilter.build(
        spots, fishList, emptyList(), month = 7,
        maxDistanceKm = 5, userLat = userLat, userLon = userLon,
    )
    check("5km 内只剩近点", byRadius.any { it.spot.id == "a-near" })
    check("远点被距离筛掉", byRadius.none { it.spot.id == "b-far" })
    check("无坐标点在距离筛选下保守保留", byRadius.any { it.spot.id == "e-nocoord" })

    val names = all.first { it.spot.id == "a-near" }.fishNames
    check("鱼种名映射正确", names == listOf("翘嘴"), names.toString())

    // 夹具里没有 baitiao，正好验证「引用了鱼种表里不存在的 id」不会被崩掉
    val withUnknown = SpotFilter.build(
        listOf(spot("z-unknown", "带未知鱼种的点", "顺义区", 40.11, 116.69, listOf("qiaozui", "no-such-fish"))),
        fishList, emptyList(), month = 7,
    )
    check("未知鱼种 id 被安全忽略", withUnknown.first().fishNames == listOf("翘嘴"),
        withUnknown.first().fishNames.toString())
}

private fun testSpotFilterBanOrdering() {
    section("[13] 禁钓点必须沉到列表底部")
    // 把禁钓方块盖在这个点上
    val inside = spot("x-inside", "禁区里的点", "密云区", 40.510, 116.900, listOf("qiaozui"))
    val outside = spot("y-outside", "正常点", "顺义区", 40.110, 116.690, listOf("qiaozui"))
    val cards = SpotFilter.build(
        listOf(inside, outside), fishList, listOf(squareBan), month = 7,
        userLat = 40.100, userLon = 116.680,
    )
    check("两个点都还在列表里（不静默隐藏）", cards.size == 2, "实际 ${cards.size}")
    check("禁区里的点被判 blocked", cards.first { it.spot.id == "x-inside" }.blocked)
    check("正常点没被判 blocked", !cards.first { it.spot.id == "y-outside" }.blocked)
    check("blocked 的点排在最后 —— 哪怕它离用户更远也一样", cards.last().spot.id == "x-inside",
        cards.last().spot.id)

    val quality = SpotFilter.dataQuality(cards)
    check("质量报告统计了禁钓风险点", quality["风险点"] == 1 || quality["禁钓风险点"] == 1, quality.toString())
    check("质量报告总数正确", quality["总数"] == 2)
}

// ---------------------------------------------------------------------------
// 出钓指数
// ---------------------------------------------------------------------------

private const val DAY_MS_T = 86_400_000L
private const val HOUR_MS = 3_600_000L

/** 造一个可控的天气快照，用来校准指数模型 */
private fun makeSnapshot(
    nowMillis: Long,
    tempC: Double,
    apparentC: Double,
    pressure: Double,
    windMs: Double,
    weatherCode: Int,
    waterTempC: Double,
    pressureTrendPerHour: Double = 0.0,
): WeatherSnapshot {
    val tz = java.util.TimeZone.getTimeZone("Asia/Shanghai")
    fun cal(ms: Long) = java.util.Calendar.getInstance(tz).apply { timeInMillis = ms }

    // 当天 6:00 日出、18:30 日落
    val sunrise = cal(nowMillis).apply {
        set(java.util.Calendar.HOUR_OF_DAY, 6); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val sunset = cal(nowMillis).apply {
        set(java.util.Calendar.HOUR_OF_DAY, 18); set(java.util.Calendar.MINUTE, 30)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    val current = WeatherPoint(
        timeMillis = nowMillis, temperatureC = tempC, apparentTemperatureC = apparentC,
        humidity = 45, windSpeedMs = windMs, windDirectionDeg = 280,
        pressureHpa = pressure, precipitationMm = 0.0, cloudCover = 20,
        weatherCode = weatherCode, isDay = nowMillis in sunrise..sunset,
    )

    // 前后各 24 小时的逐小时数据，气压按给定斜率变化
    val hourly = ArrayList<WeatherPoint>()
    for (h in -24..24) {
        val t = nowMillis + h * HOUR_MS
        val p = pressure + pressureTrendPerHour * h
        val c = cal(t)
        val hourOfDay = c.get(java.util.Calendar.HOUR_OF_DAY)
        hourly += WeatherPoint(
            timeMillis = t,
            temperatureC = tempC + if (hourOfDay in 11..15) 3.0 else -1.0,
            apparentTemperatureC = apparentC,
            humidity = 45,
            windSpeedMs = windMs,
            windDirectionDeg = 280,
            pressureHpa = p,
            precipitationMm = 0.0,
            cloudCover = 20,
            weatherCode = weatherCode,
            isDay = hourOfDay in 6..18,
        )
    }

    val daily = (0..6).map { d ->
        WeatherDay(
            dateLabel = "第$d 天",
            tempMaxC = tempC + 4, tempMinC = tempC - 4,
            precipitationMm = 0.0, weatherCode = weatherCode,
            sunriseMillis = sunrise + d * DAY_MS_T,
            sunsetMillis = sunset + d * DAY_MS_T,
        )
    }

    return WeatherSnapshot(
        placeName = "测试点", latitude = 40.0, longitude = 116.2,
        current = current, hourly = hourly, daily = daily,
        pastTemperatures = List(5) { waterTempC },
        fetchedAtMillis = nowMillis,
    )
}

/** 取当天某个钟点的时间戳 */
private fun atHour(baseMillis: Long, hour: Int, minute: Int = 0): Long {
    val tz = java.util.TimeZone.getTimeZone("Asia/Shanghai")
    return java.util.Calendar.getInstance(tz).apply {
        timeInMillis = baseMillis
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, minute)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun testFishingIndexCalibration() {
    section("[14] 出钓指数校准")
    val base = System.currentTimeMillis()

    // --- 场景 A：深秋夜里，9℃，水温 14.2℃（低于 16℃ 开口线）---
    val autumnNight = atHour(base, 22, 20)
    val a = FishingIndex.evaluate(
        makeSnapshot(autumnNight, 9.0, 6.0, 1019.0, 1.2, 0, 14.2), month = 10, nowMillis = autumnNight,
    )
    check("深秋冷夜不该给到「极佳」", a.grade != "极佳", "${a.score} 分 / ${a.grade}")
    check("深秋冷夜分数落在 50-84 之间", a.score in 50..84, "${a.score} 分")

    // --- 场景 A2：所有外部条件都完美，但水温就是没到开口线 ---
    // 早上 6:20 的日出窗口 + 气压稳定 + 微风 + 多云，唯独水温 14.2℃
    val perfectButCold = atHour(base, 6, 20)
    val a2 = FishingIndex.evaluate(
        makeSnapshot(perfectButCold, 12.0, 10.0, 1017.0, 2.0, 2, 14.2), month = 10, nowMillis = perfectButCold,
    )
    check("水温没到开口线时封顶在 82 分以内", a2.score <= 82, "${a2.score} 分 / ${a2.grade}")
    check("水温没到开口线时不给「极佳」", a2.grade != "极佳", a2.grade)
    check("水温确实被判为低于开口线",
        a2.factors.first { it.name == "水温" }.detail.contains("开口线"),
        a2.factors.first { it.name == "水温" }.detail)

    // --- 场景 B：理想的秋天傍晚，水温 20℃ ---
    val autumnEvening = atHour(base, 17, 30)
    val b = FishingIndex.evaluate(
        makeSnapshot(autumnEvening, 20.0, 19.0, 1015.0, 2.5, 2, 20.0), month = 10, nowMillis = autumnEvening,
    )
    check("理想秋天傍晚应该 85 分以上", b.score >= 85, "${b.score} 分 / ${b.grade}")
    check("理想条件评为极佳", b.grade == "极佳", b.grade)

    // --- 场景 C：盛夏正午，水温 30℃ ---
    val summerNoon = atHour(base, 13, 0)
    val c = FishingIndex.evaluate(
        makeSnapshot(summerNoon, 34.0, 38.0, 1004.0, 0.3, 0, 30.0), month = 7, nowMillis = summerNoon,
    )
    check("盛夏正午明显扣分", c.score < 70, "${c.score} 分 / ${c.grade}")

    // --- 场景 D：冬天，水温 4℃ ---
    val winter = atHour(base, 10, 0)
    val d = FishingIndex.evaluate(
        makeSnapshot(winter, -2.0, -6.0, 1025.0, 3.0, 1, 4.0), month = 1, nowMillis = winter,
    )
    check("冬季低分", d.score < 55, "${d.score} 分 / ${d.grade}")

    // --- 场景 E：雷暴天再好的温度也该压下去 ---
    val storm = atHour(base, 16, 0)
    val e = FishingIndex.evaluate(
        makeSnapshot(storm, 22.0, 22.0, 1012.0, 3.0, 95, 20.0), month = 9, nowMillis = storm,
    )
    check("雷暴天分数明显低于同温好天气", e.score < b.score - 10, "雷暴 ${e.score} vs 好天 ${b.score}")

    // --- 场景 F：气压骤降 ---
    val dropping = atHour(base, 16, 0)
    val f = FishingIndex.evaluate(
        makeSnapshot(dropping, 20.0, 20.0, 1015.0, 2.0, 2, 20.0, pressureTrendPerHour = -2.0),
        month = 10, nowMillis = dropping,
    )
    check("气压骤降会扣分", f.score < b.score, "骤降 ${f.score} vs 稳定 ${b.score}")

    // --- 因子明细完整性 ---
    check("因子数量为 7 项", a.factors.size == 7, "${a.factors.size}")
    check("各因子得分不超过满分", a.factors.all { it.score <= it.maxScore }, a.factors.joinToString { "${it.name}:${it.score}/${it.maxScore}" })
    check("总分等于各因子之和（裁剪前）", a.factors.sumOf { it.score } >= a.score)
    check("水温因子权重最高", a.factors.first { it.name == "水温" }.maxScore == 22)

    // --- 推荐时段 ---
    val windows = FishingIndex.bestWindows(
        makeSnapshot(base, 18.0, 18.0, 1015.0, 2.0, 2, 18.0), nowMillis = base, limit = 3,
    )
    check("给出了推荐时段", windows.isNotEmpty(), "${windows.size}")
    check("时段标签带今天/明天字样",
        windows.all { it.label.startsWith("今天") || it.label.startsWith("明天") || it.label.startsWith("后天") },
        windows.joinToString { it.label })
    check("时段标签互不重复", windows.map { it.label }.toSet().size == windows.size,
        windows.joinToString { it.label })
    check("时段按时间先后排列", windows.zipWithNext().all { (x, y) -> x.startMillis <= y.startMillis })

    // --- 穿衣 ---
    check("30℃ 穿短袖", FishingIndex.clothingFor(30.0, 1.0, 0.0).level == "酷热")
    check("20℃ 舒适", FishingIndex.clothingFor(20.0, 1.0, 0.0).level == "舒适")
    check("5℃ 穿羽绒服", FishingIndex.clothingFor(5.0, 1.0, 0.0).headline.contains("羽绒"))
    check("大风会提醒防风", FishingIndex.clothingFor(15.0, 7.0, 0.0).detail.any { it.contains("风大") })
    check("下雨会提醒带雨衣", FishingIndex.clothingFor(15.0, 1.0, 3.0).detail.any { it.contains("雨") })

    // --- 水温估算 ---
    val wt = FishingIndex.estimateWaterTemp(List(5) { 10.0 }, 20.0)
    check("水温估算被当前气温拉动但不会等于气温", wt > 10.0 && wt < 20.0, "%.1f".format(wt))
    check("极端高温下水温被限制在 33℃ 以内", FishingIndex.estimateWaterTemp(List(5) { 45.0 }, 45.0) <= 33.0)
    check("极端低温下水温不为负", FishingIndex.estimateWaterTemp(List(5) { -20.0 }, -20.0) >= 0.0)
}
