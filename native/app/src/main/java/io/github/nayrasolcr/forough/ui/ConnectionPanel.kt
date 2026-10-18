package io.github.nayrasolcr.forough.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.nayrasolcr.forough.ForoughBus
import io.github.nayrasolcr.forough.R
import io.github.nayrasolcr.forough.net.NetTools
import io.github.nayrasolcr.forough.tunnel.Region
import io.github.nayrasolcr.forough.tunnel.TunnelStats
import io.github.nayrasolcr.forough.ui.theme.AdwAccent
import io.github.nayrasolcr.forough.ui.theme.AdwAccentGlow
import io.github.nayrasolcr.forough.ui.theme.AdwDestructive
import io.github.nayrasolcr.forough.ui.theme.ConnectedGreen
import io.github.nayrasolcr.forough.ui.theme.ConnectedGreenGlow
import io.github.nayrasolcr.forough.ui.theme.GraphRx
import io.github.nayrasolcr.forough.ui.theme.GraphTx
import io.github.nayrasolcr.forough.ui.theme.ForoughGold
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * ConnectionPanel — the hero of the main screen, Android counterpart of the
 * desktop widget of the same name. A 132dp power button wrapped in a live
 * status ring (sweeping arc while connecting, calm green glow while
 * connected), springy press physics, haptics, animated speed cards and the
 * 60-second traffic graph — all driven by springs, no hard state swaps.
 */
