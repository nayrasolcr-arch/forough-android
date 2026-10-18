package io.github.nayrasolcr.forough.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.nayrasolcr.forough.ui.theme.BrushAccentBlue
import io.github.nayrasolcr.forough.ui.theme.BrushFire
import io.github.nayrasolcr.forough.ui.theme.BrushGold
import io.github.nayrasolcr.forough.ui.theme.BrushGreen
import io.github.nayrasolcr.forough.ui.theme.BrushViolet

/**
 * ForoughIcons (v2.4.0) — the hand-drawn gradient icon set.
 *
 * Every icon is a real vector painted on a Canvas: a soft chromatic glow
 * underneath, then the glyph stroked with one of the brand gradient brushes
 * (cyan→blue, violet→magenta, amber→orange, lime→emerald, orange→rose).
 * Replaces the flat single-color Material icons across the shell so the
 * whole app carries one vivid, coherent chromatic language.
 *
 * All glyphs live in a normalized 24×24 coordinate space scaled to the
 * target size, so strokes stay proportional at any density.
 */

// Gradient assignment per glyph — stable identities across the app.
object ForoughBrushes {
    val scan = BrushAccentBlue
    val speed = BrushFire
    val diagnostics = BrushGreen
    val settings = BrushViolet
    val share = BrushGold
    val logs = BrushAccentBlue
    val info = BrushGreen
    val power = BrushFire
    val globe = BrushAccentBlue
    val flame = BrushFire
    val shield = BrushGold
    val rescue = BrushViolet
}

// Halo colors — the tint of the soft radial glow painted under each glyph.
object ForoughGlows {
    val scan = Color(0xFF38E1FF)
    val speed = Color(0xFFFF7A3D)
    val diagnostics = Color(0xFF17C97B)
    val settings = Color(0xFF8B5CF6)
    val share = Color(0xFFFFB13D)
    val logs = Color(0xFF38E1FF)
    val info = Color(0xFF17C97B)
    val power = Color(0xFFFF5470)
    val globe = Color(0xFF3D6BFF)
    val flame = Color(0xFFFF5470)
    val shield = Color(0xFFFFB13D)
    val rescue = Color(0xFFE64CC8)
}

/**
 * Soft radial glow + scaled 24×24 canvas host shared by all glyphs.
 * When `halo = false` the glyph paints clean — used inside filled
 * surfaces (the power button) where a second glow would read as a
 * misplaced blob overlapping the button's own gradient.
 */
@Composable
private fun GlyphHost(
    size: Dp,
    glowColor: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    halo: Boolean = true,
    draw: DrawScope.() -> Unit
) {
    val scoped = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else {
        modifier
    }
    Box(modifier = scoped.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val r = this.size.minDimension
            if (halo) {
                // Two-stop radial halo — cheap, GPU-friendly, no blur passes.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = 0.34f),
                            glowColor.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = r * 0.5f
                    ),
                    radius = r * 0.5f,
                    center = center
                )
            }
            val scale = r / 24f
            withTransformScaled(scale) { draw() }
        }
    }
}

private fun DrawScope.withTransformScaled(scale: Float, block: DrawScope.() -> Unit) {
    drawContext.transform.scale(scale, scale, pivot = Offset(0f, 0f))
    block()
    drawContext.transform.scale(1f / scale, 1f / scale, pivot = Offset(0f, 0f))
}

private val StrokeWidth = 1.9f

private fun DrawScope.stroke(brush: Brush, path: Path, width: Float = StrokeWidth) {
    drawPath(
        path = path, brush = brush,
        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

// ---------------------------------------------------------------------------
// Radar / scanner — concentric arcs + sweep needle.
// ---------------------------------------------------------------------------

@Composable
fun ScanIcon(size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    GlyphHost(size, ForoughGlows.scan, modifier, contentDescription) {
        val b = ForoughBrushes.scan
        val c = Offset(12f, 12f)
        drawCircle(brush = b, radius = 8.2f, center = c, style = Stroke(1.2f))
        drawCircle(brush = b, radius = 4.6f, center = c, style = Stroke(1.2f))
        drawCircle(brush = b, radius = 1.4f, center = c)
        val needle = Path().apply {
            moveTo(12f, 12f); lineTo(18.2f, 7.6f)
        }
        stroke(b, needle, 1.7f)
        // Sweep wedge.
        val wedge = Path().apply {
            moveTo(12f, 12f)
            lineTo(12f + 8f * 0.42f, 12f - 8f * 0.91f)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    12f - 8f, 12f - 8f, 12f + 8f, 12f + 8f
                ),
                startAngleDegrees = -65f, sweepAngleDegrees = 40f, forceMoveTo = false
            )
            close()
        }
        drawPath(wedge, brush = b, alpha = 0.35f)
    }
}

