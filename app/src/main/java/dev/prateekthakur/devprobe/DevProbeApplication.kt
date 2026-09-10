package dev.prateekthakur.devprobe

import android.app.Application
import dev.prateekthakur.devprobe.data.crash.CrashMonitorChannels
import dev.prateekthakur.devprobe.di.AppContainer

class DevProbeApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        CrashMonitorChannels.createChannels(this)
    }
}
