package com.bjlure.app.domain

import com.bjlure.app.domain.model.ClothingAdvice
import com.bjlure.app.domain.model.FishingOutlook
import com.bjlure.app.domain.model.FishingWindow
import com.bjlure.app.domain.model.IndexFactor
import com.bjlure.app.domain.model.WeatherPoint
import com.bjlure.app.domain.model.WeatherSnapshot
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 出钓指数与推荐时段。
 *
 * 这里刻意不抄任何现成 App 的公式，而是按北京路亚的实际约束逐条立因子，
 * 每一项的分数怎么来的都写在 [IndexFactor.detail] 里，界面上能展开看。
 *
 * 因子权重和理由：
 *  - 水温 22 分：鱼是变温动物，决定开口与否的第一因素。气温只是间接指标，
 *    水体热惯性大，所以先估算水温再打分。
 *  - 气压 18 分 + 气压趋势 12 分：气压直接影响溶氧与鱼的泳层，
 *    而且「气压正在怎么变」比「气压是多少」更关键 —— 骤降天几乎不开口。
 *  - 风力 12 分：1-4 m/s 起涟漪、增溶氧、又不太影响抛投；无风闷、大风没法钓。
 *  - 降水与云量 10 分：多云和毛毛雨反而好，强对流天气最差。
 *  - 季节 10 分：北京春秋最好、夏季只能钓早晚、冬季路亚基本停口。
 *  - 时段 16 分：日出前后与日落前是硬窗口，这一点各路钓友的说法高度一致。
 */
object FishingIndex {

    private const val MAX_WATER_TEMP = 22
    private const val MAX_PRESSURE = 18
    private const val MAX_PRESSURE_TREND = 12
    private const val MAX_WIND = 12
    private const val MAX_WEATHER = 10
    private const val MAX_SEASON = 10
    private const val MAX_TIME = 16

    /** 低于/高于这个区间，鱼口明显变差。16℃ 是翘嘴、黑鱼开口的下限，定低了会虚高 */
    private const val WATER_BEST_LOW = 16.0
    private const val WATER_BEST_HIGH = 26.0

    /** 水温低于下限时每低 1℃ 扣多少分 —— 这是权重最高的一条，斜率也要最陡 */
    private const val WATER_PENALTY_PER_DEG_LOW = 2.4
    private const val WATER_PENALTY_PER_DEG_HIGH = 2.0

    // -----------------------------------------------------------------------
    // 水温估算
    // -----------------------------------------------------------------------

    /**
     * 用过去几天的气温估算水温。
     *
     * 水体热惯性大：升温比气温慢、降温也比气温慢，且日变化被抹平。
     * 这里用「近期气温的指数加权平均」占七成、当前气温占三成，
     * 并对结果做物理上下限约束（不结冰、不超 33℃）。
     */
    fun estimateWaterTemp(pastTemperatures: List<Double>, currentTemp: Double): Double {
        if (pastTemperatures.isEmpty()) {
            return clamp(currentTemp * 0.85, 0.5, 33.0)
        }
        // 越近的天权重越大
        val recent = pastTemperatures.takeLast(7)
        var num = 0.0
        var den = 0.0
        recent.reversed().forEachIndexed { i, t ->
            val w = 1.0 / (1.0 + i * 0.55)
            num += t * w
            den += w
        }
        val weighted = if (den > 0) num / den else currentTemp
        val blended = weighted * 0.68 + currentTemp * 0.32
        return clamp(blended, 0.5, 33.0)
    }

    // -----------------------------------------------------------------------
    // 指数
    // -----------------------------------------------------------------------