// ---------------------------------------------------------------------------
// Bolt / speed — lightning glyph with motion trails.
// ---------------------------------------------------------------------------

@Composable
fun BoltIcon(size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    GlyphHost(size, ForoughGlows.speed, modifier, contentDescription) {
        val b = ForoughBrushes.speed
        val bolt = Path().apply {
            moveTo(13.2f, 2.6f)
            lineTo(6.4f, 13.4f)
            lineTo(11.2f, 13.4f)
            lineTo(10.2f, 21.4f)
            lineTo(17.6f, 10.2f)
            lineTo(12.6f, 10.2f)
            close()
        }
        drawPath(bolt, brush = b, alpha = 0.28f)
        stroke(b, bolt, 1.6f)
        val trail = Path().apply {
            moveTo(3.2f, 9.4f); lineTo(6.8f, 9.4f)
            moveTo(2.6f, 13.6f); lineTo(5.4f, 13.6f)
            moveTo(18.6f, 16.2f); lineTo(21.4f, 16.2f)
        }
        stroke(b, trail, 1.4f)
    }
}

// ---------------------------------------------------------------------------
// Pulse / diagnostics — heartbeat line inside a rounded frame.
// ---------------------------------------------------------------------------

@Composable
fun PulseIcon(size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    GlyphHost(size, ForoughGlows.diagnostics, modifier, contentDescription) {
        val b = ForoughBrushes.diagnostics
        val frame = Path().apply {
            moveTo(3.2f, 8.4f); lineTo(3.2f, 5.6f)
            cubicTo(3.2f, 4.3f, 4.3f, 3.2f, 5.6f, 3.2f)
            lineTo(8.4f, 3.2f)
            moveTo(15.6f, 3.2f); lineTo(18.4f, 3.2f)
            cubicTo(19.7f, 3.2f, 20.8f, 4.3f, 20.8f, 5.6f)
            lineTo(20.8f, 8.4f)
            moveTo(20.8f, 15.6f); lineTo(20.8f, 18.4f)
            cubicTo(20.8f, 19.7f, 19.7f, 20.8f, 18.4f, 20.8f)
            lineTo(15.6f, 20.8f)
            moveTo(8.4f, 20.8f); lineTo(5.6f, 20.8f)
            cubicTo(4.3f, 20.8f, 3.2f, 19.7f, 3.2f, 18.4f)
            lineTo(3.2f, 15.6f)
        }
        stroke(b, frame, 1.6f)
        val pulse = Path().apply {
            moveTo(4.6f, 12.4f)
            lineTo(8.2f, 12.4f)
            lineTo(10.0f, 8.0f)
            lineTo(12.6f, 16.4f)
            lineTo(14.6f, 12.4f)
            lineTo(19.4f, 12.4f)
        }
        stroke(b, pulse, 1.7f)
    }
}

// ---------------------------------------------------------------------------
// Sliders / settings — three gradient rails with knob dots.
// ---------------------------------------------------------------------------

@Composable
fun SlidersIcon(size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    GlyphHost(size, ForoughGlows.settings, modifier, contentDescription) {
        val b = ForoughBrushes.settings
        val rails = Path().apply {
            moveTo(4f, 6.6f); lineTo(20f, 6.6f)
            moveTo(4f, 12f); lineTo(20f, 12f)
            moveTo(4f, 17.4f); lineTo(20f, 17.4f)
        }
        stroke(b, rails, 1.6f)
        drawCircle(brush = b, radius = 2.1f, center = Offset(14.6f, 6.6f))
        drawCircle(brush = b, radius = 2.1f, center = Offset(8.6f, 12f))
        drawCircle(brush = b, radius = 2.1f, center = Offset(16.2f, 17.4f))
    }
}

