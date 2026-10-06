package com.bjlure.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 取色沿用桌面版地图工具的那套：深蓝主色 + 绿色代表可钓 + 红色代表禁钓
val DeepBlue = Color(0xFF0D3B66)
val MidBlue = Color(0xFF1B6CA8)
val LightBlue = Color(0xFFE8F1FB)

val GoGreen = Color(0xFF18A058)
val GoGreenBg = Color(0xFFE8F8EE)
val NoRed = Color(0xFFE2504A)
val NoRedBg = Color(0xFFFDECEB)
val WarnOrange = Color(0xFFE08A1E)
val WarnOrangeBg = Color(0xFFFDF3E3)

// 淡蓝主题：全站底色偏蓝，卡片用「更亮一档的淡蓝 + 一圈可见的细框线」。
// 关键是卡片底不能图省事写近似白 —— 那样一眼看去还是白板，
// 只是名字叫淡蓝而已。这里卡片和页面底都实打实带蓝，靠明度差分层。
val PageBlue = Color(0xFFD8E6F6)        // 页面底色：明显的淡蓝
val CardBlue = Color(0xFFEAF2FC)        // 卡片底：比页面亮一档，但仍明显是蓝
val ChipBlue = Color(0xFFD5E5F6)        // 次级容器
val OutlineBlue = Color(0xFFA8C6E6)     // 框线（加深了，小屏上也分得清）
val OutlineBlueSoft = Color(0xFFBCD4EC) // 分隔线

private val LightColors = lightColorScheme(
    primary = DeepBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC3DCF5),
    onPrimaryContainer = DeepBlue,
    secondary = MidBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2E5F8),
    onSecondaryContainer = DeepBlue,
    error = NoRed,
    onError = Color.White,
    errorContainer = NoRedBg,
    onErrorContainer = Color(0xFF8C2F2A),
    background = PageBlue,
    onBackground = Color(0xFF16283C),
    surface = CardBlue,
    onSurface = Color(0xFF16283C),
    surfaceVariant = ChipBlue,
    onSurfaceVariant = Color(0xFF5B6F86),
    outline = OutlineBlue,
    outlineVariant = OutlineBlueSoft,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FB2E5),
    onPrimary = Color(0xFF0A2540),
    secondary = Color(0xFF8FC7F0),
    error = Color(0xFFF08B86),
    background = Color(0xFF101922),
    onBackground = Color(0xFFE3EAF2),
    surface = Color(0xFF16212C),
    onSurface = Color(0xFFE3EAF2),
    surfaceVariant = Color(0xFF22303D),
    onSurfaceVariant = Color(0xFFB3C2D1),
    outline = Color(0xFF3A4C5E),
    outlineVariant = Color(0xFF2A3A49),
)

@Composable
fun BjLureTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

/**
 * 选中态 chip 的统一配色：**深蓝底 + 白字**。
 *
 * 默认的 Material 选中态是「浅色底 + 主色字」，在本来就偏蓝的主题里
 * 几乎看不出来选没选 —— 深蓝底白字才有对比度。
 */
@Composable
fun selectedChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = DeepBlue,
    selectedLabelColor = Color.White,
    selectedLeadingIconColor = Color.White,
    selectedTrailingIconColor = Color.White,
)
