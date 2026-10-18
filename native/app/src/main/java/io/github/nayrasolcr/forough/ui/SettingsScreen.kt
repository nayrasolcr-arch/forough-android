package io.github.nayrasolcr.forough.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nayrasolcr.forough.R
import io.github.nayrasolcr.forough.tunnel.ProtocolMode
import io.github.nayrasolcr.forough.tunnel.Region
import io.github.nayrasolcr.forough.tunnel.SettingsStore
import io.github.nayrasolcr.forough.tunnel.TunnelOptions
import io.github.nayrasolcr.forough.ui.theme.ForoughGold

/**
 * SettingsScreen — Android port of the desktop SettingsDialog plus the
 * Android power features: auto-connect, haptics, split tunneling (with the
 * app picker), battery-optimization exemption and the system Always-on VPN
 * / kill switch shortcut.
 *
 * Desktop-only rows (sing-box TUN, system proxy, Fake-IP experiment,
 * binary paths) have no meaning on Android — the VpnService is the
 * whole-device routing — and are intentionally absent.
 */
@Composable
fun SettingsScreen(
    store: SettingsStore,
    onSave: (String) -> Unit,
    onLanguageChanged: () -> Unit,
    onBatteryHelper: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenShare: () -> Unit
) {
    var options by remember { mutableStateOf(store.load()) }
    var autoConnect by remember { mutableStateOf(store.autoConnect) }
    var haptics by remember { mutableStateOf(store.hapticsEnabled) }
    val savedText = stringResource(R.string.settings_saved)
    val context = LocalContext.current

    fun persist() {
        store.autoConnect = autoConnect
        store.hapticsEnabled = haptics
        Haptics.setEnabled(haptics)
        store.save(options)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // ===================== Connection =====================
        SectionCard(
            title = stringResource(R.string.settings_connection),
            glyph = { ShieldBoltIcon(22.dp) }
        ) {
            Text(
                stringResource(R.string.settings_connection_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            DropdownRow(
                label = stringResource(R.string.settings_protocol),
                value = when (options.protocol) {
                    ProtocolMode.AUTO -> stringResource(R.string.mode_auto)
                    ProtocolMode.CONDUIT -> stringResource(R.string.mode_conduit)
                    ProtocolMode.DIRECT -> stringResource(R.string.mode_direct)
                    ProtocolMode.EXTREME -> stringResource(R.string.mode_extreme)
                    ProtocolMode.CDN_FRONTING -> stringResource(R.string.mode_cdn)
                },
                items = listOf(
                    stringResource(R.string.mode_auto),
                    stringResource(R.string.mode_conduit),
                    stringResource(R.string.mode_direct),
                    stringResource(R.string.mode_cdn),
                    stringResource(R.string.mode_extreme)
                )
            ) { index ->
                options = options.copy(
                    protocol = when (index) {
                        1 -> ProtocolMode.CONDUIT
                        2 -> ProtocolMode.DIRECT
                        3 -> ProtocolMode.CDN_FRONTING
                        4 -> ProtocolMode.EXTREME
                        else -> ProtocolMode.AUTO
                    }
                )
            }
            // Mode hint: each protocol mode gets one plain-language line so
            // the power features are discoverable (v2.4.0).
            Text(
                text = when (options.protocol) {
                    ProtocolMode.EXTREME -> stringResource(R.string.mode_extreme_hint)
                    ProtocolMode.CDN_FRONTING -> stringResource(R.string.mode_cdn_hint)
                    else -> ""
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (options.protocol == ProtocolMode.EXTREME) {
                    ForoughGold
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(bottom = 6.dp)
            )

            val regionLabel = if (options.region.isBlank()) {
                stringResource(R.string.region_any)
            } else {
                val region = Region.ALL.firstOrNull { it.code == options.region }
                "${region?.flagEmoji() ?: ""} ${region?.code ?: options.region} - ${region?.name ?: ""}"
            }
            DropdownRow(
                label = stringResource(R.string.settings_region),
                value = regionLabel,
                items = listOf(
                    stringResource(R.string.region_any)
                ) + Region.ALL.map { "${it.flagEmoji()} ${it.code} - ${it.name}" }
            ) { index ->
                options = options.copy(
                    region = if (index == 0) "" else Region.ALL[index - 1].code
                )
            }

            SwitchRow(
                stringResource(R.string.settings_beast),
                stringResource(R.string.settings_beast_desc),
                options.enableBeastMode
            ) { options = options.copy(enableBeastMode = it) }

            // ----- Connection workers (parallel dial pool) -----
            // v2.4.0: pool raised — 64 default, up to 256 workers for
            // maximum parallel dial pressure on censored networks.
            val workerValues = listOf(64, 96, 128, 192, 256)
            DropdownRow(
                label = stringResource(R.string.settings_workers),
                value = stringResource(
                    R.string.settings_workers_value,
                    if (options.connectionWorkers in workerValues) options.connectionWorkers else 64
                ),
                items = workerValues.map { stringResource(R.string.settings_workers_value, it) }
            ) { index ->
                options = options.copy(connectionWorkers = workerValues[index])
            }

            SwitchRow(
                stringResource(R.string.settings_turbo),
                stringResource(R.string.settings_turbo_desc),
                options.enableTurboNetwork
            ) { options = options.copy(enableTurboNetwork = it) }

            SwitchRow(
                stringResource(R.string.settings_timeout),
                stringResource(R.string.settings_timeout_desc),
                options.enableTimeout
            ) { options = options.copy(enableTimeout = it) }

            SwitchRow(
                stringResource(R.string.settings_lan),
                stringResource(R.string.settings_lan_desc),
                options.enableLan
            ) { options = options.copy(enableLan = it) }

            // ----- Proxy sharing screen shortcut -----
            OutlinedButton(
                onClick = {
                    Haptics.tap()
                    onOpenShare()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.share_open))
            }
            Text(
                stringResource(R.string.share_open_desc),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ===================== Power features =====================
        SectionCard(
            title = stringResource(R.string.settings_power),
            glyph = { BoltIcon(22.dp) }
        ) {
            Text(
                stringResource(R.string.settings_power_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            SwitchRow(
                stringResource(R.string.settings_auto_connect),
                stringResource(R.string.settings_auto_connect_desc),
                autoConnect
            ) { autoConnect = it }

            SwitchRow(
                stringResource(R.string.settings_haptics),
                stringResource(R.string.settings_haptics_desc),
                haptics
            ) { haptics = it }

            // ----- Split tunneling -----
            Text(
                stringResource(R.string.settings_split_tunnel),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 6.dp)
            )
            val splitModes = listOf(
                SettingsStore.SPLIT_OFF to stringResource(R.string.split_mode_off),
                SettingsStore.SPLIT_ALLOW to stringResource(R.string.split_mode_allow),
                SettingsStore.SPLIT_EXCLUDE to stringResource(R.string.split_mode_exclude)
            )
            DropdownRow(
                label = stringResource(R.string.settings_split_tunnel),
                value = splitModes.firstOrNull { it.first == options.splitTunnelMode }?.second
                    ?: splitModes[0].second,
                items = splitModes.map { it.second }
            ) { index ->
                options = options.copy(splitTunnelMode = splitModes[index].first)
            }

            val splitCount = options.splitTunnelPackages.size
            OutlinedButton(
                onClick = {
                    Haptics.tap()
                    onOpenApps()
                },
                enabled = options.splitTunnelMode != SettingsStore.SPLIT_OFF,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (splitCount > 0) {
                        "${stringResource(R.string.settings_split_pick)} (${String.format(stringResource(R.string.split_summary_fmt), splitCount)})"
                    } else {
                        stringResource(R.string.settings_split_pick)
                    }
                )
            }

            // ----- Battery optimization -----
            Text(
                stringResource(R.string.settings_battery),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 6.dp)
            )
            OutlinedButton(
                onClick = {
                    Haptics.tap()
                    onBatteryHelper()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_battery))
            }
            Text(
                stringResource(R.string.settings_battery_desc),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ----- Always-on VPN / kill switch -----
            Text(
                stringResource(R.string.settings_alwayson),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 6.dp)
            )
            OutlinedButton(
                onClick = {
                    Haptics.tap()
                    try {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_VPN_SETTINGS)
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        try {
                            val fallback = android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                            fallback.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(fallback)
                        } catch (_: Exception) {
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_alwayson))
            }
            Text(
                stringResource(R.string.settings_alwayson_desc),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.settings_tile_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // ===================== CDN fronting =====================
        SectionCard(title = stringResource(R.string.settings_cdn_group)) {
            TextFieldRow(
                stringResource(R.string.settings_cdn_ips),
                stringResource(R.string.settings_cdn_ips_desc),
                options.cdnFrontingIpList
            ) { options = options.copy(cdnFrontingIpList = it) }
            TextFieldRow(
                stringResource(R.string.settings_cdn_sni),
                stringResource(R.string.settings_cdn_sni_desc),
                options.cdnFrontingSni
            ) { options = options.copy(cdnFrontingSni = it) }
        }

        // ===================== Advanced =====================
        SectionCard(title = stringResource(R.string.settings_advanced), glyph = { SlidersIcon(22.dp) }) {
            TextFieldRow(
                stringResource(R.string.settings_upstream),
                stringResource(R.string.settings_upstream_hint),
                options.upstreamProxyUrl
            ) { options = options.copy(upstreamProxyUrl = it) }
            TextFieldRow(
                stringResource(R.string.settings_compartment),
                stringResource(R.string.settings_compartment_desc),
                options.inproxyCompartmentId
            ) { options = options.copy(inproxyCompartmentId = it) }
            TextFieldRow(
                stringResource(R.string.settings_osl),
                stringResource(R.string.settings_osl_desc),
                options.oslUrls
            ) { options = options.copy(oslUrls = it) }
        }

        // ===================== Interface =====================
        SectionCard(title = stringResource(R.string.settings_interface)) {
            val langLabels = listOf(
                stringResource(R.string.lang_system),
                stringResource(R.string.lang_fa),
                stringResource(R.string.lang_en)
            )
            val currentLangIndex = when (store.language) {
                "fa" -> 1
                "en" -> 2
                else -> 0
            }
            DropdownRow(
                label = stringResource(R.string.settings_language),
                value = langLabels[currentLangIndex],
                items = langLabels
            ) { index ->
                store.language = when (index) {
                    1 -> "fa"
                    2 -> "en"
                    else -> "system"
                }
                onLanguageChanged()
            }
        }

        Button(
            onClick = {
                Haptics.confirm()
                persist()
                onSave(savedText)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.settings_save))
        }

        Text(
            stringResource(R.string.settings_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
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
private fun TextFieldRow(label: String, hint: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = { Text(hint) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownRow(label: String, value: String, items: List<String>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEachIndexed { index, item ->
                DropdownMenuItem(
                    text = { Text(item) },
                    onClick = {
                        Haptics.tap()
                        onSelect(index)
                        expanded = false
                    }
                )
            }
        }
    }
}
