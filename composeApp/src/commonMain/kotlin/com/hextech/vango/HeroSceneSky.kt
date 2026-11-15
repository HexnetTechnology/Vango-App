package com.hextech.vango

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Fixed star field (normalized). Avoid left clock badge band (x≲0.32, y≲0.14)
 * and right weather pill (x≳0.78, y≲0.12). Size tier: 0=tiny, 1=med, 2=bright.
 */
internal data class HeroStar(
    val x: Float,
    val y: Float,
    val tier: Int,
    val cool: Boolean,
)

internal val HeroStarField: List<HeroStar> = listOf(
    // Upper mid / right sky
    HeroStar(0.38f, 0.10f, 1, true),
    HeroStar(0.42f, 0.16f, 0, false),
    HeroStar(0.46f, 0.08f, 2, true),
    HeroStar(0.49f, 0.14f, 0, true),
    HeroStar(0.53f, 0.07f, 1, false),
    HeroStar(0.56f, 0.15f, 0, true),
    HeroStar(0.59f, 0.10f, 1, true),
    HeroStar(0.62f, 0.18f, 0, false),
    HeroStar(0.65f, 0.09f, 2, false),
    HeroStar(0.68f, 0.14f, 0, true),
    HeroStar(0.71f, 0.07f, 1, true),
    HeroStar(0.74f, 0.16f, 0, false),
    HeroStar(0.77f, 0.11f, 1, true),
    HeroStar(0.80f, 0.19f, 0, true),
    HeroStar(0.84f, 0.13f, 1, false),
    HeroStar(0.88f, 0.20f, 0, true),
    HeroStar(0.91f, 0.15f, 1, true),
    HeroStar(0.94f, 0.22f, 0, false),
    // Lower sky band (above mountains / haze)
    HeroStar(0.40f, 0.22f, 0, true),
    HeroStar(0.48f, 0.24f, 0, false),
    HeroStar(0.55f, 0.21f, 1, true),
    HeroStar(0.63f, 0.25f, 0, true),
    HeroStar(0.70f, 0.23f, 0, false),
    HeroStar(0.78f, 0.26f, 0, true),
    HeroStar(0.86f, 0.24f, 1, false),
    // Sparse left of mid (below clock band)
    HeroStar(0.34f, 0.18f, 0, true),
    HeroStar(0.36f, 0.25f, 0, false),
    HeroStar(0.44f, 0.20f, 1, true),
)

/** Keep old name for any external refs. */
internal val HeroStarPositions: List<Pair<Float, Float>> =
    HeroStarField.map { it.x to it.y }

