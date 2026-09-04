package com.example.camera.water.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.camera.water.permission.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * 水印相机的定位数据源。
 *
 * 这里集中处理 provider 选择、一次定位、超时兜底和地址反查，Activity/ViewModel 不直接接触
 * LocationManager 的回调式 API。
 */
class LocationController(context: Context) {

    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(LocationManager::class.java)
    private var activeListener: LocationListener? = null
    private var activeCancellationSignal: CancellationSignal? = null

    @SuppressLint("MissingPermission")
    suspend fun requestCurrentLocation(): LocationUiState {
        if (!PermissionUtils.hasAnyLocationPermission(appContext)) {
            return LocationUiState.PermissionDenied
        }

        return runCatching {
            withContext(Dispatchers.Main.immediate) {
                cancel()
                val provider = enabledLocationProvider() ?: return@withContext LocationUiState.ProviderDisabled
                val location = withTimeoutOrNull(LOCATION_TIMEOUT_MILLIS) {
                    awaitCurrentLocation(provider)
                } ?: newestLastKnownLocation()

                if (location == null) {
                    LocationUiState.Unavailable
                } else {
                    val locationText = withContext(Dispatchers.IO) { reverseGeocode(location) }
                    LocationUiState.Success(locationText)
                }
            }
        }.getOrElse { throwable ->
            LocationUiState.Failure(throwable.message ?: "定位失败")
        }
    }

    fun cancel() {
        activeCancellationSignal?.cancel()
        activeCancellationSignal = null
        activeListener?.let { listener ->
            runCatching { locationManager.removeUpdates(listener) }
        }
        activeListener = null
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private suspend fun awaitCurrentLocation(provider: String): Location? {
        return suspendCancellableCoroutine { continuation ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                activeCancellationSignal = signal
                locationManager.getCurrentLocation(
                    provider,
                    signal,
                    ContextCompat.getMainExecutor(appContext),
                ) { location ->
                    activeCancellationSignal = null
                    if (continuation.isActive) continuation.resume(location)
                }
                continuation.invokeOnCancellation {
                    signal.cancel()
                    activeCancellationSignal = null
                }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        clearLegacyListener(this)
                        if (continuation.isActive) continuation.resume(location)
                    }

                    @Deprecated("Deprecated by framework")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

                    override fun onProviderEnabled(provider: String) = Unit

                    override fun onProviderDisabled(provider: String) = Unit
                }
                activeListener = listener
                locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                continuation.invokeOnCancellation { clearLegacyListener(listener) }
            }
        }
    }

    private fun clearLegacyListener(listener: LocationListener) {
        if (activeListener === listener) activeListener = null
        runCatching { locationManager.removeUpdates(listener) }
    }

    @SuppressLint("MissingPermission")
    private fun newestLastKnownLocation(): Location? {
        return locationManager.getProviders(true)
            .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull(Location::getTime)
    }

    private fun enabledLocationProvider(): String? {
        return when {
            PermissionUtils.hasPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) &&
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocode(location: Location): String {
        val fallback = String.format(
            Locale.getDefault(),
            "%.5f, %.5f",
            location.latitude,
            location.longitude,
        )
        if (!Geocoder.isPresent()) return fallback
        return runCatching {
            val address = Geocoder(appContext, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
            address?.displayText().orEmpty().ifBlank { fallback }
        }.getOrDefault(fallback)
    }

    private fun Address.displayText(): String {
        getAddressLine(0)?.takeIf { it.isNotBlank() }?.let { return it }
        return listOfNotNull(adminArea, locality, subLocality, thoroughfare, featureName)
            .distinct()
            .joinToString("")
    }

    private companion object {
        const val LOCATION_TIMEOUT_MILLIS = 10_000L
    }

}
