package dev.enthusiastdev.netinspector.ui.screens.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow

/**
 * Small shared pieces of the Settings screen, split out of `SettingsScreen.kt` to keep that
 * file under detekt's per-file function count.
 *
 * [SegmentLabel]: [SingleChoiceSegmentedButtonRow] sizes its height to a single line of text
 * (its internal `height(IntrinsicSize.Min)`), so a label that wraps to two lines at a high UI
 * scale overflows its segment and leaves that segment visibly taller than its neighbours.
 * Pinning the label to one line keeps every segment the same height. A slightly smaller text
 * style keeps the short labels here readable rather than ellipsised at the top of the scale
 * range.
 */
@Composable
internal fun SegmentLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