    fun evaluate(snapshot: WeatherSnapshot, month: Int, nowMillis: Long = System.currentTimeMillis()): FishingOutlook {
        val current = snapshot.current
        val waterTemp = snapshot.estimatedWaterTempC

        val factors = listOf(
            scoreWaterTemp(waterTemp),
            scorePressure(current.pressureHpa),
            scorePressureTrend(snapshot.hourly, nowMillis),
            scoreWind(current.windSpeedMs),
            scoreWeather(current),
            scoreSeason(month),
            scoreTimeOfDay(snapshot, nowMillis, waterTemp),
        )

        val raw = factors.sumOf { it.score }
        val total = applyHardLimits(raw, waterTemp).coerceIn(0, 100)
        val grade = when {
            total >= 85 -> "极佳"
            total >= 70 -> "良好"
            total >= 50 -> "一般"
            else -> "较差"
        }
        // 由总分推一个「有明显鱼口」的参考概率，做非线性压缩，封顶 92%
        val probability = ((total - 30).coerceAtLeast(0) * 1.35).roundToInt().coerceIn(0, 92)

        val windows = bestWindows(snapshot, nowMillis)
        val clothing = clothingFor(
            apparentTemp = current.apparentTemperatureC,
            windMs = current.windSpeedMs,
            precipMm = current.precipitationMm,
        )

        return FishingOutlook(
            score = total,
            grade = grade,
            probabilityPercent = probability,
            headline = buildHeadline(total, grade, factors),
            factors = factors,
            windows = windows,
            suggestions = buildSuggestions(snapshot, factors, waterTemp, month),
            waterTempC = waterTemp,
            clothing = clothing,
        )
    }

    // -----------------------------------------------------------------------
    // 各因子
    // -----------------------------------------------------------------------

    /**
     * 水溫是限制性因子，不是加分项。
     *
     * 单靠「水温」这一项扣分是不够的：冬天就算气压、风力、天气全是满分，
     * 加起来也能凑到 50 多分，看上去像「可以一试」——但实际是白跑一趟。
     * 所以水温跌破开口线时，直接把总分压到对应档位以下。
     */
    private fun applyHardLimits(raw: Int, waterTemp: Double): Int = when {
        waterTemp < 8.0 -> minOf(raw, 45)   // 接近停口
        waterTemp < 11.0 -> minOf(raw, 60)  // 只能碰运气
        // 没到开口线就不该出现「极佳」：气压、风力、时段再完美，鱼不开口也是白搭
        waterTemp < WATER_BEST_LOW -> minOf(raw, 82)
        waterTemp > 32.0 -> minOf(raw, 55)  // 高温缺氧，鱼不开口
        else -> raw
    }

    private fun scoreWaterTemp(waterTemp: Double): IndexFactor {
        val f = when {
            waterTemp in WATER_BEST_LOW..WATER_BEST_HIGH -> MAX_WATER_TEMP.toDouble()
            waterTemp < WATER_BEST_LOW ->
                max(0.0, MAX_WATER_TEMP - (WATER_BEST_LOW - waterTemp) * WATER_PENALTY_PER_DEG_LOW)
            else ->
                max(0.0, MAX_WATER_TEMP - (waterTemp - WATER_BEST_HIGH) * WATER_PENALTY_PER_DEG_HIGH)
        }
        val detail = when {
            waterTemp in WATER_BEST_LOW..WATER_BEST_HIGH ->
                "估算水温 %.1f℃，正处在鱼最活跃的 %.0f-%.0f℃ 区间".format(waterTemp, WATER_BEST_LOW, WATER_BEST_HIGH)
            waterTemp < WATER_BEST_LOW ->
                "估算水温 %.1f℃，已经低于 %.0f℃ 这条开口线，鱼代谢慢、追饵意愿弱，饵要放慢放小".format(
                    waterTemp, WATER_BEST_LOW
                )
            else ->
                "估算水温 %.1f℃，偏高，鱼多在深水或阴凉处，建议钓早晚".format(waterTemp)
        }
        return IndexFactor("水温", f.roundToInt(), MAX_WATER_TEMP, detail)
    }

