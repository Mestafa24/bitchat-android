package com.bitchat.android.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class GeoPoint(val lat: Double, val lon: Double, val accuracy: Float? = null)

object LocationHelper {

    private fun hasLocationPermission(ctx: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PermissionChecker.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PermissionChecker.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Fetch best-effort location (precise if available; else approximate).
     * Returns null quickly if no permission or provider is off.
     */
    @SuppressLint("MissingPermission") // We check at runtime.
    suspend fun getBestLocation(ctx: Context, timeoutMs: Long = 1500L): GeoPoint? {
        if (!hasLocationPermission(ctx)) return null

        val fused = LocationServices.getFusedLocationProviderClient(ctx)

        // First try a fresh fix (may be null/slow indoors, so we bound with timeout)
        val fresh: Location? = withTimeoutOrNullCompat(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val token = com.google.android.gms.tasks.CancellationTokenSource()
                val task = fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, token.token)
                task.addOnCompleteListener { t ->
                    if (!cont.isCompleted) cont.resume(t.result)
                }
                cont.invokeOnCancellation { token.cancel() }
            }
        }

        val loc = fresh ?: suspendCancellableCoroutine<Location?> { cont ->
            val t: Task<Location> = fused.lastLocation
            t.addOnCompleteListener(OnCompleteListener { task ->
                if (!cont.isCompleted) cont.resume(task.result)
            })
        }

        return loc?.let { GeoPoint(it.latitude, it.longitude, it.accuracy) }
    }

    // Local tiny timeout helper to avoid adding kotlinx-coroutines-timeout imports
    private suspend fun <T> withTimeoutOrNullCompat(timeoutMs: Long, block: suspend () -> T): T? {
        return try {
            kotlinx.coroutines.withTimeout(timeoutMs) { block() }
        } catch (_: Exception) {
            null
        }
    }
}
