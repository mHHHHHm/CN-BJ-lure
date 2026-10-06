package com.bjlure.app.domain

import com.bjlure.app.domain.model.GuardHit
import com.bjlure.app.domain.model.GuardLevel
import com.bjlure.app.domain.model.GuardResult
import com.bjlure.app.domain.model.NoFishingZone

/**
 * 禁钓校验引擎 —— App 的合规底线。
 *
 * 设计要点（这里和常见做法不一样，值得说明）：
 *
 * 1. 「禁渔区」不等于「禁钓区」。
 *    京政农发〔2019〕63 号通告写明禁止的是「除钓具（不包括河湖延绳钓）之外的所有作业方式」。
 *    所以永定河、潮白河、温榆河、清河这些全年禁渔河道，一人一杆一饵一钩照样能钓。
 *    把这类水域标成红色禁钓，是把用户往错误的合规判断上带。
 *
 * 2. 只有三类水域真的不能下杆：饮用水水源一级保护区（含市级河湖禁游区）、
 *    增殖放流水域、公园内的非钓鱼区。
 *
 * 3. 季节判断要按出钓日期算，不能只看当前时间。
 */
object NoFishingGuard {

    /** 靠近真禁钓区多少米以内就给黄色警告 */
    const val NEAR_BAN_WARN_METERS = 500.0

    /** 季节性禁渔的默认月份（4 月 1 日 — 9 月 24 日） */
    val SEASONAL_BAN_MONTHS = listOf(4, 5, 6, 7, 8, 9)

    /**
     * 校验一个坐标。
     *
     * @param month 出钓月份 1..12，默认取当前月
     */
    fun check(
        lat: Double?,
        lon: Double?,
        zones: List<NoFishingZone>,
        month: Int,
    ): GuardResult {
        if (lat == null || lon == null) {
            return GuardResult(
                level = GuardLevel.INFO,
                headline = "该钓点坐标待核实",
                detail = "没有可信坐标，无法做禁钓区距离校验。出发前请以现场标识和管理人员口径为准。",
                hits = emptyList(),
            )
        }

        val hits = ArrayList<GuardHit>(4)
        for (zone in zones) {
            val inside = Geo.isInsideZone(lat, lon, zone)
            val dist = if (inside) 0.0 else Geo.distanceToZoneMeters(lat, lon, zone)
            val activeNow = isActiveInMonth(zone, month)

            // 只关心「真禁钓」和「距离很近的」两类，避免提示信息刷屏
            val blocks = zone.type.blocksAngling
            val relevant = when {
                inside -> true
                blocks && dist <= NEAR_BAN_WARN_METERS -> true
                else -> false
            }
            if (relevant && activeNow) {
                hits += GuardHit(zone, dist, inside, true)
            }
        }

        val blockingInside = hits.filter { it.inside && it.zone.type.blocksAngling }
        if (blockingInside.isNotEmpty()) {
            val z = blockingInside.first().zone
            return GuardResult(
                level = GuardLevel.IN_BAN,
                headline = "这里是禁止垂钓水域",
                detail = "该坐标落在「${z.name}」范围内。${z.ruleDescription}\n依据：${z.legalBasis}",
                hits = hits,
            )
        }

        val blockingNear = hits.filter { !it.inside && it.zone.type.blocksAngling }
            .sortedBy { it.distanceMeters }
        if (blockingNear.isNotEmpty()) {
            val h = blockingNear.first()
            return GuardResult(
                level = GuardLevel.NEAR_BAN,
                headline = "附近有禁止垂钓区",
                detail = "距「${h.zone.name}」约 ${h.distanceMeters.toInt()} 米，" +
                    "注意别越界。${h.zone.ruleDescription}\n依据：${h.zone.legalBasis}",
                hits = hits,
            )
        }

        // 落在「只禁作业方式」的禁渔区里：可以钓，但要讲清楚边界
        val bans = hits.filter { it.zone.type == com.bjlure.app.domain.model.ZoneType.PERMANENT_FISHING_BAN }
        val seasonal = hits.filter { it.zone.type == com.bjlure.app.domain.model.ZoneType.SEASONAL_FISHING_BAN }
        if (bans.isNotEmpty() || seasonal.isNotEmpty()) {
            val names = (bans + seasonal).joinToString("、") { it.zone.name }
            val inSeason = seasonal.isNotEmpty()
            val headline = if (inSeason) "当前处于禁渔期，但钓具垂钓不受限" else "该水域属禁渔区，钓具垂钓不受限"
            val detail = buildString {
                append("「$names」按通告禁止的是：除钓具（不包括河湖延绳钓）之外的所有作业方式，")
                append("也就是网捕、电鱼、毒鱼、多钩延绳钓这些。")
                append("\n一人一杆一饵一钩的娱乐性垂钓不在禁止范围内，可以正常作钓。")
                if (inSeason) {
                    append("\n季节性禁渔期为 4 月 1 日 0 时至 9 月 24 日 24 时，")
                    append("当前月份（$month 月）在禁渔期内，仍只限制非钓具作业。")
                }
                append("\n依据：京政农发〔2019〕63 号《关于调整禁渔区、禁渔期的通告》。")
            }
            return GuardResult(GuardLevel.INFO, headline, detail, hits)
        }

        return GuardResult(
            level = GuardLevel.OK,
            headline = "未发现禁钓限制",
            detail = "该坐标不在已知禁钓区范围内。仍请留意现场标识与临时管制公告。",
            hits = emptyList(),
        )
    }

    /** 季节性规定是否在当前月份生效；activeMonths 为空表示全年生效 */
    fun isActiveInMonth(zone: NoFishingZone, month: Int): Boolean {
        if (zone.activeMonths.isEmpty()) return true
        return zone.activeMonths.contains(month)
    }

    /**
     * 给一批钓点做批量体检，返回「有问题的」点及其结论。
     * 用于数据自检：种子数据里如果有钓点落在禁钓区里，应该当场发现。
     */
    fun audit(
        spots: List<Pair<String, Pair<Double, Double>?>>,
        zones: List<NoFishingZone>,
        month: Int,
    ): List<Triple<String, GuardLevel, String>> {
        return spots.map { (id, coord) ->
            val r = check(coord?.first, coord?.second, zones, month)
            Triple(id, r.level, r.headline)
        }
    }
}