    private fun scorePressure(p: Double): IndexFactor {
        // 1010-1022 hPa 是北京比较舒服的区间
        val f = when {
            p in 1010.0..1022.0 -> MAX_PRESSURE.toDouble()
            p < 1010.0 -> max(0.0, MAX_PRESSURE - (1010.0 - p) * 1.5)
            else -> max(0.0, MAX_PRESSURE - (p - 1022.0) * 1.8)
        }
        val detail = when {
            p in 1010.0..1022.0 -> "气压 %.0f hPa，处于适宜区间，溶氧和鱼的泳层都正常".format(p)
            p < 1010.0 -> "气压 %.0f hPa 偏低，水里溶氧差，鱼容易浮头不吃饵".format(p)
            else -> "气压 %.0f hPa 偏高，鱼常贴底、吃口轻".format(p)
        }
        return IndexFactor("气压", f.roundToInt(), MAX_PRESSURE, detail)
    }

    /** 气压「怎么变」比「是多少」更要命 */
    private fun scorePressureTrend(hourly: List<WeatherPoint>, nowMillis: Long): IndexFactor {
        val around = hourly.filter { it.timeMillis in (nowMillis - 4 * 3600_000L)..(nowMillis + 1 * 3600_000L) }
        if (around.size < 2) {
            return IndexFactor("气压趋势", (MAX_PRESSURE_TREND * 0.6).roundToInt(), MAX_PRESSURE_TREND, "缺少前后时段数据，按中性计分")
        }
        val first = around.first().pressureHpa
        val last = around.last().pressureHpa
        val delta = last - first
        val f = when {
            delta >= -1.0 && delta <= 3.0 -> MAX_PRESSURE_TREND.toDouble()      // 稳定或缓升：最好
            delta > 3.0 -> MAX_PRESSURE_TREND * 0.7                              // 升太快，鱼一时不适应
            delta >= -3.0 -> MAX_PRESSURE_TREND * 0.5                            // 缓降
            else -> 0.0                                                          // 骤降：几乎停口
        }
        val detail = when {
            delta >= -1.0 && delta <= 3.0 -> "近几小时气压稳定（%+.1f hPa），鱼口最稳的一种天气".format(delta)
            delta > 3.0 -> "气压在快速上升（%+.1f hPa），鱼需要时间适应，头一两个小时口可能乱".format(delta)
            delta >= -3.0 -> "气压缓降（%+.1f hPa），鱼口一般".format(delta)
            else -> "气压骤降 %.1f hPa，这种天气基本不开口，建议改天".format(delta)
        }
        return IndexFactor("气压趋势", f.roundToInt(), MAX_PRESSURE_TREND, detail)
    }

    private fun scoreWind(w: Double): IndexFactor {
        val f = when {
            w < 0.5 -> MAX_WIND * 0.45           // 无风闷热，水面死
            w <= 4.0 -> MAX_WIND.toDouble()      // 最佳
            w <= 6.0 -> MAX_WIND * 0.7
            w <= 8.0 -> MAX_WIND * 0.35
            else -> 0.0                          // 抛不出去，也不安全
        }
        val detail = when {
            w < 0.5 -> "几乎无风（%.1f m/s），水面平静溶氧低，鱼口偏闷".format(w)
            w <= 4.0 -> "风速 %.1f m/s，水面有涟漪、溶氧足，又不影响抛投".format(w)
            w <= 6.0 -> "风速 %.1f m/s 偏大，注意站位与人身安全".format(w)
            else -> "风速 %.1f m/s 太大，路亚很难控制，也危险".format(w)
        }
        return IndexFactor("风力", f.roundToInt(), MAX_WIND, detail)
    }

