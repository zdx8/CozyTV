package app.cozytv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

data class WeatherInfo(
    val city: String,
    val tempC: Int,
    val minTempC: Int,
    val maxTempC: Int,
    val feelsLikeC: Int,
    val description: String,
    val advice: String,
    val updatedAt: Long,
)

/**
 * Key-free weather line for the home screen. The city comes from the
 * public IP first, then Open-Meteo supplies current conditions. If that
 * chain fails, wttr.in's IP-based endpoint is used as a fallback. The
 * last successful result is cached for offline startup.
 */
object WeatherRepository {
    private const val PREFS = "cozy_weather"
    private const val KEY_LAST = "last"
    private const val FRESH_MS = 30 * 60 * 1000L

    fun cached(context: Context): WeatherInfo? = read(context)

    suspend fun load(context: Context, force: Boolean = false): WeatherInfo? =
        withContext(Dispatchers.IO) {
            val cached = read(context)
            if (!force && cached != null) {
                val age = System.currentTimeMillis() - cached.updatedAt
                // A negative age means the clock was moved backwards
                // (NTP correction). The old check treated that as "fresh
                // forever" and the weather never refreshed again.
                if (age in 0 until FRESH_MS) {
                    return@withContext cached
                }
            }
            val fetched = runCatching { fetch() }.getOrNull()
            if (fetched != null) {
                write(context, fetched)
                fetched
            } else {
                cached
            }
        }

    private data class IpLocation(
        val city: String,
        val latitude: Double,
        val longitude: Double,
    )

    private fun fetch(): WeatherInfo {
        val location = runCatching { fetchIpLocation() }.getOrNull()
        if (location != null) {
            runCatching { fetchOpenMeteo(location) }.getOrNull()?.let { return it }
        }
        return fetchWttrAuto()
    }

    private fun fetchIpLocation(): IpLocation {
        val json = getJson(
            "http://ip-api.com/json/?lang=zh-CN&fields=status,regionName,city,lat,lon",
        )
        require(json.optString("status") == "success") { "IP location unavailable" }
        val city = json.optString("city")
            .ifBlank { json.optString("regionName") }
            .ifBlank { "当前位置" }
        return IpLocation(
            city = city,
            latitude = json.getDouble("lat"),
            longitude = json.getDouble("lon"),
        )
    }

