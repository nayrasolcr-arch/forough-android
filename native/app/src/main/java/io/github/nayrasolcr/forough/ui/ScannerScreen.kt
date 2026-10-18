package io.github.nayrasolcr.forough.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.nayrasolcr.forough.R
import io.github.nayrasolcr.forough.scanner.CdnScanner
import io.github.nayrasolcr.forough.tunnel.ProtocolMode
import io.github.nayrasolcr.forough.tunnel.SettingsStore
import io.github.nayrasolcr.forough.ui.theme.ConnectedGreen
import io.github.nayrasolcr.forough.ui.theme.ForoughGold
import kotlinx.coroutines.delay
import java.util.Collections

/**
 * ScannerScreen — Android port of the desktop ScannerDialog.
 *
 * v2.2.0 rework (the "manual scan lags" fix):
 *  - worker threads NEVER touch compose state directly; every result lands
 *    in a synchronized staging list and a single main-thread drain loop
 *    (120ms cadence) folds it into the UI. A 96-worker scan used to
 *    recompose the whole result list dozens of times per second — that was
 *    the lag.
 *  - results render through a LazyColumn with stable keys; alive edges are
 *    sorted fastest-first and capped, dead probes collapse into a counter.
 *  - the fastest edges auto-check (top 12) instead of every TLS-alive IP —
 *    a previous release auto-checked ALL alive IPs (a Cloudflare-wide scan
 *    finds hundreds), and the tunnel then spent its whole establishment
 *    budget dialing dead fronting pairs before reaching the working
 *    overrides — that was the "I select active IPs and they don't connect"
 *    symptom.
 *  - "Use selected & connect" writes the candidates (fastest first, capped),
 *    switches the protocol to CDN fronting and starts the tunnel in one tap.
 */
