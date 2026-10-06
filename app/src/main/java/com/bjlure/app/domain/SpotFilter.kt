package com.bjlure.app.domain

import com.bjlure.app.domain.model.FishSpecies
import com.bjlure.app.domain.model.FishingSpot
import com.bjlure.app.domain.model.GearClass
import com.bjlure.app.domain.model.GuardLevel
import com.bjlure.app.domain.model.GuardResult
import com.bjlure.app.domain.model.Lure
import com.bjlure.app.domain.model.NoFishingZone

/**
 * 钓点列表的筛选、排序与禁钓体检。
 *
 * 放在 domain 层而不是 ViewModel 里，是为了能用纯 Kotlin 单元测试覆盖
 * 「筛选 + 排序 + 合规标记」这套组合逻辑 —— 这恰恰是最容易写错、
 * 又最不容易在真机上发现的那部分。
 */
object SpotFilter {

    data class Card(
        val spot: FishingSpot,
        val distanceKm: Double?,
        val guard: GuardResult,
        val fishNames: List<String>,
    ) {
        /** 落在真正的禁钓区里 */
        val blocked: Boolean get() = guard.level == GuardLevel.IN_BAN

        /** 需要提醒但不禁 */
        val needsAttention: Boolean get() = guard.level == GuardLevel.NEAR_BAN

        val hasWarning: Boolean get() = blocked || needsAttention
    }

    fun build(
        spots: List<FishingSpot>,
        fish: List<FishSpecies>,
        zones: List<NoFishingZone>,
        month: Int,
        selectedFishIds: Set<String> = emptySet(),
        district: String? = null,
        maxDistanceKm: Int? = null,
        userLat: Double? = null,
        userLon: Double? = null,
        /** 装备档位筛选：微物 / 泛用 / 雷强 / 远投 */
        gear: GearClass? = null,
        /** 判断钓点适不适合某个档位，要靠拟饵库反推 */
        lures: List<Lure> = emptyList(),
    ): List<Card> {
        val fishById = fish.associateBy { it.id }

        val cards = ArrayList<Card>(spots.size)
        for (spot in spots) {
            // 免费是硬性条件，这里再兜一次底，防止有人绕过 DAO 直接把收费点喂进来
            if (spot.accessType != com.bjlure.app.domain.model.AccessType.FREE) continue
            if (!spot.isActive) continue
            if (!spot.fishingMethods.contains("路亚")) continue

            if (selectedFishIds.isNotEmpty() && spot.targetFishIds.none { selectedFishIds.contains(it) }) {
                continue
            }
            if (district != null && spot.district != district) continue

            // 档位筛选：拿这个点的鱼种和地形去反推拟饵，看有没有该档位的饵可用
            if (gear != null) {
                if (lures.isEmpty()) continue
                val recs = LureMatcher.recommend(spot.targetFishIds, spot.terrain, lures, fish, month)
                if (recs.none { it.lure.gearClass == gear }) continue
            }

            val distanceKm = if (userLat != null && userLon != null &&
                spot.latitude != null && spot.longitude != null
            ) {
                Geo.distanceMeters(userLat, userLon, spot.latitude, spot.longitude) / 1000.0
            } else {
                null
            }

            if (maxDistanceKm != null) {
                // 没有坐标的点在「按距离筛选」时保守地保留，宁可多显示也不要让用户漏掉好点
                if (distanceKm != null && distanceKm > maxDistanceKm) continue
            }

            val guard = NoFishingGuard.check(spot.latitude, spot.longitude, zones, month)
            cards += Card(
                spot = spot,
                distanceKm = distanceKm,
                guard = guard,
                fishNames = spot.targetFishIds.mapNotNull { fishById[it]?.name },
            )
        }

        return cards.sortedWith(ordering())
    }

    /** 排序：能钓的优先 → 有距离的按近到远 → 其余按名称 */
    private fun ordering(): Comparator<Card> = Comparator { a, b ->
        if (a.blocked != b.blocked) {
            return@Comparator if (a.blocked) 1 else -1
        }
        val da = a.distanceKm
        val db = b.distanceKm
        if (da != null && db != null) {
            val c = da.compareTo(db)
            if (c != 0) return@Comparator c
        } else if (da != null) {
            return@Comparator -1
        } else if (db != null) {
            return@Comparator 1
        }
        a.spot.name.compareTo(b.spot.name)
    }

    /** 汇总一份数据质量报告，App 的「关于数据」页会展示 */
    fun dataQuality(cards: List<Card>): Map<String, Int> {
        val map = LinkedHashMap<String, Int>()
        map["总数"] = cards.size
        map["坐标已核实"] = cards.count { it.spot.coordAccuracy == com.bjlure.app.domain.model.CoordAccuracy.EXACT }
        map["坐标为大致位置"] = cards.count { it.spot.coordAccuracy == com.bjlure.app.domain.model.CoordAccuracy.APPROXIMATE }
        map["位置待核实"] = cards.count { it.spot.coordAccuracy == com.bjlure.app.domain.model.CoordAccuracy.UNVERIFIED }
        map["禁钓风险点"] = cards.count { it.blocked }
        map["邻近禁钓区"] = cards.count { it.needsAttention }
        return map
    }
}
