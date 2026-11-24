package com.accesolibre.app.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.accesolibre.app.domain.model.Coordinates
import com.accesolibre.app.domain.repository.LocationSource
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class FusedLocationSource(context: Context) : LocationSource {

    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): Result<Coordinates> {
        val fresh = withTimeoutOrNull(10_000L) {
            fetchCurrentFix()
        } ?: fetchLastKnown()

        return if (fresh != null) {
            Result.success(
                Coordinates(
                    latitude = fresh.latitude,
                    longitude = fresh.longitude,
                    accuracyMeters = if (fresh.hasAccuracy()) fresh.accuracy else null,
                )
            )
        } else {
            Result.failure(
                IllegalStateException("No se pudo obtener tu ubicación GPS. Activa la ubicación del teléfono e intenta nuevamente.")
            )
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchCurrentFix(): Location? = suspendCancellableCoroutine { cont ->
        val cts = CancellationTokenSource()
        cont.invokeOnCancellation { cts.cancel() }
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { location ->
                if (cont.isActive) cont.resume(location)
            }
            .addOnFailureListener {
                if (cont.isActive) cont.resume(null)
            }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchLastKnown(): Location? = suspendCancellableCoroutine { cont ->
        client.lastLocation
            .addOnSuccessListener { location ->
                if (cont.isActive) cont.resume(location)
            }
            .addOnFailureListener {
                if (cont.isActive) cont.resume(null)
            }
    }
}
