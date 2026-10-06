package com.bjlure.app.data.local

/**
 * 多环多边形的字符串编解码。
 *
 * 格式：环之间用 `|` 分隔，环内顶点之间用 `;` 分隔，单个顶点为 `纬度,经度`。
 * 例：`40.5,116.9;40.5,116.91|41.1,117.2;41.1,117.21`
 *
 * 选这个格式而不是 JSON，是因为禁钓区多边形动辄上万点，
 * 导入时用 split 解析比 JSON 解析快一个量级。
 */
object PolyCodec {

    fun encode(polygons: List<List<Pair<Double, Double>>>): String {
        if (polygons.isEmpty()) return ""
        val sb = StringBuilder()
        for ((ri, ring) in polygons.withIndex()) {
            if (ri > 0) sb.append('|')
            for ((pi, point) in ring.withIndex()) {
                if (pi > 0) sb.append(';')
                sb.append(point.first).append(',').append(point.second)
            }
        }
        return sb.toString()
    }

    fun decode(raw: String?): List<List<Pair<Double, Double>>> {
        if (raw.isNullOrBlank()) return emptyList()
        val out = ArrayList<List<Pair<Double, Double>>>(4)
        for (ringRaw in raw.split('|')) {
            if (ringRaw.isBlank()) continue
            val ring = ArrayList<Pair<Double, Double>>(64)
            for (ptRaw in ringRaw.split(';')) {
                if (ptRaw.isBlank()) continue
                val comma = ptRaw.indexOf(',')
                if (comma <= 0) continue
                val lat = ptRaw.substring(0, comma).toDoubleOrNull() ?: continue
                val lon = ptRaw.substring(comma + 1).toDoubleOrNull() ?: continue
                ring.add(lat to lon)
            }
            if (ring.size >= 3) out.add(ring)
        }
        return out
    }
}

/** 逗号分隔列表的编解码，用于 id 列表、枚举名列表 */
object ListCodec {
    fun encode(list: List<String>): String = list.joinToString(",")

    fun decode(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }
}

/** 月份列表编解码 */
object MonthCodec {
    fun encode(months: List<Int>): String = months.joinToString(",")

    fun decode(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(',').mapNotNull { it.trim().toIntOrNull() }
    }
}
