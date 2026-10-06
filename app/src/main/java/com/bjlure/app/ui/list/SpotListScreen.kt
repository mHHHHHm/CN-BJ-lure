package com.bjlure.app.ui.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bjlure.app.domain.SpotFilter
import com.bjlure.app.domain.model.CoordAccuracy
import com.bjlure.app.domain.model.GearClass
import com.bjlure.app.domain.model.SpotSourceType
import com.bjlure.app.ui.FishingUiState
import com.bjlure.app.ui.FishingViewModel
import com.bjlure.app.ui.Origin
import com.bjlure.app.ui.theme.GoGreen
import com.bjlure.app.ui.theme.NoRed
import com.bjlure.app.ui.theme.NoRedBg
import com.bjlure.app.ui.theme.WarnOrange
import com.bjlure.app.ui.theme.WarnOrangeBg
import com.bjlure.app.ui.weather.WeatherPanel
import kotlinx.coroutines.launch

@Composable
fun SpotListScreen(
    state: FishingUiState,
    viewModel: FishingViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenSpot: (String) -> Unit,
    onRequestLocation: () -> Unit,
) {
    var showOriginPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        FilterBar(
            state = state,
            viewModel = viewModel,
            onPickOrigin = { showOriginPicker = true },
            snackbarHostState = snackbarHostState,
        )

        when {
            state.loading -> CenterBox("正在载入钓点数据…")
            state.error != null -> ErrorBox(state.error, onRetry = { viewModel.load() })
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "weather") {
                    WeatherPanel(
                        state = state.weather,
                        originName = state.origin?.name,
                        onRefresh = { viewModel.refreshWeather() },
                        onPickOrigin = { showOriginPicker = true },
                    )
                }

                if (state.cards.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "当前条件下没有钓点，把筛选放宽一点试试",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                } else {
                    items(state.cards, key = { it.spot.id }) { card ->
                        SpotRow(card = card, onClick = { onOpenSpot(card.spot.id) })
                    }
                }
            }
        }
    }

    if (showOriginPicker) {
        OriginPickerDialog(
            presets = viewModel.presetOrigins,
            current = state.origin,
            onDismiss = { showOriginPicker = false },
            onPick = { o ->
                showOriginPicker = false
                viewModel.setOrigin(o)
            },
            onUseGps = {
                showOriginPicker = false
                onRequestLocation()
            },
        )
    }
}

/**
 * 筛选条。
 *
 * 默认收起来，只留一行「按条件筛选」—— 距离不单独做筛选维度，
 * 因为默认就是拿到本人定位、按距离从近到远排，翻下去就是由近及远。
 * 展开后是京东那种「维度 : 选项」的一行一维，全部非必选。
 */
