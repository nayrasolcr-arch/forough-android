package io.github.nayrasolcr.forough.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.nayrasolcr.forough.ForoughBus
import io.github.nayrasolcr.forough.R
import io.github.nayrasolcr.forough.engine.XrayServer
import io.github.nayrasolcr.forough.engine.XrayShareLink
import io.github.nayrasolcr.forough.tunnel.SettingsStore
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * XrayScreen (v2.5.0) — manage the second engine's servers:
 * import share links from the clipboard, import a subscription URL,
 * test reachability, pick the first-dial server, remove entries.
 * The same vless/vmess/trojan/ss links and subscription URLs the whole
 * v2ray ecosystem uses.
 */
@Composable
fun XrayScreen(
    store: SettingsStore,
    onToast: (String) -> Unit
) {
    var links by remember { mutableStateOf(store.xrayServerLinks) }
    var importText by remember { mutableStateOf("") }
    var subUrl by remember { mutableStateOf("") }
    var fragmentTls by remember { mutableStateOf(store.xrayFragmentTls) }
    var testedLatency by remember { mutableStateOf<Map<Int, Long>>(emptyMap()) }
    val clipboard = LocalClipboardManager.current
    val ioPool = remember { Executors.newSingleThreadExecutor() }
    val fetching = remember { AtomicBoolean(false) }

    fun persist(newLinks: List<String>, activeIndex: Int = store.xrayActiveIndex) {
        links = newLinks
        store.xrayServerLinks = newLinks
        store.xrayActiveIndex = activeIndex.coerceIn(0, maxOf(newLinks.size - 1, 0))
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SectionCard(
            title = stringResource(R.string.xray_import_title),
            glyph = { BoltIcon(22.dp) }
        ) {
            OutlinedTextField(
                value = importText,
                onValueChange = { importText = it },
                label = { Text(stringResource(R.string.xray_import_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val parsed = XrayShareLink.parseMany(importText)
                    if (parsed.isEmpty()) {
                        onToast("no valid links found")
                    } else {
                        val merged = links + parsed.map { it.rawLink }
                        persist(merged.distinct())
                        onToast("${parsed.size} servers imported")
                        importText = ""
                    }
                }) { Text(stringResource(R.string.xray_import_clipboard)) }
                OutlinedButton(onClick = {
                    val text = clipboard.getText()?.text ?: ""
                    if (text.isBlank()) {
                        onToast("clipboard is empty")
                    } else {
                        importText = text
                    }
                }) { Text(stringResource(R.string.xray_paste)) }
            }

            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = subUrl,
                onValueChange = { subUrl = it },
                label = { Text(stringResource(R.string.xray_sub_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = {
                val url = subUrl.trim()
                if (url.isBlank() || !url.startsWith("http")) {
                    onToast("enter a subscription URL")
                    return@Button
                }
                if (!fetching.compareAndSet(false, true)) return@Button
                onToast("fetching subscription…")
                ioPool.execute {
                    val parsed = try {
                        XrayShareLink.fetchSubscription(url)
                    } catch (e: Exception) {
                        ForoughBus.appendLog("subscription fetch failed: ${e.message}")
                        emptyList()
                    }
                    fetching.set(false)
                    if (parsed.isEmpty()) {
                        onToast("subscription: no servers found")
                    } else {
                        val merged = (links + parsed.map { it.rawLink }).distinct()
                        persist(merged)
                        onToast("${parsed.size} servers from subscription")
                        subUrl = ""
                    }
                }
            }) { Text(stringResource(R.string.xray_sub_fetch)) }
        }

        SectionCard(
            title = stringResource(R.string.xray_servers_title),
            glyph = { GlobeIcon(22.dp) }
        ) {
            if (links.isEmpty()) {
                Text(
                    stringResource(R.string.xray_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((links.size.coerceAtMost(6) * 76).dp)
                ) {
                    itemsIndexed(links) { index, link ->
                        val server = remember(link) { XrayShareLink.parse(link) }
                        ServerRow(
                            server = server,
                            raw = link,
                            active = index == store.xrayActiveIndex,
                            latencyMs = testedLatency[index],
                            onActivate = { persist(links, index) },
                            onDelete = {
                                val newLinks = links.toMutableList()
                                newLinks.removeAt(index)
                                val newActive = if (index < store.xrayActiveIndex) {
                                    store.xrayActiveIndex - 1
                                } else {
                                    store.xrayActiveIndex.coerceAtMost(newLinks.size - 1)
                                }
                                persist(newLinks, maxOf(newActive, 0))
                            },
                            onTest = {
                                val s = server ?: return@ServerRow
                                ioPool.execute {
                                    val ms = tcpPing(s.address, s.port)
                                    testedLatency = testedLatency + (index to ms)
                                    if (ms > 0) {
                                        onToast("${s.name}: ${ms}ms")
                                    } else {
                                        onToast("${s.name}: unreachable")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        SectionCard(
            title = stringResource(R.string.xray_options_title),
            glyph = { SlidersIcon(22.dp) }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.xray_fragment),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        stringResource(R.string.xray_fragment_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = fragmentTls, onCheckedChange = {
                    fragmentTls = it
                    store.xrayFragmentTls = it
                })
            }
        }
    }
}

@Composable
private fun ServerRow(
    server: XrayServer?,
    raw: String,
    active: Boolean,
    latencyMs: Long?,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (active) MaterialTheme.colorScheme.surfaceContainerHighest
        else MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onActivate,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = (server?.name?.ifBlank { null } ?: raw.take(28)) +
                        if (active) "  ●" else "",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1
                )
                Text(
                    text = (server?.protocolLabel ?: "?") + " · " +
                        (server?.address ?: "?") + ":" + (server?.port ?: 0) +
                        (latencyMs?.let { " · ${it}ms" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            OutlinedButton(onClick = onTest) {
                Text(stringResource(R.string.xray_test), style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Text(stringResource(R.string.xray_delete), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Plain TCP connect ping (0 = unreachable). */
private fun tcpPing(host: String, port: Int): Long {
    return try {
        val t0 = System.nanoTime()
        java.net.Socket().use { s ->
            s.connect(java.net.InetSocketAddress(host, port), 5_000)
        }
        (System.nanoTime() - t0) / 1_000_000
    } catch (_: Exception) {
        0L
    }
}
