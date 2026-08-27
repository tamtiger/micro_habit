package vn.nhip2phut.app

import android.app.Application

class Nhip2PhutApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