internal fun DrawScope.drawHeroCelestial(
    palette: HeroScenePalette,
    sky: HeroSkySnapshot,
    w: Float,
    h: Float,
    sunX: Float,
    sunY: Float,
    moonX: Float,
    moonY: Float,
    starVis: Float,
    fxT: Float,
    animateMotion: Boolean,
    diskAlpha: Float,
    glowAlpha: Float,
) {
    val r = min(w, h) * 0.052f
    val twinkleT = if (animateMotion) fxT else 0f
    val starBase = if (palette.star.alpha > 0.01f) palette.star.alpha else 0.82f

    // Shared slow field rotation for stars (not independent wander)
    val rot = if (animateMotion) twinkleT * 0.000035f else 0f
    if (starVis > 0.02f) {
        val pivotX = w * 0.55f
        val pivotY = h * 0.20f
        val c = cos(rot)
        val s = sin(rot)
        HeroStarField.forEachIndexed { i, star ->
            val cx0 = w * star.x
            val cy0 = h * star.y
            val dx = cx0 - pivotX
            val dy = cy0 - pivotY
            val x = pivotX + dx * c - dy * s
            val y = pivotY + dx * s + dy * c
            val tw = 0.48f + 0.52f * sin(twinkleT * (0.32f + i * 0.06f) + i * 1.7f)
            val tierMul = when (star.tier) {
                2 -> 1f
                1 -> 0.78f
                else -> 0.52f
            }
            val a = (starVis * tw * starBase * tierMul).coerceIn(0f, 1f)
            if (a > 0.02f) {
                val rad = when (star.tier) {
                    2 -> 1.35.dp.toPx()
                    1 -> 1.05.dp.toPx()
                    else -> 0.75.dp.toPx()
                }
                val col = if (star.cool) {
                    Color(0xFFE8F0FF).copy(alpha = a)
                } else {
                    Color(0xFFFFF6E8).copy(alpha = a)
                }
                drawCircle(color = col, radius = rad, center = Offset(x, y))
                // Soft spark on brighter stars
                if (star.tier >= 1 && a > 0.35f) {
                    drawCircle(
                        color = col.copy(alpha = a * 0.35f),
                        radius = rad * 2.2f,
                        center = Offset(x, y),
                    )
                }
            }
        }
        // Occasional shooting stars at dusk / night
        if (animateMotion && starVis > 0.22f) {
            drawHeroShootingStars(w, h, twinkleT, starVis)
        }
    }

    val sunAbove = sky.sun?.aboveHorizon == true && sunY < 0.64f
    if (sunAbove && (diskAlpha > 0.02f || glowAlpha > 0.02f)) {
        drawSoftSun(
            center = Offset(w * sunX, h * sunY),
            radius = r,
            diskAlpha = diskAlpha,
            glowAlpha = glowAlpha,
            warm = sky.period == HeroSkyPeriod.Dawn || sky.period == HeroSkyPeriod.Dusk,
        )
    }

    val moon = sky.moon
    val moonAbove = moon != null && moon.pos.aboveHorizon && moonY < 0.64f && moon.illumination > 0.03f
    if (moonAbove && moon != null && diskAlpha > 0.02f) {
        val mc = Offset(w * moonX, h * moonY)
        // Glow only when nearly full — crescent must not leave a tonal disk on the sky
        if (glowAlpha > 0.04f && moon.illumination > 0.82f) {
            val glowR = r * 1.6f
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to Color.White.copy(alpha = 0.08f * glowAlpha),
                        0.60f to Color.White.copy(alpha = 0.02f * glowAlpha),
                        1.0f to Color.Transparent,
                    ),
                    center = mc,
                    radius = glowR,
                ),
                radius = glowR,
                center = mc,
            )
        }
        drawMoonPhase(
            center = mc,
            radius = r * 0.92f,
            illumination = moon.illumination,
            waxing = moon.waxing,
            lit = palette.moon.copy(alpha = diskAlpha),
        )
    }
}

/**
 * Sparse dusk/night meteors — short bright streaks, not a shower.
 * Two staggered streams so sometimes minutes pass with none.
 */
private fun DrawScope.drawHeroShootingStars(
    w: Float,
    h: Float,
    tSec: Float,
    starVis: Float,
) {
    // Stream A ~22s, stream B ~31s — skip some cycles
    drawOneShootingStar(w, h, tSec, stream = 0, cycle = 22.5f, active = 0.55f, starVis = starVis)
    drawOneShootingStar(w, h, tSec, stream = 1, cycle = 31.0f, active = 0.48f, starVis = starVis)
}

