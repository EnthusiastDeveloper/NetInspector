package dev.enthusiastdev.netinspector.data.persistence.host

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** docs/ideas.md D - nicknames keyed by [SavedHostEntity.key], exposed
 * as a plain map so callers can join it against a `Host` list by `nicknameKey()` without a
 * per-host query. Also carries ideas.md #24's "known device" flag on the same
 * row, exposed separately as a key set so existing [observeNicknames] callers are unaffected. */
interface SavedHostRepository {
    fun observeNicknames(): Flow<Map<String, String>>

    fun observeKnownDeviceKeys(): Flow<Set<String>>

    suspend fun setNickname(
        key: String,
        nickname: String,
    )

    suspend fun setKnownDevice(
        key: String,
        isKnown: Boolean,
    )
}

class DefaultSavedHostRepository
    @Inject
    constructor(
        private val dao: SavedHostDao,
    ) : SavedHostRepository {
        // ideas.md #24 - a row can now exist for the known-device flag alone, with
        // no nickname (blank string, not absent) - filtered out here so this map keeps its
        // original meaning ("present means an actual nickname to display"), rather than
        // pushing blank-string handling onto every caller that joins this against a Host list.
        override fun observeNicknames(): Flow<Map<String, String>> =
            dao.observeAll().map { entities ->
                entities.filter { it.nickname.isNotBlank() }.associate { it.key to it.nickname }
            }

        override fun observeKnownDeviceKeys(): Flow<Set<String>> =
            dao.observeAll().map { entities -> entities.filter { it.isKnownDevice }.mapTo(mutableSetOf()) { it.key } }

        /** A blank [nickname] clears the entry rather than persisting an empty label - "remove
         * the nickname" and "set it to blank" are the same user intent - unless the row is
         * also carrying a "known device" flag, in which case the row stays with an empty
         * nickname rather than losing that flag. */
        override suspend fun setNickname(
            key: String,
            nickname: String,
        ) {
            val trimmed = nickname.trim()
            val existing = dao.get(key)
            if (trimmed.isEmpty() && existing?.isKnownDevice != true) {
                dao.delete(key)
            } else {
                dao.upsert(
                    SavedHostEntity(key = key, nickname = trimmed, isKnownDevice = existing?.isKnownDevice ?: false),
                )
            }
        }

        /** Mirrors [setNickname]'s delete-when-empty logic from the other direction: clearing
         * the flag on a row with no nickname removes the row entirely. */
        override suspend fun setKnownDevice(
            key: String,
            isKnown: Boolean,
        ) {
            val existing = dao.get(key)
            if (!isKnown && existing?.nickname.isNullOrEmpty()) {
                dao.delete(key)
            } else {
                dao.upsert(SavedHostEntity(key = key, nickname = existing?.nickname.orEmpty(), isKnownDevice = isKnown))
            }
        }
    }
