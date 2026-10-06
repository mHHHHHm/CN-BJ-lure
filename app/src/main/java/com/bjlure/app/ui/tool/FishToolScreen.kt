package com.bjlure.app.ui.tool

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bjlure.app.domain.model.FishSpecies
import com.bjlure.app.domain.model.GearClass
import com.bjlure.app.domain.model.LureRecommendation
import com.bjlure.app.domain.model.Terrain
import com.bjlure.app.ui.FishingViewModel
import com.bjlure.app.ui.theme.GoGreen
import com.bjlure.app.ui.theme.WarnOrange
import com.bjlure.app.ui.theme.selectedChipColors

/**
 * 鱼种 → 路亚饵 匹配工具页。
 *
 * 这里回答的是钓友最常问的两个问题：
 * 「钓这个鱼用什么饵」以及「我这套装备该买哪些饵」——
 * 后者就是装备档位（微物 / 泛用 / 雷强 / 远投）那一排筛选。
 */
@Composable
fun FishToolScreen(viewModel: FishingViewModel) {
    val fishList = remember { viewModel.allFish() }
    var selectedFishId by remember { mutableStateOf(fishList.firstOrNull()?.id) }
    var terrainFilter by remember { mutableStateOf(setOf<Terrain>()) }
    var gearFilter by remember { mutableStateOf<GearClass?>(null) }

    val selected = fishList.firstOrNull { it.id == selectedFishId }
    val recs = remember(selectedFishId, terrainFilter, gearFilter, viewModel.currentMonthValue()) {
        selectedFishId?.let {
            viewModel.recommendationsForFish(it, terrainFilter.toList(), gearFilter)
        } ?: emptyList()
    }
    val month = viewModel.currentMonthValue()

    // 这个鱼种每个档位各有多少款饵。没货的档位要置灰，
    // 否则新手选了「泛用」去钓白条，只会看到一句「没有匹配的」，然后不知所措。
    val gearCounts = remember(selectedFishId) {
        selectedFishId?.let { viewModel.gearCountsForFish(it) } ?: emptyMap()
    }

    // 换了鱼种之后，原来选的档位可能在这个鱼种下根本没饵，那就自动落回「不限」
    LaunchedEffect(selectedFishId, gearCounts) {
        if (gearFilter != null && (gearCounts[gearFilter] ?: 0) == 0) {
            gearFilter = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = "当前按 $month 月的鱼情推荐",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
                Spacer(Modifier.height(6.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(fishList, key = { it.id }) { f ->
                        FilterChip(
                            selected = f.id == selectedFishId,
                            onClick = { selectedFishId = f.id },
                            label = { Text(f.name, fontSize = 13.sp) },
                            colors = selectedChipColors(),
                        )
                    }
                }
            }
        }

        if (selected == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("鱼种数据还没载入", fontSize = 14.sp)
            }
        } else {
            FishLureList(
                selected = selected,
                month = month,
                terrainFilter = terrainFilter,
                gearFilter = gearFilter,
                gearCounts = gearCounts,
                recs = recs,
                onToggleTerrain = { t ->
                    terrainFilter = if (terrainFilter.contains(t)) terrainFilter - t else terrainFilter + t
                },
                onToggleGear = { g -> gearFilter = if (gearFilter == g) null else g },
            )
        }
    }
}

/**
 * 没选档位时的提示语。
 *
 * 直接点名这个鱼该用哪一档 —— 新手不知道「白条 = 微物」，
 * 与其让他自己试错撞空列表，不如把答案写在他眼前。
 */
private fun buildGearHint(fishName: String, counts: Map<GearClass, Int>): String {
    if (counts.isEmpty()) return "这款鱼暂时没有对应的假饵记录。"
    val top = counts.maxByOrNull { it.value }!!.key
    val others = counts.keys.filter { it != top }
    return buildString {
        append("「").append(fishName).append("」主要用「").append(top.label).append("」档的假饵")
        if (others.isNotEmpty()) {
            append("，也能用 ").append(others.joinToString("、") { it.label })
        }
        append("。灰色档位表示这款鱼没有对应的饵，点了也不会有结果。")
    }
}

