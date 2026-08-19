package dev.enthusiastdev.netinspector.data.wifi

import dev.enthusiastdev.netinspector.core.model.connection.ConnectionSnapshot
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface ConnectionRepository {
    /** `null` while not connected to Wi-Fi. */
    val connectionSnapshot: Flow<ConnectionSnapshot?>
}

class DefaultConnectionRepository
    @Inject
    constructor(
        private val dataSource: ConnectivityDataSource,
    ) : ConnectionRepository {
        override val connectionSnapshot: Flow<ConnectionSnapshot?> = dataSource.connectionSnapshots()
    }
