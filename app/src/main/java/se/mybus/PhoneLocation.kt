package se.mybus

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

// The phone's position for "stops near me", straight from Android's LocationManager so the app
// doesn't need Google Play services. Callers make sure a location permission is granted first.
object PhoneLocation {
    // A fix this recent is used as-is: instant, and nobody has moved far.
    private const val FRESH_MS = 2 * 60_000L
    private const val TIMEOUT_MS = 15_000L

    // Null when location is turned off or no fix arrives in time.
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = lm.getProviders(true).filter { it != LocationManager.PASSIVE_PROVIDER }
        if (providers.isEmpty()) return null
        val known = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        known.filter { System.currentTimeMillis() - it.time < FRESH_MS }.minByOrNull { it.accuracy }?.let { return it }

        val provider = preferredProviders().firstOrNull { it in providers } ?: providers.first()
        return withTimeoutOrNull(TIMEOUT_MS) { requestOne(context, lm, provider) }
            // An older fix beats nothing; the list is sorted by distance anyway.
            ?: known.maxByOrNull { it.time }
    }

    private fun preferredProviders() = buildList {
        if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        add(LocationManager.GPS_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestOne(context: Context, lm: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            if (Build.VERSION.SDK_INT >= 30) {
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                lm.getCurrentLocation(provider, signal, context.mainExecutor) { location ->
                    if (cont.isActive) cont.resume(location)
                }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        lm.removeUpdates(this)
                        if (cont.isActive) cont.resume(location)
                    }

                    // Before Android 11 these have no default implementation in the framework, so
                    // leaving them out crashes with AbstractMethodError when Android calls them.
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                cont.invokeOnCancellation { lm.removeUpdates(listener) }
                lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            }
        }
}
