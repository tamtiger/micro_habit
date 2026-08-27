package vn.nhip2phut.platform.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import vn.nhip2phut.domain.time.ClockUpdateReason
import java.util.concurrent.atomic.AtomicBoolean

fun interface ClockSignalCompletion {
    fun complete(success: Boolean)
}

fun interface ClockSignalHandler {
    fun handle(reason: ClockUpdateReason, completion: ClockSignalCompletion)
}

interface ClockSignalHandlerOwner {
    val clockSignalHandler: ClockSignalHandler
}

object ClockSignalActions {
    fun fromAction(action: String?): ClockUpdateReason? = when (action) {
        Intent.ACTION_BOOT_COMPLETED -> ClockUpdateReason.BOOT_COMPLETED
        Intent.ACTION_TIME_CHANGED -> ClockUpdateReason.TIME_SET
        Intent.ACTION_TIMEZONE_CHANGED -> ClockUpdateReason.TIMEZONE_CHANGED
        else -> null
    }
}

internal class CompleteOnce(
    private val finish: () -> Unit,
) {
    private val completed = AtomicBoolean(false)

    fun complete(): Boolean {
        if (!completed.compareAndSet(false, true)) return false
        finish()
        return true
    }
}

class BootReconcileReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reason = ClockSignalActions.fromAction(intent.action) ?: return
        val pendingResult = goAsync()
        val completion = CompleteOnce(pendingResult::finish)
        val owner = context.applicationContext as? ClockSignalHandlerOwner
        if (owner == null) {
            completion.complete()
            return
        }
        try {
            owner.clockSignalHandler.handle(reason) { completion.complete() }
        } catch (_: Throwable) {
            completion.complete()
        }
    }
}