@Composable
private fun FishLureList(
    selected: FishSpecies,
    month: Int,
    terrainFilter: Set<Terrain>,
    gearFilter: GearClass?,
    gearCounts: Map<GearClass, Int>,
    recs: List<LureRecommendation>,
    onToggleTerrain: (Terrain) -> Unit,
    onToggleGear: (GearClass) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(selected.name, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.size(8.dp))
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    RoundedCornerShape(20.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(selected.category.label, fontSize = 11.sp)
                        }
                        // 没有假饵的鱼种直接在标题上打标，不用点进去才知道
                        if (gearCounts.isEmpty()) {
                            Spacer(Modifier.size(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(WarnOrange.copy(alpha = 0.16f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("台钓对象鱼", fontSize = 11.sp, color = WarnOrange)
                            }
                        }
                    }
                    if (selected.aliases.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "别名：${selected.aliases.joinToString("、")}",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    LabeledLine("路亚旺季", selected.lureActiveSeason)
                    LabeledLine("最佳时段", selected.bestTimeOfDay)
                    LabeledLine("栖息偏好", selected.habitatPreference)

                    // 本鱼种没有对应假饵时，把话说在前面
                    if (gearCounts.isEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(WarnOrange.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                "⚠️ 本鱼种不适合假饵作钓：它不追假饵，捕获基本靠台钓。"
                                    + "所以下面不会有假饵推荐，这不是数据缺失。",
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                                color = WarnOrange,
                            )
                        }
                    }
                }
            }
        }

        // ---- 装备档位 ----
        item {
            Text(
                "你玩的是哪一档（决定买什么饵）",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
        item {
            Column {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = gearFilter == null,
                            onClick = { if (gearFilter != null) onToggleGear(gearFilter!!) },
                            label = { Text("不限", fontSize = 12.sp) },
                        )
                    }
                    items(GearClass.entries.toList(), key = { it.name }) { g ->
                        val count = gearCounts[g] ?: 0
                        FilterChip(
                            selected = gearFilter == g,
                            // 这个鱼种没有该档位的饵就直接禁用，别让新手点进去撞空列表
                            enabled = count > 0,
                            onClick = { onToggleGear(g) },
                            label = {
                                Text(
                                    if (count > 0) "${g.label} $count" else g.label,
                                    fontSize = 12.sp,
                                )
                            },
                            colors = selectedChipColors(),
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = gearFilter?.let { "${it.label}：${it.hint}" }
                            ?: buildGearHint(selected.name, gearCounts),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ---- 地形 ----
        item {
            Text(
                "按地形收窄（可选）",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp, top = 2.dp),
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(Terrain.entries.toList(), key = { it.name }) { t ->
                    FilterChip(
                        selected = terrainFilter.contains(t),
                        onClick = { onToggleTerrain(t) },
                        label = { Text(t.label, fontSize = 12.sp) },
                        colors = selectedChipColors(),
                    )
                }
            }
        }

        item {
            Text(
                "推荐假饵 ${recs.size} 款",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp),
            )
        }

        if (recs.isEmpty()) {
            item {
                // 分两种情况说清楚：这鱼压根不吃假饵，还是只是被筛没了
                if (gearCounts.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = WarnOrange.copy(alpha = 0.10f)
                        ),
                        border = BorderStroke(1.dp, WarnOrange.copy(alpha = 0.35f)),
                    ) {
                        Column(modifier = Modifier.padding(13.dp)) {
                            Text(
                                "「${selected.name}」主要靠台钓，路亚基本钓不到",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = WarnOrange,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "它不在任何一款假饵的适配范围里，所以这里不会有推荐 —— "
                                    + "不是数据缺失，是这鱼本来就不追假饵。想钓它得用台钓："
                                    + "鲫鱼用腥香饵、鲤鱼用玉米或薯香饵、草鱼用嫩草或发酵饵、"
                                    + "鲢鳙用雾化饵。",
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "想看假饵推荐，点上面换成：翘嘴、马口、黑鱼、白条、青稍、鲶鱼、鳜鱼、"
                                    + "鲈鱼、虹鳟、黄颡鱼、溪哥、柳根",
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                } else {
                    Text(
                        "这个组合下没有匹配的假饵。换个鱼种、清掉地形筛选，或者把档位调回「不限」。",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }
        } else {
            items(recs) { rec -> ToolLureCard(rec) }
        }
    }
}

@Composable
private fun ToolLureCard(rec: LureRecommendation) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rec.lure.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.size(8.dp))
                Box(
                    modifier = Modifier
                        .background(GoGreen.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 7.dp, vertical = 1.dp)
                ) {
                    Text("推荐度 ${rec.confidence}", fontSize = 11.sp, color = GoGreen)
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(20.dp),
                        )
                        .padding(horizontal = 7.dp, vertical = 1.dp)
                ) {
                    Text(rec.lure.gearClass.label, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { rec.confidence / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp),
                color = GoGreen,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(Modifier.height(9.dp))
            LabeledLine("操作手法", rec.lure.actionStyle)
            LabeledLine("克重水层", "${rec.lure.weightRange} · ${rec.lure.waterLayer.label}")
            Spacer(Modifier.height(4.dp))
            Text(rec.tip, fontSize = 12.5.sp, lineHeight = 18.sp)

            // 饵料页给全量目标鱼：让人知道这一枚饵的适用面有多宽
            if (rec.allTargetFishNames.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "鱼种：${rec.allTargetFishNames.joinToString("、")}",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (rec.matchedTerrain.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "匹配地形：${rec.matchedTerrain.joinToString("、") { it.label }}",
                    fontSize = 12.sp,
                    color = WarnOrange,
                )
            }
        }
    }
}

@Composable
private fun LabeledLine(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(width = 72.dp, height = 18.dp),
        )
        Text(text = value, fontSize = 12.5.sp, lineHeight = 18.sp)
    }
}
