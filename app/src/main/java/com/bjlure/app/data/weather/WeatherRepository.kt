package com.bjlure.app.data.weather

import android.util.Log
import com.bjlure.app.domain.model.WeatherDay
import com.bjlure.app.domain.model.WeatherPoint
import com.bjlure.app.domain.model.WeatherSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 天气数据源：Open-Meteo。
 *
 * 选它的理由很直接：免费、不用注册、不用 API Key，支持按经纬度取数，
 * 而且一次请求就能拿到逐小时 + 逐日 + 日出日落，正好够算出钓指数。
 * 用 Retrofit 也是可以的，但这里只有两个接口、返回结构固定，
 * 用 HttpURLConnection + org.json 反而少两个依赖。
 */
class WeatherRepository {

    private var cached: CacheEntry? = null

    private data class CacheEntry(
        val lat: Double,
        val lon: Double,
        val snapshot: WeatherSnapshot,
        val atMillis: Long,
    )

    /**
     * @param force 忽略缓存强制刷新
     */
    suspend fun get(
        lat: Double,
        lon: Double,
        placeName: String,
        force: Boolean = false,
    ): WeatherSnapshot = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val c = cached
        // 15 分钟内且位置没变就直接用缓存，省流量也省电
        if (!force && c != null && now - c.atMillis < CACHE_TTL_MS &&
            kotlin.math.abs(c.lat - lat) < 0.05 && kotlin.math.abs(c.lon - lon) < 0.05
        ) {
            return@withContext c.snapshot
        }

