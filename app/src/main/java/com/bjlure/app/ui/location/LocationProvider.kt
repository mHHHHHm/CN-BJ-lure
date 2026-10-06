package com.bjlure.app.ui.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 定位提供者。
 *
 * 用系统原生 [LocationManager] 而不是 Google 的 FusedLocationProvider：
 * 国内大量 Android 设备没有 Google Play 服务，GMS 定位在国内基本不可用，
 * 而 GPS/基站定位是系统级的，哪儿都能用。
 */
object LocationProvider {

    fun hasPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    /** 取一次当前位置：先问 GPS，拿不到再退到网络定位 */
    suspend fun currentLocation(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        lastKnown(manager)?.let { return it }

        return try {
            suspendCancellableCoroutine { cont ->
                val provider = when {
                    manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    else -> null
                }

                if (provider == null) {
                    // 定位服务全关着，直接给出空结果，不要挂着不返回
                    cont.resumeWith(Result.success(null))
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            if (cont.isActive) {
                                manager.removeUpdates(this)
                                cont.resumeWith(Result.success(location))
                            }
                        }

                        @Deprecated("Android S 起废弃，这里保留以兼容低版本")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                        override fun onProviderEnabled(provider: String) = Unit
                        override fun onProviderDisabled(provider: String) = Unit
                    }

                    try {
                        manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                        cont.invokeOnCancellation { manager.removeUpdates(listener) }
                    } catch (t: SecurityException) {
                        if (cont.isActive) cont.resumeWith(Result.success(null))
                    }
                }
            }
        } catch (t: Throwable) {
            null
        }
    }

    /** 持续位置流，用于「跟随定位更新距离」 */
    fun locationFlow(context: Context): Flow<Location> = callbackFlow {
        if (!hasPermission(context)) {
            close()
            return@callbackFlow
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (manager == null) {
            close()
            return@callbackFlow
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location)
            }

            @Deprecated("Android S 起废弃")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        try {
            val provider = if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                LocationManager.GPS_PROVIDER
            } else {
                LocationManager.NETWORK_PROVIDER
            }
            manager.requestLocationUpdates(provider, 10_000L, 50f, listener, Looper.getMainLooper())
        } catch (t: SecurityException) {
            close()
        }
        awaitClose {
            try {
                manager.removeUpdates(listener)
            } catch (_: Throwable) {
            }
        }
    }

    private fun lastKnown(manager: LocationManager): Location? {
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        var best: Location? = null
        for (p in providers) {
            val loc = try {
                manager.getLastKnownLocation(p)
            } catch (t: SecurityException) {
                null
            } ?: continue
            // 超过 30 分钟的旧位置不如不要，容易把距离算歪
            if (System.currentTimeMillis() - loc.time > 30 * 60 * 1000L) continue
            if (best == null || loc.time > best.time) best = loc
        }
        return best
    }
}
