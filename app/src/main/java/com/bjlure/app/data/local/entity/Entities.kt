package com.bjlure.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room 实体。字段与 assets/seed 目录下的各个 JSON 一一对应。
 *
 * 列表类字段统一用字符串编码存储（见 [com.bjlure.app.data.local.PolyCodec] 与 TypeConverters），
 * 这样可以避免为几个小列表额外建表，也让种子数据导入变成一次纯字符串搬运。
 */

@Entity(tableName = "spots")
data class SpotEntity(
    @PrimaryKey val id: String,
    val name: String,
    val district: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    /** EXACT / APPROXIMATE / UNVERIFIED */
    val coordAccuracy: String,
    /** 钓点覆盖范围半径（米），地图上按这个画圈 */
    val radiusMeters: Int,
    /** RIVER / RESERVOIR / POND / WETLAND / CANAL */
    val waterType: String,
    val waterName: String,
    /** FREE / PAID，列表层只展示 FREE */
    val accessType: String,
    /** OFFICIAL / WILD / RISKY */
    val sourceType: String,
    /** 逗号分隔，如 "路亚,台钓" */
    val fishingMethods: String,
    /** 逗号分隔的鱼种 id */
    val targetFishIds: String,
    /** 逗号分隔的地形枚举名 */
    val terrain: String,
    val parking: Boolean?,
    val notes: String,
    val bestSeason: String,
    val bestTimeOfDay: String,
    /** JSON 数组字符串 */
    val lures: String,
    val isActive: Boolean,
    val source: String,
    val sourceYear: Int,
    val legalWarning: String?,
)

@Entity(tableName = "fish")
data class FishEntity(
    @PrimaryKey val id: String,
    val name: String,
    val aliases: String,
    val category: String,
    val lureActiveSeason: String,
    val activeMonths: String,
    val bestTimeOfDay: String,
    val habitatPreference: String,
    val abundance: Int,
)

@Entity(tableName = "lures")
data class LureEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val targetFishIds: String,
    val waterLayer: String,
    val weightRange: String,
    val actionStyle: String,
    val sceneNote: String,
    val goodTerrain: String,
    /** MICRO / GENERAL / HEAVY / DISTANCE */
    val gearClass: String,
)

@Entity(
    tableName = "zones",
    indices = [Index("type")],
)
data class ZoneEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** WATER_SOURCE_PROTECTION / PROPAGATION_RELEASE / PARK_NO_FISHING / PERMANENT_FISHING_BAN / SEASONAL_FISHING_BAN */
    val type: String,
    val boundaryType: String,
    val centerLat: Double?,
    val centerLng: Double?,
    val radiusMeters: Int?,
    /** 编码后的多环多边形，见 PolyCodec */
    val polygons: String,
    /** 逗号分隔的生效月份，空表示全年 */
    val activeMonths: String,
    val ruleDescription: String,
    val legalBasis: String,
)
