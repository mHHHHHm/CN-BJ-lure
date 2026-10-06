package com.bjlure.app.domain.model

/**
 * 领域模型。
 *
 * 这一层刻意不引用任何 Android / Room 类型，好处是可以用纯 Kotlin 编译器
 * 离线编译并跑单元测试，业务规则不会因为「跑不起来」而失守。
 */

/** 水域类型 */
enum class WaterType(val label: String) {
    RIVER("河流"),
    RESERVOIR("水库"),
    POND("池塘"),
    WETLAND("湿地"),
    CANAL("渠道"),
}

/**
 * 钓点来源。这不是分类癖 —— 官方公布的点有文件背书，
 * 民间分享的点只能自己现场判断，用户有权知道自己在看哪一种。
 */
enum class SpotSourceType(val label: String) {
    OFFICIAL("官方公布"),
    WILD("民间野钓点"),
    RISKY("管制易变"),
}

/** 是否收费。按产品要求，收费钓场不进入任何列表。 */
enum class AccessType(val label: String) {
    FREE("免费"),
    PAID("收费"),
}

/**
 * 坐标可信度。不编造坐标：查不到确切位置的点如实降级，App 里明确提示用户。
 * EXACT       —— 有可核对的坐标（OSM 水体/桥梁、官方文件点名地标）
 * APPROXIMATE —— 地标级近似，误差可能几百米到一两公里
 * UNVERIFIED  —— 只有地名，没有可信坐标，列表可展示但不落图
 */
enum class CoordAccuracy(val label: String, val showOnMap: Boolean) {
    EXACT("坐标已核实", true),
    APPROXIMATE("坐标为大致位置", true),
    UNVERIFIED("位置待核实", false),
}

/** 拟饵所在水层 */
enum class WaterLayer(val label: String) {
    SURFACE("表层"),
    SUBSURFACE("亚表层"),
    MID("中层"),
    BOTTOM("底层"),
}

/** 鱼的活动水层 */
enum class FishCategory(val label: String) {
    SURFACE("中上层"),
    MID("中层"),
    BOTTOM("底层"),
    ALL("全水层"),
}

/** 拟饵分类 */
enum class LureCategory(val label: String) {
    MINNOW("米诺"),
    METAL_JIG("铁板"),
    SPOON("亮片"),
    FROG("雷蛙"),
    SOFT_PLASTIC("软虫"),
    CRANK("摇滚"),
    PENCIL("铅笔"),
    VIB("VIB"),
    JIG_HEAD("铅头钩"),
}

/**
 * 装备档位。钓友圈子里真正在用的分类方式，比按饵型分更实用：
 * 新手最先要决定的就是「我这一套是玩微物还是玩泛用」。
 */
enum class GearClass(val label: String, val hint: String) {
    MICRO("微物", "UL/L 软调竿 + 1000 型轮 + 0.4-0.8 号 PE，1-5g 小饵，主打马口、白条、小翘嘴"),
    GENERAL("泛用", "ML/M 调竿 + 2000-2500 型轮 + 0.8-1.2 号 PE，5-15g，什么都能钓一点"),
    HEAVY("雷强", "MH/XH 硬竿 + 鼓轮或大号水滴轮 + 5 号雷强线，重草区把黑鱼直接拔出来"),
    DISTANCE("远投", "M/MH 长竿 + 大线杯，15-25g 铁板，搜大水面翘嘴"),
}

/**
 * 禁钓区类型。
 *
 * 这里是对需求文档做的一处关键修正：文档把「全年禁渔河道」和「饮用水源保护区」
 * 并列放进同一张禁钓表，但二者法律后果完全不同。
 * 北京市农业农村局京政农发〔2019〕63 号通告明确：
 * 「禁止作业类型：除钓具（不包括河湖延绳钓）之外的所有作业方式。」
 * 也就是说禁渔区/禁渔期内，一人一杆一饵一钩的娱乐性垂钓是允许的。
 * 因此本模型用 [blocksAngling] 区分「真的不能下杆」和「只是限制作业方式」。
 */
enum class ZoneType(val label: String, val blocksAngling: Boolean) {
    /** 饮用水水源一级保护区、市级河湖禁游区 —— 真禁止垂钓 */
    WATER_SOURCE_PROTECTION("饮用水水源一级保护区 / 河湖禁游区", true),

    /** 增殖放流水域（京农发〔2008〕180 号）—— 真禁止垂钓 */
    PROPAGATION_RELEASE("增殖放流水域", true),

    /** 公园内的非钓鱼区 —— 真禁止垂钓 */
    PARK_NO_FISHING("公园非钓鱼区", true),

    /** 全年禁渔区：只禁网捕、电毒、多钩延绳等，钓具垂钓不受限 */
    PERMANENT_FISHING_BAN("全年禁渔区（钓具除外）", false),

    /** 季节性禁渔区：同上，只是限定在禁渔期内 */
    SEASONAL_FISHING_BAN("季节性禁渔区（钓具除外）", false),
}

