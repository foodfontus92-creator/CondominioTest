package com.accesolibre.app.domain.repository

import com.accesolibre.app.domain.model.AccessLogItem
import com.accesolibre.app.domain.model.Coordinates
import com.accesolibre.app.domain.model.Device
import com.accesolibre.app.domain.model.IntercomCall
import com.accesolibre.app.domain.model.OpenOutcome
import com.accesolibre.app.domain.model.Pass
import com.accesolibre.app.domain.model.Session
import com.accesolibre.app.domain.model.UnitSecret
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val serverUrl: StateFlow<String?>
    /** Returns a normalized "http://host:port" or null when the input is not a valid address. */
    fun normalize(raw: String): String?
    fun save(url: String)
    suspend fun testConnection(url: String): Result<String>
}

interface AuthRepository {
    val session: StateFlow<Session?>
    suspend fun login(condominiumCode: String, email: String, password: String): Result<Session>
    suspend fun refreshUnitSecret(): Result<UnitSecret>
    fun unitSecret(): UnitSecret?
    suspend fun logout()
}

interface AccessRepository {
    suspend fun devices(): Result<List<Device>>
    suspend fun open(deviceId: String, coordinates: Coordinates): Result<OpenOutcome>
    fun observeLogs(): Flow<List<AccessLogItem>>
    /** Fetches a page from the backend and caches it. Returns true when it was the last page. */
    suspend fun refreshLogs(page: Int, size: Int): Result<Boolean>
}

interface PassRepository {
    fun observe(): Flow<List<Pass>>
    suspend fun get(passId: String): Pass?
    suspend fun saveLocal(pass: Pass)
    /** Registers pending offline-generated passes in the backend. Returns how many were synced. */
    suspend fun syncPending(): Int
    suspend fun revoke(passId: String): Result<Unit>
}

interface LocationSource {
    /** Real GPS fix of the phone (FusedLocationProviderClient on Android). */
    suspend fun currentLocation(): Result<Coordinates>
}

interface IntercomRepository {
    suspend fun calls(page: Int, size: Int): Result<List<IntercomCall>>
    suspend fun unlock(callId: String): Result<Unit>
    suspend fun reject(callId: String): Result<Unit>
}
