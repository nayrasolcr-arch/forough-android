package io.github.nayrasolcr.forough.ui

import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import android.view.HapticFeedbackConstants
import android.view.View
import io.github.nayrasolcr.forough.ui.theme.ForoughGold

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