    private fun scoreWeather(p: WeatherPoint): IndexFactor {
        val code = p.weatherCode
        val f = when {
            code in 51..57 -> MAX_WEATHER * 0.95     // 毛毛雨 / 冻雨：好
            code in 1..3 -> MAX_WEATHER.toDouble()   // 少云到阴：最好
            code == 0 -> MAX_WEATHER * 0.8           // 大晴天：光强，鱼警惕
            code in 61..65 -> MAX_WEATHER * 0.6      // 小到中雨
            code in 80..82 -> MAX_WEATHER * 0.4      // 阵雨
            code in 95..99 -> 0.0                    // 雷暴：别去
            code in 71..77 -> MAX_WEATHER * 0.3      // 下雪
            else -> MAX_WEATHER * 0.6
        }
        val desc = when {
            code == 0 -> "晴，光照强，鱼更贴边或下深水，注意影子别投到水面"
            code in 1..3 -> "多云到阴，光线柔和，是路亚最舒服的天气"
            code in 51..57 -> "下毛毛雨，水面有波纹、溶氧高，鱼口常常很好"
            code in 61..67 -> "有雨，能钓但注意安全和涨水"
            code in 80..82 -> "阵雨，说来就来，注意随时撤"
            code in 95..99 -> "雷暴天气，绝对不要在水边逗留，碳素竿就是引雷针"
            else -> "天气一般"
        }
        return IndexFactor("天气", f.roundToInt(), MAX_WEATHER, desc)
    }

    private fun scoreSeason(month: Int): IndexFactor {
        // 北京实际：秋季最好，春季次之，夏季只能钓早晚，冬季路亚基本停口
        val (f, desc) = when (month) {
            in 9..11 -> MAX_SEASON.toDouble() to "秋季，贴秋膘，全年最好的路亚季节"
            in 4..6 -> (MAX_SEASON * 0.9) to "春季，鱼从深水往浅水走，口逐渐打开"
            in 7..8 -> (MAX_SEASON * 0.6) to "盛夏，白天太热，只能抓清晨、傍晚和夜场"
            in 3..3 -> (MAX_SEASON * 0.55) to "早春，水还凉，要找浅滩晒太阳的鱼"
            else -> (MAX_SEASON * 0.2) to "冬季，路亚基本停口，手竿冰钓是另一回事"
        }
        return IndexFactor("季节", f.roundToInt(), MAX_SEASON, desc)
    }

    /** 当前时刻离日出/日落有多近。低温季节的夜场要额外打折 —— 夏天夜钓有效，10℃ 的夜里基本没口 */
    private fun scoreTimeOfDay(snapshot: WeatherSnapshot, nowMillis: Long, waterTemp: Double): IndexFactor {
        val (score, desc) = timeScore(snapshot, nowMillis, waterTemp)
        return IndexFactor("时段", score, MAX_TIME, desc)
    }

    private fun timeScore(snapshot: WeatherSnapshot, millis: Long, waterTemp: Double): Pair<Int, String> {
        val day = snapshot.daily.firstOrNull()
        val sunrise = day?.sunriseMillis
        val sunset = day?.sunsetMillis
        if (sunrise == null || sunset == null) {
            return MAX_TIME / 2 to "缺少日出日落数据，按中性计分"
        }
        val cal = calendar(millis)
        val hour = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60.0

        val sunriseH = hoursOf(sunrise)
        val sunsetH = hoursOf(sunset)

        val toSunrise = abs(hour - sunriseH)
        val toSunset = abs(hour - sunsetH)
        val cold = waterTemp < WATER_BEST_LOW

        return when {
            toSunrise <= 1.0 -> MAX_TIME to "正处在日出窗口（日出 %.1f 时），鱼沿岸觅食，全天最稳的一段".format(sunriseH)
            toSunrise <= 2.5 -> (MAX_TIME * 0.8).roundToInt() to "日出后不久（日出 %.1f 时），仍是不错的窗口".format(sunriseH)
            toSunset <= 2.0 -> MAX_TIME to "正处在黄昏窗口（日落 %.1f 时），大鱼靠边进食".format(sunsetH)
            hour < sunriseH -> {
                if (cold) (MAX_TIME * 0.5).roundToInt() to
                    "天没亮，水温只有 %.0f℃ 的季节里早口要再晚一点，守着日出前后就行".format(waterTemp)
                else (MAX_TIME * 0.7).roundToInt() to "天还没亮，赶早口可以，注意安全与照明"
            }
            hour > sunsetH -> {
                if (cold) (MAX_TIME * 0.25).roundToInt() to
                    "夜里水温降到 %.0f℃，这个季节的夜场基本没口，不如早点睡、等明早那一波".format(waterTemp)
                else (MAX_TIME * 0.6).roundToInt() to "夜场，夏季夜钓黑鱼鲶鱼有效，冬季基本没戏"
            }
            else -> (MAX_TIME * 0.35).roundToInt() to "白天中段，光强温度高，鱼多在深水，效率最低的一段"
        }
    }

