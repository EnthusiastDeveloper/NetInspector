package dev.enthusiastdev.netinspector.core.designsystem.graph

import androidx.compose.ui.geometry.Offset

/** Picks whichever drawn node (hub or spoke) [tap] landed closest to, within [hitRadiusPx] -
 * mirrors the codebase's other Canvas hit-testing (`hitTestCurve`): a plain nearest-neighbor
 * search over the same geometry the draw pass used, not a hit-region shape of its own. */
internal fun hitTestNode(
    tap: Offset,
    hub: Pair<String, Offset>?,
    spokes: List<Pair<String, Offset>>,
    hitRadiusPx: Float,
): String? =
    (listOfNotNull(hub) + spokes)
        .map { (id, offset) -> id to (offset - tap).getDistance() }
        .filter { (_, distance) -> distance <= hitRadiusPx }
        .minByOrNull { (_, distance) -> distance }
        ?.first
