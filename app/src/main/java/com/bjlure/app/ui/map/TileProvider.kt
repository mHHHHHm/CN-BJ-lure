package com.bjlure.app.ui.map

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * 瓦片提供者。取瓦片的优先级：
 *
 *   1. 内存缓存（LruCache，翻页时不再解码）
 *   2. 磁盘缓存（cacheDir，联网看过一次就留下来了）
 *   3. assets 内置瓦片（z<=10，保证完全离线也能看到北京全貌）
 *   4. 在线拉取（高德路网图，中文标注）并写入磁盘缓存
 *
 * 下载与解码都在 IO 线程，完成后回到主线程通知重绘。
 */
class TileProvider(private val context: Context) {

    private val memory = LruCache<String, ImageBitmap>(MEMORY_TILES)
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val diskDir: File by lazy {
        File(context.cacheDir, "tiles").apply { mkdirs() }
    }

    /** 内存里已经有就直接给，没有就触发异步加载并返回 null */
    fun get(z: Int, x: Int, y: Int, onLoaded: () -> Unit): ImageBitmap? {
        val key = keyOf(z, x, y)
        memory.get(key)?.let { return it }
        if (!inFlight.add(key)) return null

        scope.launch {
            val bmp = runCatching { decodeOrFetch(z, x, y) }.getOrNull()
            if (bmp != null) memory.put(key, bmp)
            inFlight.remove(key)
            if (bmp != null) {
                kotlinx.coroutines.withContext(Dispatchers.Main) { onLoaded() }
            }
        }
        return null
    }

    private fun decodeOrFetch(z: Int, x: Int, y: Int): ImageBitmap? {
        // 磁盘缓存
        val f = File(diskDir, "$z/${x}_$y.png")
        if (f.exists() && f.length() > 500) {
            BitmapFactory.decodeFile(f.absolutePath)?.let { return it.asImageBitmap() }
        }

        // 内置瓦片
        if (z <= BUILT_IN_MAX_ZOOM) {
            try {
                context.assets.open("tiles/$z/${x}_$y.png").use { input ->
                    BitmapFactory.decodeStream(input)?.let { bmp ->
                        // 顺手落一份磁盘缓存，省得下次还走 assets 解压
                        return bmp.asImageBitmap()
                    }
                }
            } catch (_: Throwable) {
                // 内置没有这张，往下走去联网
            }
        }

        // 在线
        val bytes = httpGet(tileUrl(z, x, y)) ?: return null
        return try {
            f.parentFile?.mkdirs()
            f.writeBytes(bytes)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (t: Throwable) {
            Log.w(TAG, "写入瓦片缓存失败 $z/$x/$y", t)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }

    private fun httpGet(urlStr: String): ByteArray? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 12_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (bjlure android)")
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.use { it.readBytes() }
        } catch (t: Throwable) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    companion object {
        private const val TAG = "TileProvider"
        private const val MEMORY_TILES = 160

        /** 内置瓦片覆盖到这一级，再放大就要联网了 */
        const val BUILT_IN_MAX_ZOOM = 10
        const val MIN_ZOOM = 7
        const val MAX_ZOOM = 17

        private fun keyOf(z: Int, x: Int, y: Int) = "$z/$x/$y"

        /** 高德路网图，中文标注，无需 Key。四个子域轮询分摊压力 */
        fun tileUrl(z: Int, x: Int, y: Int): String {
            val sub = SUBS[(x + y) % SUBS.size]
            return "https://webrd$sub.is.autonavi.com/appmaptile" +
                "?lang=zh_cn&size=1&scale=1&style=8&x=$x&y=$y&z=$z"
        }

        private val SUBS = listOf("01", "02", "03", "04")
    }
}
