package com.hextech.vango

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

private val CrabShell = Color(0xFFC45A3A)
private val CrabShellDark = Color(0xFF8B3A28)
private val CrabClaw = Color(0xFFD47850)
private val TurtleShell = Color(0xFF4A6B3A)
private val TurtleShellLite = Color(0xFF6A8B52)
private val TurtleSkin = Color(0xFF8FA86A)
private val SeaTurtleShell = Color(0xFF3A5A48)
private val SeaTurtleShellLite = Color(0xFF5A7A62)
private val SeaTurtleSkin = Color(0xFF6A8870)

private enum class BeachCritterKind { Crab, LandTurtle, SeaTurtle }

/**
 * Occasional shore fauna: crabs / land turtles walk the wet–dry sand;
 * sea turtles sometimes emerge from the water and crawl onto the beach.
 * Draw after park (on top of shore), before vehicle.
 */
internal fun DrawScope.drawHeroBeachFauna(
    w: Float,
    h: Float,
    sceneTSec: Float,
    animate: Boolean,
    period: HeroSkyPeriod,
    weather: HeroSkyWeather,
    iceCoverage: Float,
    snowCover: Float,
) {
    if (!animate) return
    if (iceCoverage > 0.18f || snowCover > 0.28f) return
    val weatherMul = when (weather) {
        HeroSkyWeather.Rain, HeroSkyWeather.Snow -> 0.15f
        HeroSkyWeather.Fog -> 0.45f
        HeroSkyWeather.Overcast -> 0.80f
        HeroSkyWeather.PartlyCloudy -> 0.95f
        HeroSkyWeather.Clear -> 1f
    }
    if (weatherMul < 0.12f) return
    val periodMul = when (period) {
        HeroSkyPeriod.Night -> 0.22f
        HeroSkyPeriod.Dusk -> 0.70f
        HeroSkyPeriod.Dawn -> 0.90f
        HeroSkyPeriod.Day -> 1f
    }
    val alphaMul = weatherMul * periodMul
    if (alphaMul < 0.10f) return

    val t = sceneTSec.coerceAtLeast(0f)
    // Staggered visit streams so more than one critter can appear over time.
    drawBeachVisit(w, h, t, stream = 0, kindBias = BeachCritterKind.Crab, alphaMul = alphaMul)
    drawBeachVisit(w, h, t, stream = 1, kindBias = BeachCritterKind.LandTurtle, alphaMul = alphaMul)
    drawBeachVisit(w, h, t, stream = 2, kindBias = BeachCritterKind.SeaTurtle, alphaMul = alphaMul)
    drawBeachVisit(w, h, t, stream = 3, kindBias = BeachCritterKind.Crab, alphaMul = alphaMul * 0.92f)
}

