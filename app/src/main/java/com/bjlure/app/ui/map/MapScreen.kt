package com.bjlure.app.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bjlure.app.domain.model.CoordAccuracy
import com.bjlure.app.domain.model.FishingSpot
import com.bjlure.app.domain.model.SpotSourceType
import com.bjlure.app.ui.FishingViewModel
import com.bjlure.app.ui.theme.GoGreen
import com.bjlure.app.ui.theme.WarnOrange
import com.bjlure.app.ui.theme.selectedChipColors
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

private const val TILE_SIZE = 256.0

private val OfficialBlue = Color(0xFF1B4F8A)
private val WildBlue = Color(0xFF2B6CB0)
private val PlaceholderColor = Color(0xFFE6EDF5)

/**
 * 地图上的颜色语义就两条，红蓝对照：
 *
 *   红 = 禁止垂钓（不能下杆的地方）
 *   蓝 = 可钓水域（我们整理出来的钓点范围）
 *
 * 都是半透明填充 + 实线描边：范围看得出来，底图的路名也不会被盖住。
 */
private val ZoneFill = Color(0x3DE2504A)
private val ZoneStroke = Color(0xB3E2504A)
private val ZoneLegend = Color(0x99E2504A)

private val WaterFill = Color(0x2E2B6CB0)
private val WaterStroke = Color(0x8C2B6CB0)
private val WaterLegend = Color(0x992B6CB0)

/** 初始缩放：北京装得下、又不至于把河北也框进来 */
private const val DEFAULT_ZOOM = 10.2f

/**
 * 地图页。
 *
 * 底图用高德路网瓦片（中文标注、国内快、无需 Key）：
 *  - z7~z10 已打包进 assets，完全离线也能看到北京全貌和主要路网
 *  - 再放大就联网拉，拉过的写进 cacheDir，之后离线也能看
 *
 * 坐标必须说清楚：钓点来自 OpenStreetMap（WGS-84），高德瓦片是 GCJ-02，
 * 两者在北京差 300-500 米。所有要素绘制前统一走 [Gcj02]，
 * 否则钓点会整片偏到河对岸去。
 *
 * 布局用的是「地图铺满 + 控件浮层」，不是上下切两块 ——
 * 后者会把顶部工具条挤扁，文字甚至画到地图上去。
 */
