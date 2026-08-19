package dev.enthusiastdev.netinspector.data.persistence.diagnostics

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject

/** Bundles [DiagnosticRunRepository.record]'s fields into one type rather than a six-parameter
 * function (detekt's `LongParameterList`) - also doubles as the shape callers in `:app` build
 * once they have a completed run, rather than threading six positional arguments through. */
data class DiagnosticRunRecord(
    val toolType: String,
    val target: String,
    val durationMillis: Long,
    val summary: String,
    val parametersJson: String,
    val resultJson: String,
)

interface DiagnosticRunRepository {
    suspend fun record(record: DiagnosticRunRecord): Long

    fun recent(limit: Int = 200): Flow<List<DiagnosticRunEntity>>

    suspend fun get(id: Long): DiagnosticRunEntity?

    suspend fun deleteOlderThan(retentionDays: Int)
}

class DefaultDiagnosticRunRepository
    @Inject
    constructor(
        private val dao: DiagnosticRunDao,
    ) : DiagnosticRunRepository {
        override suspend fun record(record: DiagnosticRunRecord): Long =
            dao.insert(
                DiagnosticRunEntity(
                    toolType = record.toolType,
                    target = record.target,
                    timestampMillis = Instant.now().toEpochMilli(),
                    durationMillis = record.durationMillis,
                    summary = record.summary,
                    parametersJson = record.parametersJson,
                    resultJson = record.resultJson,
                ),
            )

        override fun recent(limit: Int) = dao.observeRecent(limit)

        override suspend fun get(id: Long) = dao.get(id)

        override suspend fun deleteOlderThan(retentionDays: Int) {
            val cutoff = Instant.now().minusSeconds(retentionDays * 24L * 3600L)
            dao.deleteOlderThan(cutoff.toEpochMilli())
        }
    }
