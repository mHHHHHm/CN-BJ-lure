package com.bjlure.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bjlure.app.BjLureApplication
import com.bjlure.app.data.weather.WeatherRepository
import com.bjlure.app.domain.FishingIndex
import com.bjlure.app.domain.LureMatcher
import com.bjlure.app.domain.NoFishingGuard
import com.bjlure.app.domain.SpotFilter
import com.bjlure.app.domain.model.FishSpecies
import com.bjlure.app.domain.model.FishingOutlook
import com.bjlure.app.domain.model.FishingSpot
import com.bjlure.app.domain.model.GearClass
import com.bjlure.app.domain.model.Lure
import com.bjlure.app.domain.model.LureRecommendation
import com.bjlure.app.domain.model.NoFishingZone
import com.bjlure.app.domain.model.Terrain
import com.bjlure.app.domain.model.WeatherSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

/** 出行的起点。用户定位或手动选的预设地点，二选一。 */
data class Origin(
    val name: String,
    val lat: Double,
    val lon: Double,
    /** true 表示来自 GPS 定位 */
    val fromGps: Boolean = false,
)

data class WeatherUiState(
    val loading: Boolean = false,
    val snapshot: WeatherSnapshot? = null,
    val outlook: FishingOutlook? = null,
    val error: String? = null,
    val lastUpdatedMillis: Long = 0L,
)

data class FishingUiState(
    val loading: Boolean = true,
    val error: String? = null,
    /** 已按筛选条件处理的列表 */
    val cards: List<SpotFilter.Card> = emptyList(),
    val fishOptions: List<FishSpecies> = emptyList(),
    val districtOptions: List<String> = emptyList(),
    val selectedFishIds: Set<String> = emptySet(),
    val district: String? = null,
    val radiusKm: Int? = null,
    /** 装备档位：微物 / 泛用 / 雷强 / 远投，null 表示不限 */
    val selectedGear: GearClass? = null,
    val locating: Boolean = false,
    val month: Int = 7,
    val quality: Map<String, Int> = emptyMap(),
    /** 当前起点，null 表示还没定位也没选 */
    val origin: Origin? = null,
    val weather: WeatherUiState = WeatherUiState(),
)

/**
 * 全 App 共用一个 ViewModel。
 * 数据量很小（几十个点 + 十几条饵），一次性装进内存比反复查库省事也更快，
 * 禁钓区多边形比较大，更是不能在每个列表项里现算。
 */
class FishingViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as BjLureApplication).repository
    private val weatherRepo = WeatherRepository()

    private var spots: List<FishingSpot> = emptyList()
    private var fish: List<FishSpecies> = emptyList()
    private var lures: List<Lure> = emptyList()
    private var zones: List<NoFishingZone> = emptyList()

    private val _state = MutableStateFlow(FishingUiState())
    val state: StateFlow<FishingUiState> = _state.asStateFlow()

    private var month: Int = currentMonth()

    /**
     * 预设起点。坐标都取自前面核实过的地标（OSM / Photon 地理编码），
     * 不是随手填的区中心 —— 反正只是用来算距离排序，宁可给准的。
     */
    val presetOrigins: List<Origin> = listOf(
        Origin("海淀 · 上庄", 40.126700, 116.204400),
        Origin("海淀 · 中关村", 39.983600, 116.316400),
        Origin("朝阳 · 国贸", 39.908800, 116.461000),
        Origin("东城 · 天安门", 39.908700, 116.397500),
        Origin("通州 · 北关闸", 39.917600, 116.662700),
        Origin("顺义 · 潮白河滨河", 40.167400, 116.676100),
        Origin("怀柔 · 四渡河", 40.339100, 116.474100),
        Origin("昌平 · 十三陵水库", 40.257200, 116.259200),
        Origin("房山 · 十渡", 39.636200, 115.584700),
        Origin("延庆 · 妫水河", 40.480500, 116.066800),
    )

    fun currentMonthValue(): Int = month

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                // 种子导入发生在 Application.onCreate，这里等它落地
                var snapshot = repo.snapshot()
                if (snapshot.spots.isEmpty()) {
                    kotlinx.coroutines.delay(400)
                    snapshot = repo.snapshot()
                }
                spots = snapshot.spots
                fish = snapshot.fish
                lures = snapshot.lures
                zones = snapshot.zones

                _state.value = _state.value.copy(
                    loading = false,
                    fishOptions = fish,
                    districtOptions = spots.map { it.district }.distinct().sorted(),
                    error = if (spots.isEmpty()) "钓点数据为空，请检查 assets/seed 是否正确打包" else null,
                )
                recompute()
                // 起点的默认值：没定位也没手选时，先给一个，免得天气卡空着
                if (_state.value.origin == null) {
                    setOrigin(presetOrigins.first(), silent = true)
                }
            } catch (t: Throwable) {
                _state.value = _state.value.copy(loading = false, error = t.message ?: "载入失败")
            }
        }
    }

    // ---------------- 筛选 ----------------

    fun toggleFish(id: String) {
        val cur = _state.value.selectedFishIds
        setFilters(selectedFishIds = if (cur.contains(id)) cur - id else cur + id)
    }

    fun clearFish() = setFilters(selectedFishIds = emptySet())

    fun setDistrict(d: String?) = setFilters(district = d)

    fun setRadius(km: Int?) = setFilters(radiusKm = km)

    /** 档位筛选：再点一次同一个就取消 */
    fun setGear(g: GearClass?) =
        setFilters(gear = if (_state.value.selectedGear == g) null else g)

    fun setMonth(m: Int) {
        month = m.coerceIn(1, 12)
        recompute()
    }

    private fun setFilters(
        selectedFishIds: Set<String> = _state.value.selectedFishIds,
        district: String? = _state.value.district,
        radiusKm: Int? = _state.value.radiusKm,
        gear: GearClass? = _state.value.selectedGear,
    ) {
        _state.value = _state.value.copy(
            selectedFishIds = selectedFishIds,
            district = district,
            radiusKm = radiusKm,
            selectedGear = gear,
        )
        recompute()
    }

    fun resetFilters() = setFilters(
        selectedFishIds = emptySet(),
        district = null,
        radiusKm = null,
        gear = null,
    )

    // ---------------- 起点与天气 ----------------

    /** 用户在界面上选了一个起点（预设或定位结果） */
    fun setOrigin(origin: Origin, silent: Boolean = false) {
        _state.value = _state.value.copy(origin = origin, locating = false)
        recompute()
        loadWeather(origin, force = !silent)
    }

    /** GPS 定位成功 */
    fun onLocation(lat: Double, lon: Double) {
        setOrigin(Origin("我的位置", lat, lon, fromGps = true), silent = false)
    }

    fun onLocating() {
        _state.value = _state.value.copy(locating = true)
    }

    fun onLocationFailed() {
        _state.value = _state.value.copy(locating = false)
    }

    fun refreshWeather() {
        val o = _state.value.origin ?: return
        loadWeather(o, force = true)
    }

    private fun loadWeather(origin: Origin, force: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                weather = _state.value.weather.copy(loading = true, error = null),
            )
            try {
                val snap = weatherRepo.get(origin.lat, origin.lon, origin.name, force)
                val outlook = FishingIndex.evaluate(snap, month)
                _state.value = _state.value.copy(
                    weather = WeatherUiState(
                        loading = false,
                        snapshot = snap,
                        outlook = outlook,
                        error = null,
                        lastUpdatedMillis = System.currentTimeMillis(),
                    ),
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    weather = _state.value.weather.copy(
                        loading = false,
                        error = "天气获取失败：${t.message ?: "网络不可用"}",
                    ),
                )
            }
        }
    }

    private fun recompute() {
        val s = _state.value
        val o = s.origin
        val cards = SpotFilter.build(
            spots = spots,
            fish = fish,
            zones = zones,
            month = month,
            selectedFishIds = s.selectedFishIds,
            district = s.district,
            maxDistanceKm = s.radiusKm,
            userLat = o?.lat,
            userLon = o?.lon,
            gear = s.selectedGear,
            lures = lures,
        )
        _state.value = s.copy(
            cards = cards,
            month = month,
            quality = SpotFilter.dataQuality(cards),
        )
    }

    // ---------------- 详情 ----------------

    fun spotById(id: String): FishingSpot? = spots.firstOrNull { it.id == id }

    fun fishById(id: String): FishSpecies? = fish.firstOrNull { it.id == id }

    fun fishNames(ids: List<String>): List<String> = ids.mapNotNull { fishById(it)?.name }

    /** 某个钓点的饵料推荐 */
    fun recommendationsFor(spot: FishingSpot): List<LureRecommendation> =
        LureMatcher.recommend(
            fishIds = spot.targetFishIds,
            terrain = spot.terrain,
            lures = lures,
            fish = fish,
            month = month,
        )

    /** 某个鱼种的饵料推荐（鱼种工具页用） */
    fun recommendationsForFish(
        fishId: String,
        terrain: List<Terrain> = emptyList(),
        gear: GearClass? = null,
    ): List<LureRecommendation> = LureMatcher.recommendForFish(
        fishId = fishId,
        terrain = terrain,
        lures = if (gear == null) lures else lures.filter { it.gearClass == gear },
        fish = fish,
        month = month,
    )

    /** 某个鱼种在当前装备档位下的推荐（鱼种工具页的档位切换） */
    fun allLures(): List<Lure> = lures

    /** 单独对一个坐标做禁钓校验，用于「导航前确认」 */
    fun guardFor(spot: FishingSpot) = NoFishingGuard.check(
        lat = spot.latitude,
        lon = spot.longitude,
        zones = zones,
        month = month,
    )

    fun allFish(): List<FishSpecies> = fish

    /**
     * 某个鱼种在每个装备档位下各有多少款饵。
     *
     * 这是给界面「置灰没货的档位」用的。新手并不知道白条只能玩微物档，
     * 选了「泛用」看到空列表就会一脸懵 —— 那是设计的问题，不是他的问题。
     */
    fun gearCountsForFish(fishId: String): Map<GearClass, Int> =
        lures.filter { it.targetFishIds.contains(fishId) }
            .groupingBy { it.gearClass }
            .eachCount()

    fun allZones(): List<NoFishingZone> = zones

    fun allSpots(): List<FishingSpot> = spots

    private fun currentMonth(): Int = Calendar.getInstance().get(Calendar.MONTH) + 1
}
