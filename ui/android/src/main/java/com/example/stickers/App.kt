package com.example.stickers

import android.app.Application
import android.content.Context

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * Manual DI (docs/ARCHITECTURE.md). Built once in [App.onCreate]; the keyboard service reaches it
 * through `(applicationContext as App).container`. Repositories are added in M1.
 */
class AppContainer(@Suppress("unused") private val appContext: Context)
