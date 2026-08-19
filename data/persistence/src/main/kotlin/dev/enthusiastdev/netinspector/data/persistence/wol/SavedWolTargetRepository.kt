package dev.enthusiastdev.netinspector.data.persistence.wol

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface SavedWolTargetRepository {
    fun observeAll(): Flow<List<SavedWolTarget>>

    suspend fun save(
        label: String,
        mac: String,
        broadcastAddress: String,
    )

    suspend fun delete(target: SavedWolTarget)
}

class DefaultSavedWolTargetRepository
    @Inject
    constructor(
        private val dao: SavedWolTargetDao,
    ) : SavedWolTargetRepository {
        override fun observeAll(): Flow<List<SavedWolTarget>> = dao.observeAll()

        override suspend fun save(
            label: String,
            mac: String,
            broadcastAddress: String,
        ) {
            dao.insert(SavedWolTarget(label = label, mac = mac, broadcastAddress = broadcastAddress))
        }

        override suspend fun delete(target: SavedWolTarget) {
            dao.delete(target)
        }
    }
