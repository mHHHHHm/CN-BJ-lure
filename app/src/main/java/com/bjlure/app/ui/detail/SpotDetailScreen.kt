package com.bjlure.app.ui.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bjlure.app.domain.Geo
import com.bjlure.app.domain.model.CoordAccuracy
import com.bjlure.app.domain.model.GuardLevel
import com.bjlure.app.domain.model.LureRecommendation
import com.bjlure.app.ui.FishingViewModel
import com.bjlure.app.ui.theme.GoGreen
import com.bjlure.app.ui.theme.GoGreenBg
import com.bjlure.app.ui.theme.NoRed
import com.bjlure.app.ui.theme.NoRedBg
import com.bjlure.app.ui.theme.WarnOrange
import com.bjlure.app.ui.theme.WarnOrangeBg
import kotlinx.coroutines.launch

@Composable
fun SpotDetailScreen(
    spotId: String,
    viewModel: FishingViewModel,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
) {
    val spot = remember(spotId) { viewModel.spotById(spotId) }

    if (spot == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("找不到这个钓点", fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onBack) { Text("返回") }
            }
        }
        return
    }

    val guard = remember(spotId) { viewModel.guardFor(spot) }
    val recs = remember(spotId) { viewModel.recommendationsFor(spot) }
    val fishNames = remember(spotId) { viewModel.fishNames(spot.targetFishIds) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmNavigate by remember { mutableStateOf(false) }

    // 从当前起点过去有多远
    val origin = viewModel.state.value.origin
    val distanceText = remember(spotId, origin) {
        if (origin != null && spot.latitude != null && spot.longitude != null) {
            val km = Geo.distanceMeters(origin.lat, origin.lon, spot.latitude, spot.longitude) / 1000.0
            "距「${origin.name}」约 %.1f km".format(km)
        } else null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 禁钓校验横幅 —— 文档要求的「时机B」
        item {
            GuardBanner(
                level = guard.level,
                headline = guard.headline,
                detail = guard.detail,
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(spot.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = listOf(spot.district, spot.waterType.label, spot.waterName, "免费")
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (distanceText != null) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = distanceText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    InfoRow("有什么鱼", fishNames.joinToString("、").ifEmpty { "待补充" })
                    InfoRow("适合钓法", spot.fishingMethods.joinToString("、"))
                    InfoRow("什么时候去", spot.bestSeason.ifEmpty { "—" })
                    InfoRow("黄金时段", spot.bestTimeOfDay.ifEmpty { "清晨与傍晚" })
                    InfoRow("地形", spot.terrain.joinToString("、") { it.label }.ifEmpty { "—" })
                    InfoRow("停车", when (spot.parking) {
                        true -> "有免费停车"
                        false -> "不好停车"
                        null -> "未标注"
                    })
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.size(5.dp))
                        Text("位置与导航", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = spot.address.ifEmpty { "按钓点名称导航到附近，再沿岸找可下杆的位置" },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(8.dp))
                    if (spot.latitude != null && spot.longitude != null) {
                        Text(
                            text = "坐标 %.5f, %.5f".format(spot.latitude, spot.longitude),
                            fontSize = 13.sp,
                        )
                        Text(
                            text = spot.coordAccuracy.label,
                            fontSize = 12.sp,
                            color = if (spot.coordAccuracy == CoordAccuracy.EXACT) GoGreen else WarnOrange,
                        )
                    } else {
                        Text(
                            text = "没有可信坐标，地图上不显示此点，请按名称搜索导航",
                            fontSize = 12.sp,
                            color = WarnOrange,
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (spot.latitude != null) confirmNavigate = true
                                else openAmapByKeyword(context, spot.name, spot.address)
                            },
                            enabled = true,
                        ) {
                            Text("导航前往")
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "本地搜索关键词：${spot.name} 北京"
                                    )
                                }
                            },
                        ) {
                            Text("复制关键词")
                        }
                    }
                }
            }
        }

        if (recs.isNotEmpty()) {
            item {
                Text(
                    "推荐假饵（按适配度排序）",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp, start = 2.dp),
                )
            }
            items(recs.size) { i ->
                LureCard(recs[i])
            }
        }

        if (spot.notes.isNotBlank()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("现场提示", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(5.dp))
                        Text(spot.notes, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                }
            }
        }

        if (!spot.legalWarning.isNullOrBlank()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NoRedBg, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = NoRed,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.size(5.dp))
                            Text("合规提示", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = NoRed)
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(spot.legalWarning, fontSize = 13.sp, color = NoRed, lineHeight = 20.sp)
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(top = 6.dp)) {
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "数据来源：${spot.source}${if (spot.sourceYear > 0) "（${spot.sourceYear} 年）" else ""}",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "钓点信息会过时，出行前请以现场标识和管理要求为准。",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // 文档要求的「时机C」：点导航前先弹窗过一遍边界
    if (confirmNavigate) {
        AlertDialog(
            onDismissRequest = { confirmNavigate = false },
            title = { Text("出发前确认") },
            text = {
                Column {
                    Text(guard.headline, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(guard.detail, fontSize = 13.sp, lineHeight = 19.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "导航只带你到附近，具体下杆位置请自行判断是否越界。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmNavigate = false
                    openMapApp(context, spot.name, spot.latitude, spot.longitude, spot.address)
                }) { Text("知道了，去导航") }
            },
            dismissButton = {
                TextButton(onClick = { confirmNavigate = false }) { Text("再想想") }
            },
        )
    }
}

@Composable
private fun GuardBanner(level: GuardLevel, headline: String, detail: String) {
    val (bg, fg) = when (level) {
        GuardLevel.IN_BAN -> NoRedBg to NoRed
        GuardLevel.NEAR_BAN -> WarnOrangeBg to WarnOrange
        GuardLevel.INFO -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        GuardLevel.OK -> GoGreenBg to GoGreen
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column {
            Text(headline, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = fg)
            Spacer(Modifier.height(4.dp))
            Text(detail, fontSize = 12.5.sp, color = fg, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun LureCard(rec: LureRecommendation) {
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
                Text(rec.lure.weightRange, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

            Spacer(Modifier.height(8.dp))
            Text(rec.tip, fontSize = 12.5.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurface)

            // 标注这个饵能钓这个点里的哪些鱼，别让人猜
            if (rec.matchedFishNames.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "鱼种：${rec.matchedFishNames.joinToString("、")}",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(width = 74.dp, height = 20.dp),
        )
        Text(text = value, fontSize = 13.sp, lineHeight = 20.sp)
    }
}

// ---------------------------------------------------------------------------
// 导航跳转
// ---------------------------------------------------------------------------

private fun openMapApp(context: Context, name: String, lat: Double?, lon: Double?, address: String) {
    if (lat == null || lon == null) {
        openAmapByKeyword(context, name, address)
        return
    }
    // 优先唤起高德，失败再唤起百度，最后退到网页
    val amapScheme = Uri.parse(
        "androidamap://viewMap?sourceApplication=bjlure&poiname=${Uri.encode(name)}" +
            "&lat=$lat&lon=$lon&dev=0"
    )
    if (tryStart(context, amapScheme)) return

    val baiduScheme = Uri.parse(
        "baidumap://map/marker?location=$lat,$lon&title=${Uri.encode(name)}&content=${Uri.encode(name)}&src=android.bjlure"
    )
    if (tryStart(context, baiduScheme)) return

    tryStart(context, Uri.parse("https://uri.amap.com/marker?position=$lon,$lat&name=${Uri.encode(name)}"))
}

private fun openAmapByKeyword(context: Context, name: String, address: String) {
    val keyword = if (address.isNotBlank()) "$name $address" else name
    val uri = Uri.parse("https://uri.amap.com/search?keyword=${Uri.encode(keyword)}&city=${Uri.encode("北京")}")
    tryStart(context, uri)
}

private fun tryStart(context: Context, uri: Uri): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (t: Throwable) {
    false
}
