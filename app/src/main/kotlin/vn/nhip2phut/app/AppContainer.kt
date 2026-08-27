package vn.nhip2phut.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import vn.nhip2phut.app.time.ClockIntegrityRuntime
import vn.nhip2phut.app.time.ClockStateUpdateGateway
import vn.nhip2phut.app.time.LoadedClockGenerationSource
import vn.nhip2phut.data.crypto.AesGcmEnvelopeCryptoV1
import vn.nhip2phut.data.crypto.AndroidKeyStoreKeyProviderV1
import vn.nhip2phut.data.storage.DurableClockStateCoordinator
import vn.nhip2phut.data.storage.EncryptedClockStateRepository
import vn.nhip2phut.data.storage.Nhip2PhutDatabase
import vn.nhip2phut.platform.notification.ClockSignalHandler
import vn.nhip2phut.platform.time.AndroidClock
import vn.nhip2phut.platform.time.AndroidRawClockSource

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    private val database: Nhip2PhutDatabase = Nhip2PhutDatabase.open(appContext)
    private val keyProvider = AndroidKeyStoreKeyProviderV1()
    private val clockRepository = EncryptedClockStateRepository(
        dao = database.clockStateDao(),
        crypto = AesGcmEnvelopeCryptoV1(keyProvider),
        database = database,
    )
    private val clockCoordinator = DurableClockStateCoordinator(clockRepository)
    private val rawClockSource = AndroidRawClockSource(appContext)
    private val generationSource = LoadedClockGenerationSource()
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val clock = AndroidClock(rawClockSource, generationSource)
    val clockSignalHandler: ClockSignalHandler
        get() = clockIntegrityRuntime

    private val clockIntegrityRuntime = ClockIntegrityRuntime(
        gateway = ClockStateUpdateGateway { reason, raw -> clockCoordinator.update(reason, raw) },
        rawClockSource = rawClockSource,
        generationSource = generationSource,
        scope = applicationScope,
    )

    init {
        clockIntegrityRuntime.start()
    }

    fun onAppResume() {
        clockIntegrityRuntime.onAppResume()
    }
}

