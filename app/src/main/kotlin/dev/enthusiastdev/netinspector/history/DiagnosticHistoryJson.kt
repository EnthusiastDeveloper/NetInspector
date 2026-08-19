package dev.enthusiastdev.netinspector.history

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** design §10 - "serialised results" for `diagnostic_run.resultJson`/`.parametersJson`.
 * `:data:persistence` can't depend on `:data:diagnostics` (design §2.1), so the per-tool DTOs
 * in this package and their JSON encoding live here in `:app`, the one module that sees both
 * a tool's domain result and the persistence repository. Kept separate from the domain types
 * in `:core:model` rather than annotating those directly, so that dependency-free module stays
 * free of a serialization library dependency it has no other use for. */
val diagnosticHistoryJson = Json { ignoreUnknownKeys = true }

/** Every tool's parameters are a small flat string map - count/interval/size for ping, a port
 * range for the scanner, and so on - so one shared encoder covers all of them without a
 * per-tool DTO that would otherwise carry no more information than the map already does. */
fun diagnosticRunParametersJson(parameters: Map<String, String>): String =
    diagnosticHistoryJson.encodeToString(MapSerializer(String.serializer(), String.serializer()), parameters)
