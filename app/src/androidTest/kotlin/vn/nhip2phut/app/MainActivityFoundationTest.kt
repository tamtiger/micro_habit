package vn.nhip2phut.app

import android.content.res.Configuration
import android.os.LocaleList
import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.nhip2phut.ui.FOUNDATION_SCREEN_TEST_TAG
import vn.nhip2phut.domain.time.ClockUpdateReason

@RunWith(AndroidJUnit4::class)
class MainActivityFoundationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun activityIsSecureAndUsesTheApplicationScopedContainer() {
        val activity = composeRule.activity
        val application = activity.application as Nhip2PhutApplication

        assertTrue(
            activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0,
        )
        assertSame(application.container, activity.appContainer)
    }

    @Test
    fun appOwnedNavigationStartsAtTheFoundationScreen() {
        composeRule.onNodeWithTag(FOUNDATION_SCREEN_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Nhịp hôm nay").assertIsDisplayed()
    }

    @Test
    fun exactVietnameseLocaleResolvesAppAndUiResources() {
        val baseContext = composeRule.activity
        val configuration = Configuration(baseContext.resources.configuration).apply {
            setLocales(LocaleList(Locale.forLanguageTag("vi-VN")))
        }
        val localizedContext = baseContext.createConfigurationContext(configuration)

        assertEquals("Nhịp 2 Phút", localizedContext.getString(R.string.app_name))
        assertEquals(
            "Nhịp hôm nay",
            localizedContext.getString(vn.nhip2phut.ui.R.string.home_title),
        )
    }

    @Test
    fun productionClockLoadsDurableGenerationAndVerifiedBootMarker() {
        val container = composeRule.activity.appContainer
        val initial = awaitClockSnapshot(container)
        val completion = CountDownLatch(1)
        var updateSucceeded = false

        container.clockSignalHandler.handle(ClockUpdateReason.TIME_SET) { succeeded ->
            updateSucceeded = succeeded
            completion.countDown()
        }

        assertTrue(completion.await(10, TimeUnit.SECONDS))
        assertTrue(updateSucceeded)
        val updated = awaitClockSnapshot(container)
        assertEquals(initial.clockGeneration + 1, updated.clockGeneration)
        assertTrue(updated.bootMarker >= 0)
    }

    private fun awaitClockSnapshot(container: AppContainer): vn.nhip2phut.domain.model.ClockSnapshot {
        val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        var lastFailure: RuntimeException? = null
        while (System.nanoTime() < deadlineNanos) {
            try {
                return container.clock.snapshot()
            } catch (failure: RuntimeException) {
                lastFailure = failure
                Thread.sleep(20)
            }
        }
        throw AssertionError("Durable clock generation did not load.", lastFailure)
    }
}