private fun DrawScope.drawBeachVisit(
    w: Float,
    h: Float,
    t: Float,
    stream: Int,
    kindBias: BeachCritterKind,
    alphaMul: Float,
) {
    val cycle = 38f + stream * 7.5f
    val activeDur = when (kindBias) {
        BeachCritterKind.Crab -> 11f
        BeachCritterKind.LandTurtle -> 14f
        BeachCritterKind.SeaTurtle -> 16f
    }
    val gap = cycle - activeDur
    if (gap <= 1f) return
    val phaseOff = stream * 11.3f + 3.7f
    val local = ((t + phaseOff) % cycle + cycle) % cycle
    if (local > activeDur) return
    val p = (local / activeDur).coerceIn(0f, 1f)

    // Skip some cycles so appearances feel occasional
    val cycleIndex = floor((t + phaseOff) / cycle).toInt()
    val skipHash = abs((cycleIndex * 1103515245 + stream * 12345) % 7)
    if (skipHash == 0 || skipHash == 3) return

    val kind = when {
        kindBias == BeachCritterKind.SeaTurtle && skipHash == 1 -> BeachCritterKind.Crab
        kindBias == BeachCritterKind.LandTurtle && skipHash == 5 -> BeachCritterKind.Crab
        else -> kindBias
    }

    val lane = 0.20f + ((stream * 0.09f + (cycleIndex % 3) * 0.05f) % 0.32f)
    val x0 = 0.18f + (stream % 2) * 0.06f
    val x1 = 0.52f - (stream % 2) * 0.04f
    val goRight = (cycleIndex + stream) % 2 == 0

    when (kind) {
        BeachCritterKind.Crab -> {
            val fade = beachFade(p, 0.08f, 0.88f)
            if (fade < 0.02f) return
            val u = beachEase(p)
            val xFrac = if (goRight) lerpFloat(x0, x1, u) else lerpFloat(x1, x0, u)
            val shoreY = heroNearShoreY(h, xFrac)
            val wet = heroWetWidthFrac(xFrac)
            val y = shoreY + h * (0.010f + wet * 0.35f + lane * 0.012f)
            val scuttle = sin(local * 9.5f) * h * 0.0022f
            val size = minOf(w, h) * 0.028f
            drawCrab(
                cx = w * xFrac,
                cy = y + scuttle,
                size = size,
                facingRight = goRight,
                alpha = fade * alphaMul,
                walkPhase = local * 10f,
            )
        }
        BeachCritterKind.LandTurtle -> {
            val fade = beachFade(p, 0.10f, 0.86f)
            if (fade < 0.02f) return
            val u = beachEase(p)
            val xFrac = if (goRight) lerpFloat(x0 + 0.02f, x1 - 0.02f, u) else lerpFloat(x1 - 0.02f, x0 + 0.02f, u)
            val shoreY = heroNearShoreY(h, xFrac)
            val wet = heroWetWidthFrac(xFrac)
            val y = shoreY + h * (0.018f + wet * 0.55f + 0.008f)
            val size = minOf(w, h) * 0.036f
            drawLandTurtle(
                cx = w * xFrac,
                cy = y,
                size = size,
                facingRight = goRight,
                alpha = fade * alphaMul,
                walkPhase = local * 4.2f,
            )
        }
        BeachCritterKind.SeaTurtle -> {
            // 0–0.22 emerge from water → 0.22–0.72 beach walk → 0.72–1 return to sea
            val fade = beachFade(p, 0.06f, 0.92f)
            if (fade < 0.02f) return
            val emergeEnd = 0.22f
            val leaveStart = 0.72f
            val xMid = (x0 + x1) * 0.5f + if (goRight) 0.04f else -0.04f
            val shoreY = heroNearShoreY(h, xMid)
            val size = minOf(w, h) * 0.042f
            val (xFrac, y, facingRight, flipperBoost) = when {
                p < emergeEnd -> {
                    val e = (p / emergeEnd).coerceIn(0f, 1f)
                    val ee = beachEase(e)
                    val yInWater = shoreY - h * (0.045f * (1f - ee))
                    val yOnSand = shoreY + h * 0.012f
                    Quadruple(
                        xMid + (if (goRight) -0.03f else 0.03f) * (1f - ee),
                        lerpFloat(yInWater, yOnSand, ee),
                        goRight,
                        1.35f,
                    )
                }
                p < leaveStart -> {
                    val u = ((p - emergeEnd) / (leaveStart - emergeEnd)).coerceIn(0f, 1f)
                    val uu = beachEase(u)
                    val xf = if (goRight) lerpFloat(xMid - 0.02f, xMid + 0.10f, uu)
                    else lerpFloat(xMid + 0.02f, xMid - 0.10f, uu)
                    val sy = heroNearShoreY(h, xf)
                    Quadruple(xf, sy + h * 0.014f, goRight, 1f)
                }
                else -> {
                    val e = ((p - leaveStart) / (1f - leaveStart)).coerceIn(0f, 1f)
                    val ee = beachEase(e)
                    val xf = if (goRight) xMid + 0.10f else xMid - 0.10f
                    val sy = heroNearShoreY(h, xf)
                    val ySand = sy + h * 0.012f
                    val ySea = sy - h * 0.050f
                    Quadruple(xf, lerpFloat(ySand, ySea, ee), !goRight, 1.4f)
                }
            }
            drawSeaTurtle(
                cx = w * xFrac,
                cy = y,
                size = size,
                facingRight = facingRight,
                alpha = fade * alphaMul,
                walkPhase = local * 3.6f,
                flipperBoost = flipperBoost,
                submergedHint = p < emergeEnd * 0.55f || p > leaveStart + 0.35f,
            )
        }
    }
}

