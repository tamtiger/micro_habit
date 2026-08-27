package vn.nhip2phut.app

import android.content.Context
import vn.nhip2phut.platform.time.AndroidClock

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val clock = AndroidClock()
}

