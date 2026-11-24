package com.accesolibre.app.di

import android.content.Context
import com.accesolibre.app.data.local.AppDatabase
import com.accesolibre.app.data.local.SecureStorage
import com.accesolibre.app.data.location.FusedLocationSource
import com.accesolibre.app.data.remote.NetworkFactory
import com.accesolibre.app.data.remote.StompIntercomClient
import com.accesolibre.app.data.repository.AccessRepositoryImpl
import com.accesolibre.app.data.repository.AuthRepositoryImpl
import com.accesolibre.app.data.repository.IntercomRepositoryImpl
import com.accesolibre.app.data.repository.PassRepositoryImpl
import com.accesolibre.app.data.repository.SettingsRepositoryImpl
import com.accesolibre.app.domain.repository.AccessRepository
import com.accesolibre.app.domain.repository.AuthRepository
import com.accesolibre.app.domain.repository.IntercomRepository
import com.accesolibre.app.domain.repository.LocationSource
import com.accesolibre.app.domain.repository.PassRepository
import com.accesolibre.app.domain.repository.SettingsRepository
import com.accesolibre.app.domain.usecase.GeneratePassUseCase
import com.accesolibre.app.domain.usecase.LoginUseCase
import com.accesolibre.app.domain.usecase.OpenAccessUseCase
import com.accesolibre.app.domain.usecase.SyncPendingPassesUseCase

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val secureStorage = SecureStorage(appContext)
    val database = AppDatabase.build(appContext)
    val networkFactory = NetworkFactory(secureStorage)
    val stompClient = StompIntercomClient(networkFactory.okHttpClient, secureStorage, networkFactory.gson)
    val locationSource: LocationSource = FusedLocationSource(appContext)

    val settingsRepository: SettingsRepository = SettingsRepositoryImpl(secureStorage, networkFactory)
    val authRepository: AuthRepository = AuthRepositoryImpl(
        secureStorage,
        networkFactory,
        database.passDao(),
        database.accessLogDao(),
    )
    val accessRepository: AccessRepository = AccessRepositoryImpl(
        secureStorage,
        networkFactory,
        database.accessLogDao(),
    )
    val passRepository: PassRepository = PassRepositoryImpl(
        secureStorage,
        networkFactory,
        database.passDao(),
    )
    val intercomRepository: IntercomRepository = IntercomRepositoryImpl(secureStorage, networkFactory)

    val loginUseCase = LoginUseCase(authRepository)
    val openAccessUseCase = OpenAccessUseCase(locationSource, accessRepository)
    val generatePassUseCase = GeneratePassUseCase(authRepository, passRepository)
    val syncPendingPassesUseCase = SyncPendingPassesUseCase(passRepository)
}