// ---------------------------------------------------------------------------
// Share nodes — three orbs connected by gradient links.
// ---------------------------------------------------------------------------

@Composable
fun ShareNodesIcon(size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    GlyphHost(size, ForoughGlows.share, modifier, contentDescription) {
        val b = ForoughBrushes.share
        val links = Path().apply {
            moveTo(8.6f, 10.6f); lineTo(15.2f, 7.4f)
            moveTo(8.6f, 13.4f); lineTo(15.2f, 16.6f)
        }
        stroke(b, links, 1.6f)
        drawCircle(brush = b, radius = 2.5f, center = Offset(6.2f, 12f))
        drawCircle(brush = b, radius = 2.5f, center = Offset(17.6f, 6f))
        drawCircle(brush = b, radius = 2.5f, center = Offset(17.6f, 18f))
    }
}

// ---------------------------------------------------------------------------
// Scroll / logs — document sheet with written lines.
// ---------------------------------------------------------------------------

@Composable
fun ScrollIcon(size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    GlyphHost(size, ForoughGlows.logs, modifier, contentDescription) {
        val b = ForoughBrushes.logs
        val sheet = Path().apply {
            moveTo(6f, 3.4f); lineTo(15.4f, 3.4f); lineTo(19f, 7f)
            lineTo(19f, 20.6f); lineTo(6f, 20.6f); close()
            moveTo(15.4f, 3.4f); lineTo(15.4f, 7f); lineTo(19f, 7f)
        }
        stroke(b, sheet, 1.6f)
        val lines = Path().apply {
            moveTo(8.8f, 10.6f); lineTo(16.2f, 10.6f)
            moveTo(8.8f, 13.6f); lineTo(16.2f, 13.6f)
            moveTo(8.8f, 16.6f); lineTo(13.2f, 16.6f)
        }
        stroke(b, lines, 1.3f)
    }
}

// ---------------------------------------------------------------------------
// Info — gradient ring + i dot.
// ---------------------------------------------------------------------------

@Composable
fun InfoOrbIcon(size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    GlyphHost(size, ForoughGlows.info, modifier, contentDescription) {
        val b = ForoughBrushes.info
        drawCircle(brush = b, radius = 8.6f, center = Offset(12f, 12f), style = Stroke(1.7f))
        drawCircle(brush = b, radius = 1.25f, center = Offset(12f, 7.8f))
        val stem = Path().apply {
            moveTo(12f, 11.2f); lineTo(12f, 16.8f)
        }
        stroke(b, stem, 1.9f)
    }
}

// ---------------------------------------------------------------------------
// Power — the main button glyph: ring + bolt core (energy, not just "off").
// ---------------------------------------------------------------------------

@Composable
fun PowerBoltIcon(
    size: Dp,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    contentDescription: String? = null,
    halo: Boolean = true
) {
    GlyphHost(size, ForoughGlows.power, modifier, contentDescription, halo) {
        val b = tint?.let { Brush.verticalGradient(listOf(it, it)) } ?: ForoughBrushes.power
        drawArc(
            brush = b, startAngle = 52f, sweepAngle = 256f, useCenter = false,
            topLeft = Offset(3.6f, 3.6f),
            size = androidx.compose.ui.geometry.Size(16.8f, 16.8f),
            style = Stroke(width = 2.4f, cap = StrokeCap.Round)
        )
        val bolt = Path().apply {
            moveTo(12.9f, 6.8f)
            lineTo(8.9f, 12.9f)
            lineTo(11.6f, 12.9f)
            lineTo(11.1f, 17.2f)
            lineTo(15.1f, 11.1f)
            lineTo(12.4f, 11.1f)
            close()
        }
        drawPath(bolt, brush = b)
    }
}

// ---------------------------------------------------------------------------
// Globe — meridian sphere.
// ---------------------------------------------------------------------------

@Composable
fun GlobeIcon(size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    GlyphHost(size, ForoughGlows.globe, modifier, contentDescription) {
        val b = ForoughBrushes.globe
        drawCircle(brush = b, radius = 8.4f, center = Offset(12f, 12f), style = Stroke(1.5f))
        val equator = Path().apply {
            moveTo(3.6f, 12f); lineTo(20.4f, 12f)
        }
        stroke(b, equator, 1.3f)
        // Meridian ellipse.
        val meridian = Path().apply {
            moveTo(12f, 3.6f)
            cubicTo(8.2f, 6.2f, 8.2f, 17.8f, 12f, 20.4f)
            cubicTo(15.8f, 17.8f, 15.8f, 6.2f, 12f, 3.6f)
        }
        stroke(b, meridian, 1.3f)
    }
}