private data class Quadruple(
    val a: Float,
    val b: Float,
    val c: Boolean,
    val d: Float,
)

private fun beachFade(p: Float, inEnd: Float, outStart: Float): Float = when {
    p < inEnd -> (p / inEnd).coerceIn(0f, 1f)
    p > outStart -> ((1f - p) / (1f - outStart).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
    else -> 1f
}

private fun beachEase(u: Float): Float {
    val t = u.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun DrawScope.drawCrab(
    cx: Float,
    cy: Float,
    size: Float,
    facingRight: Boolean,
    alpha: Float,
    walkPhase: Float,
) {
    val a = alpha.coerceIn(0f, 1f)
    if (a < 0.02f) return
    val dir = if (facingRight) 1f else -1f
    // Legs
    for (i in 0 until 3) {
        val side = if (i % 2 == 0) 1f else -1f
        val legPhase = walkPhase + i * 0.9f
        val kick = sin(legPhase) * size * 0.18f
        val baseX = cx + dir * size * (0.05f + i * 0.08f)
        val baseY = cy + size * 0.08f
        val tipX = baseX + side * size * 0.55f + kick * 0.3f
        val tipY = baseY + size * 0.42f + abs(sin(legPhase)) * size * 0.08f
        drawLine(
            color = CrabShellDark.copy(alpha = a * 0.85f),
            start = Offset(baseX, baseY),
            end = Offset(tipX, tipY),
            strokeWidth = size * 0.10f,
            cap = StrokeCap.Round,
        )
    }
    // Body
    drawOval(
        color = CrabShell.copy(alpha = a),
        topLeft = Offset(cx - size * 0.55f, cy - size * 0.32f),
        size = Size(size * 1.10f, size * 0.62f),
    )
    drawOval(
        color = CrabShellDark.copy(alpha = a * 0.35f),
        topLeft = Offset(cx - size * 0.35f, cy - size * 0.18f),
        size = Size(size * 0.70f, size * 0.28f),
    )
    // Claws
    val clawY = cy - size * 0.05f
    val clawReach = size * 0.72f
    drawCircle(
        color = CrabClaw.copy(alpha = a),
        radius = size * 0.16f,
        center = Offset(cx + dir * clawReach, clawY - size * 0.12f),
    )
    drawCircle(
        color = CrabClaw.copy(alpha = a),
        radius = size * 0.14f,
        center = Offset(cx + dir * clawReach * 0.78f, clawY + size * 0.18f),
    )
    drawLine(
        color = CrabShellDark.copy(alpha = a * 0.9f),
        start = Offset(cx + dir * size * 0.35f, cy),
        end = Offset(cx + dir * clawReach, clawY - size * 0.12f),
        strokeWidth = size * 0.09f,
        cap = StrokeCap.Round,
    )
    // Eyes
    drawCircle(
        color = Color.White.copy(alpha = a * 0.9f),
        radius = size * 0.07f,
        center = Offset(cx + dir * size * 0.22f, cy - size * 0.28f),
    )
    drawCircle(
        color = Color(0xFF1A1A1A).copy(alpha = a),
        radius = size * 0.035f,
        center = Offset(cx + dir * size * 0.24f, cy - size * 0.28f),
    )
}

private fun DrawScope.drawLandTurtle(
    cx: Float,
    cy: Float,
    size: Float,
    facingRight: Boolean,
    alpha: Float,
    walkPhase: Float,
) {
    val a = alpha.coerceIn(0f, 1f)
    if (a < 0.02f) return
    val dir = if (facingRight) 1f else -1f
    val bob = sin(walkPhase) * size * 0.04f
    // Feet
    for (i in 0 until 4) {
        val fx = cx + dir * size * (-0.35f + i * 0.22f)
        val fy = cy + size * 0.28f + abs(sin(walkPhase + i * 1.1f)) * size * 0.06f
        drawOval(
            color = TurtleSkin.copy(alpha = a * 0.9f),
            topLeft = Offset(fx - size * 0.10f, fy - size * 0.06f),
            size = Size(size * 0.20f, size * 0.12f),
        )
    }
    // Shell dome
    val shell = Path().apply {
        moveTo(cx - size * 0.55f, cy + size * 0.10f + bob)
        quadraticTo(cx - size * 0.50f, cy - size * 0.55f + bob, cx, cy - size * 0.62f + bob)
        quadraticTo(cx + size * 0.50f, cy - size * 0.55f + bob, cx + size * 0.55f, cy + size * 0.10f + bob)
        close()
    }
    drawPath(shell, TurtleShell.copy(alpha = a))
    drawPath(
        shell,
        TurtleShellLite.copy(alpha = a * 0.45f),
        style = Stroke(width = size * 0.06f),
    )
    // Head
    drawOval(
        color = TurtleSkin.copy(alpha = a),
        topLeft = Offset(cx + dir * size * 0.42f - size * 0.14f, cy - size * 0.18f + bob),
        size = Size(size * 0.28f, size * 0.22f),
    )
    drawCircle(
        color = Color(0xFF1A1A1A).copy(alpha = a),
        radius = size * 0.035f,
        center = Offset(cx + dir * size * 0.55f, cy - size * 0.12f + bob),
    )
}

private fun DrawScope.drawSeaTurtle(
    cx: Float,
    cy: Float,
    size: Float,
    facingRight: Boolean,
    alpha: Float,
    walkPhase: Float,
    flipperBoost: Float,
    submergedHint: Boolean,
) {
    val a = alpha.coerceIn(0f, 1f) * if (submergedHint) 0.72f else 1f
    if (a < 0.02f) return
    val dir = if (facingRight) 1f else -1f
    val flap = sin(walkPhase * flipperBoost) * size * 0.12f * flipperBoost
    // Flippers
    drawOval(
        color = SeaTurtleSkin.copy(alpha = a * 0.85f),
        topLeft = Offset(cx - size * 0.15f + dir * flap * 0.2f, cy + size * 0.05f),
        size = Size(size * 0.55f, size * 0.18f),
    )
    drawOval(
        color = SeaTurtleSkin.copy(alpha = a * 0.85f),
        topLeft = Offset(cx - size * 0.05f - dir * flap * 0.15f, cy + size * 0.12f),
        size = Size(size * 0.48f, size * 0.16f),
    )
    // Flatter shell
    val shell = Path().apply {
        moveTo(cx - size * 0.62f, cy + size * 0.08f)
        quadraticTo(cx - size * 0.40f, cy - size * 0.38f, cx, cy - size * 0.42f)
        quadraticTo(cx + size * 0.40f, cy - size * 0.38f, cx + size * 0.62f, cy + size * 0.08f)
        close()
    }
    drawPath(shell, SeaTurtleShell.copy(alpha = a))
    drawPath(shell, SeaTurtleShellLite.copy(alpha = a * 0.40f), style = Stroke(width = size * 0.05f))
    // Head
    drawOval(
        color = SeaTurtleSkin.copy(alpha = a),
        topLeft = Offset(cx + dir * size * 0.48f - size * 0.12f, cy - size * 0.12f),
        size = Size(size * 0.30f, size * 0.18f),
    )
    drawCircle(
        color = Color(0xFF1A1A1A).copy(alpha = a),
        radius = size * 0.03f,
        center = Offset(cx + dir * size * 0.62f, cy - size * 0.06f),
    )
    if (submergedHint) {
        drawOval(
            color = Color.White.copy(alpha = a * 0.18f),
            topLeft = Offset(cx - size * 0.5f, cy - size * 0.08f),
            size = Size(size * 1.0f, size * 0.10f),
        )
    }
}
