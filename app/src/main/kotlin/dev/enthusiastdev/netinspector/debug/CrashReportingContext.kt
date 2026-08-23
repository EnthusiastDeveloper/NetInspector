package dev.enthusiastdev.netinspector.debug

import dev.enthusiastdev.netinspector.data.persistence.preferences.AppSettingsRepository
import dev.enthusiastdev.netinspector.data.wifi.ConnectionRepository
import dev.enthusiastdev.netinspector.data.wifi.WifiScanRepository
import dev.enthusiastdev.netinspector.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** improvement-ideas.md #21 - [dev.enthusiastdev.netinspector.debug.CrashHandler] runs on the
 * crashing thread and can't suspend to read a `Flow`, so this collects the two things it needs
 * into `@Volatile` fields for the lifetime of the process, using the app-wide [ApplicationScope]
 * rather than anything tied to a screen's lifecycle. */
@Singleton
class CrashReportingContext
    @Inject
    constructor(
        appSettingsRepository: AppSettingsRepository,
        connectionRepository: ConnectionRepository,
        wifiScanRepository: WifiScanRepository,
        @ApplicationScope scope: CoroutineScope,
    ) {
        @Volatile
        var isReportingEnabled: Boolean = false
            private set

        @Volatile
        var knownSsids: Set<String> = emptySet()
            private set

        init {
            scope.launch {
                appSettingsRepository.crashReportingEnabled.collect { isReportingEnabled = it }
            }
            scope.launch {
                combine(
                    connectionRepository.connectionSnapshot,
                    wifiScanRepository.scanState,
                ) { connection, scan ->
                    buildSet {
                        connection?.ssid?.let(::add)
                        scan.accessPoints.forEach { add(it.ssid) }
                    }
                }.collect { knownSsids = it }
            }
        }
    }
