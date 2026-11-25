package com.accesolibre.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.accesolibre.app.domain.model.Session
import com.accesolibre.app.domain.model.UnitSecret

class SecureStorage(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "accesolibre_secure_prefs",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var serverUrl: String?
        get() = prefs.getString(KEY_SERVER_URL, null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_SERVER_URL) else putString(KEY_SERVER_URL, value)
            }.apply()
        }

    var lastCommunityType: String?
        get() = prefs.getString(KEY_LAST_COMMUNITY_TYPE, null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_LAST_COMMUNITY_TYPE) else putString(KEY_LAST_COMMUNITY_TYPE, value)
            }.apply()
        }

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_ACCESS_TOKEN) else putString(KEY_ACCESS_TOKEN, value)
            }.apply()
        }

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_REFRESH_TOKEN) else putString(KEY_REFRESH_TOKEN, value)
            }.apply()
        }

    fun saveSession(session: Session) {
        prefs.edit()
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_CONDO_ID, session.condominiumId)
            .putString(KEY_UNIT_ID, session.unitId)
            .putString(KEY_UNIT_NUMBER, session.unitNumber)
            .putString(KEY_BLOCK_NAME, session.blockName)
            .putString(KEY_EMAIL, session.email)
            .putString(KEY_FULL_NAME, session.fullName)
            .putString(KEY_ROLE, session.role)
            .putString(KEY_COMMUNITY_TYPE, session.communityType)
            .putString(KEY_LAST_COMMUNITY_TYPE, session.communityType)
            .apply()
    }

    fun readSession(): Session? {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val condoId = prefs.getString(KEY_CONDO_ID, null) ?: return null
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        val fullName = prefs.getString(KEY_FULL_NAME, null) ?: return null
        val role = prefs.getString(KEY_ROLE, null) ?: return null
        if (accessToken.isNullOrBlank()) return null
        return Session(
            userId = userId,
            condominiumId = condoId,
            unitId = prefs.getString(KEY_UNIT_ID, null),
            email = email,
            fullName = fullName,
            role = role,
            communityType = prefs.getString(KEY_COMMUNITY_TYPE, null) ?: lastCommunityType ?: "CONDOMINIUM",
            blockName = prefs.getString(KEY_BLOCK_NAME, null),
            unitNumber = prefs.getString(KEY_UNIT_NUMBER, null),
        )
    }

    fun saveUnitSecret(secret: UnitSecret) {
        prefs.edit()
            .putString(KEY_SECRET_CONDO_ID, secret.condominiumId)
            .putString(KEY_SECRET_UNIT_ID, secret.unitId)
            .putString(KEY_SECRET_UNIT_NUMBER, secret.unitNumber)
            .putString(KEY_SECRET_BLOCK_NAME, secret.blockName)
            .putString(KEY_UNIT_SECRET_HEX, secret.secretHex)
            .putInt(KEY_HMAC_KEY_VERSION, secret.hmacKeyVersion)
            .apply()
    }

    fun readUnitSecret(): UnitSecret? {
        val condoId = prefs.getString(KEY_SECRET_CONDO_ID, null) ?: return null
        val unitId = prefs.getString(KEY_SECRET_UNIT_ID, null) ?: return null
        val unitNumber = prefs.getString(KEY_SECRET_UNIT_NUMBER, null) ?: return null
        val blockName = prefs.getString(KEY_SECRET_BLOCK_NAME, null)
        val hex = prefs.getString(KEY_UNIT_SECRET_HEX, null) ?: return null
        val version = prefs.getInt(KEY_HMAC_KEY_VERSION, 1)
        return UnitSecret(condoId, unitId, unitNumber, blockName, hex, version)
    }

    fun clearAuth() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_CONDO_ID)
            .remove(KEY_UNIT_ID)
            .remove(KEY_UNIT_NUMBER)
            .remove(KEY_BLOCK_NAME)
            .remove(KEY_EMAIL)
            .remove(KEY_FULL_NAME)
            .remove(KEY_ROLE)
            .remove(KEY_COMMUNITY_TYPE)
            .remove(KEY_SECRET_CONDO_ID)
            .remove(KEY_SECRET_UNIT_ID)
            .remove(KEY_SECRET_UNIT_NUMBER)
            .remove(KEY_SECRET_BLOCK_NAME)
            .remove(KEY_UNIT_SECRET_HEX)
            .remove(KEY_HMAC_KEY_VERSION)
            .apply()
    }

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_CONDO_ID = "condo_id"
        private const val KEY_UNIT_ID = "unit_id"
        private const val KEY_UNIT_NUMBER = "unit_number"
        private const val KEY_BLOCK_NAME = "block_name"
        private const val KEY_EMAIL = "email"
        private const val KEY_FULL_NAME = "full_name"
        private const val KEY_ROLE = "role"
        private const val KEY_COMMUNITY_TYPE = "community_type"
        private const val KEY_LAST_COMMUNITY_TYPE = "last_community_type"
        private const val KEY_SECRET_CONDO_ID = "secret_condo_id"
        private const val KEY_SECRET_UNIT_ID = "secret_unit_id"
        private const val KEY_SECRET_UNIT_NUMBER = "secret_unit_number"
        private const val KEY_SECRET_BLOCK_NAME = "secret_block_name"
        private const val KEY_UNIT_SECRET_HEX = "unit_secret_hex"
        private const val KEY_HMAC_KEY_VERSION = "hmac_key_version"
    }
}
