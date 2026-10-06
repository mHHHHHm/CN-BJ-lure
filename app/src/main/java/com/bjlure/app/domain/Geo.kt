package com.bjlure.app.domain

import com.bjlure.app.domain.model.NoFishingZone
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 纯几何工具：距离计算与多边形判定。
 * 不依赖 Android，便于单元测试。
 */
object Geo {

    private const val EARTH_RADIUS_M = 6_371_008.8

    /** 两点球面距离（米） */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }

    /**
     * 射线法判断点是否在多边形内。
     * ring 为闭合或非闭合均可，元素为 [纬度, 经度]。
     */
    fun pointInPolygon(lat: Double, lon: Double, ring: List<Pair<Double, Double>>): Boolean {
        if (ring.size < 3) return false
        var inside = false
        val n = ring.size
        var j = n - 1
        for (i in 0 until n) {
            val yi = ring[i].first
            val xi = ring[i].second
            val yj = ring[j].first
            val xj = ring[j].second
            // 用经度当 x、纬度当 y 做标准射线法
            if ((yi > lat) != (yj > lat)) {
                val xCross = xi + (lat - yi) / (yj - yi) * (xj - xi)
                if (lon < xCross) inside = !inside
            }
            j = i
        }
        return inside
    }

    /**
     * 点到线段的距离（米）。纬度跨度小时用平面近似足够（北京纬度 40° 附近，
     * 在几公里尺度上误差远小于本工具需要的精度）。
     */
    fun pointToSegmentMeters(
        lat: Double, lon: Double,
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double,
    ): Double {
        val kx = 111_320.0 * cos(Math.toRadians(lat))
        val ky = 110_540.0
        val px = (lon - lon1) * kx
        val py = (lat - lat1) * ky
        val dx = (lon2 - lon1) * kx
        val dy = (lat2 - lat1) * ky
        val len2 = dx * dx + dy * dy
        if (len2 <= 1e-9) return sqrt(px * px + py * py)
        var t = (px * dx + py * dy) / len2
        t = max(0.0, min(1.0, t))
        val cx = t * dx
        val cy = t * dy
        return sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy))
    }

    /** 点到多边形边界的最短距离（米）；点在内部时返回 0 */
    fun distanceToPolygonMeters(lat: Double, lon: Double, ring: List<Pair<Double, Double>>): Double {
        if (ring.size < 2) return Double.MAX_VALUE
        if (pointInPolygon(lat, lon, ring)) return 0.0
        var best = Double.MAX_VALUE
        var j = ring.size - 1
        for (i in ring.indices) {
            val d = pointToSegmentMeters(
                lat, lon,
                ring[i].first, ring[i].second,
                ring[j].first, ring[j].second,
            )
            if (d < best) best = d
            j = i
        }
        return best
    }

    /** 点到多环水体的最短距离；落在任意一环内部都算 0 */
    fun distanceToPolygonsMeters(
        lat: Double,
        lon: Double,
        rings: List<List<Pair<Double, Double>>>,
    ): Double {
        if (rings.isEmpty()) return Double.MAX_VALUE
        var best = Double.MAX_VALUE
        for (ring in rings) {
            val d = distanceToPolygonMeters(lat, lon, ring)
            if (d == 0.0) return 0.0
            if (d < best) best = d
        }
        return best
    }

    /** 点是否落在禁钓区范围内 */
    fun isInsideZone(lat: Double, lon: Double, zone: NoFishingZone): Boolean {
        return when (zone.boundaryType) {
            com.bjlure.app.domain.model.BoundaryType.POINT_RADIUS -> {
                val cLat = zone.centerLat
                val cLon = zone.centerLng
                val r = zone.radiusMeters
                if (cLat == null || cLon == null || r == null) false
                else distanceMeters(lat, lon, cLat, cLon) <= r
            }
            com.bjlure.app.domain.model.BoundaryType.POLYGON ->
                zone.polygons.any { pointInPolygon(lat, lon, it) }
        }
    }

    /** 点到禁钓区的最短距离（米）；在区域内返回 0 */
    fun distanceToZoneMeters(lat: Double, lon: Double, zone: NoFishingZone): Double {
        return when (zone.boundaryType) {
            com.bjlure.app.domain.model.BoundaryType.POINT_RADIUS -> {
                val cLat = zone.centerLat
                val cLon = zone.centerLng
                val r = zone.radiusMeters
                if (cLat == null || cLon == null || r == null) Double.MAX_VALUE
                else max(0.0, distanceMeters(lat, lon, cLat, cLon) - r)
            }
            com.bjlure.app.domain.model.BoundaryType.POLYGON ->
                distanceToPolygonsMeters(lat, lon, zone.polygons)
        }
    }

    /** 多边形的粗略面积（平方公里），用于数据自检 */
    fun polygonAreaKm2(ring: List<Pair<Double, Double>>): Double {
        if (ring.size < 3) return 0.0
        val latMid = ring.map { it.first }.average()
        val kx = 111.32 * cos(Math.toRadians(latMid))
        val ky = 110.54
        var sum = 0.0
        var j = ring.size - 1
        for (i in ring.indices) {
            val xi = ring[i].second * kx
            val yi = ring[i].first * ky
            val xj = ring[j].second * kx
            val yj = ring[j].first * ky
            sum += xj * yi - xi * yj
            j = i
        }
        return abs(sum) / 2.0
    }
}
