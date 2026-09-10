package dev.prateekthakur.devprobe.domain.usecase

import dev.prateekthakur.devprobe.data.profiling.AppProfiler
import dev.prateekthakur.devprobe.domain.model.AppProfile
import dev.prateekthakur.devprobe.domain.model.ProfilingTimeRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProfileInstalledAppsUseCase(private val appProfiler: AppProfiler) {
    suspend fun execute(range: ProfilingTimeRange): List<AppProfile> =
        withContext(Dispatchers.IO) { appProfiler.profileInstalledApps(range) }
}
