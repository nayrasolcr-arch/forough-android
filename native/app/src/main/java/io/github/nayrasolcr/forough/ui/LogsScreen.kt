package io.github.nayrasolcr.forough.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.nayrasolcr.forough.ForoughBus
import io.github.nayrasolcr.forough.R

/** LogsScreen — the desktop LogsWindow as a screen (core log tail). */
@Composable
fun LogsScreen() {
    val logs by ForoughBus.logs.collectAsState()
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    val copiedText = stringResource(R.string.logs_copied)

    // Auto-scroll to the newest line like the desktop log window.
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    SectionCard(title = stringResource(R.string.logs_title)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                clipboard.setText(AnnotatedString(logs.joinToString("\n")))
                ForoughBus.setToast(copiedText)
            }) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp))
                Text(stringResource(R.string.logs_copy))
            }
        }

        if (logs.isEmpty()) {
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
                itemsIndexed(logs) { _, line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = when {
                            line.contains("failed", true) || line.contains("error", true) ->
                                MaterialTheme.colorScheme.error
                            line.contains("connected", true) || line.contains("listening", true) ->
                                MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}
