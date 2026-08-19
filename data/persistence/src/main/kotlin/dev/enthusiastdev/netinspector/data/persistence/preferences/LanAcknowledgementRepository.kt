package dev.enthusiastdev.netinspector.data.persistence.preferences

import androidx.datastore.core.DataStore
import dev.enthusiastdev.netinspector.data.persistence.proto.AppPreferences
import dev.enthusiastdev.netinspector.data.persistence.proto.copy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** design §11.4 - the first-run LAN scanning acknowledgement: not skippable, not repeated. */
interface LanAcknowledgementRepository {
    val isAcknowledged: Flow<Boolean>

    suspend fun acknowledge()
}

class DefaultLanAcknowledgementRepository
    @Inject
    constructor(
        private val dataStore: DataStore<AppPreferences>,
    ) : LanAcknowledgementRepository {
        override val isAcknowledged: Flow<Boolean> = dataStore.data.map { it.lanScanAcknowledged }

        override suspend fun acknowledge() {
            dataStore.updateData { it.copy { lanScanAcknowledged = true } }
        }
    }