@Composable
private fun FilterBar(
    state: FishingUiState,
    viewModel: FishingViewModel,
    onPickOrigin: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    var expanded by remember { mutableStateOf(false) }
    var districtExpanded by remember { mutableStateOf(false) }

    val hasFilter = state.district != null || state.selectedGear != null ||
        state.selectedFishIds.isNotEmpty()
    val summary = buildList {
        state.district?.let { add(it) }
        state.selectedGear?.let { add(it.label) }
        if (state.selectedFishIds.isNotEmpty()) {
            add(
                state.fishOptions.filter { state.selectedFishIds.contains(it.id) }
                    .joinToString("/") { it.name }
            )
        }
    }.joinToString(" · ")

    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {

            // ---- 起点 + 结果计数 ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = state.origin?.let { "从「${it.name}」出发 · 由近到远" } ?: "未定位",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onPickOrigin),
                )
                Text(
                    text = "${state.cards.size} 个免费点",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // ---- 折叠开关 ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "按条件筛选",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (hasFilter) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                )
                if (summary.isNotEmpty()) {
                    Text(
                        text = " · $summary",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (hasFilter) {
                    Text(
                        text = "清空",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable { viewModel.resetFilters() }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---- 展开后的各维度 ----
            AnimatedVisibility(visible = expanded) {
                Column {
                    // 区域
                    val districtsShown = if (districtExpanded) {
                        state.districtOptions
                    } else {
                        state.districtOptions.take(DISTRICT_COLLAPSED)
                    }
                    val hasMore = state.districtOptions.size > DISTRICT_COLLAPSED
                    FilterRow(
                        label = "区域",
                        options = buildList {
                            add("不限" to (state.district == null))
                            districtsShown.forEach { d -> add(d to (state.district == d)) }
                            if (hasMore) add((if (districtExpanded) "收起" else "更多") to false)
                        },
                        onSelect = { idx ->
                            when {
                                idx == 0 -> viewModel.setDistrict(null)
                                hasMore && idx == districtsShown.size + 1 ->
                                    districtExpanded = !districtExpanded
                                else -> viewModel.setDistrict(districtsShown[idx - 1])
                            }
                        },
                    )

                    // 装备档位
                    val gears = listOf<GearClass?>(null) + GearClass.entries.toList()
                    FilterRow(
                        label = "装备",
                        options = gears.map { g -> (g?.label ?: "不限") to (state.selectedGear == g) },
                        onSelect = { idx -> viewModel.setGear(gears[idx]) },
                    )

                    // 鱼种（可多选）
                    val fishes = state.fishOptions
                    FilterRow(
                        label = "鱼种",
                        options = buildList {
                            add("不限" to state.selectedFishIds.isEmpty())
                            fishes.forEach { f -> add(f.name to state.selectedFishIds.contains(f.id)) }
                        },
                        onSelect = { idx ->
                            if (idx == 0) viewModel.clearFish()
                            else viewModel.toggleFish(fishes[idx - 1].id)
                        },
                    )
                }
            }
        }
    }
}

private const val DISTRICT_COLLAPSED = 4

/** 一行一个筛选维度：左边灰色标签，右边横滑选项 */
@Composable
private fun FilterRow(
    label: String,
    options: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(31.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(options) { i, opt ->
                FilterOption(text = opt.first, selected = opt.second) { onSelect(i) }
            }
        }
    }
}

@Composable
private fun FilterOption(text: String, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(13.dp))
            // 选中 = 深蓝底 + 白字。主题本来就偏蓝，再用浅底根本看不出选没选
            .background(if (selected) primary else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(13.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OriginPickerDialog(
    presets: List<Origin>,
    current: Origin?,
    onDismiss: () -> Unit,
    onPick: (Origin) -> Unit,
    onUseGps: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择出发点") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "距离排序和天气都按这个位置算。想用真实定位就点下面的按钮。",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))

                OutlinedButton(onClick = onUseGps, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("用我的定位")
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(6.dp))
                Text("或者选一个常用出发点", fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))

                presets.forEach { o ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(o) }
                            .padding(vertical = 9.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            o.name,
                            fontSize = 13.5.sp,
                            fontWeight = if (o.name == current?.name) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Spacer(Modifier.weight(1f))
                        if (o.name == current?.name) {
                            Text("当前", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun SpotRow(card: SpotFilter.Card, onClick: () -> Unit) {
    val spot = card.spot
    val accent = when {
        card.blocked -> NoRed
        card.needsAttention -> WarnOrange
        else -> GoGreen
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(width = 5.dp, height = 96.dp)
                    .background(accent)
            )
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = spot.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.size(6.dp))
                    Tag(text = "免费", fg = GoGreen, bg = GoGreen.copy(alpha = 0.12f))
                }

                Spacer(Modifier.height(3.dp))

                Text(
                    text = listOfNotNull(
                        spot.district,
                        spot.waterName,
                        card.distanceKm?.let { formatDistance(it) },
                    ).joinToString(" · "),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = card.fishNames.joinToString(" · ").ifEmpty { "鱼种待补充" },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val srcFg = when (spot.sourceType) {
                        SpotSourceType.OFFICIAL -> Color(0xFF2B6CB0)
                        SpotSourceType.RISKY -> WarnOrange
                        SpotSourceType.WILD -> GoGreen
                    }
                    Tag(text = spot.sourceType.label, fg = srcFg, bg = srcFg.copy(alpha = 0.12f))
                    Tag(
                        text = spot.waterType.label,
                        fg = MaterialTheme.colorScheme.primary,
                        bg = MaterialTheme.colorScheme.primaryContainer,
                    )
                    if (spot.coordAccuracy != CoordAccuracy.EXACT) {
                        Tag(
                            text = spot.coordAccuracy.label,
                            fg = MaterialTheme.colorScheme.onSurfaceVariant,
                            bg = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }

                if (card.hasWarning) {
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (card.blocked) NoRedBg else WarnOrangeBg,
                                RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = card.guard.headline,
                            fontSize = 12.sp,
                            color = if (card.blocked) NoRed else WarnOrange,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String, fg: Color, bg: Color) {
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text = text, fontSize = 11.sp, color = fg)
    }
}

private fun formatDistance(km: Double): String =
    if (km < 1) "${(km * 1000).toInt()}m" else "%.1fkm".format(km)

@Composable
private fun CenterBox(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorBox(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, fontSize = 14.sp, color = NoRed)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("重试")
            }
        }
    }
}
