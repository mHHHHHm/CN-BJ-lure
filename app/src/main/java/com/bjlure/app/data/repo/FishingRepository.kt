package com.bjlure.app.data.repo

import com.bjlure.app.data.local.ListCodec
import com.bjlure.app.data.local.MonthCodec
import com.bjlure.app.data.local.PolyCodec
import com.bjlure.app.data.local.dao.FishingDao
import com.bjlure.app.data.local.entity.FishEntity
import com.bjlure.app.data.local.entity.LureEntity
import com.bjlure.app.data.local.entity.SpotEntity
import com.bjlure.app.data.local.entity.ZoneEntity
import com.bjlure.app.domain.model.AccessType
import com.bjlure.app.domain.model.BoundaryType
import com.bjlure.app.domain.model.CoordAccuracy
import com.bjlure.app.domain.model.FishCategory
import com.bjlure.app.domain.model.FishSpecies
import com.bjlure.app.domain.model.FishingSpot
import com.bjlure.app.domain.model.GearClass
import com.bjlure.app.domain.model.Lure
import com.bjlure.app.domain.model.LureCategory
import com.bjlure.app.domain.model.NoFishingZone
import com.bjlure.app.domain.model.SpotSourceType
import com.bjlure.app.domain.model.Terrain
import com.bjlure.app.domain.model.WaterLayer
import com.bjlure.app.domain.model.WaterType
import com.bjlure.app.domain.model.ZoneType
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 数据仓库：负责 entity ↔ domain 的映射。
 * 上层（ViewModel）只看见领域模型，看不见 Room 与字符串编码。
 */
class FishingRepository(private val dao: FishingDao) {

    suspend fun spots(): List<FishingSpot> = withContext(Dispatchers.IO) {
        dao.getFreeSpots().map { it.toDomain() }
    }

    suspend fun spot(id: String): FishingSpot? = withContext(Dispatchers.IO) {
        dao.getSpot(id)?.toDomain()
    }

    suspend fun fish(): List<FishSpecies> = withContext(Dispatchers.IO) {
        dao.getFish().map { it.toDomain() }
    }

    suspend fun lures(): List<Lure> = withContext(Dispatchers.IO) {
        dao.getLures().map { it.toDomain() }
    }

    suspend fun zones(): List<NoFishingZone> = withContext(Dispatchers.IO) {
        dao.getZones().map { it.toDomain() }
    }

    /** 一次性把校验需要的数据取全，避免在列表滚动时反复查库 */
    suspend fun snapshot(): Snapshot = withContext(Dispatchers.IO) {
        Snapshot(
            spots = dao.getFreeSpots().map { it.toDomain() },
            fish = dao.getFish().map { it.toDomain() },
            lures = dao.getLures().map { it.toDomain() },
            zones = dao.getZones().map { it.toDomain() },
        )
    }

    data class Snapshot(
        val spots: List<FishingSpot>,
        val fish: List<FishSpecies>,
        val lures: List<Lure>,
        val zones: List<NoFishingZone>,
    )
}

// ---------------------------------------------------------------------------
// 映射
// ---------------------------------------------------------------------------

private fun SpotEntity.toDomain() = FishingSpot(
    id = id,
    name = name,
    district = district,
    address = address,
    latitude = latitude,
    longitude = longitude,
    coordAccuracy = enumOrDefault(coordAccuracy, CoordAccuracy.UNVERIFIED),
    radiusMeters = radiusMeters,
    waterType = enumOrDefault(waterType, WaterType.RIVER),
    waterName = waterName,
    accessType = enumOrDefault(accessType, AccessType.FREE),
    sourceType = enumOrDefault(sourceType, SpotSourceType.WILD),
    fishingMethods = ListCodec.decode(fishingMethods),
    targetFishIds = ListCodec.decode(targetFishIds),
    terrain = ListCodec.decode(terrain).mapNotNull { name ->
        Terrain.entries.firstOrNull { it.name == name }
    },
    parking = parking,
    notes = notes,
    bestSeason = bestSeason,
    bestTimeOfDay = bestTimeOfDay,
    lures = decodeJsonArray(lures),
    isActive = isActive,
    source = source,
    sourceYear = sourceYear,
    legalWarning = legalWarning,
)

private fun FishEntity.toDomain() = FishSpecies(
    id = id,
    name = name,
    aliases = ListCodec.decode(aliases),
    category = enumOrDefault(category, FishCategory.ALL),
    lureActiveSeason = lureActiveSeason,
    activeMonths = MonthCodec.decode(activeMonths),
    bestTimeOfDay = bestTimeOfDay,
    habitatPreference = habitatPreference,
    abundance = abundance,
)

private fun LureEntity.toDomain() = Lure(
    id = id,
    name = name,
    category = enumOrDefault(category, LureCategory.MINNOW),
    targetFishIds = ListCodec.decode(targetFishIds),
    waterLayer = enumOrDefault(waterLayer, WaterLayer.MID),
    weightRange = weightRange,
    actionStyle = actionStyle,
    sceneNote = sceneNote,
    goodTerrain = ListCodec.decode(goodTerrain).mapNotNull { name ->
        Terrain.entries.firstOrNull { it.name == name }
    },
    gearClass = enumOrDefault(gearClass, GearClass.GENERAL),
)

private fun ZoneEntity.toDomain() = NoFishingZone(
    id = id,
    name = name,
    type = enumOrDefault(type, ZoneType.PERMANENT_FISHING_BAN),
    boundaryType = enumOrDefault(boundaryType, BoundaryType.POLYGON),
    centerLat = centerLat,
    centerLng = centerLng,
    radiusMeters = radiusMeters,
    polygons = PolyCodec.decode(polygons),
    activeMonths = MonthCodec.decode(activeMonths),
    ruleDescription = ruleDescription,
    legalBasis = legalBasis,
)

private inline fun <reified T : Enum<T>> enumOrDefault(name: String, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: fallback

private fun decodeJsonArray(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(raw)
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) out.add(arr.getString(i))
        out
    } catch (t: Throwable) {
        emptyList()
    }
}
