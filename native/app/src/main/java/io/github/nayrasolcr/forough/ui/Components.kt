package io.github.nayrasolcr.forough.ui

import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import android.view.HapticFeedbackConstants
import android.view.View
import io.github.nayrasolcr.forough.ui.theme.ForoughGold
import kotlin.math.PI
import kotlin.math.sin

/**
 * Shared UI building blocks — the motion + touch language of the client:
 * springy press feedback, haptics, staggered entrance, section cards.
 */

/**
 * Haptics — callable from ANY context (compositions, onClick handlers,
 * coroutines). Bound to the activity's decor view once per composition;
 * honours the user's "haptic feedback" setting via [setEnabled].
 */
object Haptics {
    @Volatile private var enabled: Boolean = true
    @Volatile private var view: View? = null

    /** Wire to the window decor view — call once from the app shell. */
    fun bind(v: View?) {
        view = v
    }

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    /** Light tick — small taps, toggles, menu picks. */
    fun tap() {
        perform(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** Stronger double-tick feel — connect / disconnect / big confirmations. */
    fun confirm() {
        perform(HapticFeedbackConstants.LONG_PRESS)
    }

    private fun perform(constant: Int) {
        if (!enabled) return
        try {
            view?.performHapticFeedback(constant)
        } catch (_: Exception) {
        }
    }
}

/**
 * Springy press scale driven by an interaction source — wire it into any
 * Button/clickable via its own interactionSource and the element squishes
 * softly under the finger (the "alive" feel).
 */
@Composable
fun pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.94f
): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pressScale"
    )
    return scale
}

/**
 * Staggered entrance: content appears with a slight upward drift + fade,
 * delayed by its index — the "polished app" feel on every screen.
 */
@Composable
fun StaggeredItem(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(360, delayMillis = (index * 55).coerceAtMost(440), easing = EaseOutBack),
        label = "stagger$index"
    )
    Column(
        modifier = modifier.graphicsLayer {
            alpha = progress.coerceIn(0f, 1f)
            translationY = (1f - progress) * 36f
        }
    ) {
        content()
    }
}

/** Section card with title + icon — groups settings and details blocks. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                if (icon != null) {
                    Icon(
                        icon, contentDescription = null,
                        tint = ForoughGold,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = ForoughGold
                )
            }
            content()
        }
    }
}

/** Soft breathing pulse — used behind the connect button while connecting. */
@Composable
fun breathingPulse(target: Float, periodMs: Int = 900): Float {
    val transition = rememberInfiniteTransition(label = "breath")
    val value by transition.animateFloat(
        initialValue = 1f,
        targetValue = target,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathValue"
    )
    return value
}

/**
 * AuroraBackground — three drifting color fields (gold / green / azure)
 * painted as huge soft radial gradients on a Canvas. This is the v2.2.0
 * "more graphics" layer: the main screen glows like a living aurora —
 * barely visible while idle, luminous while connected. Pure Canvas, no
 * bitmap allocations, so it holds 60fps on old devices.
 */
@Composable
fun AuroraBackground(active: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auroraPhase"
    )
    val glow by animateFloatAsState(
        targetValue = if (active) 1f else 0.45f,
        animationSpec = tween(900),
        label = "auroraGlow"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val s = glow * 0.16f
        if (s <= 0.002f) return@Canvas

        // Field 1 — warm gold, upper third, slow horizontal drift.
        val c1 = Offset(
            w * (0.30f + 0.16f * sin(phase)),
            h * (0.16f + 0.05f * sin(phase * 0.7f + 1.1f))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(ForoughGold.copy(alpha = s), Color.Transparent),
                center = c1,
                radius = w * 0.75f
            ),
            radius = w * 0.75f,
            center = c1
        )

        // Field 2 — connected-green, lower half, counter-drift.
        val c2 = Offset(
            w * (0.78f + 0.14f * sin(phase * 0.83f + 2.2f)),
            h * (0.66f + 0.07f * sin(phase * 0.6f + 0.4f))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF35C77B).copy(alpha = s * 0.9f), Color.Transparent),
                center = c2,
                radius = w * 0.8f
            ),
            radius = w * 0.8f,
            center = c2
        )

        // Field 3 — deep azure, middle, vertical breathing.
        val c3 = Offset(
            w * (0.5f + 0.2f * sin(phase * 0.55f + 4.0f)),
            h * (0.42f + 0.1f * sin(phase * 0.9f + 2.8f))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF3E7BD6).copy(alpha = s * 0.7f), Color.Transparent),
                center = c3,
                radius = w * 0.85f
            ),
            radius = w * 0.85f,
            center = c3
        )

        // Field 4 (v2.3.0) — violet, sweeping across the lower third with a
        // faster period so the aurora never looks static; very low alpha so
        // it reads as depth, not noise.
        val c4 = Offset(
            w * (0.22f + 0.24f * sin(phase * 1.21f + 3.6f)),
            h * (0.86f + 0.05f * sin(phase * 0.47f + 5.1f))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF8A63D2).copy(alpha = s * 0.55f), Color.Transparent),
                center = c4,
                radius = w * 0.7f
            ),
            radius = w * 0.7f,
            center = c4
        )
    }
}

/**
 * OdometerFloat — smooth numeric count animation. The displayed value
 * glides to each new target instead of jumping, so live counters (speeds,
 * totals) read like a physical gauge. Snaps instantly to zero so idle
 * resets don't linger.
 */
@Composable
fun odometerFloat(target: Float, label: String): Float {
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = if (target <= 0f) {
            snap()
        } else {
            tween(420, easing = FastOutSlowInEasing)
        },
        label = label
    )
    return animated
}
