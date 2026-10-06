package com.bjlure.app.data.seed

import android.content.Context
import android.util.Log
import com.bjlure.app.data.local.ListCodec
import com.bjlure.app.data.local.MonthCodec
import com.bjlure.app.data.local.PolyCodec
import com.bjlure.app.data.local.dao.FishingDao
import com.bjlure.app.data.local.entity.FishEntity
import com.bjlure.app.data.local.entity.LureEntity
import com.bjlure.app.data.local.entity.SpotEntity
import com.bjlure.app.data.local.entity.ZoneEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 从 assets/seed 目录下的 JSON 导入种子数据。
 *
 * 这些 JSON 由 tools/gen_seed.py 生成，数据来源是已交叉核实过的公开资料
 * （北京市水务局适宜垂钓区域名单、京政农发〔2019〕63 号禁渔通告、
 *   OpenStreetMap 水体边界），不是随手编的示例数据。
 */
class SeedImporter(
    private val context: Context,
    private val dao: FishingDao,
) {

    /**
     * 只在库空的时候导入。种子数据升级时可以改 [SEED_VERSION]，
     * 版本变了就整包重灌（钓点是只读参考数据，不需要保留用户改动）。
     */
    suspend fun importIfNeeded() = withContext(Dispatchers.IO) {
        val hasData = dao.spotCount() > 0 && dao.zoneCount() > 0
        if (hasData && prefs().getInt(KEY_VERSION, 0) == SEED_VERSION) {
            Log.i(TAG, "种子数据已是最新（v$SEED_VERSION），跳过导入")
            return@withContext
        }
        try {
            val spots = parseSpots(readAsset("spots.json"))
            val fish = parseFish(readAsset("fish.json"))
            val lures = parseLures(readAsset("lures.json"))
            val zones = parseZones(readAsset("zones.json"))
            dao.replaceAll(spots, fish, lures, zones)
            prefs().edit().putInt(KEY_VERSION, SEED_VERSION).apply()
            Log.i(TAG, "种子数据导入完成：钓点 ${spots.size}、鱼种 ${fish.size}、" +
                "拟饵 ${lures.size}、禁钓区 ${zones.size}")
        } catch (t: Throwable) {
            Log.e(TAG, "种子数据导入失败", t)
            throw t
        }
    }

    private fun prefs() = context.getSharedPreferences("seed", Context.MODE_PRIVATE)

    private fun readAsset(name: String): String =
        context.assets.open("seed/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }

    // ------------------------------------------------------------------

    private fun parseSpots(text: String): List<SpotEntity> {
        val arr = JSONArray(text)
        val out = ArrayList<SpotEntity>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out += SpotEntity(
                id = o.getString("id"),
                name = o.getString("name"),
                district = o.getString("district"),
                address = o.optString("address"),
                latitude = o.optDoubleOrNull("latitude"),
                longitude = o.optDoubleOrNull("longitude"),
                coordAccuracy = o.optString("coordAccuracy", "UNVERIFIED"),
                radiusMeters = o.optInt("radiusMeters", 300),
                waterType = o.optString("waterType", "RIVER"),
                waterName = o.optString("waterName"),
                accessType = o.optString("accessType", "FREE"),
                sourceType = o.optString("sourceType", "WILD"),
                fishingMethods = ListCodec.encode(o.optStringList("fishingMethods")),
                targetFishIds = ListCodec.encode(o.optStringList("targetFishIds")),
                terrain = ListCodec.encode(o.optStringList("terrain")),
                parking = o.optBooleanOrNull("parking"),
                notes = o.optString("notes"),
                bestSeason = o.optString("bestSeason"),
                bestTimeOfDay = o.optString("bestTimeOfDay"),
                lures = JSONArray(o.optStringList("lures")).toString(),
                isActive = o.optBoolean("isActive", true),
                source = o.optString("source"),
                sourceYear = o.optInt("sourceYear", 0),
                legalWarning = o.optStringOrNull("legalWarning"),
            )
        }
        return out
    }

    private fun parseFish(text: String): List<FishEntity> {
        val arr = JSONArray(text)
        val out = ArrayList<FishEntity>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out += FishEntity(
                id = o.getString("id"),
                name = o.getString("name"),
                aliases = ListCodec.encode(o.optStringList("aliases")),
                category = o.optString("category", "ALL"),
                lureActiveSeason = o.optString("lureActiveSeason"),
                activeMonths = MonthCodec.encode(o.optIntList("activeMonths")),
                bestTimeOfDay = o.optString("bestTimeOfDay"),
                habitatPreference = o.optString("habitatPreference"),
                abundance = o.optInt("abundance", 50),
            )
        }
        return out
    }

    private fun parseLures(text: String): List<LureEntity> {
        val arr = JSONArray(text)
        val out = ArrayList<LureEntity>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out += LureEntity(
                id = o.getString("id"),
                name = o.getString("name"),
                category = o.optString("category", "MINNOW"),
                targetFishIds = ListCodec.encode(o.optStringList("targetFishIds")),
                waterLayer = o.optString("waterLayer", "MID"),
                weightRange = o.optString("weightRange"),
                actionStyle = o.optString("actionStyle"),
                sceneNote = o.optString("sceneNote"),
                goodTerrain = ListCodec.encode(o.optStringList("goodTerrain")),
                gearClass = o.optString("gearClass", "GENERAL"),
            )
        }
        return out
    }

    private fun parseZones(text: String): List<ZoneEntity> {
        val arr = JSONArray(text)
        val out = ArrayList<ZoneEntity>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val polygonsRaw = o.optJSONArray("polygons") ?: JSONArray()
            val polygons = ArrayList<List<Pair<Double, Double>>>(polygonsRaw.length())
            for (ri in 0 until polygonsRaw.length()) {
                val ringRaw = polygonsRaw.getJSONArray(ri)
                val ring = ArrayList<Pair<Double, Double>>(ringRaw.length())
                for (pi in 0 until ringRaw.length()) {
                    val pt = ringRaw.getJSONArray(pi)
                    ring.add(pt.getDouble(0) to pt.getDouble(1))
                }
                if (ring.size >= 3) polygons.add(ring)
            }
            out += ZoneEntity(
                id = o.getString("id"),
                name = o.getString("name"),
                type = o.optString("type", "PERMANENT_FISHING_BAN"),
                boundaryType = o.optString("boundaryType", "POLYGON"),
                centerLat = o.optDoubleOrNull("centerLat"),
                centerLng = o.optDoubleOrNull("centerLng"),
                radiusMeters = o.optIntOrNull("radiusMeters"),
                polygons = PolyCodec.encode(polygons),
                activeMonths = MonthCodec.encode(o.optIntList("activeMonths")),
                ruleDescription = o.optString("ruleDescription"),
                legalBasis = o.optString("legalBasis"),
            )
        }
        return out
    }

    // ---------------- JSON 小工具 ----------------
    // org.json 的 optDouble 在缺字段时返回 NaN 而不是 null，这里统一收口

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val v = optDouble(key, Double.NaN)
        return if (v.isNaN()) null else v
    }

    private fun JSONObject.optIntOrNull(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return optInt(key)
    }

    private fun JSONObject.optBooleanOrNull(key: String): Boolean? {
        if (!has(key) || isNull(key)) return null
        return optBoolean(key)
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val v = optString(key)
        return v.ifBlank { null }
    }

    private fun JSONObject.optStringList(key: String): List<String> {
        val a = optJSONArray(key) ?: return emptyList()
        val out = ArrayList<String>(a.length())
        for (i in 0 until a.length()) out.add(a.getString(i))
        return out
    }

    private fun JSONObject.optIntList(key: String): List<Int> {
        val a = optJSONArray(key) ?: return emptyList()
        val out = ArrayList<Int>(a.length())
        for (i in 0 until a.length()) out.add(a.getInt(i))
        return out
    }

    companion object {
        private const val TAG = "SeedImporter"

        /** 记录已导入的种子版本，存在独立的 SharedPreferences 里 */
        private const val KEY_VERSION = "seed_version"

        /** 种子数据结构或内容有变化时 +1，App 下次启动会整包重灌 */
        const val SEED_VERSION = 11
    }
}
