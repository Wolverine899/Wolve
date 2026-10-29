package com.wolve.app

import android.app.Application
import com.wolve.app.data.Graph

class WolveApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}