@Composable
fun ScannerScreen(
    store: SettingsStore,
    running: Boolean,
    onConnect: () -> Unit,
    onToast: (String) -> Unit
) {
    var scanCf by remember { mutableStateOf(store.scanCloudflare) }
    var scanFastly by remember { mutableStateOf(store.scanFastly) }
    var scanAkamai by remember { mutableStateOf(store.scanAkamai) }
    var scanCloudfront by remember { mutableStateOf(store.scanCloudfront) }
    var scanBunny by remember { mutableStateOf(store.scanBunny) }
    var sampleCount by remember { mutableIntStateOf(store.scanSampleCount) }
    var concurrency by remember { mutableIntStateOf(store.scanConcurrency) }
    var customCidrs by remember { mutableStateOf(store.scanCustomCidrs) }
    var customSnis by remember { mutableStateOf(store.scanCustomSnis) }

    var isRunning by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var aliveCount by remember { mutableIntStateOf(0) }
    var totalCount by remember { mutableIntStateOf(0) }
    var checkedCount by remember { mutableIntStateOf(0) }
    var deadCount by remember { mutableIntStateOf(0) }
    var autoChecked by remember { mutableStateOf(false) }

    // Fastest-first alive edges (drained from the staging buffer).
    var alive by remember { mutableStateOf(listOf<CdnScanner.Result>()) }
    // Whole-set checkbox state — O(1) membership, one state write per change.
    var checked by remember { mutableStateOf(emptySet<String>()) }

    // Staging buffer written by scanner workers; drained on the main thread.
    val staged = remember { Collections.synchronizedList(ArrayList<CdnScanner.Result>()) }
    val stagedAlive = remember { java.util.concurrent.atomic.AtomicInteger(0) }
    val stagedDead = remember { java.util.concurrent.atomic.AtomicInteger(0) }
    val scanner = remember { CdnScanner() }

    // Pre-resolved strings for non-composable callbacks (onClick handlers).
    val scanFinishedFmt = stringResource(R.string.scanner_finished_fmt)
    val scanStoppedText = stringResource(R.string.scan_stopped)
    val selectNeededText = stringResource(R.string.scanner_select_needed)
    val edgesAppliedFmt = stringResource(R.string.scanner_edges_applied_fmt)
    val edgesConnectedFmt = stringResource(R.string.scanner_edges_connected_fmt)
    val alreadyRunningText = stringResource(R.string.scanner_already_connected)

    DisposableEffect(scanner) {
        onDispose {
            if (scanner.isRunning) scanner.stop()
        }
    }

    // Main-thread drain loop — the ONLY place staged results become UI
    // state. Runs while a scan is active plus one final drain after it
    // (the last batch of worker results lands after onFinished).
    LaunchedEffect(isRunning) {
        while (isRunning) {
            delay(120)
            drainStaged(
                staged, stagedAlive, stagedDead,
                onDrain = { batch, aliveDelta, deadDelta ->
                    if (batch.isNotEmpty()) {
                        val merged = (alive + batch)
                            .distinctBy { it.ip }
                            .sortedBy { it.totalMs }
                        alive = merged
                        // Auto-check the fastest edges once, like the desktop's
                        // "best edges" selection — capped so the tunnel's
                        // establishment order starts with genuinely fast,
                        // fronting-capable candidates.
                        if (!autoChecked && alive.isNotEmpty()) {
                            autoChecked = true
                            checked = alive.take(AUTO_CHECK_LIMIT).map { it.ip }.toSet()
                            checkedCount = checked.size
                        }
                    }
                    if (aliveDelta > 0) aliveCount += aliveDelta
                    if (deadDelta > 0) deadCount += deadDelta
                }
            )
        }
        // Final drain — swallow whatever arrived between the last tick and
        // the finished callback.
        delay(60)
        drainStaged(
            staged, stagedAlive, stagedDead,
            onDrain = { batch, aliveDelta, deadDelta ->
                if (batch.isNotEmpty()) {
                    alive = (alive + batch)
                        .distinctBy { it.ip }
                        .sortedBy { it.totalMs }
                }
                if (aliveDelta > 0) aliveCount += aliveDelta
                if (deadDelta > 0) deadCount += deadDelta
            }
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // ===================== Scope =====================
        SectionCard(title = stringResource(R.string.scanner_scope), glyph = { ScanIcon(22.dp) }) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ScopeSwitch(stringResource(R.string.scanner_cloudflare), stringResource(R.string.scanner_cf_desc), scanCf) {
                    scanCf = it
                    store.scanCloudflare = it
                }
                ScopeSwitch(stringResource(R.string.scanner_fastly), stringResource(R.string.scanner_fastly_desc), scanFastly) {
                    scanFastly = it
                    store.scanFastly = it
                }
                ScopeSwitch(stringResource(R.string.scanner_akamai), stringResource(R.string.scanner_akamai_desc), scanAkamai) {
                    scanAkamai = it
                    store.scanAkamai = it
                }
                ScopeSwitch(stringResource(R.string.scanner_cloudfront), stringResource(R.string.scanner_cloudfront_desc), scanCloudfront) {
                    scanCloudfront = it
                    store.scanCloudfront = it
                }
                ScopeSwitch(stringResource(R.string.scanner_bunny), stringResource(R.string.scanner_bunny_desc), scanBunny) {
                    scanBunny = it
                    store.scanBunny = it
                }

                Stepper(stringResource(R.string.scanner_sample_size), stringResource(R.string.scanner_sample_desc), sampleCount, 128, 4096, 128) {
                    sampleCount = it
                    store.scanSampleCount = it
                }
                Stepper(stringResource(R.string.scanner_concurrency), stringResource(R.string.scanner_conc_desc), concurrency, 16, 256, 16) {
                    concurrency = it
                    store.scanConcurrency = it
                }

                OutlinedTextField(
                    value = customCidrs,
                    onValueChange = {
                        customCidrs = it
                        store.scanCustomCidrs = it
                    },
                    label = { Text(stringResource(R.string.scanner_custom_ranges)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
                OutlinedTextField(
                    value = customSnis,
                    onValueChange = {
                        customSnis = it
                        store.scanCustomSnis = it
                    },
                    label = { Text(stringResource(R.string.scanner_custom_sni)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        Haptics.confirm()
                        staged.clear()
                        stagedAlive.set(0)
                        stagedDead.set(0)
                        alive = emptyList()
                        checked = emptySet()
                        autoChecked = false
                        checkedCount = 0
                        deadCount = 0
                        progress = 0f
                        aliveCount = 0
                        totalCount = 0
                        scanner.scanCloudflare = scanCf
                        scanner.scanFastly = scanFastly
                        scanner.scanAkamai = scanAkamai
                        scanner.scanCloudfront = scanCloudfront
                        scanner.scanBunny = scanBunny
                        scanner.customCidrs = customCidrs
                        scanner.customSnis = customSnis
                        scanner.sampleCount = sampleCount
                        scanner.concurrency = concurrency
                        try {
                            scanner.start(object : CdnScanner.Listener {
                                // Worker threads: ONLY touch the staging
                                // buffer + atomics — never compose state.
                                override fun onProgress(scanned: Int, total: Int, aliveNow: Int) {
                                    progress = if (total > 0) scanned.toFloat() / total else 0f
                                    totalCount = total
                                }

                                override fun onResult(result: CdnScanner.Result) {
                                    staged.add(result)
                                    if (result.alive) stagedAlive.incrementAndGet()
                                    else stagedDead.incrementAndGet()
                                }

                                override fun onFinished(aliveTotal: Int, total: Int) {
                                    isRunning = false
                                    aliveCount = aliveTotal
                                    totalCount = total
                                    onToast(String.format(scanFinishedFmt, aliveTotal, total))
                                }
                            })
                            isRunning = true
                        } catch (e: Exception) {
                            onToast(e.message ?: "scan error")
                        }
                    },
                    enabled = !isRunning
                ) {
                    Text(stringResource(R.string.scanner_start))
                }
                OutlinedButton(
                    onClick = {
                        Haptics.tap()
                        scanner.stop()
                        isRunning = false
                        onToast(scanStoppedText)
                    },
                    enabled = isRunning,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.scanner_stop))
                }
            }

            if (isRunning || totalCount > 0) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                // Live stat chips — probed / alive / dead at a glance.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatChip(label = stringResource(R.string.scanner_alive), value = aliveCount.toString(), color = ConnectedGreen)
                    StatChip(label = stringResource(R.string.scanner_dead), value = deadCount.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StatChip(label = stringResource(R.string.scanner_probed), value = totalCount.toString(), color = ForoughGold)
                }
            }
        }

        // ===================== Results =====================
        SectionCard(title = stringResource(R.string.scanner_title)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // One-tap path: apply the fastest selected edges AND connect.
                Button(
                    onClick = {
                        Haptics.confirm()
                        if (applySelection(store, checked, alive, onToast, selectNeededText, edgesAppliedFmt, edgesConnectedFmt)) {
                            if (running) {
                                onToast(alreadyRunningText)
                            } else {
                                onConnect()
                            }
                        }
                    },
                    enabled = checked.isNotEmpty()
                ) {
                    Text(stringResource(R.string.scanner_use_connect))
                }
                OutlinedButton(
                    onClick = {
                        Haptics.tap()
                        if (applySelection(store, checked, alive, onToast, selectNeededText, edgesAppliedFmt, edgesConnectedFmt)) {
                            // applied — toast already shown
                        }
                    },
                    enabled = checked.isNotEmpty()
                ) {
                    Text(stringResource(R.string.scanner_use_selected))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    Haptics.tap()
                    checked = alive.map { it.ip }.take(AUTO_CHECK_LIMIT).toSet()
                    checkedCount = checked.size
                }) {
                    Text(stringResource(R.string.scanner_select_fastest))
                }
                OutlinedButton(onClick = {
                    Haptics.tap()
                    checked = alive.map { it.ip }.toSet()
                    checkedCount = checked.size
                }) {
                    Text(stringResource(R.string.scanner_select_all))
                }
            }

            Text(
                text = stringResource(R.string.scanner_selected_fmt, checkedCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // LazyColumn: stable keys + bounded size — smooth even with a
            // full scan of results on screen.
            if (alive.isEmpty()) {
                Text(
                    stringResource(R.string.scanner_no_results),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(alive.take(MAX_ROWS), key = { it.ip }) { result ->
                        ResultRow(
                            result = result,
                            rank = alive.indexOf(result) + 1,
                            checked = result.ip in checked,
                            onChecked = { c ->
                                checked = if (c) checked + result.ip else checked - result.ip
                                checkedCount = checked.size
                            }
                        )
                    }
                }
                if (alive.size > MAX_ROWS) {
                    Text(
                        text = stringResource(R.string.scanner_more_fmt, alive.size - MAX_ROWS),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Writes the selected edges into the CDN fronting settings — fastest first,
 * capped (the tunnel tries them in order before falling back to the built-in
 * edges, so a short, fast list connects quickly). Protocol switches to CDN
 * fronting so the candidates are actually used. Returns true when applied.
 */
private fun applySelection(
    store: SettingsStore,
    checked: Set<String>,
    alive: List<CdnScanner.Result>,
    onToast: (String) -> Unit,
    selectNeededText: String,
    appliedFmt: String,
    connectedFmt: String
): Boolean {
    if (checked.isEmpty()) {
        onToast(selectNeededText)
        return false
    }
    val byIp = alive.associateBy { it.ip }
    val selected = checked
        .mapNotNull { byIp[it] }
        .sortedBy { it.totalMs }
        .take(APPLY_LIMIT)

    val snis = selected.map { it.sni }.filter { it.isNotBlank() }.distinct()
    val current = store.load()
    store.save(
        current.copy(
            protocol = ProtocolMode.CDN_FRONTING,
            cdnFrontingIpList = selected.joinToString(" ") { it.ip },
            cdnFrontingSni = if (current.cdnFrontingSni.isBlank()) snis.joinToString(" ") else current.cdnFrontingSni
        )
    )
    onToast(String.format(connectedFmt, selected.size))
    return true
}

/** Fold the staging buffer into UI state on the main thread. */
private fun drainStaged(
    staged: MutableList<CdnScanner.Result>,
    stagedAlive: java.util.concurrent.atomic.AtomicInteger,
    stagedDead: java.util.concurrent.atomic.AtomicInteger,
    onDrain: (List<CdnScanner.Result>, Int, Int) -> Unit
) {
    if (staged.isEmpty()) return
    val batch: List<CdnScanner.Result>
    synchronized(staged) {
        batch = ArrayList(staged)
        staged.clear()
    }
    val a = stagedAlive.getAndSet(0)
    val d = stagedDead.getAndSet(0)
    onDrain(batch, a, d)
}

@Composable
private fun ResultRow(
    result: CdnScanner.Result,
    rank: Int,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (checked) ForoughGold.copy(alpha = 0.08f)
                else Color.Transparent
            )
            .padding(horizontal = 4.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = {
                Haptics.tap()
                onChecked(it)
            },
            colors = CheckboxDefaults.colors(checkedColor = ForoughGold)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Rank badge — #1..#3 get the gold treatment.
                val badgeColor = if (rank <= 3) ForoughGold else MaterialTheme.colorScheme.onSurfaceVariant
                Text(
                    "#$rank",
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor,
                    modifier = Modifier.width(34.dp)
                )
                Text(result.ip, fontFamily = FontFamily.Monospace)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${result.sni} · TCP ${result.tcpMs}ms + TLS ${result.tlsMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(3.dp))
            // Relative speed bar — instantly shows which edges are fastest.
            val fraction = if (result.totalMs <= 0) 0f
                else (1f - (result.totalMs.toFloat() / (result.totalMs + 400f))).coerceIn(0.08f, 1f)
            val animatedFraction by animateFloatAsState(
                targetValue = fraction,
                animationSpec = tween(500),
                label = "speedBar"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedFraction)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (rank <= 3) ForoughGold else ConnectedGreen.copy(alpha = 0.7f))
                )
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.widthIn(min = 72.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = color)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScopeSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = {
            Haptics.tap()
            onChange(it)
        })
    }
}

@Composable
private fun Stepper(
    title: String,
    subtitle: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    onChange: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(
            onClick = { Haptics.tap(); onChange((value - step).coerceAtLeast(min)) },
            modifier = Modifier.widthIn(min = 8.dp)
        ) { Text("-") }
        Text(
            text = value.toString(),
            modifier = Modifier.padding(horizontal = 12.dp),
            style = MaterialTheme.typography.titleMedium
        )
        OutlinedButton(
            onClick = { Haptics.tap(); onChange((value + step).coerceAtMost(max)) }
        ) { Text("+") }
    }
}

private const val AUTO_CHECK_LIMIT = 12
private const val APPLY_LIMIT = 16
private const val MAX_ROWS = 60
