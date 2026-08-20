package dev.enthusiastdev.netinspector.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** A titled group of [InfoRow]s - the list-detail pattern every screen's detail sections use.
 * [trailingContent] sits at the far end of the title row (e.g. a dismiss button) - empty by
 * default so existing callers are unaffected. */
@Composable
fun InfoCard(
    title: String,
    modifier: Modifier = Modifier,
    trailingContent: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                trailingContent()
            }
            HorizontalDivider()
            content()
        }
    }
}

/** A label/value pair. Either side can be arbitrarily long (a URL, a UUID, a banner string) -
 * both are weighted to at most half the row rather than measured at their natural width, so a
 * long one wraps within its own half instead of either overlapping the other or, worse,
 * hogging the whole row and squeezing the other into a near-zero width where text wraps one
 * character per line. `fill = false` keeps the common case (two short strings) visually
 * unchanged: each stays at its compact natural width, positioned by `SpaceBetween`, unless it
 * actually needs the extra room. */
@Composable
fun InfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false).padding(start = 8.dp),
        )
    }
}
