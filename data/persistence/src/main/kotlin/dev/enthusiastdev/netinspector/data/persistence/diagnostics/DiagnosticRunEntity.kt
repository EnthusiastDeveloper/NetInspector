package dev.enthusiastdev.netinspector.data.persistence.diagnostics

import androidx.room.Entity
import androidx.room.PrimaryKey

/** design §10 - "ping/traceroute/scan runs with parameters and serialised results." [toolType]
 * is a free-form string rather than a Room-mapped enum (kept in sync with `DiagnosticToolType`
 * in `:app`, the only module that both runs diagnostics and writes history - this module
 * can't depend on `:data:diagnostics` per the module graph, design §2.1). [parametersJson]/
 * [resultJson] are opaque to this module: `:app` serialises whichever tool-specific shape it
 * ran into JSON before calling [DiagnosticRunRepository.record], and deserialises it back out
 * for the history detail screen and JSON export - this entity never inspects their contents. */
@Entity(tableName = "diagnostic_run")
data class DiagnosticRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val toolType: String,
    val target: String,
    val timestampMillis: Long,
    val durationMillis: Long,
    val summary: String,
    val parametersJson: String,
    val resultJson: String,
)
