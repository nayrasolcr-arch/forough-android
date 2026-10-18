package io.github.nayrasolcr.forough.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nayrasolcr.forough.ForoughBus
import io.github.nayrasolcr.forough.R
import io.github.nayrasolcr.forough.ui.theme.GradEmerald
import io.github.nayrasolcr.forough.ui.theme.GradLime
import io.github.nayrasolcr.forough.ui.theme.GradRose
import io.github.nayrasolcr.forough.ui.theme.ForoughGold

/**
 * LogsScreen (v2.5.0 redesign) — the core log tail, formatted for humans:
 *
 *  - severity classified per line (engine success / warning / error / raw)
 *    and shown as a colored tag chip + the message
 *  - long engine error chains collapse to the meaningful tail
 *  - filter chips: All / Engine / Errors
 *  - copy everything, clear, auto-scroll to newest
 */
@Composable
fun LogsScreen() {
    val logs by ForoughBus.logs.collectAsState()
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    val copiedText = stringResource(R.string.logs_copied)
    var filter by remember { mutableIntStateOf(FILTER_ALL) }

    // Auto-scroll to the newest line like the desktop log window.
    LaunchedEffect(logs.size, filter) {
        val visible = logs.size
        if (visible > 0) {
            listState.animateScrollToItem(visible - 1)
        }
    }

    val filtered = remember(logs, filter) { logs.filter { matchesFilter(it, filter) } }

    SectionCard(title = stringResource(R.string.logs_title)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            FilterChip(
                selected = filter == FILTER_ALL,
                onClick = { filter = FILTER_ALL },
                label = { Text(stringResource(R.string.logs_filter_all)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ForoughGold.copy(alpha = 0.22f)
                )
            )
            FilterChip(
                selected = filter == FILTER_ENGINE,
                onClick = { filter = FILTER_ENGINE },
                label = { Text(stringResource(R.string.logs_filter_engine)) }
            )
            FilterChip(
                selected = filter == FILTER_ERRORS,
                onClick = { filter = FILTER_ERRORS },
                label = { Text(stringResource(R.string.logs_filter_errors)) }
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Button(onClick = {
                clipboard.setText(AnnotatedString(logs.joinToString("\n")))
                ForoughBus.setToast(copiedText)
            }) {
                Text(stringResource(R.string.logs_copy))
            }
            OutlinedButton(onClick = { ForoughBus.clearLogs() }) {
                Text(stringResource(R.string.logs_clear))
            }
        }

        if (filtered.isEmpty()) {
            Text(
                stringResource(R.string.logs_empty),
                modifier = Modifier.padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                itemsIndexed(filtered) { _, line ->
                    LogRow(line)
                }
            }
        }
    }
}

private const val FILTER_ALL = 0
private const val FILTER_ENGINE = 1
private const val FILTER_ERRORS = 2

private fun matchesFilter(line: String, filter: Int): Boolean = when (filter) {
    FILTER_ENGINE -> line.startsWith("[xray]") || line.contains("engine", true) ||
        line.contains("psiphon.", true) || line.contains("rescue", true)
    FILTER_ERRORS -> line.contains("failed", true) || line.contains("error", true) ||
        line.contains("timeout", true) || line.contains("exhausted", true)
    else -> true
}

private data class LogStyle(val tag: String, val color: Color, val text: String)

private fun styleFor(line: String): LogStyle = when {
    line.contains("xray engine: ONLINE", true) || line.contains("xray engine online", true) ->
        LogStyle("OK", GradEmerald, line)
    line.contains("connected", true) || line.contains("online", true) ||
        line.contains("listening", true) || line.startsWith("xray engine: ONLINE") ->
        LogStyle("OK", GradEmerald, line)
    line.contains("failed", true) || line.contains("error", true) ||
        line.contains("exhausted", true) ->
        LogStyle("ERR", GradRose, collapseEngineChain(line))
    line.contains("rescue", true) || line.contains("switch", true) ||
        line.contains("timeout", true) || line.contains("warning", true) ->
        LogStyle("WRN", ForoughGold, collapseEngineChain(line))
    else ->
        LogStyle("···", GradLime.copy(alpha = 0.8f), line)
}

/** Long psiphon error chains keep only the last meaningful segments. */
private fun collapseEngineChain(line: String): String {
    if (line.length <= 180) return line
    val tail = line.substringAfterLast("failed to connect to", line)
    return if (tail.length in 1..240) "…$tail" else line.take(60) + " … " + line.takeLast(120)
}

@Composable
private fun LogRow(line: String) {
    val style = remember(line) { styleFor(line) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(end = 8.dp, top = 2.dp)
                .background(
                    style.color.copy(alpha = 0.16f),
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 6.dp, vertical = 1.dp)
        ) {
            Text(
                style.tag,
                style = MaterialTheme.typography.labelSmall,
                color = style.color,
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            style.text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = if (style.tag == "···") MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
