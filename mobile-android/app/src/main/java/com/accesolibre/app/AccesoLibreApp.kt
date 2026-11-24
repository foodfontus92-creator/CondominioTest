package com.accesolibre.app

import android.app.Application
import com.accesolibre.app.di.AppContainer

class AccesoLibreApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
