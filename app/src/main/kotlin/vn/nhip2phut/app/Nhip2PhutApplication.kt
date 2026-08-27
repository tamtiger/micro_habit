package vn.nhip2phut.app

import android.app.Application
import vn.nhip2phut.platform.notification.ClockSignalHandler
import vn.nhip2phut.platform.notification.ClockSignalHandlerOwner

class Nhip2PhutApplication : Application(), ClockSignalHandlerOwner {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override val clockSignalHandler: ClockSignalHandler
        get() = container.clockSignalHandler
}