private fun DrawScope.drawOneShootingStar(
    w: Float,
    h: Float,
    tSec: Float,
    stream: Int,
    cycle: Float,
    active: Float,
    starVis: Float,
) {
    val phaseOff = 4.7f + stream * 11.3f
    val local = ((tSec + phaseOff) % cycle + cycle) % cycle
    if (local > active) return
    val cycleIndex = floor((tSec + phaseOff) / cycle).toInt()
    val skip = abs((cycleIndex * 1103515245 + stream * 9973) % 5)
    // Often skip — “sometimes” not constant
    if (skip == 0 || skip == 2 || skip == 4) return

    val p = (local / active).coerceIn(0f, 1f)
    // Bright head early, fade out
    val fade = when {
        p < 0.12f -> p / 0.12f
        p > 0.55f -> ((1f - p) / 0.45f).coerceIn(0f, 1f)
        else -> 1f
    } * starVis
    if (fade < 0.04f) return

    val seed = abs(cycleIndex * 17 + stream * 41)
    val x0 = w * (0.35f + (seed % 45) / 100f)
    val y0 = h * (0.05f + (seed % 17) / 100f)
    val len = w * (0.10f + (seed % 9) / 100f)
    val ang = (28f + (seed % 20)) * (PI.toFloat() / 180f) // down-right
    val travel = len * (0.35f + p * 0.95f)
    val hx = x0 + cos(ang) * travel
    val hy = y0 + sin(ang) * travel
    val tx = hx - cos(ang) * len * (0.55f + p * 0.25f)
    val ty = hy - sin(ang) * len * (0.55f + p * 0.25f)

    // Soft trail
    drawLine(
        color = Color(0xFFEAF2FF).copy(alpha = fade * 0.28f),
        start = Offset(tx, ty),
        end = Offset(hx, hy),
        strokeWidth = 2.2.dp.toPx(),
        cap = StrokeCap.Round,
    )
    drawLine(
        color = Color.White.copy(alpha = fade * 0.85f),
        start = Offset(lerpFloat(tx, hx, 0.45f), lerpFloat(ty, hy, 0.45f)),
        end = Offset(hx, hy),
        strokeWidth = 1.35.dp.toPx(),
        cap = StrokeCap.Round,
    )
    // Hot head
    drawCircle(
        color = Color.White.copy(alpha = fade),
        radius = 1.6.dp.toPx(),
        center = Offset(hx, hy),
    )
}

/**
 * Soft sun: cream core → warm yellow rim; glow is a radial fade (no hard halo ring).
 */
internal fun DrawScope.drawSoftSun(
    center: Offset,
    radius: Float,
    diskAlpha: Float,
    glowAlpha: Float,
    warm: Boolean,
) {
    val glowR = radius * (if (warm) 3.6f else 3.2f)
    if (glowAlpha > 0.02f) {
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to Color(0xFFFFE8A0).copy(alpha = 0.42f * glowAlpha),
                    0.28f to Color(0xFFFFD070).copy(alpha = 0.18f * glowAlpha),
                    0.55f to Color(0xFFFFC060).copy(alpha = 0.06f * glowAlpha),
                    1.00f to Color.Transparent,
                ),
                center = center,
                radius = glowR,
            ),
            radius = glowR,
            center = center,
        )
    }
    if (diskAlpha <= 0.02f) return
    val diskR = radius * (if (warm) 0.95f else 1f)
    // Soft disk: open cream center → warm yellow edge (no stroked ring)
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.00f to Color(0xFFFFF8E8).copy(alpha = diskAlpha),
                0.55f to Color(0xFFFFE8B0).copy(alpha = diskAlpha),
                0.88f to Color(0xFFFFD060).copy(alpha = diskAlpha * 0.95f),
                1.00f to Color(0xFFFFC040).copy(alpha = 0f),
            ),
            center = center,
            radius = diskR,
        ),
        radius = diskR,
        center = center,
    )
}

/**
 * Lit moon only — unlit side stays transparent (no filled “shade disk” on the sky).
 * Illumination 0=new … 1=full; waxing grows from the right.
 */
internal fun DrawScope.drawMoonPhase(
    center: Offset,
    radius: Float,
    illumination: Float,
    waxing: Boolean,
    lit: Color,
) {
    val illum = illumination.coerceIn(0f, 1f)
    if (illum < 0.02f) return
    if (illum > 0.98f) {
        drawCircle(lit, radius = radius, center = center)
        return
    }
    val dir = if (waxing) 1f else -1f
    val offset = dir * radius * (1.15f - illum * 2.05f)
    val disk = Path().apply {
        addOval(
            Rect(
                left = center.x - radius,
                top = center.y - radius,
                right = center.x + radius,
                bottom = center.y + radius,
            ),
        )
    }
    val cut = Path().apply {
        addOval(
            Rect(
                left = center.x + offset - radius * 0.98f,
                top = center.y - radius * 0.98f,
                right = center.x + offset + radius * 0.98f,
                bottom = center.y + radius * 0.98f,
            ),
        )
    }
    // Crescent / gibbous = moon disk ∩ outside terminator cut — no opaque backside
    clipPath(disk) {
        clipPath(cut, ClipOp.Difference) {
            drawCircle(lit, radius = radius, center = center)
        }
    }
}