/** 边界形态 */
enum class BoundaryType { POINT_RADIUS, POLYGON }

/** 地形，用于饵料匹配打分 */
enum class Terrain(val label: String) {
    GENTLE_SLOPE("缓坡"),
    ROCK_PILE("石头堆"),
    WEED_BED("水草区"),
    DEEP_POOL("深潭"),
    SHALLOW_FLAT("浅滩"),
    STRUCTURE("桥墩/闸口结构"),
    FLOWING("流水"),
    UNKNOWN("未知"),
}

// ---------------------------------------------------------------------------
// 实体
// ---------------------------------------------------------------------------

data class FishingSpot(
    val id: String,
    val name: String,
    val district: String,
    /** 可导航的地标描述 */
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val coordAccuracy: CoordAccuracy,
    /**
     * 钓点覆盖范围半径（米）。地图上按这个画圈 ——
     * 一个钓点本来就不该是地图上的一个针尖，而是一片能下杆的水面。
     */
    val radiusMeters: Int = 300,
    val waterType: WaterType,
    val waterName: String,
    val accessType: AccessType,
    /** 官方公布 / 民间分享 / 管制易变 */
    val sourceType: SpotSourceType = SpotSourceType.WILD,
    /** 适合的钓法，路亚必然包含 */
    val fishingMethods: List<String>,
    val targetFishIds: List<String>,
    val terrain: List<Terrain>,
    val parking: Boolean?,
    /** 现场提示 */
    val notes: String,
    /** 出钓窗口 */
    val bestSeason: String,
    val bestTimeOfDay: String,
    val lures: List<String>,
    val isActive: Boolean,
    val source: String,
    val sourceYear: Int,
    /** 已知的合规风险，会以醒目方式呈现 */
    val legalWarning: String? = null,
)

data class FishSpecies(
    val id: String,
    val name: String,
    val aliases: List<String>,
    val category: FishCategory,
    val lureActiveSeason: String,
    /** 路亚有效月份，用于按出钓月份加权 */
    val activeMonths: List<Int>,
    val bestTimeOfDay: String,
    val habitatPreference: String,
    /** 在北京水域的常见程度 0..100，用于给推荐排序加权重 */
    val abundance: Int,
)

data class Lure(
    val id: String,
    val name: String,
    val category: LureCategory,
    val targetFishIds: List<String>,
    val waterLayer: WaterLayer,
    val weightRange: String,
    val actionStyle: String,
    val sceneNote: String,
    /** 适合的地形 */
    val goodTerrain: List<Terrain>,
    /** 属于哪个装备档位 */
    val gearClass: GearClass = GearClass.GENERAL,
)

data class NoFishingZone(
    val id: String,
    val name: String,
    val type: ZoneType,
    val boundaryType: BoundaryType,
    val centerLat: Double? = null,
    val centerLng: Double? = null,
    val radiusMeters: Int? = null,
    /**
     * 多边形外环集合（MultiPolygon）。密云水库这类水体在真实数据里是几十块
     * 互不相连的水面，用单环表达会漏判。每环为 [纬度, 经度] 序列。
     */
    val polygons: List<List<Pair<Double, Double>>> = emptyList(),
    /** 季节性生效月份，空表示全年 */
    val activeMonths: List<Int> = emptyList(),
    val ruleDescription: String,
    val legalBasis: String,
)

/** 是否需要拦住用户 */
enum class GuardLevel {
    /** 可以钓 */
    OK,

    /** 落在「只禁作业方式」的禁渔区里 —— 可以钓，给提示 */
    INFO,

    /** 靠近真正的禁钓区 —— 黄色警告 */
    NEAR_BAN,

    /** 落在真正的禁钓区里 —— 红色拦截 */
    IN_BAN,
}

data class GuardHit(
    val zone: NoFishingZone,
    val distanceMeters: Double,
    val inside: Boolean,
    val activeNow: Boolean,
)

data class GuardResult(
    val level: GuardLevel,
    val headline: String,
    val detail: String,
    val hits: List<GuardHit>,
) {
    val blocked: Boolean get() = level == GuardLevel.IN_BAN
    val warn: Boolean get() = level == GuardLevel.IN_BAN || level == GuardLevel.NEAR_BAN
}

data class LureRecommendation(
    val lure: Lure,
    val confidence: Int,
    val tip: String,
    val matchedTerrain: List<Terrain>,
    /**
     * 这个饵在**当前场景**下匹配到的鱼（钓点详情页用）。
     *
     * 例：在只出白条和黑鱼的钓点上，某个饵只会列出这两种里它真能钓的。
     */
    val matchedFishNames: List<String> = emptyList(),
    /**
     * 这个饵**本身**能钓的所有鱼（饵料页用）。
     *
     * 新手看「旋转亮片」不知道它能钓什么；只列当前选中的那一种又太单薄，
     * 所以这里给全量，让他知道这一枚饵的适用面有多宽。
     */
    val allTargetFishNames: List<String> = emptyList(),
)
