package com.example.dara.arbridge

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.Locale

class RealWorldLocationTracker(
    context: Context,
    private val mainHandler: Handler,
    private val logTag: String,
    private val onPositionTextChanged: (String) -> Unit
) : LocationListener {
    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var isStarted = false

    @SuppressLint("MissingPermission")
    fun start() {
        if (isStarted) return

        if (!hasLocationPermission(appContext)) {
            publish(LOCATION_PERMISSION_PENDING_TEXT)
            return
        }

        val providers = locationProviders()
        if (providers.isEmpty()) {
            publish(LOCATION_PROVIDER_DISABLED_TEXT)
            return
        }

        isStarted = true
        val lastLocation = providers
            .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
            .maxWithOrNull(::compareLocations)

        lastLocation?.let { publish(formatLocation(it)) } ?: publish(LOCATION_WAITING_TEXT)

        providers.forEach { provider ->
            runCatching {
                locationManager.requestLocationUpdates(
                    provider,
                    MIN_TIME_MS,
                    MIN_DISTANCE_METERS,
                    this,
                    Looper.getMainLooper()
                )
            }.onFailure {
                android.util.Log.w(logTag, "Location updates unavailable for provider=$provider", it)
            }
        }
    }

    fun stop() {
        if (!isStarted) return
        runCatching { locationManager.removeUpdates(this) }
        isStarted = false
    }

    override fun onLocationChanged(location: Location) {
        publish(formatLocation(location))
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderEnabled(provider: String) {
        if (isStarted) publish(LOCATION_WAITING_TEXT)
    }

    override fun onProviderDisabled(provider: String) {
        if (locationProviders().isEmpty()) publish(LOCATION_PROVIDER_DISABLED_TEXT)
    }

    private fun locationProviders(): List<String> {
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { provider ->
                runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
            }
    }

    private fun publish(text: String) {
        mainHandler.post { onPositionTextChanged(text) }
    }

    companion object {
        private const val MIN_TIME_MS = 1_000L
        private const val MIN_DISTANCE_METERS = 0.25f
        const val LOCATION_WAITING_TEXT = "aguardando GPS"
        const val LOCATION_PERMISSION_PENDING_TEXT = "permissao GPS pendente"
        const val LOCATION_PERMISSION_DENIED_TEXT = "permissao GPS negada"
        const val LOCATION_PROVIDER_DISABLED_TEXT = "GPS desativado"
        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        fun hasLocationPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
        }

        fun formatLocation(location: Location): String {
            val altitudeText = if (location.hasAltitude()) {
                String.format(Locale.US, " alt=%.1f m", location.altitude)
            }
            else {
                ""
            }

            val accuracyText = if (location.hasAccuracy()) {
                String.format(Locale.US, " acc=%.1f m", location.accuracy)
            }
            else {
                ""
            }

            return String.format(
                Locale.US,
                "lat=%.7f lon=%.7f%s%s",
                location.latitude,
                location.longitude,
                altitudeText,
                accuracyText
            )
        }

        private fun compareLocations(first: Location, second: Location): Int {
            val accuracyComparison = when {
                first.hasAccuracy() && second.hasAccuracy() -> second.accuracy.compareTo(first.accuracy)
                first.hasAccuracy() -> 1
                second.hasAccuracy() -> -1
                else -> 0
            }

            return if (accuracyComparison != 0) {
                accuracyComparison
            }
            else {
                first.time.compareTo(second.time)
            }
        }
    }
}
