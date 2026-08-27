package vn.nhip2phut.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import vn.nhip2phut.app.navigation.Nhip2PhutNavHost
import vn.nhip2phut.ui.theme.Nhip2PhutTheme

class MainActivity : ComponentActivity() {
    val appContainer: AppContainer
        get() = (application as Nhip2PhutApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val applicationContainer = appContainer
        setContent {
            Nhip2PhutTheme {
                Nhip2PhutNavHost(appContainer = applicationContainer)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        appContainer.onAppResume()
    }
}