    // -----------------------------------------------------------------------
    // 推荐时段
    // -----------------------------------------------------------------------

    /**
     * 从未来 48 小时里挑出得分最高的几段窗口。
     * 逐小时先算分，再按「连续 3 小时得分之和」滑动取最优，避免给出零碎的一小时。
     */
    fun bestWindows(snapshot: WeatherSnapshot, nowMillis: Long, limit: Int = 3): List<FishingWindow> {
        val future = snapshot.hourly.filter { it.timeMillis >= nowMillis - 3600_000L }.take(48)
        if (future.size < 3) return emptyList()

        val scores = future.map { hourlyScore(snapshot, it) }

        data class Cand(val idx: Int, val sum: Int)
        val cands = ArrayList<Cand>()
        for (i in 0..(scores.size - 3)) {
            cands += Cand(i, scores[i] + scores[i + 1] + scores[i + 2])
        }
        val picked = ArrayList<FishingWindow>()
        val used = BooleanArray(scores.size)

        for (c in cands.sortedByDescending { it.sum }) {
            if (picked.size >= limit) break
            // 与已选窗口重叠就跳过，避免三条建议其实是同一段时间
            if ((c.idx until c.idx + 3).any { used[it] }) continue
            for (k in c.idx until c.idx + 3) used[k] = true
            val start = future[c.idx]
            val end = future[c.idx + 2]
            picked += FishingWindow(
                startMillis = start.timeMillis,
                endMillis = end.timeMillis + 3600_000L,
                score = (c.sum / 3.0).roundToInt(),
                // 必须带上「今天/明天/后天」：不带的话未来两天同一个钟点会显示成两条一模一样的建议
                label = "%s %s-%s".format(
                    dayLabel(start.timeMillis, nowMillis),
                    hhmm(start.timeMillis),
                    hhmm(end.timeMillis + 3600_000L),
                ),
                reason = windowReason(snapshot, start),
            )
        }
        return picked.sortedBy { it.startMillis }
    }

    private fun hourlyScore(snapshot: WeatherSnapshot, p: WeatherPoint): Int {
        var s = 40
        s += when {
            p.temperatureC in 15.0..28.0 -> 20
            p.temperatureC in 8.0..15.0 -> 10
            p.temperatureC in 28.0..32.0 -> 8
            else -> 0
        }
        s += when {
            p.windSpeedMs in 0.8..4.5 -> 14
            p.windSpeedMs <= 6.5 -> 8
            else -> 0
        }
        s += when {
            p.weatherCode in 1..3 -> 12
            p.weatherCode in 51..57 -> 10
            p.weatherCode == 0 -> 8
            p.weatherCode in 95..99 -> -40
            else -> 4
        }
        s += when {
            p.pressureHpa in 1010.0..1022.0 -> 10
            p.pressureHpa in 1005.0..1026.0 -> 5
            else -> 0
        }
        // 时段加成
        val cal = calendar(p.timeMillis)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val sunriseH = snapshot.daily.firstOrNull()?.sunriseMillis?.let { hoursOf(it) } ?: 6.0
        val sunsetH = snapshot.daily.firstOrNull()?.sunsetMillis?.let { hoursOf(it) } ?: 19.0
        val isNight = hour < sunriseH || hour > sunsetH
        s += when {
            abs(hour - sunriseH) <= 1.5 -> 14
            abs(hour - sunsetH) <= 1.5 -> 14
            isNight -> 6
            else -> 0
        }

        // 水温没到开口线时，夜场基本是白熬 —— 这一条各路钓友的说法高度一致
        if (isNight && snapshot.estimatedWaterTempC < WATER_BEST_LOW) {
            s -= 16
        }
        return s.coerceIn(0, 100)
    }

