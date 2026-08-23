package dev.enthusiastdev.netinspector.data.persistence.host

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** docs/device-identification-ideas.md D - nicknames keyed by [SavedHostEntity.key], exposed
 * as a plain map so callers can join it against a `Host` list by `nicknameKey()` without a
 * per-host query. */
interface SavedHostRepository {
    fun observeNicknames(): Flow<Map<String, String>>

    suspend fun setNickname(
        key: String,
        nickname: String,
    )
}

class DefaultSavedHostRepository
    @Inject
    constructor(
        private val dao: SavedHostDao,
    ) : SavedHostRepository {
        override fun observeNicknames(): Flow<Map<String, String>> =
            dao.observeAll().map { entities -> entities.associate { it.key to it.nickname } }

        /** A blank [nickname] clears the entry rather than persisting an empty label - "remove
         * the nickname" and "set it to blank" are the same user intent. */
        override suspend fun setNickname(
            key: String,
            nickname: String,
        ) {
            val trimmed = nickname.trim()
            if (trimmed.isEmpty()) {
                dao.delete(key)
            } else {
                dao.upsert(SavedHostEntity(key = key, nickname = trimmed))
            }
        }
    }
