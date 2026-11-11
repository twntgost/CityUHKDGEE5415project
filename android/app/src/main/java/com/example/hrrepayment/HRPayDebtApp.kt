package com.example.hrrepayment

import android.app.Application
import com.example.hrrepayment.di.AppContainer

/**
 * Single application instance. Hosts the [AppContainer] used as a manual
 * dependency-injection root (技术文档 §2: 初期手动依赖注入即可).
 *
 * Initialization that depends on Android Context lives here; everything else
 * stays inside :core.
 */
class HRPayDebtApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer.create(applicationContext)
    }
}