    private fun windowReason(snapshot: WeatherSnapshot, start: WeatherPoint): String {
        val sunriseH = snapshot.daily.firstOrNull()?.sunriseMillis?.let { hoursOf(it) } ?: 6.0
        val sunsetH = snapshot.daily.firstOrNull()?.sunsetMillis?.let { hoursOf(it) } ?: 19.0
        val h = calendar(start.timeMillis).get(Calendar.HOUR_OF_DAY).toDouble()
        val timePart = when {
            abs(h - sunriseH) <= 2.0 -> "日出窗口，鱼靠边觅食"
            abs(h - sunsetH) <= 2.0 -> "黄昏窗口，大鱼进食高峰"
            h < sunriseH || h > sunsetH -> "夜场，适合黑鱼、鲶鱼"
            else -> "白天时段，条件尚可"
        }
        val weatherPart = when {
            start.weatherCode in 1..3 -> "，多云光线柔和"
            start.weatherCode in 51..57 -> "，有毛毛雨，鱼口通常不错"
            start.windSpeedMs in 0.8..4.5 -> "，有风起涟漪"
            else -> ""
        }
        return timePart + weatherPart + "，气温 %.0f℃".format(start.temperatureC)
    }

    // -----------------------------------------------------------------------
    // 穿衣
    // -----------------------------------------------------------------------

    /**
     * 水边穿衣和城市里不是一回事：久坐不动、水面风更大、清晨更冷，
     * 所以按体感温度分档，并额外给钓鱼场景的提醒。
     */
    fun clothingFor(apparentTemp: Double, windMs: Double, precipMm: Double): ClothingAdvice {
        val (level, headline, base) = when {
            apparentTemp >= 30 -> Triple("酷热", "短袖速干衣裤", listOf(
                "速干短袖 + 速干长裤，别穿棉的，出汗后贴身上难受",
                "宽檐帽 + 偏光镜，水面反光很伤眼",
                "防晒袖套比防晒霜好用，还不脏手",
            ))
            apparentTemp >= 25 -> Triple("炎热", "短袖 + 薄长裤", listOf(
                "短袖速干 + 薄长裤，早晚可加一件皮肤衣",
                "带足水，正午别硬钓",
            ))
            apparentTemp >= 20 -> Triple("舒适", "长袖速干 + 薄外套", listOf(
                "长袖速干衣是路亚最舒服的，防晒又防蚊",
                "清晨水边比城里低 2-3℃，备一件薄外套",
            ))
            apparentTemp >= 14 -> Triple("微凉", "抓绒/薄夹克", listOf(
                "长袖 + 抓绒或薄冲锋衣",
                "水边风大，外套选防风的好",
            ))
            apparentTemp >= 7 -> Triple("凉", "厚外套 + 长裤", listOf(
                "抓绒 + 冲锋衣，或一件薄羽绒",
                "戴手套，碳素竿在低温下握久了手会僵",
            ))
            apparentTemp >= 0 -> Triple("冷", "羽绒服 / 厚冲锋衣", listOf(
                "羽绒内胆 + 防风外套，下身穿加绒裤",
                "帽子手套围脖三件套，水边体感比市区低好几度",
                "保温杯装热水，这是冬天钓鱼的命",
            ))
            else -> Triple("严寒", "重羽绒 + 全套保暖", listOf(
                "重羽绒 + 加绒裤 + 雪地靴，能穿多厚穿多厚",
                "露在外面的皮肤越少越好，注意防冻伤",
                "路亚冬季基本停口，硬要去就钓向阳深水",
            ))
        }

        val extra = ArrayList<String>()
        if (windMs >= 5.0) extra += "今天风大（%.1f m/s），一定要防风外套，帽子选能系的".format(windMs)
        if (precipMm > 0.5) extra += "有降水，带防水外套或雨衣，别打伞 —— 甩竿不方便还危险"
        extra += "涉水鞋或防滑鞋，多数野钓点是泥滩和碎石坡"

        return ClothingAdvice(level = level, headline = headline, detail = base + extra)
    }

