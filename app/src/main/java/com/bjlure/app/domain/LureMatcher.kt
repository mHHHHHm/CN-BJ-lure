package com.bjlure.app.domain

import com.bjlure.app.domain.model.FishSpecies
import com.bjlure.app.domain.model.Lure
import com.bjlure.app.domain.model.LureRecommendation
import com.bjlure.app.domain.model.Terrain

/**
 * 鱼种 → 路亚饵 匹配引擎。
 *
 * 打分是显式的、可解释的：每一分来自哪一条规则都能在 [explain] 里看到，
 * 免得用户看到「推荐度 87」却不知道凭什么。
 */
object LureMatcher {

    /** 只有拟饵明确适配该鱼种才会进入候选，否则根本不该出现在列表里 */
    private const val SCORE_TARGET_MATCH = 45

    /** 地形对味 */
    private const val SCORE_TERRAIN_PER_HIT = 6
    private const val SCORE_TERRAIN_CAP = 18

    /** 季节对味 */
    private const val SCORE_IN_SEASON = 12
    private const val SCORE_OUT_OF_SEASON = -15

    /** 鱼在北京水域越常见，推荐越靠前 */
    private const val ABUNDANCE_WEIGHT = 0.18

    private const val BASE_SCORE = 40

    /**
     * 给指定鱼种 + 地形 + 月份产出拟饵推荐。
     *
     * @param fishIds  钓点目标鱼种
     * @param terrain  钓点地形
     * @param month    出钓月份 1..12
     */
    fun recommend(
        fishIds: List<String>,
        terrain: List<Terrain>,
        lures: List<Lure>,
        fish: List<FishSpecies>,
        month: Int,
        limit: Int = 6,
    ): List<LureRecommendation> {
        if (fishIds.isEmpty()) return emptyList()
        val fishById = fish.associateBy { it.id }
        val targets = fishIds.mapNotNull { fishById[it] }
        if (targets.isEmpty()) return emptyList()

        val out = ArrayList<Pair<Int, LureRecommendation>>(lures.size)
        for (lure in lures) {
            val matchedFish = targets.filter { lure.targetFishIds.contains(it.id) }
            if (matchedFish.isEmpty()) continue

            var score = BASE_SCORE + SCORE_TARGET_MATCH

            val hitTerrain = lure.goodTerrain.filter { terrain.contains(it) }
            score += minOf(SCORE_TERRAIN_CAP, hitTerrain.size * SCORE_TERRAIN_PER_HIT)

            // 按最贴合的那个目标鱼算季节与常见度
            val best = matchedFish.maxByOrNull { it.abundance } ?: matchedFish.first()
            val inSeason = best.activeMonths.contains(month)
            score += if (inSeason) SCORE_IN_SEASON else SCORE_OUT_OF_SEASON
            score += (best.abundance * ABUNDANCE_WEIGHT).toInt()

            // 展示分做上限裁剪，但排序必须用未裁剪的原始分。
            // 否则多个饵一起顶到上限后区分度全丢，会被无关的并列规则决定先后。
            val display = score.coerceIn(5, 99)
            out += score to LureRecommendation(
                lure = lure,
                confidence = display,
                tip = buildTip(lure, best, hitTerrain, inSeason, month),
                matchedTerrain = hitTerrain,
                matchedFishNames = matchedFish.map { it.name },
                allTargetFishNames = lure.targetFishIds.mapNotNull { fishById[it]?.name },
            )
        }
        return out
            .sortedWith(
                compareByDescending<Pair<Int, LureRecommendation>> { it.first }
                    .thenByDescending { it.second.matchedTerrain.size }
                    .thenByDescending { it.second.lure.targetFishIds.size }
                    .thenBy { it.second.lure.id }
            )
            .map { it.second }
            .take(limit)
    }

    /** 给某一条鱼单独出推荐，用于「鱼种工具」页 */
    fun recommendForFish(
        fishId: String,
        terrain: List<Terrain>,
        lures: List<Lure>,
        fish: List<FishSpecies>,
        month: Int,
        limit: Int = 6,
    ): List<LureRecommendation> = recommend(listOf(fishId), terrain, lures, fish, month, limit)

    private fun buildTip(
        lure: Lure,
        fish: FishSpecies,
        hitTerrain: List<Terrain>,
        inSeason: Boolean,
        month: Int,
    ): String {
        val sb = StringBuilder()
        sb.append(lure.sceneNote)
        if (hitTerrain.isNotEmpty()) {
            sb.append("　地形匹配：")
            sb.append(hitTerrain.joinToString("、") { it.label })
            sb.append("。")
        }
        sb.append("　手法：").append(lure.actionStyle).append("，").append(lure.weightRange).append("。")
        if (!inSeason) {
            sb.append("　注意：").append(fish.name).append("在 $month 月属于淡季（")
                .append(fish.lureActiveSeason).append("），口可能很轻。")
        }
        return sb.toString()
    }

    /**
     * 输出可读的打分依据，用于调试和「为什么推荐它」的展开说明。
     */
    fun explain(
        rec: LureRecommendation,
        fishIds: List<String>,
        terrain: List<Terrain>,
        fish: List<FishSpecies>,
        month: Int,
    ): List<String> {
        val lines = ArrayList<String>(4)
        val matched = fishIds.mapNotNull { id -> fish.firstOrNull { it.id == id } }
            .filter { rec.lure.targetFishIds.contains(it.id) }
        lines += "适配鱼种：${matched.joinToString("、") { it.name }}（+$SCORE_TARGET_MATCH）"
        if (rec.matchedTerrain.isNotEmpty()) {
            lines += "地形对味：${rec.matchedTerrain.joinToString("、") { it.label }}" +
                "（+${minOf(SCORE_TERRAIN_CAP, rec.matchedTerrain.size * SCORE_TERRAIN_PER_HIT)}）"
        }
        val best = matched.maxByOrNull { it.abundance }
        if (best != null) {
            lines += if (best.activeMonths.contains(month)) {
                "$month 月正处于${best.name}的路亚旺季（+$SCORE_IN_SEASON）"
            } else {
                "$month 月不在${best.name}的旺季（${best.lureActiveSeason}）（$SCORE_OUT_OF_SEASON）"
            }
        }
        lines += "基础分 $BASE_SCORE，最终 ${rec.confidence}"
        return lines
    }
}
