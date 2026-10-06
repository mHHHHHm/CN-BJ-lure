package com.bjlure.app.domain.model

import com.bjlure.app.domain.FishingIndex

/**
 * 天气与出钓指数相关的领域模型。
 *
 * 这些模型是纯数据的，取数逻辑在 data/weather 里，算分逻辑在 domain/FishingIndex.kt。
 * 分开的好处是：指数算法可以完全离线单元测试，不需要联网。
 */

/** 天气实况 / 预报的一个时间点 */
data class WeatherPoint(
    /** epoch 毫秒 */
    val timeMillis: Long,
    val temperatureC: Double,
    val apparentTemperatureC: Double,
    /** 相对湿度 % */
    val humidity: Int,
    /** 10 米风速 m/s */
    val windSpeedMs: Double,
    /** 风向角度 */
    val windDirectionDeg: Int,
    /** 海平面气压 hPa */
    val pressureHpa: Double,
    /** 降水量 mm */
    val precipitationMm: Double,
    /** 云量 % */
    val cloudCover: Int,
    /** WMO 天气代码 */
    val weatherCode: Int,
    /** 是否为白天 */
    val isDay: Boolean,
)

/** 某天的概览 */
data class WeatherDay(
    val dateLabel: String,
    val tempMaxC: Double,
    val tempMinC: Double,
    val precipitationMm: Double,
    val weatherCode: Int,
    val sunriseMillis: Long?,
    val sunsetMillis: Long?,
)

data class WeatherSnapshot(
    val placeName: String,
    val latitude: Double,
    val longitude: Double,
    val current: WeatherPoint,
    /** 未来 48 小时逐小时 */
    val hourly: List<WeatherPoint>,
    /** 未来 7 天 */
    val daily: List<WeatherDay>,
    /** 过去若干天的气温，用于估算水温 */
    val pastTemperatures: List<Double>,
    val fetchedAtMillis: Long,
) {
    /** 估算水温：水体热惯性大，用过去几天气温的指数加权平均近似 */
    val estimatedWaterTempC: Double get() = FishingIndex.estimateWaterTemp(pastTemperatures, current.temperatureC)
}

/** 穿衣建议 */
data class ClothingAdvice(
    val level: String,
    val headline: String,
    val detail: List<String>,
)

/** 一个推荐出钓时段 */
data class FishingWindow(
    val startMillis: Long,
    val endMillis: Long,
    val score: Int,
    val label: String,
    val reason: String,
)

/** 出钓指数总评 */
data class FishingOutlook(
    val score: Int,
    val grade: String,
    val probabilityPercent: Int,
    val headline: String,
    /** 因子明细：名称 → (得分, 满分, 说明) */
    val factors: List<IndexFactor>,
    val windows: List<FishingWindow>,
    val suggestions: List<String>,
    val waterTempC: Double,
    val clothing: ClothingAdvice,
)

data class IndexFactor(
    val name: String,
    val score: Int,
    val maxScore: Int,
    val detail: String,
)