// ---------------------------------------------------------------------------
// Flame — for the EXTREME mode badge.
// ---------------------------------------------------------------------------

@Composable
fun FlameIcon(size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    GlyphHost(size, ForoughGlows.flame, modifier, contentDescription) {
        val b = ForoughBrushes.flame
        val flame = Path().apply {
            moveTo(12f, 2.8f)
            cubicTo(14.2f, 6.0f, 18.6f, 8.4f, 18.6f, 13.2f)
            cubicTo(18.6f, 17.4f, 15.8f, 20.8f, 12f, 20.8f)
            cubicTo(8.2f, 20.8f, 5.4f, 17.4f, 5.4f, 13.2f)
            cubicTo(5.4f, 10.4f, 7.0f, 8.6f, 8.4f, 7.0f)
            cubicTo(8.6f, 9.4f, 9.6f, 10.6f, 10.8f, 11.2f)
            cubicTo(10.8f, 8.4f, 11.2f, 5.2f, 12f, 2.8f)
            close()
        }
        drawPath(flame, brush = b, alpha = 0.30f)
        stroke(b, flame, 1.6f)
        val core = Path().apply {
            moveTo(12f, 20.8f)
            cubicTo(9.9f, 20.8f, 8.6f, 19.0f, 8.6f, 17.0f)
            cubicTo(8.6f, 15.2f, 10.2f, 14.0f, 12f, 12.6f)
            cubicTo(13.8f, 14.0f, 15.4f, 15.2f, 15.4f, 17.0f)
            cubicTo(15.4f, 19.0f, 14.1f, 20.8f, 12f, 20.8f)
            close()
        }
        drawPath(core, brush = b, alpha = 0.55f)
    }
}

// ---------------------------------------------------------------------------
// Shield bolt — brand mark (about / header).
// ---------------------------------------------------------------------------

@Composable
fun ShieldBoltIcon(size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    GlyphHost(size, ForoughGlows.shield, modifier, contentDescription) {
        val b = ForoughBrushes.shield
        val shield = Path().apply {
            moveTo(12f, 2.8f)
            lineTo(19.4f, 5.8f)
            lineTo(19.4f, 11.6f)
            cubicTo(19.4f, 16.4f, 16.4f, 19.8f, 12f, 21.2f)
            cubicTo(7.6f, 19.8f, 4.6f, 16.4f, 4.6f, 11.6f)
            lineTo(4.6f, 5.8f)
            close()
        }
        drawPath(shield, brush = b, alpha = 0.26f)
        stroke(b, shield, 1.7f)
        val bolt = Path().apply {
            moveTo(12.8f, 7.4f)
            lineTo(9.6f, 12.4f)
            lineTo(11.7f, 12.4f)
            lineTo(11.3f, 16.4f)
            lineTo(14.5f, 11.4f)
            lineTo(12.4f, 11.4f)
            close()
        }
        drawPath(bolt, brush = b)
    }
}

// ---------------------------------------------------------------------------
// Life buoy — rescue engine badge.
// ---------------------------------------------------------------------------

@Composable
fun RescueIcon(size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    GlyphHost(size, ForoughGlows.rescue, modifier, contentDescription) {
        val b = ForoughBrushes.rescue
        drawCircle(brush = b, radius = 8.4f, center = Offset(12f, 12f), style = Stroke(1.7f))
        drawCircle(brush = b, radius = 3.6f, center = Offset(12f, 12f), style = Stroke(1.4f))
        val spokes = Path().apply {
            moveTo(12f, 3.6f); lineTo(12f, 8.4f)
            moveTo(12f, 15.6f); lineTo(12f, 20.4f)
            moveTo(3.6f, 12f); lineTo(8.4f, 12f)
            moveTo(15.6f, 12f); lineTo(20.4f, 12f)
        }
        stroke(b, spokes, 1.5f)
    }
}