@Composable
fun ConnectionPanel(
    stats: TunnelStats,
    running: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSpeedTest: () -> Unit,
    onDiagnostics: () -> Unit,
    onLogs: () -> Unit,
    onAbout: () -> Unit
) {
    val window = 60
    val rxHistory = remember { mutableStateListOf<Double>().apply { repeat(window) { add(0.0) } } }
    val txHistory = remember { mutableStateListOf<Double>().apply { repeat(window) { add(0.0) } } }
    var lastRx by remember { mutableLongStateOf(0L) }
    var lastTx by remember { mutableLongStateOf(0L) }
    var haveLast by remember { mutableStateOf(false) }
    var downSpeed by remember { mutableLongStateOf(0L) }
    var upSpeed by remember { mutableLongStateOf(0L) }

    val connected = running && stats.status == "connected"
    val connecting = running && !connected

    // Sample twice per second — smoother than the desktop's 1s cadence so
    // the speed numbers and graph feel live. Deltas are EMA-smoothed to
    // keep the digits from flickering between samples.
    LaunchedEffect(running) {
        if (!running) {
            haveLast = false
            downSpeed = 0
            upSpeed = 0
            for (i in 0 until window) {
                rxHistory[i] = 0.0
                txHistory[i] = 0.0
            }
            return@LaunchedEffect
        }
        var emaDown = 0.0
        var emaUp = 0.0
        while (true) {
            val current = ForoughBus.stats.value
            val rx = current.totalDownBytes
            val tx = current.totalUpBytes
            if (haveLast) {
                val rawDown = (rx - lastRx).coerceAtLeast(0) * 2.0   // per second
                val rawUp = (tx - lastTx).coerceAtLeast(0) * 2.0
                emaDown = if (emaDown == 0.0) rawDown else emaDown * 0.55 + rawDown * 0.45
                emaUp = if (emaUp == 0.0) rawUp else emaUp * 0.55 + rawUp * 0.45
                downSpeed = emaDown.toLong()
                upSpeed = emaUp.toLong()
                pushSample(rxHistory, emaDown)
                pushSample(txHistory, emaUp)
            }
            lastRx = rx
            lastTx = tx
            haveLast = true
            delay(500)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        // ----- Hero: status ring + power button -----
        Box(contentAlignment = Alignment.Center) {
            StatusRing(
                connected = connected,
                connecting = connecting,
                modifier = Modifier.size(172.dp)
            )
            PowerButton(
                running = running,
                connected = connected,
                onConnect = onConnect,
                onDisconnect = onDisconnect
            )
        }

        // ----- Status label + region/IP line -----
        AnimatedContent(
            targetState = statusLabel(stats, running),
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 3 })
                    .togetherWith(fadeOut(tween(150)) + slideOutVertically(tween(180)) { -it / 4 })
            },
            label = "statusLabel"
        ) { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
        }

        val ipLine = ipLabel(stats)
        if (ipLine.isNotEmpty()) {
            AnimatedContent(
                targetState = ipLine,
                transitionSpec = { fadeIn(tween(240)).togetherWith(fadeOut(tween(160))) },
                label = "ipLine"
            ) { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Text(
            text = if (connecting) stringResource(R.string.status_trying_hint) else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        // ----- Live speed cards (appear when the session runs) -----
        androidx.compose.animation.AnimatedVisibility(
            visible = running,
            enter = fadeIn(tween(300)) + slideInVertically(tween(360)) { it / 4 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(240)) { -it / 4 }
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                SpeedCard(
                    label = stringResource(R.string.speed_down),
                    speedBps = downSpeed,
                    color = GraphRx,
                    icon = "↓",
                    modifier = Modifier.weight(1f)
                )
                SpeedCard(
                    label = stringResource(R.string.speed_up),
                    speedBps = upSpeed,
                    color = GraphTx,
                    icon = "↑",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ----- Live traffic graph -----
        TrafficGraph(rxHistory.toList(), txHistory.toList())

        Text(
            text = stringResource(R.string.graph_title),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )

        // ----- Quick actions -----
        QuickActionsRow(
            onSpeedTest = onSpeedTest,
            onDiagnostics = onDiagnostics,
            onLogs = onLogs,
            onAbout = onAbout
        )
    }
}

// ---------------------------------------------------------------------
// Status ring
// ---------------------------------------------------------------------

@Composable
private fun StatusRing(connected: Boolean, connecting: Boolean, modifier: Modifier) {
    // Rotating sweep while the tunnel establishes.
    val sweep = rememberInfiniteTransition(label = "ringSweep")
    val sweepAngle by sweep.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepAngle"
    )
    // Counter-rotating second arc — richer establishment motion.
    val sweepAngle2 by sweep.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepAngle2"
    )
    // Connected: a slow white light sweep riding the steady ring (v2.3.0).
    val connectedSweep by sweep.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "connectedSweep"
    )
    // Breathing glow while idle.
    val idlePulse = breathingPulse(target = 0.86f, periodMs = 1200)
    // Connected: soft luminous halo breathing.
    val haloPulse = if (connected) breathingPulse(target = 0.92f, periodMs = 1600) else 1f

    // Success burst — one-shot particle explosion when the tunnel connects.
    var burstKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(connected) {
        if (connected) burstKey++
    }
    var burstProgress by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(burstKey) {
        if (burstKey > 0) {
            burstProgress = 0f
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(900, easing = FastOutSlowInEasing)
            ) { value, _ -> burstProgress = value }
        }
    }

    val ringColor = when {
        connected -> ConnectedGreen
        connecting -> AdwAccent
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val glowColor = if (connected) ConnectedGreenGlow else AdwAccentGlow

    Canvas(modifier = modifier.graphicsLayer {
        if (!connected && !connecting) {
            scaleX = idlePulse
            scaleY = idlePulse
        }
    }) {
        val stroke = 5.dp.toPx()
        val diameter = size.minDimension - stroke * 2
        val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)
        val topLeft = Offset(
            (size.width - diameter) / 2f,
            (size.height - diameter) / 2f
        )

        // Soft outer glow — layered translucent circles, breathing when live.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glowColor.copy(alpha = glowColor.alpha * haloPulse), Color.Transparent),
                center = center,
                radius = size.minDimension / 2f
            ),
            radius = size.minDimension / 2f
        )

        if (connecting) {
            // Track + TWO rotating gradient arcs sweeping opposite ways —
            // "working on it" with visible depth.
            drawArc(
                color = ringColor.copy(alpha = 0.18f),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(
                    0f to ringColor.copy(alpha = 0f),
                    0.6f to ringColor,
                    1f to ringColor.copy(alpha = 0f),
                    center = center
                ),
                startAngle = sweepAngle, sweepAngle = 130f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(
                    0f to ringColor.copy(alpha = 0.05f),
                    0.5f to ringColor.copy(alpha = 0.55f),
                    1f to ringColor.copy(alpha = 0.05f),
                    center = center
                ),
                startAngle = sweepAngle2, sweepAngle = 70f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(stroke * 0.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        } else {
            // Steady ring: full when connected, faint when idle.
            val alpha = if (connected) 0.95f else 0.45f
            drawArc(
                color = ringColor.copy(alpha = alpha),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
            if (connected) {
                // v2.3.0: slow light sweep riding on the steady ring — the
                // session feels alive rather than parked.
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to Color.Transparent,
                        0.12f to Color.White.copy(alpha = 0.55f),
                        0.24f to Color.Transparent,
                        1f to Color.Transparent,
                        center = center
                    ),
                    startAngle = connectedSweep, sweepAngle = 90f, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }
        }

        // Success burst: 14 particles flying outward, fading + shrinking.
        if (connected && burstProgress < 1f) {
            val radius = diameter / 2f
            val travel = burstProgress
            for (i in 0 until BURST_PARTICLES) {
                val angle = (i.toFloat() / BURST_PARTICLES) * 2f * PI.toFloat()
                val dist = radius * (1f + travel * 0.55f)
                val px = center.x + cos(angle) * dist
                val py = center.y + sin(angle) * dist
                val particleAlpha = (1f - travel).coerceIn(0f, 1f) * 0.85f
                val particleRadius = (1f - travel * 0.7f) * 3.2.dp.toPx()
                drawCircle(
                    color = ConnectedGreen.copy(alpha = particleAlpha),
                    radius = particleRadius,
                    center = Offset(px, py)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------
// Power button
// ---------------------------------------------------------------------

@Composable
private fun PowerButton(
    running: Boolean,
    connected: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val targetColor = when {
        connected -> ConnectedGreen
        running -> AdwDestructive
        else -> AdwAccent
    }
    val buttonColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(420),
        label = "powerColor"
    )
    val interaction = remember { MutableInteractionSource() }
    val press = pressScale(interaction, pressedScale = 0.9f)
    // Gentle breathing while connecting — the button is visibly "working".
    val breathe = if (running && !connected) breathingPulse(target = 1.045f) else 1f

    Button(
        onClick = {
            if (running) {
                Haptics.confirm()
                onDisconnect()
            } else {
                Haptics.confirm()
                onConnect()
            }
        },
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonColor,
            contentColor = Color.White
        ),
        interactionSource = interaction,
        modifier = Modifier
            .size(132.dp)
            .graphicsLayer {
                scaleX = press * breathe
                scaleY = press * breathe
                shadowElevation = if (running) 16f else 8f
            }
    ) {
        AnimatedContent(
            targetState = running,
            transitionSpec = {
                (fadeIn(tween(200)) + slideInVertically(tween(260)) { it / 3 })
                    .togetherWith(fadeOut(tween(140)) + slideOutVertically(tween(200)) { -it / 3 })
            },
            label = "powerIcon"
        ) { isRunning ->
            Icon(
                imageVector = Icons.Filled.PowerSettingsNew,
                contentDescription = if (isRunning) {
                    stringResource(R.string.disconnect)
                } else stringResource(R.string.connect),
                modifier = Modifier.size(52.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------
// Speed cards
// ---------------------------------------------------------------------

@Composable
private fun SpeedCard(
    label: String,
    speedBps: Long,
    color: Color,
    icon: String,
    modifier: Modifier = Modifier
) {
    // Session-relative intensity — the bar fills toward the fastest burst
    // seen this session, giving the numbers a physical, gauge-like feel.
    var peak by remember { mutableLongStateOf(0L) }
    if (speedBps > peak) peak = speedBps
    val barFraction by animateFloatAsState(
        targetValue = if (peak <= 0) 0f else (speedBps.toFloat() / peak).coerceIn(0f, 1f),
        animationSpec = tween(420),
        label = "speedBar"
    )
    // Odometer digits: the number glides to each new sample instead of
    // stepping between formatted strings — the v2.3.0 smoothness upgrade.
    val animatedSpeed = odometerFloat(speedBps.toFloat(), "speedOdo$label")
    // Shimmer sweep while the gauge is near its session peak — the card
    // visibly "burns" at full throttle.
    val shimmer = rememberInfiniteTransition(label = "speedShimmer")
    val shimmerPhase by shimmer.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "speedShimmerPhase"
    )
    val shimmerActive = barFraction > 0.85f

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, color = color, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.size(4.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Odometer digits — glide between samples (see odometerFloat).
            Text(
                text = NetTools.humanSpeed(animatedSpeed.toLong()) + "/s",
                style = MaterialTheme.typography.titleMedium,
                color = color
            )
            // Gradient intensity bar under the digits, with a shimmer sweep
            // once the session is running near its peak.
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth(0.72f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color.copy(alpha = 0.15f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(barFraction)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(color.copy(alpha = 0.55f), color)
                            )
                        )
                )
                if (shimmerActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(
                                Brush.horizontalGradient(
                                    0f to Color.Transparent,
                                    shimmerPhase.coerceIn(0.1f, 0.9f) to
                                        Color.White.copy(alpha = 0.55f),
                                    1f to Color.Transparent
                                )
                            )
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
// Quick actions row
// ---------------------------------------------------------------------

@Composable
private fun QuickActionsRow(
    onSpeedTest: () -> Unit,
    onDiagnostics: () -> Unit,
    onLogs: () -> Unit,
    onAbout: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        QuickAction(
            icon = Icons.Outlined.Speed,
            label = stringResource(R.string.quick_speed),
            onClick = onSpeedTest,
            modifier = Modifier.weight(1f)
        )
        QuickAction(
            icon = Icons.Filled.Info,
            label = stringResource(R.string.quick_diag),
            onClick = onDiagnostics,
            modifier = Modifier.weight(1f)
        )
        QuickAction(
            icon = Icons.Outlined.Article,
            label = stringResource(R.string.quick_logs),
            onClick = onLogs,
            modifier = Modifier.weight(1f)
        )
        QuickAction(
            icon = Icons.Filled.Info,
            label = stringResource(R.string.quick_about),
            onClick = onAbout,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val press = pressScale(interaction, 0.92f)
    Surface(
        onClick = {
            Haptics.tap()
            onClick()
        },
        interactionSource = interaction,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.graphicsLayer {
            scaleX = press
            scaleY = press
        }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
        ) {
            Icon(icon, contentDescription = label, tint = ForoughGold,
                modifier = Modifier.size(20.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

// ---------------------------------------------------------------------
// Traffic graph
// ---------------------------------------------------------------------

@Composable
private fun TrafficGraph(rxHistory: List<Double>, txHistory: List<Double>) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .padding(8.dp)
        ) {
            drawRect(Color(0x80, 0x80, 0x80, 26))
            drawSeries(rxHistory, GraphRx)
            drawSeries(txHistory, GraphTx)
        }
    }
}

/** Smooth filled sparkline — cubic segments through sample midpoints. */
private fun DrawScope.drawSeries(history: List<Double>, color: Color) {
    if (history.size < 2) return
    var max = 1024.0 * 8.0   // keep small idle values readable
    for (v in history) if (v > max) max = v

    val step = size.width / (history.size - 1)
    fun yOf(v: Double): Float =
        (size.height - ((v / max) * (size.height - 6)).toFloat() - 3f)
            .coerceIn(0f, size.height)

    val line = Path()
    line.moveTo(0f, yOf(history[0]))
    for (i in 1 until history.size) {
        val x0 = (i - 1) * step
        val x1 = i * step
        val midX = (x0 + x1) / 2f
        val y0 = yOf(history[i - 1])
        val y1 = yOf(history[i])
        line.cubicTo(midX, y0, midX, y1, x1, y1)
    }

    // Fill under the line.
    val fill = Path().apply {
        addPath(line)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(
        fill,
        brush = Brush.verticalGradient(
            0f to color.copy(alpha = 0.32f),
            1f to color.copy(alpha = 0.04f)
        )
    )
    drawPath(line, color.copy(alpha = 0.95f), style = Stroke(2.dp.toPx()))
}

private const val BURST_PARTICLES = 14

private fun pushSample(history: MutableList<Double>, value: Double) {
    for (i in 0 until history.size - 1) {
        history[i] = history[i + 1]
    }
    history[history.size - 1] = value
}

@Composable
private fun statusLabel(stats: TunnelStats, running: Boolean): String {
    if (!running && stats.status != "disconnecting") return stringResource(R.string.status_ready)
    return when (stats.status) {
        "connected" -> stringResource(R.string.status_connected_in, stats.connectDurationMs / 1000.0)
        "connecting" -> stringResource(R.string.status_connecting)
        "reconnecting" -> stringResource(R.string.status_reconnecting)
        "disconnecting", "exiting" -> stringResource(R.string.status_disconnecting)
        else -> stringResource(R.string.status_ready)
    }
}

private fun ipLabel(stats: TunnelStats): String {
    if (stats.status != "connected") return ""
    val flag = Region.flagForCode(stats.egressRegion)
    return when {
        stats.publicIp != "-" && stats.publicIp.isNotEmpty() -> "$flag ${stats.publicIp}"
        flag.isNotEmpty() -> "$flag ${stats.egressRegion}"
        else -> stats.egressRegion
    }
}