    // -----------------------------------------------------------------------
    // 文案
    // -----------------------------------------------------------------------

    private fun buildHeadline(total: Int, grade: String, factors: List<IndexFactor>): String {
        val worst = factors.minByOrNull { it.score.toDouble() / it.maxScore } ?: return ""
        val best = factors.maxByOrNull { it.score.toDouble() / it.maxScore } ?: return ""
        return when {
            total >= 85 -> "今天条件很好，最大加分项是${best.name}。"
            total >= 70 -> "条件不错，主要是${best.name}给面子。短板在${worst.name}，留意一下。"
            total >= 50 -> "条件一般。短板在${worst.name}，去的话别抱太高期望。"
            else -> "今天条件差，短板在${worst.name}。建议改天，或者就当去吹风。"
        }
    }

    private fun buildSuggestions(
        snapshot: WeatherSnapshot,
        factors: List<IndexFactor>,
        waterTemp: Double,
        month: Int,
    ): List<String> {
        val out = ArrayList<String>(6)
        val cur = snapshot.current

        out += if (waterTemp < 12) {
            "水温 %.0f℃ 偏低，饵要放慢：软虫慢跳、亮片匀收，别指望快抽有反应".format(waterTemp)
        } else if (waterTemp > 28) {
            "水温 %.0f℃ 偏高，鱼躲深水，优先钓清晨与夜场，选深潭、桥墩阴影".format(waterTemp)
        } else {
            "水温 %.0f℃ 正合适，可以主动搜索，亮片、米诺、VIB 都能用".format(waterTemp)
        }

        if (cur.windSpeedMs in 1.5..4.5) {
            out += "有风起浪，优先钓迎风岸 —— 风把浮游生物和饵鱼都吹到那边，掠食鱼跟着走"
        }
        if (cur.weatherCode == 0 && cur.isDay) {
            out += "大晴天，注意别把影子投到水面，往有树荫或背光的一侧打"
        }
        if (month in 6..8) {
            out += "夏季正午基本没口，把力气留在日出前后和傍晚"
        }
        if (month in 11..12 || month in 1..2) {
            out += "冬季路亚对象鱼基本停口，想去就找向阳深水，把期望放低"
        }
        if (cur.pressureHpa < 1005) {
            out += "气压偏低，鱼容易上浮不吃饵，换小饵、慢手法试试"
        }
        out += "出发前记得看禁钓区标记 —— 条件再好，不能钓的水域也白搭"
        return out
    }

    // -----------------------------------------------------------------------

    private fun calendar(millis: Long): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")).apply { timeInMillis = millis }

    private fun hoursOf(millis: Long): Double {
        val c = calendar(millis)
        return c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60.0
    }

    private fun hhmm(millis: Long): String {
        val c = calendar(millis)
        return "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    private fun startOfDay(millis: Long): Long {
        val c = calendar(millis)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun dayLabel(millis: Long, nowMillis: Long): String {
        val diff = ((startOfDay(millis) - startOfDay(nowMillis)) / 86_400_000L).toInt()
        return when {
            diff <= 0 -> "今天"
            diff == 1 -> "明天"
            diff == 2 -> "后天"
            else -> "${diff} 天后"
        }
    }

    private fun clamp(v: Double, lo: Double, hi: Double): Double = min(hi, max(lo, v))
}
