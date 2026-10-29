package com.wolve.app.data

import android.content.Context
import com.wolve.app.ble.BleClient
import com.wolve.app.service.AutoBackup
import com.wolve.app.ui.UiPrefs
import kotlinx.coroutines.flow.MutableStateFlow

/** Process-wide singletons, created once by [com.wolve.app.WolveApplication]. */
object Graph {
    lateinit var store: Store
        private set

    lateinit var appContext: Context
        private set

    /** One Bluetooth client for the whole process, so the connection survives screen changes. */
    val ble: BleClient by lazy { BleClient(appContext) }

    /** Incremented whenever a workout is saved outside the UI (auto detection). */
    val workoutsVersion = MutableStateFlow(0)

    fun init(ctx: Context) {
        appContext = ctx.applicationContext
        store = Store(ctx.applicationContext)
        StrainTracker.init(store)
        SleepRecorder.restore(store)
        StressTracker.init(store)
        UiPrefs.load(store, ctx.filesDir)
        AutoBackup.scheduleIfEnabled(ctx.applicationContext)
    }
}
