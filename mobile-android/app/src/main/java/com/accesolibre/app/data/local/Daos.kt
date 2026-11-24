package com.accesolibre.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PassDao {
    @Query("SELECT * FROM offline_passes ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<PassEntity>>

    @Query("SELECT * FROM offline_passes WHERE passId = :passId LIMIT 1")
    suspend fun getById(passId: String): PassEntity?

    @Query("SELECT * FROM offline_passes WHERE synced = 0 AND revoked = 0 ORDER BY createdAtMs ASC")
    suspend fun listPendingSync(): List<PassEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pass: PassEntity)

    @Query("UPDATE offline_passes SET synced = 1, syncError = NULL WHERE passId = :passId")
    suspend fun markSynced(passId: String)

    @Query("UPDATE offline_passes SET syncError = :error WHERE passId = :passId")
    suspend fun markSyncError(passId: String, error: String?)

    @Query("UPDATE offline_passes SET revoked = 1 WHERE passId = :passId")
    suspend fun markRevoked(passId: String)

    @Query("DELETE FROM offline_passes")
    suspend fun clear()
}

@Dao
interface AccessLogDao {
    @Query("SELECT * FROM cached_access_logs ORDER BY occurredAtMs DESC")
    fun observeAll(): Flow<List<AccessLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<AccessLogEntity>)

    @Query("DELETE FROM cached_access_logs")
    suspend fun clear()
}
