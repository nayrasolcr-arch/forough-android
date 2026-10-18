package io.github.nayrasolcr.forough.ui

import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.nayrasolcr.forough.R
import io.github.nayrasolcr.forough.tunnel.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AppsScreen — the split-tunneling app picker (power feature). Lists the
 * installed launchable apps with icons, search and multi-select; the chosen
 * packages feed VpnService's addAllowed/addDisallowedApplication at
 * establish time (mode is picked on the settings screen).
 *
 * App discovery runs on Dispatchers.IO and icons are decoded lazily per
 * visible row, so hundreds of installed apps scroll smoothly.
 */
@Composable
fun AppsScreen(store: SettingsStore, onToast: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val selected = remember { mutableStateListOf<String>().apply { addAll(store.splitTunnelPackages) } }
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }

    val savedFmt = stringResource(R.string.apps_saved_fmt)

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadInstalledApps(context) }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            label = { Text(stringResource(R.string.apps_search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val visible = filtered(apps, query)
                    for (app in visible) if (app.packageName !in selected) selected.add(app.packageName)
                },
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.apps_select_all), maxLines = 1) }
            OutlinedButton(
                onClick = { selected.clear() },
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.apps_clear), maxLines = 1) }
        }

        val list = apps
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.size(10.dp))
                    Text(
                        stringResource(R.string.apps_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                val visible = filtered(list, query)
                LazyColumn(
                    state = rememberLazyListState(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(visible, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            checked = app.packageName in selected,
                            onToggle = { c ->
                                if (c) selected.add(app.packageName) else selected.remove(app.packageName)
                            }
                        )
                    }
                }

                Button(
                    onClick = {
                        store.splitTunnelPackages = selected.toList()
                        onToast(String.format(savedFmt, selected.size))
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.apps_save) + " (${selected.size})")
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val press = pressScale(interaction, 0.97f)
    Surface(
        onClick = { Haptics.tap(); onToggle(!checked) },
        interactionSource = interaction,
        shape = RoundedCornerShape(14.dp),
        color = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            AppIcon(app)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Checkbox(checked = checked, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun AppIcon(app: AppEntry) {
    if (app.bitmap != null) {
        Image(
            bitmap = app.bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(38.dp)
        )
    } else {
        Box(Modifier.size(38.dp))
    }
}

private fun filtered(apps: List<AppEntry>?, query: String): List<AppEntry> {
    if (apps == null) return emptyList()
    val q = query.trim().lowercase()
    if (q.isEmpty()) return apps
    return apps.filter {
        it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
    }
}

/** Installed launchable app — icon decoded once at load time. */
data class AppEntry(
    val label: String,
    val packageName: String,
    val bitmap: android.graphics.Bitmap?
)

private suspend fun loadInstalledApps(context: android.content.Context): List<AppEntry> =
    withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val entries = ArrayList<AppEntry>(resolveInfos.size)
        for (ri in resolveInfos) {
            val pkg = ri.activityInfo?.packageName ?: continue
            if (pkg == context.packageName) continue
            val label = try { ri.loadLabel(pm)?.toString() ?: pkg } catch (_: Exception) { pkg }
            val icon: Drawable? = try { ri.loadIcon(pm) } catch (_: Exception) { null }
            val bitmap = try {
                icon?.toBitmap(width = 96, height = 96)
            } catch (_: Exception) {
                null
            }
            entries.add(AppEntry(label, pkg, bitmap))
        }
        entries.sortBy { it.label.lowercase() }
        entries
    }