    private fun fetchOpenMeteo(location: IpLocation): WeatherInfo {
        val lat = location.latitude
        val lon = location.longitude
        val json = getJson(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m" +
                "&daily=temperature_2m_min,temperature_2m_max&forecast_days=1" +
                "&timezone=auto",
        )
        val current = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        val temp = current.getDouble("temperature_2m").roundToInt()
        val minTemp = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt()
        val maxTemp = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt()
        val feels = current.optDouble("apparent_temperature", temp.toDouble()).roundToInt()
        val code = current.optInt("weather_code", -1)
        val wind = current.optDouble("wind_speed_10m", 0.0).roundToInt()
        return WeatherInfo(
            city = location.city,
            tempC = temp,
            minTempC = minTemp,
            maxTempC = maxTemp,
            feelsLikeC = feels,
            description = describeWmo(code),
            advice = advice(
                feels = feels,
                rain = code in WMO_RAIN,
                snow = code in WMO_SNOW,
                wind = wind,
            ),
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun fetchWttrAuto(): WeatherInfo {
        val json = getJson("https://wttr.in/?format=j1&lang=zh")
        val current = json.getJSONArray("current_condition").getJSONObject(0)
        val area = json.optJSONArray("nearest_area")?.optJSONObject(0)
        val areaName = area?.optJSONArray("areaName")
            ?.optJSONObject(0)?.optString("value").orEmpty()
        val region = area?.optJSONArray("region")
            ?.optJSONObject(0)?.optString("value").orEmpty()
        val temp = current.optString("temp_C").toIntOrNull() ?: 20
        val feels = current.optString("FeelsLikeC").toIntOrNull() ?: temp
        val today = json.optJSONArray("weather")?.optJSONObject(0)
        val minTemp = today?.optString("mintempC")?.toIntOrNull() ?: temp
        val maxTemp = today?.optString("maxtempC")?.toIntOrNull() ?: temp
        val code = current.optInt("weatherCode", -1)
        val wind = current.optString("windspeedKmph").toIntOrNull() ?: 0
        return WeatherInfo(
            city = region.ifBlank { areaName }.ifBlank { "当前位置" },
            tempC = temp,
            minTempC = minTemp,
            maxTempC = maxTemp,
            feelsLikeC = feels,
            description = describeWwo(code),
            advice = advice(
                feels = feels,
                rain = code in WWO_RAIN,
                snow = code in WWO_SNOW,
                wind = wind,
            ),
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun getJson(url: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "CozyTV/0.2")
        }
        try {
            require(connection.responseCode in 200..299) {
                "weather HTTP ${connection.responseCode}"
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun read(context: Context): WeatherInfo? = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST, null) ?: return null
        val json = JSONObject(raw)
        if (!json.has("minTemp") || !json.has("maxTemp")) return null
        WeatherInfo(
            city = json.optString("city", "当前位置"),
            tempC = json.optInt("temp", 20),
            minTempC = json.optInt("minTemp", json.optInt("temp", 20)),
            maxTempC = json.optInt("maxTemp", json.optInt("temp", 20)),
            feelsLikeC = json.optInt("feels", 20),
            description = json.optString("description", "天气"),
            advice = json.optString("advice", "建议薄外套"),
            updatedAt = json.optLong("updated", 0L),
        )
    }.getOrNull()

    private fun write(context: Context, info: WeatherInfo) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(
                KEY_LAST,
                JSONObject()
                    .put("city", info.city)
                    .put("temp", info.tempC)
                    .put("minTemp", info.minTempC)
                    .put("maxTemp", info.maxTempC)
                    .put("feels", info.feelsLikeC)
                    .put("description", info.description)
                    .put("advice", info.advice)
                    .put("updated", info.updatedAt)
                    .toString(),
            )
            .apply()
    }

    private fun describeWmo(code: Int): String = when (code) {
        0 -> "晴"
        1 -> "大部晴"
        2 -> "多云"
        3 -> "阴"
        45, 48 -> "雾"
        51, 53, 55 -> "毛毛雨"
        56, 57, 66, 67 -> "冻雨"
        61 -> "小雨"
        63 -> "中雨"
        65 -> "大雨"
        71 -> "小雪"
        73 -> "中雪"
        75 -> "大雪"
        77 -> "雪粒"
        80 -> "阵雨"
        81, 82 -> "强阵雨"
        85, 86 -> "阵雪"
        95 -> "雷雨"
        96, 99 -> "雷暴冰雹"
        else -> "天气"
    }

    private fun describeWwo(code: Int): String = when (code) {
        113 -> "晴"
        116 -> "多云"
        119, 122 -> "阴"
        143, 248, 260 -> "雾"
        176, 263, 266, 281, 284, 293, 296 -> "小雨"
        299, 302, 305, 308 -> "中到大雨"
        311, 314, 317, 320, 362, 365 -> "雨夹雪"
        179, 182, 185, 227, 230, 323, 326, 329, 332, 335, 338, 368, 371 -> "雪"
        200, 386, 389, 392, 395 -> "雷雨"
        353, 356, 359 -> "阵雨"
        else -> "天气"
    }

    private fun advice(feels: Int, rain: Boolean, snow: Boolean, wind: Int): String {
        val base = when {
            feels <= 0 -> "羽绒服"
            feels <= 8 -> "厚外套"
            feels <= 15 -> "外套"
            feels <= 22 -> "薄外套"
            feels <= 28 -> "长袖或薄衫"
            else -> "短袖"
        }
        return buildString {
            append("建议")
            append(base)
            if (rain) append("，带伞")
            if (snow) append("，注意防滑保暖")
            if (wind >= 30) append("，注意防风")
        }
    }

    private val WMO_RAIN = setOf(
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67,
        80, 81, 82, 95, 96, 99,
    )
    private val WMO_SNOW = setOf(71, 73, 75, 77, 85, 86)
    private val WWO_RAIN = setOf(
        176, 263, 266, 281, 284, 293, 296, 299, 302, 305, 308,
        311, 314, 353, 356, 359, 386, 389,
    )
    private val WWO_SNOW = setOf(
        179, 182, 185, 227, 230, 317, 320, 323, 326, 329, 332,
        335, 338, 362, 365, 368, 371, 392, 395,
    )
}
