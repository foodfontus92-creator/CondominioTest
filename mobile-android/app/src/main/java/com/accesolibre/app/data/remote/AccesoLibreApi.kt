package com.accesolibre.app.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AccesoLibreApi {

    @GET("actuator/health")
    suspend fun health(): Response<HealthDto>

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginRequestDto): Response<AuthResponseDto>

    @POST("api/v1/auth/activate-unit")
    suspend fun activateUnit(@Body body: ActivateUnitRequestDto): Response<AuthResponseDto>

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body body: RefreshTokenRequestDto): Response<AuthResponseDto>

    @POST("api/v1/auth/logout")
    suspend fun logout(@Body body: LogoutRequestDto): Response<Unit>

    @GET("api/v1/access-passes/unit-secret")
    suspend fun getUnitSecret(): Response<UnitSecretResponseDto>

    @GET("api/v1/condos/{condoId}/devices")
    suspend fun listDevices(@Path("condoId") condoId: String): Response<List<DeviceResponseDto>>

    @POST("api/v1/access/open")
    suspend fun remoteOpen(@Body body: RemoteOpenRequestDto): Response<RemoteOpenResponseDto>

    @POST("api/v1/condos/{condoId}/access-passes/register")
    suspend fun registerOfflinePass(
        @Path("condoId") condoId: String,
        @Body body: RegisterOfflinePassRequestDto,
    ): Response<AccessPassResponseDto>

    @POST("api/v1/condos/{condoId}/access-passes/{passId}/revoke")
    suspend fun revokePass(
        @Path("condoId") condoId: String,
        @Path("passId") passId: String,
    ): Response<AccessPassResponseDto>

    @GET("api/v1/condos/{condoId}/access-logs")
    suspend fun listAccessLogs(
        @Path("condoId") condoId: String,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): Response<PageDto<AccessLogResponseDto>>

    @GET("api/v1/condos/{condoId}/intercom-calls")
    suspend fun listIntercomCalls(
        @Path("condoId") condoId: String,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): Response<PageDto<IntercomCallResponseDto>>

    @POST("api/v1/condos/{condoId}/intercom-calls/{callId}/unlock")
    suspend fun unlockIntercomCall(
        @Path("condoId") condoId: String,
        @Path("callId") callId: String,
    ): Response<IntercomCallResponseDto>

    @POST("api/v1/condos/{condoId}/intercom-calls/{callId}/reject")
    suspend fun rejectIntercomCall(
        @Path("condoId") condoId: String,
        @Path("callId") callId: String,
    ): Response<IntercomCallResponseDto>
}
