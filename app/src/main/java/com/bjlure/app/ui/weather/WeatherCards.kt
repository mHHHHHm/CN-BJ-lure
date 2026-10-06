package com.bjlure.app.ui.weather

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bjlure.app.domain.model.FishingOutlook
import com.bjlure.app.domain.model.WeatherSnapshot
import com.bjlure.app.ui.WeatherUiState
import com.bjlure.app.ui.theme.GoGreen
import com.bjlure.app.ui.theme.NoRed
import com.bjlure.app.ui.theme.WarnOrange

/** WMO 天气代码 → 中文 + 图标字符 */
fun weatherText(code: Int): Pair<String, String> = when (code) {
    0 -> "晴" to "☀️"
    1 -> "少云" to "🌤"
    2 -> "多云" to "⛅"
    3 -> "阴" to "☁️"
    45, 48 -> "有雾" to "🌫"
    51, 53, 55 -> "毛毛雨" to "🌦"
    56, 57 -> "冻毛毛雨" to "🌧"
    61 -> "小雨" to "🌦"
    63 -> "中雨" to "🌧"
    65 -> "大雨" to "🌧"
    66, 67 -> "冻雨" to "🌧"
    71, 73, 75, 77 -> "下雪" to "🌨"
    80, 81 -> "阵雨" to "🌦"
    82 -> "强阵雨" to "⛈"
    85, 86 -> "阵雪" to "🌨"
    95 -> "雷阵雨" to "⛈"
    96, 99 -> "雷暴" to "⛈"
    else -> "未知" to "🌡"
}

private fun gradeColor(score: Int): Color = when {
    score >= 80 -> GoGreen
    score >= 65 -> Color(0xFF52A852)
    score >= 45 -> WarnOrange
    else -> NoRed
}

@Composable
fun WeatherPanel(
    state: WeatherUiState,
    originName: String?,
    onRefresh: () -> Unit,
    onPickOrigin: () -> Unit,
) {
    val snap = state.snapshot
    val outlook = state.outlook

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(13.dp),
    ) {
        // ---- 第一行：地点 + 温度 ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "📍 " + (originName ?: "未选择位置"),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onPickOrigin),
            )
            Spacer(Modifier.size(4.dp))
            Text("切换", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onPickOrigin))
            Spacer(Modifier.weight(1f))
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onRefresh, modifier = Modifier.size(26.dp)) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "刷新天气",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (state.error != null) {
            Spacer(Modifier.height(6.dp))
            Text(state.error, fontSize = 12.sp, color = WarnOrange)
        }

        if (snap == null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "选一个起点，本鱼给你看实时天气和出钓指数",
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        // ---- 天气实况 ----
        val (wText, wIcon) = weatherText(snap.current.weatherCode)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(wIcon, fontSize = 30.sp)
            Spacer(Modifier.size(8.dp))
            Text(
                text = "%.0f°".format(snap.current.temperatureC),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.size(6.dp))
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                Text(wText, fontSize = 13.sp)
                Text(
                    "体感 %.0f°".format(snap.current.apparentTemperatureC),
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 4.dp)) {
                Text("风 %.1f m/s".format(snap.current.windSpeedMs), fontSize = 11.5.sp)
                Text("气压 %.0f hPa".format(snap.current.pressureHpa), fontSize = 11.5.sp)
                Text("湿度 ${snap.current.humidity}%", fontSize = 11.5.sp)
            }
        }

        Spacer(Modifier.height(7.dp))
        Row {
            Text(
                "估算水温 %.1f℃".format(snap.estimatedWaterTempC),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(10.dp))
            val today = snap.daily.firstOrNull()
            if (today != null) {
                Text(
                    "今日 %.0f~%.0f℃".format(today.tempMinC, today.tempMaxC),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---- 出钓指数 ----
        if (outlook != null) {
            Spacer(Modifier.height(12.dp))
            OutlookSection(outlook)
        }
    }
}

@Composable
private fun OutlookSection(outlook: FishingOutlook) {
    var expanded by remember { mutableStateOf(false) }
    val color = gradeColor(outlook.score)

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("出钓指数", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(8.dp))
            Box(
                modifier = Modifier
                    .background(color.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp, vertical = 1.dp)
            ) {
                Text(outlook.grade, fontSize = 11.5.sp, color = color, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${outlook.score}",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text("/100", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(5.dp))
        LinearProgressIndicator(
            progress = { outlook.score / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        Spacer(Modifier.height(7.dp))
        Text(outlook.headline, fontSize = 12.5.sp, lineHeight = 18.sp)

        if (outlook.windows.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("推荐时段", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            outlook.windows.forEach { w ->
                Row(modifier = Modifier.padding(top = 3.dp)) {
                    Text("· ${w.label}", fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = color)
                    Spacer(Modifier.size(6.dp))
                    Text(w.reason, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(9.dp))
        Row(
            modifier = Modifier.clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (expanded) "收起打分依据" else "看看分数是怎么来的",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 6.dp)) {
                outlook.factors.forEach { f ->
                    Row(
                        modifier = Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            f.name,
                            fontSize = 12.sp,
                            modifier = Modifier.size(width = 62.dp, height = 17.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${f.score}/${f.maxScore}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.size(width = 42.dp, height = 17.dp),
                            color = if (f.score * 2 < f.maxScore) WarnOrange else GoGreen,
                        )
                        Text(f.detail, fontSize = 11.5.sp, lineHeight = 16.sp)
                    }
                }
            }
        }

        // ---- 穿衣 ----
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(9.dp))
                .padding(10.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🧥 穿衣", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.size(7.dp))
                    Box(
                        modifier = Modifier
                            .background(color.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 7.dp, vertical = 1.dp)
                    ) {
                        Text(outlook.clothing.level, fontSize = 11.sp, color = color)
                    }
                    Spacer(Modifier.size(7.dp))
                    Text(outlook.clothing.headline, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                outlook.clothing.detail.forEach { line ->
                    Text(
                        "· $line",
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }

        // ---- 出钓建议 ----
        if (outlook.suggestions.isNotEmpty()) {
            Spacer(Modifier.height(9.dp))
            Text("本鱼的建议", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            outlook.suggestions.forEach { s ->
                Text(
                    "· $s",
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}
