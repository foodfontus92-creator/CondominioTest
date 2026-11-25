package com.accesolibre.app.data.remote

import com.accesolibre.app.data.local.SecureStorage
import com.google.gson.Gson
import okhttp3.Authenticator
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

class NetworkFactory(
    private val storage: SecureStorage,
    val gson: Gson = Gson(),
) {
    private val dynamicBaseUrlInterceptor = Interceptor { chain ->
        val original = chain.request()
        val configured = storage.serverUrl?.toHttpUrlOrNull()
            ?: throw IOException("Servidor local no configurado")
        val rebuiltUrl = original.url.newBuilder()
            .scheme(configured.scheme)
            .host(configured.host)
            .port(configured.port)
            .build()
        chain.proceed(original.newBuilder().url(rebuiltUrl).build())
    }

    private val authHeaderInterceptor = Interceptor { chain ->
        val original = chain.request()
        val path = original.url.encodedPath
        val skipAuth = path.endsWith("/auth/login") ||
            path.endsWith("/auth/activate-unit") ||
            path.endsWith("/auth/refresh") ||
            path.endsWith("/setup/status") ||
            path.endsWith("/actuator/health")
        val token = storage.accessToken
        val req = if (!skipAuth && !token.isNullOrBlank()) {
            original.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            original
        }
        chain.proceed(req)
    }

    private val refreshClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val tokenAuthenticator = object : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            if (response.priorResponse != null) return null
            val path = response.request.url.encodedPath
            if (path.endsWith("/auth/login") ||
                path.endsWith("/auth/activate-unit") ||
                path.endsWith("/auth/refresh")
            ) return null

            val refreshToken = storage.refreshToken ?: return null
            val baseUrl = storage.serverUrl?.trimEnd('/') ?: return null
            synchronized(this) {
                // Another thread might have refreshed already
                val currentToken = storage.accessToken
                val usedHeader = response.request.header("Authorization")
                if (!currentToken.isNullOrBlank() && usedHeader != "Bearer $currentToken") {
                    return response.request.newBuilder()
                        .header("Authorization", "Bearer $currentToken")
                        .build()
                }

                val body = gson.toJson(RefreshTokenRequestDto(refreshToken))
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                val refreshReq = Request.Builder()
                    .url("$baseUrl/api/v1/auth/refresh")
                    .post(body)
                    .build()

                val refreshResp = runCatching { refreshClient.newCall(refreshReq).execute() }.getOrNull()
                    ?: return null
                refreshResp.use { resp ->
                    if (!resp.isSuccessful) {
                        storage.clearAuth()
                        return null
                    }
                    val parsed = runCatching {
                        gson.fromJson(resp.body?.string(), AuthResponseDto::class.java)
                    }.getOrNull() ?: return null

                    storage.accessToken = parsed.accessToken
                    storage.refreshToken = parsed.refreshToken
                    val refreshedType = parsed.user.communityType ?: parsed.communityType
                    storage.readSession()?.let { current ->
                        storage.saveSession(
                            current.copy(
                                communityType = refreshedType ?: current.communityType,
                                blockName = parsed.user.blockName ?: current.blockName,
                                unitNumber = parsed.user.unitNumber ?: current.unitNumber,
                            )
                        )
                    } ?: run {
                        if (!refreshedType.isNullOrBlank()) {
                            storage.lastCommunityType = refreshedType
                        }
                    }
                    return response.request.newBuilder()
                        .header("Authorization", "Bearer ${parsed.accessToken}")
                        .build()
                }
            }
        }
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(dynamicBaseUrlInterceptor)
        .addInterceptor(authHeaderInterceptor)
        .authenticator(tokenAuthenticator)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    val api: AccesoLibreApi = Retrofit.Builder()
        .baseUrl("http://localhost:8080/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(AccesoLibreApi::class.java)

    /** Standalone one-off health check against an arbitrary URL before saving it. */
    fun probeHealth(baseUrl: String): Result<String> = runCatching {
        val clean = baseUrl.trimEnd('/')
        val req = Request.Builder().url("$clean/actuator/health").get().build()
        refreshClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("El servidor respondió HTTP ${resp.code}")
            }
            val parsed = runCatching { gson.fromJson(resp.body?.string(), HealthDto::class.java) }.getOrNull()
            val status = parsed?.status ?: "UP"
            if (!status.equals("UP", ignoreCase = true)) {
                throw IOException("Estado del servidor: $status")
            }
            status
        }
    }

    fun <T> extractError(response: retrofit2.Response<T>, fallback: String): String {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull()
        val dto = raw?.let { runCatching { gson.fromJson(it, ApiErrorDto::class.java) }.getOrNull() }
        return when {
            !dto?.message.isNullOrBlank() -> dto!!.message!!
            !dto?.code.isNullOrBlank() -> dto!!.code!!
            else -> "$fallback (HTTP ${response.code()})"
        }
    }
}