        val json = httpGet(buildUrl(lat, lon))
        val snapshot = parse(json, lat, lon, placeName, now)
        cached = CacheEntry(lat, lon, snapshot, now)
        snapshot
    }

    // ------------------------------------------------------------------

    private fun buildUrl(lat: Double, lon: Double): String {
        val current = listOf(
            "temperature_2m", "relative_humidity_2m", "apparent_temperature", "is_day",
            "precipitation", "weather_code", "cloud_cover", "pressure_msl",
            "wind_speed_10m", "wind_direction_10m",
        ).joinToString(",")

        val hourly = listOf(
            "temperature_2m", "relative_humidity_2m", "apparent_temperature", "precipitation",
            "weather_code", "cloud_cover", "pressure_msl", "wind_speed_10m",
            "wind_direction_10m", "is_day",
        ).joinToString(",")

        val daily = listOf(
            "weather_code", "temperature_2m_max", "temperature_2m_min",
            "precipitation_sum", "sunrise", "sunset",
        ).joinToString(",")

        return buildString {
            append(BASE_URL)
            append("?latitude=").append(lat)
            append("&longitude=").append(lon)
            append("&current=").append(current)
            append("&hourly=").append(hourly)
            append("&daily=").append(daily)
            append("&past_days=").append(PAST_DAYS)
            append("&forecast_days=").append(FORECAST_DAYS)
            append("&timezone=").append(URLEncoder.encode("Asia/Shanghai", "UTF-8"))
            append("&timeformat=unixtime")
            append("&wind_speed_unit=ms")
        }
    }

    private fun httpGet(urlStr: String): String {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "bjlure-android/1.0")
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                throw java.io.IOException("天气服务返回 HTTP $code")
            }
            return conn.inputStream.bufferedReader().use(BufferedReader::readText)
        } finally {
            conn?.disconnect()
        }
    }

    // ------------------------------------------------------------------

    private fun parse(
        json: String,
        lat: Double,
        lon: Double,
        placeName: String,
        fetchedAt: Long,
    ): WeatherSnapshot {
        val root = JSONObject(json)

        val cur = root.getJSONObject("current")
        val current = WeatherPoint(
            timeMillis = cur.optLong("time") * 1000L,
            temperatureC = cur.optDouble("temperature_2m", 0.0),
            apparentTemperatureC = cur.optDouble("apparent_temperature", 0.0),
            humidity = cur.optInt("relative_humidity_2m", 0),
            windSpeedMs = cur.optDouble("wind_speed_10m", 0.0),
            windDirectionDeg = cur.optInt("wind_direction_10m", 0),
            pressureHpa = cur.optDouble("pressure_msl", 1013.0),
            precipitationMm = cur.optDouble("precipitation", 0.0),
            cloudCover = cur.optInt("cloud_cover", 0),
            weatherCode = cur.optInt("weather_code", 0),
            isDay = cur.optInt("is_day", 1) == 1,
        )

        val h = root.getJSONObject("hourly")
        val times = h.getJSONArray("time")
        val temps = h.getJSONArray("temperature_2m")
        val appTemps = h.optJSONArray("apparent_temperature")
        val hums = h.optJSONArray("relative_humidity_2m")
        val precs = h.optJSONArray("precipitation")
        val codes = h.optJSONArray("weather_code")
        val clouds = h.optJSONArray("cloud_cover")
        val pressures = h.optJSONArray("pressure_msl")
        val winds = h.optJSONArray("wind_speed_10m")
        val windDirs = h.optJSONArray("wind_direction_10m")
        val isDays = h.optJSONArray("is_day")

        val hourly = ArrayList<WeatherPoint>(times.length())
        for (i in 0 until times.length()) {
            hourly += WeatherPoint(
                timeMillis = times.optLong(i) * 1000L,
                temperatureC = temps.optDouble(i, 0.0),
                apparentTemperatureC = appTemps?.optDouble(i, temps.optDouble(i, 0.0))
                    ?: temps.optDouble(i, 0.0),
                humidity = hums?.optInt(i, 0) ?: 0,
                windSpeedMs = winds?.optDouble(i, 0.0) ?: 0.0,
                windDirectionDeg = windDirs?.optInt(i, 0) ?: 0,
                pressureHpa = pressures?.optDouble(i, 1013.0) ?: 1013.0,
                precipitationMm = precs?.optDouble(i, 0.0) ?: 0.0,
                cloudCover = clouds?.optInt(i, 0) ?: 0,
                weatherCode = codes?.optInt(i, 0) ?: 0,
                isDay = (isDays?.optInt(i, 1) ?: 1) == 1,
            )
        }

        val d = root.getJSONObject("daily")
        val dTimes = d.getJSONArray("time")
        val dMax = d.getJSONArray("temperature_2m_max")
        val dMin = d.getJSONArray("temperature_2m_min")
        val dPrec = d.optJSONArray("precipitation_sum")
        val dCode = d.optJSONArray("weather_code")
        val dSunrise = d.optJSONArray("sunrise")
        val dSunset = d.optJSONArray("sunset")

        val daily = ArrayList<WeatherDay>(dTimes.length())
        val df = SimpleDateFormat("M月d日", Locale.CHINA).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }
        for (i in 0 until dTimes.length()) {
            daily += WeatherDay(
                dateLabel = df.format(Date(dTimes.optLong(i) * 1000L)),
                tempMaxC = dMax.optDouble(i, 0.0),
                tempMinC = dMin.optDouble(i, 0.0),
                precipitationMm = dPrec?.optDouble(i, 0.0) ?: 0.0,
                weatherCode = dCode?.optInt(i, 0) ?: 0,
                sunriseMillis = dSunrise?.optLong(i)?.takeIf { it > 0 }?.times(1000L),
                sunsetMillis = dSunset?.optLong(i)?.takeIf { it > 0 }?.times(1000L),
            )
        }

        return WeatherSnapshot(
            placeName = placeName,
            latitude = lat,
            longitude = lon,
            current = current,
            hourly = hourly,
            daily = daily,
            pastTemperatures = dailyAverages(hourly, current.timeMillis),
            fetchedAtMillis = fetchedAt,
        )
    }

    /**
     * 取过去几天的日平均气温，用来估算水温。
     * 用日平均而不是瞬时值，是因为水体响应的是整体热量收支，不是某一刻的气温。
     */
    private fun dailyAverages(hourly: List<WeatherPoint>, nowMillis: Long): List<Double> {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
        cal.timeInMillis = nowMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val todayStart = cal.timeInMillis

        val buckets = sortedMapOf<Long, MutableList<Double>>()
        for (p in hourly) {
            if (p.timeMillis >= todayStart) continue
            val dayIndex = (p.timeMillis - todayStart) / DAY_MS
            buckets.getOrPut(dayIndex) { ArrayList() }.add(p.temperatureC)
        }
        return buckets.values
            .filter { it.isNotEmpty() }
            .map { it.average() }
            .takeLast(PAST_DAYS)
    }

    companion object {
        private const val TAG = "WeatherRepository"
        private const val BASE_URL = "https://api.open-meteo.com/v1/forecast"
        private const val PAST_DAYS = 5
        private const val FORECAST_DAYS = 7
        private const val CACHE_TTL_MS = 15 * 60 * 1000L
        private const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
