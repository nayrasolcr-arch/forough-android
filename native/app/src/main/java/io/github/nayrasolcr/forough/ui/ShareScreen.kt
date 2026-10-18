package io.github.nayrasolcr.forough.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.wifi.WifiManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.nayrasolcr.forough.R
import io.github.nayrasolcr.forough.tunnel.SettingsStore
import io.github.nayrasolcr.forough.tunnel.TunnelConfigBuilder
import io.github.nayrasolcr.forough.ui.theme.ForoughGold

/**
 * ShareScreen — proxy sharing (the "شر کردن پروکسی" feature): exposes the
 * tunnel's local HTTP + SOCKS proxies on the Wi-Fi / hotspot network so
 * other phones, laptops and TVs can ride this device's Forough tunnel.
 *
 * Implementation: the "share" switch binds the engine's listeners to all
 * interfaces (ListenInterface=any via TunnelConfigBuilder) and excludes the
 * private LAN subnets from the VPN routes (VpnManager) — both apply on the
 * next connect. This screen surfaces the exact endpoints to enter on the
 * other device, as text and as a scannable QR code.
 */
@Composable
fun ShareScreen(store: SettingsStore, onToast: (String) -> Unit) {
    var share by remember { mutableStateOf(store.enableLan) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val copiedText = stringResource(R.string.share_copied)
    val savedText = stringResource(R.string.share_applies_next_connect)

    val addresses = remember { localNetworkAddresses(context) }
    val httpPort = TunnelConfigBuilder.HTTP_PROXY_PORT
    val socksPort = TunnelConfigBuilder.SOCKS_PROXY_PORT
    val primary = addresses.firstOrNull()

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // ===================== Switch =====================
        SectionCard(title = stringResource(R.string.share_title), glyph = { ShareNodesIcon(22.dp) }) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.share_toggle), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.share_toggle_desc),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = share, onCheckedChange = {
                    Haptics.tap()
                    share = it
                    store.enableLan = it
                })
            }
            Text(
                if (share) savedText else stringResource(R.string.share_off_hint),
                style = MaterialTheme.typography.labelSmall,
                color = if (share) ForoughGold else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ===================== Endpoints =====================
        SectionCard(title = stringResource(R.string.share_endpoints), glyph = { GlobeIcon(22.dp) }) {
            if (share) {
                if (primary == null) {
                    Text(
                        stringResource(R.string.share_no_network),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val httpUrl = "http://$primary:$httpPort"
                    val socksUrl = "socks5://$primary:$socksPort"

                    EndpointRow(
                        label = "HTTP",
                        value = httpUrl,
                        onCopy = {
                            clipboard.setText(AnnotatedString(httpUrl))
                            onToast(copiedText)
                        }
                    )
                    EndpointRow(
                        label = "SOCKS5",
                        value = socksUrl,
                        onCopy = {
                            clipboard.setText(AnnotatedString(socksUrl))
                            onToast(copiedText)
                        }
                    )

                    // QR code — the fastest way to hand the proxy to another
                    // phone (camera → scan → paste into its proxy settings).
                    QrCode(text = httpUrl, modifier = Modifier.align(Alignment.CenterHorizontally))

                    Text(
                        stringResource(R.string.share_all_addresses),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    addresses.forEach { addr ->
                        Text(
                            "• $addr:$httpPort",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            } else {
                Text(
                    stringResource(R.string.share_disabled_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ===================== How to use =====================
        SectionCard(title = stringResource(R.string.share_howto_title)) {
            Text(
                stringResource(R.string.share_howto_1),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.size(4.dp))
            Text(
                stringResource(R.string.share_howto_2),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.size(4.dp))
            Text(
                stringResource(R.string.share_howto_3),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                stringResource(R.string.share_warning),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun EndpointRow(label: String, value: String, onCopy: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = ForoughGold)
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
        Button(onClick = {
            Haptics.tap()
            onCopy()
        }) {
            Text(stringResource(R.string.share_copy))
        }
    }
}

/** ZXing QR rendered as a compose Image — no network, pure local bitmap. */
@Composable
private fun QrCode(text: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(text) { qrBitmap(context, text) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "QR",
            modifier = modifier
                .size(180.dp)
                .padding(6.dp)
        )
    }
}

private fun qrBitmap(context: Context, text: String): Bitmap? {
    return try {
        val hints = mapOf(
            com.google.zxing.EncodeHintType.MARGIN to 1,
            com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matrix = com.google.zxing.qrcode.QRCodeWriter()
            .encode(text, com.google.zxing.BarcodeFormat.QR_CODE, 512, 512, hints)
        val size = matrix.width
        val pixels = IntArray(size * size)
        val onColor = 0xFF000000.toInt()
        val offColor = 0xFFFFFFFF.toInt()
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (matrix.get(x, y)) onColor else offColor
            }
        }
        Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    } catch (_: Exception) {
        null
    }
}

/**
 * Live local IPv4 addresses (Wi-Fi + hotspot AP + others), most likely
 * client-reachable first. Wi-Fi IPs come first; the hotspot subnet is
 * usually 192.168.43.x (older Android) or a carrier-chosen range.
 */
private fun localNetworkAddresses(context: Context): List<String> {
    val result = LinkedHashSet<String>()
    try {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        @Suppress("DEPRECATION")
        val wifiIp = wifi?.connectionInfo?.ipAddress ?: 0
        if (wifiIp != 0) {
            result.add(
                "${wifiIp and 0xFF}.${(wifiIp shr 8) and 0xFF}.${(wifiIp shr 16) and 0xFF}.${(wifiIp shr 24) and 0xFF}"
            )
        }
    } catch (_: Exception) {
    }
    try {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return result.toList()
        for (ni in interfaces.toList()) {
            for (addr in ni.inetAddresses) {
                if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                    val host = addr.hostAddress ?: continue
                    // SoftAP interfaces (swlan0/ap0) typically live here too.
                    if (host.startsWith("192.168.") || host.startsWith("10.") ||
                        host.startsWith("172.")
                    ) {
                        result.add(host)
                    }
                }
            }
        }
    } catch (_: Exception) {
    }
    return result.toList()
}