@Composable
fun MapScreen(
    viewModel: FishingViewModel,
    onOpenSpot: (String) -> Unit,
) {
    val context = LocalContext.current
    val tileProvider = remember { TileProvider(context) }

    // 重绘信号：瓦片是异步加载的，加载完把 tileVersion +1，Canvas 读到就重画
    var tileVersion by remember { mutableStateOf(0) }

    var showZones by remember { mutableStateOf(true) }
    var showSpots by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<FishingSpot?>(null) }

    // 视图状态：中心点（GCJ-02）与连续缩放级别
    var centerLat by remember { mutableStateOf(40.10) }
    var centerLon by remember { mutableStateOf(116.41) }
    var zoom by remember { mutableStateOf(DEFAULT_ZOOM) }

    val origin = viewModel.state.value.origin

    // 起点变了就把地图挪过去。不主动放大 —— 先把「大概在北京哪个位置」交代清楚。
    LaunchedEffect(origin?.name) {
        if (origin != null) {
            val (lat, lon) = Gcj02.fromWgs84(origin.lat, origin.lon)
            centerLat = lat
            centerLon = lon
            zoom = DEFAULT_ZOOM
        }
    }

    val zones = remember { viewModel.allZones().filter { it.polygons.isNotEmpty() } }
    val spots = remember { viewModel.allSpots().filter { it.latitude != null && it.longitude != null } }

    // 预先把 WGS-84 转成 GCJ-02，避免每帧都算
    val zonesGcj = remember(zones) {
        zones.map { z -> z to z.polygons.map { ring -> ring.map { Gcj02.fromWgs84(it.first, it.second) } } }
    }
    val spotsGcj = remember(spots) {
        spots.map { s -> s to Gcj02.fromWgs84(s.latitude!!, s.longitude!!) }
    }
    val originGcj = remember(origin?.lat, origin?.lon) {
        origin?.let { Gcj02.fromWgs84(it.lat, it.lon) }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ============ 1. 地图本体（铺满整屏） ============
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFDCE7F2))
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, gestureZoom, _ ->
                            val w = size.width.toDouble()
                            val h = size.height.toDouble()
                            val oldZoom = zoom
                            val newZoom = (zoom * gestureZoom).coerceIn(
                                TileProvider.MIN_ZOOM.toFloat(),
                                TileProvider.MAX_ZOOM.toFloat(),
                            )
                            val cxOld = lonToWorldX(centerLon, oldZoom.toDouble())
                            val cyOld = latToWorldY(centerLat, oldZoom.toDouble())
                            val lonAtCentroid = worldXToLon(cxOld + centroid.x - w / 2.0, oldZoom.toDouble())
                            val latAtCentroid = worldYToLat(cyOld + centroid.y - h / 2.0, oldZoom.toDouble())

                            val nc = lonToWorldX(lonAtCentroid, newZoom.toDouble()) - centroid.x + w / 2.0 - pan.x
                            val ny = latToWorldY(latAtCentroid, newZoom.toDouble()) - centroid.y + h / 2.0 - pan.y

                            zoom = newZoom
                            centerLon = worldXToLon(nc, newZoom.toDouble())
                            centerLat = worldYToLat(ny, newZoom.toDouble())
                        }
                    }
                    .pointerInput(spotsGcj, zoom) {
                        detectTapGestures { tap ->
                            val w = size.width.toDouble()
                            val h = size.height.toDouble()
                            val cx = lonToWorldX(centerLon, zoom.toDouble())
                            val cy = latToWorldY(centerLat, zoom.toDouble())
                            var best: FishingSpot? = null
                            var bestD = 44.0
                            for ((s, gcj) in spotsGcj) {
                                val sx = lonToWorldX(gcj.second, zoom.toDouble()) - cx + w / 2.0
                                val sy = latToWorldY(gcj.first, zoom.toDouble()) - cy + h / 2.0
                                val d = hypot(sx - tap.x, sy - tap.y)
                                if (d < bestD) {
                                    bestD = d
                                    best = s
                                }
                            }
                            if (best != null) selected = best
                        }
                    },
            ) {
                val w = size.width.toDouble()
                val h = size.height.toDouble()
                val z = floor(zoom.toDouble()).toInt().coerceIn(TileProvider.MIN_ZOOM, TileProvider.MAX_ZOOM)
                val k = 2.0.pow((zoom - z).toDouble())
                val cx = lonToWorldX(centerLon, zoom.toDouble())
                val cy = latToWorldY(centerLat, zoom.toDouble())
                val leftZ = (cx - w / 2.0) / k
                val topZ = (cy - h / 2.0) / k
                val tileScreen = TILE_SIZE * k
                val n = 1 shl z

                // ---- 底图瓦片 ----
                val tx0 = floor(leftZ / TILE_SIZE).toInt()
                val ty0 = floor(topZ / TILE_SIZE).toInt()
                val tx1 = floor((leftZ + w / k) / TILE_SIZE).toInt()
                val ty1 = floor((topZ + h / k) / TILE_SIZE).toInt()

                for (tx in tx0..tx1) {
                    for (ty in ty0..ty1) {
                        val sx = tx * tileScreen - cx + w / 2.0
                        val sy = ty * tileScreen - cy + h / 2.0
                        val bmp: ImageBitmap? =
                            if (tx in 0 until n && ty in 0 until n) {
                                tileProvider.get(z, tx, ty) { tileVersion++ }
                            } else null

                        if (bmp != null) {
                            drawImage(
                                image = bmp,
                                srcOffset = IntOffset.Zero,
                                srcSize = IntSize(bmp.width, bmp.height),
                                dstOffset = IntOffset(sx.roundToInt(), sy.roundToInt()),
                                dstSize = IntSize(
                                    tileScreen.roundToInt() + 1,
                                    tileScreen.roundToInt() + 1,
                                ),
                            )
                        } else {
                            drawRect(
                                color = PlaceholderColor,
                                topLeft = Offset(sx.toFloat(), sy.toFloat()),
                                size = Size(tileScreen.toFloat(), tileScreen.toFloat()),
                            )
                        }
                    }
                }
                // 读一下版本号：瓦片加载完成后触发重绘
                if (tileVersion < 0) return@Canvas

                // ---- 禁钓区（深蓝半透明） ----
                if (showZones) {
                    for ((zone, rings) in zonesGcj) {
                        if (!zone.type.blocksAngling) continue
                        for (ring in rings) {
                            val path = Path()
                            ring.forEachIndexed { i, gcj ->
                                val x = lonToWorldX(gcj.second, zoom.toDouble()) - cx + w / 2.0
                                val y = latToWorldY(gcj.first, zoom.toDouble()) - cy + h / 2.0
                                if (i == 0) path.moveTo(x.toFloat(), y.toFloat())
                                else path.lineTo(x.toFloat(), y.toFloat())
                            }
                            path.close()
                            drawPath(path, ZoneFill)
                            drawPath(path, ZoneStroke, style = Stroke(width = 1.6f))
                        }
                    }
                }

                // ---- 当前位置 ----
                if (originGcj != null) {
                    val x = lonToWorldX(originGcj.second, zoom.toDouble()) - cx + w / 2.0
                    val y = latToWorldY(originGcj.first, zoom.toDouble()) - cy + h / 2.0
                    if (x > -80 && x < w + 80 && y > -80 && y < h + 80) {
                        val c = Offset(x.toFloat(), y.toFloat())
                        drawCircle(Color(0x332B6CB0), radius = 26f, center = c)
                        drawCircle(Color(0x552B6CB0), radius = 15f, center = c)
                        drawCircle(Color.White, radius = 9f, center = c)
                        drawCircle(Color(0xFF2B6CB0), radius = 6f, center = c)
                    }
                }

                // ---- 钓点 ----
                if (showSpots) {
                    val r = (7.5f * (0.6f + 0.4f * (zoom / 13f))).coerceIn(5.5f, 15f)
                    // 当前缩放下 1 像素等于多少米（Web Mercator 标准公式），用来把「米」换算成圈半径
                    val metersPerPx =
                        156543.03392 * cos(centerLat * PI / 180.0) / 2.0.pow(zoom.toDouble())

                    for ((s, gcj) in spotsGcj) {
                        val x = lonToWorldX(gcj.second, zoom.toDouble()) - cx + w / 2.0
                        val y = latToWorldY(gcj.first, zoom.toDouble()) - cy + h / 2.0
                        if (x < -60 || x > w + 60 || y < -60 || y > h + 60) continue

                        val color = when (s.sourceType) {
                            SpotSourceType.RISKY -> WarnOrange
                            SpotSourceType.OFFICIAL -> OfficialBlue
                            SpotSourceType.WILD -> WildBlue
                        }
                        val exact = s.coordAccuracy == CoordAccuracy.EXACT
                        val c = Offset(x.toFloat(), y.toFloat())

                        // 可钓水域：蓝色半透明一块，和红色的禁钓区形成对照。
                        // 太小的圈没意义，太大的圈会把地图糊住，两头都跳过。
                        val ringPx = (s.radiusMeters / metersPerPx).toFloat()
                        if (ringPx in (r + 4f)..2600f) {
                            drawCircle(WaterFill, radius = ringPx, center = c)
                            drawCircle(
                                color = WaterStroke,
                                radius = ringPx,
                                center = c,
                                style = Stroke(
                                    width = 1.8f,
                                    // 坐标只是大致位置的，边界画虚线 —— 别让人以为圈外一步就不能钓了
                                    pathEffect = if (exact) null
                                    else PathEffect.dashPathEffect(floatArrayOf(9f, 7f)),
                                ),
                            )
                        }

                        // 中心点：底图路网很花，三层描边才看得清
                        drawCircle(Color(0x55000000), radius = r + 2.6f, center = c)
                        drawCircle(Color.White, radius = r + 1.4f, center = c)
                        if (exact) {
                            drawCircle(color, radius = r, center = c)
                        } else {
                            drawCircle(color, radius = r, center = c, style = Stroke(width = 3f))
                        }
                        if (selected?.id == s.id) {
                            drawCircle(Color(0xFF0D3B66), radius = r + 7f, center = c, style = Stroke(width = 3f))
                        }
                    }
                }
            }

            // ---- 左下角说明 ----
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 12.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f),
                shadowElevation = 3.dp,
            ) {
                Text(
                    "蓝圈 = 能下杆的一片水域（虚线表示位置只是大概）",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                )
            }

            // ---- 缩放 / 定位按钮 ----
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                RoundButton(Icons.Filled.Add) {
                    zoom = (zoom + 1f).coerceAtMost(TileProvider.MAX_ZOOM.toFloat())
                }
                RoundButton(Icons.Filled.Remove) {
                    zoom = (zoom - 1f).coerceAtLeast(TileProvider.MIN_ZOOM.toFloat())
                }
                RoundButton(Icons.Filled.MyLocation) {
                    if (origin != null) {
                        val (lat, lon) = Gcj02.fromWgs84(origin.lat, origin.lon)
                        centerLat = lat
                        centerLon = lon
                        zoom = 13f
                    }
                }
            }

            // ---- 选中钓点的底部卡片 ----
            selected?.let { s ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(10.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                    onClick = { onOpenSpot(s.id) },
                ) {
                    Column(modifier = Modifier.padding(13.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.weight(1f))
                            Text("查看详情 ›", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = listOf(
                                s.district,
                                s.waterType.label,
                                viewModel.fishNames(s.targetFishIds).joinToString("·"),
                            ).filter { it.isNotBlank() }.joinToString("  |  "),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (s.coordAccuracy != CoordAccuracy.EXACT) {
                            Spacer(Modifier.size(4.dp))
                            Text(s.coordAccuracy.label, fontSize = 11.5.sp, color = WarnOrange)
                        }
                    }
                }
            }
        }

        // ============ 2. 顶部工具条（浮层，画在最上层） ============
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shadowElevation = 3.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = showZones,
                        onClick = { showZones = !showZones },
                        label = { Text("禁钓区", fontSize = 12.sp) },
                        colors = selectedChipColors(),
                    )
                    FilterChip(
                        selected = showSpots,
                        onClick = { showSpots = !showSpots },
                        label = { Text("免费钓点", fontSize = 12.sp) },
                        colors = selectedChipColors(),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "z%.1f".format(zoom),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LegendDot(ZoneLegend, "禁止垂钓")
                    Spacer(Modifier.size(9.dp))
                    LegendDot(WaterLegend, "可钓水域")
                    Spacer(Modifier.size(9.dp))
                    LegendDot(OfficialBlue, "官方")
                    Spacer(Modifier.size(9.dp))
                    LegendDot(WarnOrange, "易变")
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (zoom <= TileProvider.BUILT_IN_MAX_ZOOM) "离线底图" else "联网底图",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        onClick = onClick,
    ) {
        Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(9.dp)) { drawCircle(color) }
        Spacer(Modifier.size(3.dp))
        Text(label, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------------
// Web Mercator 投影
// ---------------------------------------------------------------------------

private fun lonToWorldX(lon: Double, zoom: Double): Double =
    (lon + 180.0) / 360.0 * TILE_SIZE * 2.0.pow(zoom)

private fun latToWorldY(lat: Double, zoom: Double): Double {
    val rad = lat * PI / 180.0
    return (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0 * TILE_SIZE * 2.0.pow(zoom)
}

private fun worldXToLon(x: Double, zoom: Double): Double =
    x / (TILE_SIZE * 2.0.pow(zoom)) * 360.0 - 180.0

private fun worldYToLat(y: Double, zoom: Double): Double {
    val n = PI - 2.0 * PI * y / (TILE_SIZE * 2.0.pow(zoom))
    return atan(sinh(n)) * 180.0 / PI
}
