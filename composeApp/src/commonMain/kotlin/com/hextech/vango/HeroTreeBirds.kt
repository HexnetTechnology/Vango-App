package com.hextech.vango

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private val BirdWing = Color(0xFF1A1C20)
private val NestAdultBody = Color(0xFF3A3430)
private val NestAdultAccent = Color(0xFFC47848)
private val NestTwig = Color(0xFF6B4A32)
private val NestTwigLite = Color(0xFF8A6344)
private val NestInner = Color(0xFF4A3424)
private val ChickDown = Color(0xFFE8DCC8)
private val ChickBeak = Color(0xFFE09040)

/** Distinct silhouette / color varieties for hero birds. */
private enum class BirdKind {
    Sparrow,   // small warm brown
    Swallow,   // dark sleek, forked tail
    Dove,      // pale plump
    Finch,     // warm body + red breast
    Crow,      // larger black
    Gull,      // pale gray/white — likes roofs near water
}

private data class BirdLook(
    val body: Color,
    val accent: Color?,
    val sizeMul: Float,
    val forkedTail: Boolean,
    val plump: Boolean,
)

private fun birdLook(kind: BirdKind): BirdLook = when (kind) {
    BirdKind.Sparrow -> BirdLook(Color(0xFF6A4E3A), Color(0xFFC4A078), 0.92f, false, false)
    BirdKind.Swallow -> BirdLook(Color(0xFF1A2438), Color(0xFFC8D0DC), 0.95f, true, false)
    BirdKind.Dove -> BirdLook(Color(0xFFB8B4AC), Color(0xFFE8E4DC), 1.08f, false, true)
    BirdKind.Finch -> BirdLook(Color(0xFF5A4030), Color(0xFFC04038), 0.90f, false, false)
    BirdKind.Crow -> BirdLook(Color(0xFF1A1A1E), null, 1.22f, false, false)
    BirdKind.Gull -> BirdLook(Color(0xFFE8E6E0), Color(0xFF8A9098), 1.12f, false, true)
}

private fun birdKindFor(stream: Int, cycleIndex: Int, preferRoof: Boolean): BirdKind {
    val roll = abs(cycleIndex * 17 + stream * 31) % 100
    return if (preferRoof) {
        when {
            roll < 35 -> BirdKind.Gull
            roll < 55 -> BirdKind.Dove
            roll < 72 -> BirdKind.Sparrow
            roll < 88 -> BirdKind.Crow
            else -> BirdKind.Swallow
        }
    } else {
        when {
            roll < 22 -> BirdKind.Sparrow
            roll < 40 -> BirdKind.Swallow
            roll < 55 -> BirdKind.Finch
            roll < 70 -> BirdKind.Dove
            roll < 85 -> BirdKind.Crow
            else -> BirdKind.Gull
        }
    }
}

/** Canopy perch points matching near-side trees (fraction of w/h). */
private data class TreePerch(
    val x: Float,
    val y: Float,
    val swayPhase: Float,
)

private val TreePerches: List<TreePerch> = listOf(
    // Left pines / deciduous
    TreePerch(0.045f, 0.42f, 1.15f),
    TreePerch(0.105f, 0.52f, 0.88f),
    TreePerch(0.18f, 0.52f, 1.40f),
    // Right pines / deciduous
    TreePerch(0.78f, 0.50f, 0.22f),
    TreePerch(0.84f, 0.44f, 0.10f),
    TreePerch(0.895f, 0.40f, 0.37f),
    TreePerch(0.91f, 0.52f, 2.05f),
    TreePerch(0.965f, 0.38f, 0.62f),
)

/**
 * Near-tree bird life: frequent arrive/leave traffic, plus an occasional
 * nest cycle (build → chicks → family flies out together).
 * Draw after near trees so birds sit on foliage.
 */
internal fun DrawScope.drawHeroTreeBirds(
    w: Float,
    h: Float,
    sceneTSec: Float,
    animate: Boolean,
    period: HeroSkyPeriod,
    weather: HeroSkyWeather,
    season: SeasonState,
    wind: WindState,
    foliageAmount: Float,
    snowCover: Float,
) {
    if (!animate) return
    if (foliageAmount < 0.18f && season == SeasonState.Winter) return
    if (snowCover > 0.55f) return

    val weatherMul = when (weather) {
        HeroSkyWeather.Rain -> 0.35f
        HeroSkyWeather.Snow -> 0.18f
        HeroSkyWeather.Fog -> 0.55f
        HeroSkyWeather.Overcast -> 0.85f
        HeroSkyWeather.PartlyCloudy -> 1f
        HeroSkyWeather.Clear -> 1f
    }
    val periodMul = when (period) {
        HeroSkyPeriod.Night -> 0.12f
        HeroSkyPeriod.Dusk -> 0.75f
        HeroSkyPeriod.Dawn -> 1.05f
        HeroSkyPeriod.Day -> 1f
    }
    val seasonMul = when (season) {
        SeasonState.Spring -> 1.15f
        SeasonState.Summer -> 1f
        SeasonState.Autumn -> 0.85f
        SeasonState.Winter -> 0.35f
    }
    val alphaMul = (weatherMul * periodMul * seasonMul).coerceIn(0f, 1.2f)
    if (alphaMul < 0.08f) return

    val t = sceneTSec.coerceAtLeast(0f)
    val sizeBase = minOf(w, h)

    // Continuous traffic — several staggered streams
    for (stream in 0 until 5) {
        drawBirdVisit(w, h, t, stream, alphaMul, wind, sizeBase, roofPerches = null)
    }

    // Occasional nest family story on one preferred perch
    drawNestFamilyCycle(w, h, t, alphaMul, wind, sizeBase, season)
}

/**
 * Birds that land on the caravan / motorhome roof. Draw **after** the vehicle
 * so they sit on top of the body.
 */
internal fun DrawScope.drawHeroVehicleBirds(
    w: Float,
    h: Float,
    layout: HeroLayout,
    vehicleType: HeroVehicleType,
    sceneTSec: Float,
    animate: Boolean,
    period: HeroSkyPeriod,
    weather: HeroSkyWeather,
    season: SeasonState,
    snowCover: Float,
) {
    if (!animate) return
    if (snowCover > 0.55f) return
    val weatherMul = when (weather) {
        HeroSkyWeather.Rain -> 0.40f
        HeroSkyWeather.Snow -> 0.15f
        HeroSkyWeather.Fog -> 0.55f
        else -> 1f
    }
    val periodMul = when (period) {
        HeroSkyPeriod.Night -> 0.18f
        HeroSkyPeriod.Dusk -> 0.80f
        HeroSkyPeriod.Dawn -> 1.05f
        HeroSkyPeriod.Day -> 1f
    }
    val seasonMul = when (season) {
        SeasonState.Winter -> 0.40f
        SeasonState.Autumn -> 0.90f
        else -> 1f
    }
    val alphaMul = weatherMul * periodMul * seasonMul
    if (alphaMul < 0.08f) return

    val roof = caravanRoofPerches(layout, vehicleType)
    if (roof.isEmpty()) return
    val t = sceneTSec.coerceAtLeast(0f)
    val sizeBase = minOf(w, h)
    for (stream in 0 until 3) {
        drawBirdVisit(w, h, t, stream + 10, alphaMul, WindState.Calm, sizeBase, roofPerches = roof)
    }
}

/** Design-space roof sit spots → screen px (on top of vehicle body). */
private fun caravanRoofPerches(layout: HeroLayout, vehicleType: HeroVehicleType): List<Offset> {
    val o = Offset(layout.vehicleOriginX, layout.vehicleOriginY)
    val s = layout.vehicleScale
    fun p(x: Float, y: Float) = Offset(o.x + x * s, o.y + y * s)
    return when (vehicleType) {
        HeroVehicleType.Towable -> listOf(
            p(360f, 58f),
            p(520f, 42f), // near AC unit
            p(680f, 58f),
            p(820f, 58f),
        )
        HeroVehicleType.Motorhome -> listOf(
            p(400f, 58f),
            p(560f, 58f),
            p(720f, 58f),
            p(860f, 58f),
        )
    }
}

private fun DrawScope.drawBirdVisit(
    w: Float,
    h: Float,
    t: Float,
    stream: Int,
    alphaMul: Float,
    wind: WindState,
    sizeBase: Float,
    roofPerches: List<Offset>?,
) {
    val onRoof = roofPerches != null
    val cycle = if (onRoof) 11f + (stream % 3) * 2.2f else 7.5f + stream * 1.35f
    val active = if (onRoof) 6.5f + (stream % 3) * 0.8f else 4.2f + (stream % 3) * 0.55f
    val phaseOff = stream * 2.7f + if (onRoof) 1.1f else 0.4f
    val local = ((t + phaseOff) % cycle + cycle) % cycle
    if (local > active) return

    val cycleIndex = floor((t + phaseOff) / cycle).toInt()
    val skip = abs((cycleIndex * 7919 + stream * 104729) % 5)
    if (skip == 0) return // occasional gap so traffic isn't metronomic
    if (onRoof && skip == 2) return // roofs less frequent

    val p = (local / active).coerceIn(0f, 1f)
    val kind = birdKindFor(stream, cycleIndex, preferRoof = onRoof)
    val look = birdLook(kind)

    val (px, py) = if (onRoof) {
        val idx = abs((cycleIndex * 3 + stream) % roofPerches!!.size)
        val perch = roofPerches[idx]
        perch.x to (perch.y - sizeBase * 0.004f)
    } else {
        val perchIdx = abs((cycleIndex * 3 + stream * 5) % TreePerches.size)
        val perch = TreePerches[perchIdx]
        val sway = windSwayOffsetX(
            tFromBase = 0.92f,
            timeSec = t,
            phase = perch.swayPhase,
            wind = wind,
            scalePx = h * 0.55f,
        )
        (w * perch.x + sway) to (h * perch.y)
    }

    val fromLeft = (cycleIndex + stream) % 2 == 0
    val entryX = if (fromLeft) -w * 0.06f else w * 1.06f
    val entryY = h * (0.14f + (stream % 4) * 0.04f)
    val exitX = if (fromLeft) w * 1.08f else -w * 0.08f
    val exitY = h * (0.12f + ((stream + 1) % 4) * 0.035f)

    // Roof visits linger longer on top
    val approachEnd = if (onRoof) 0.22f else 0.28f
    val departStart = if (onRoof) 0.72f else 0.62f
    val (cx, cy, facingRight, flapBoost, perched) = when {
        p < approachEnd -> {
            val u = birdEase((p / approachEnd).coerceIn(0f, 1f))
            val arc = sin(u * PI.toFloat()) * h * (if (onRoof) 0.06f else 0.04f)
            BirdFlight(
                lerpFloat(entryX, px, u),
                lerpFloat(entryY, py, u) - arc,
                fromLeft,
                1.6f,
                false,
            )
        }
        p < departStart -> {
            // Occasional hop along roof
            val hop = if (onRoof && ((local * 1.7f).toInt() % 5 == 0)) {
                sin(local * 14f) * sizeBase * 0.004f
            } else {
                0f
            }
            BirdFlight(
                px + hop,
                py + sin(local * 2.2f) * h * 0.0012f,
                fromLeft,
                0.12f,
                true,
            )
        }
        else -> {
            val u = birdEase(((p - departStart) / (1f - departStart)).coerceIn(0f, 1f))
            val arc = sin(u * PI.toFloat()) * h * 0.05f
            BirdFlight(
                lerpFloat(px, exitX, u),
                lerpFloat(py, exitY, u) - arc,
                !fromLeft,
                1.7f,
                false,
            )
        }
    }

    val fade = birdFade(p, 0.06f, 0.90f) * alphaMul
    if (fade < 0.02f) return
    val birdSize = sizeBase * (0.017f + (stream % 3) * 0.0025f) * look.sizeMul
    drawBirdSilhouette(
        cx = cx,
        cy = cy,
        size = birdSize,
        facingRight = facingRight,
        alpha = fade.coerceIn(0f, 1f),
        flapPhase = local * (8f + flapBoost * 4f),
        flapAmp = if (perched) 0.10f else 1f,
        color = look.body,
        accent = look.accent,
        forkedTail = look.forkedTail,
        plump = look.plump,
    )
}

private data class BirdFlight(
    val x: Float,
    val y: Float,
    val facingRight: Boolean,
    val flapBoost: Float,
    val perched: Boolean,
)

/**
 * Long rare cycle: scout → many material trips → weave/line nest → incubate →
 * chicks grow → family departs. Build phase is the bulk of the story.
 */
private fun DrawScope.drawNestFamilyCycle(
    w: Float,
    h: Float,
    t: Float,
    alphaMul: Float,
    wind: WindState,
    sizeBase: Float,
    season: SeasonState,
) {
    val storyMul = when (season) {
        SeasonState.Spring -> 1f
        SeasonState.Summer -> 0.85f
        SeasonState.Autumn -> 0.35f
        SeasonState.Winter -> 0.0f
    }
    if (storyMul < 0.05f) return

    // ~2+ minutes visible; nesting itself ~55s of fetch/weave trips
    val cycle = 168f
    val active = 118f
    val phaseOff = 27f
    val local = ((t + phaseOff) % cycle + cycle) % cycle
    if (local > active) return

    val cycleIndex = floor((t + phaseOff) / cycle).toInt()
    val skip = abs((cycleIndex * 9973) % 4)
    if (skip == 0 || (storyMul < 0.5f && skip == 2)) return

    val p = (local / active).coerceIn(0f, 1f)
    val perchIdx = abs((cycleIndex * 2 + 3) % TreePerches.size).let { idx ->
        when (idx) {
            0 -> 1
            7 -> 5
            else -> idx
        }
    }
    val perch = TreePerches[perchIdx]
    val sway = windSwayOffsetX(
        tFromBase = 0.92f,
        timeSec = t,
        phase = perch.swayPhase,
        wind = wind,
        scalePx = h * 0.55f,
    )
    val px = w * perch.x + sway
    val py = h * perch.y
    val nestSide = if (perch.x < 0.5f) 1f else -1f
    val nestX = px + nestSide * sizeBase * 0.012f
    val nestY = py + h * 0.012f
    val fromOpen = perch.x > 0.5f // approach from lake/open side
    val openX = if (fromOpen) -w * 0.06f else w * 1.06f
    val gatherX = if (fromOpen) w * 0.28f else w * 0.72f
    val gatherY = h * 0.62f

    val fadeScene = birdFade(p, 0.025f, 0.93f) * alphaMul * storyMul
    if (fadeScene < 0.02f) return

    // 0.00–0.05  scout arrive (empty)
    // 0.05–0.50  build: many outbound/inbound material trips + nest grows
    // 0.50–0.58  finish weave / soft lining, settle
    // 0.58–0.70  incubate
    // 0.70–0.86  chicks grow + feed visits
    // 0.86–1.00  family flies out
    val adultSize = sizeBase * 0.020f
    val chickBase = sizeBase * 0.012f
    val nestFull = sizeBase * 0.030f

    when {
        p < 0.05f -> {
            val u = birdEase((p / 0.05f).coerceIn(0f, 1f))
            val cx = lerpFloat(openX, nestX, u)
            val cy = lerpFloat(h * 0.20f, nestY - h * 0.012f, u) - sin(u * PI.toFloat()) * h * 0.035f
            drawBirdSilhouette(
                cx = cx, cy = cy, size = adultSize,
                facingRight = fromOpen, alpha = fadeScene,
                flapPhase = local * 11f, flapAmp = 1f,
                color = NestAdultBody, accent = NestAdultAccent,
            )
        }
        p < 0.50f -> {
            val buildP = ((p - 0.05f) / 0.45f).coerceIn(0f, 1f)
            val tripCount = 9
            val tripF = buildP * tripCount
            val trip = floor(tripF).toInt().coerceIn(0, tripCount - 1)
            val tripLocal = (tripF - trip).coerceIn(0f, 1f)
            // Soft material on later trips
            val softLoad = trip >= 6
            // Nest completeness grows after each placed load
            val placeU = when {
                tripLocal < 0.62f -> 0f
                tripLocal < 0.88f -> ((tripLocal - 0.62f) / 0.26f).coerceIn(0f, 1f)
                else -> 1f
            }
            val nestProgress = ((trip + placeU) / tripCount.toFloat()).coerceIn(0f, 1f)
            if (nestProgress > 0.02f) {
                drawNest(nestX, nestY, nestFull, fadeScene, progress = nestProgress)
            }

            // Trip beat: inspect → fetch → return with load → place/weave
            when {
                tripLocal < 0.10f -> {
                    // Brief look / press twigs into cup
                    val weave = sin(local * 9f + trip) * adultSize * 0.18f
                    drawBirdSilhouette(
                        cx = nestX + nestSide * adultSize * 0.15f + weave,
                        cy = nestY - adultSize * 0.45f,
                        size = adultSize,
                        facingRight = nestSide > 0f,
                        alpha = fadeScene,
                        flapPhase = local * 2f,
                        flapAmp = 0.12f,
                        color = NestAdultBody,
                        accent = NestAdultAccent,
                        tucked = nestProgress > 0.55f && tripLocal > 0.04f,
                    )
                }
                tripLocal < 0.38f -> {
                    // Outbound empty toward ground / other trees
                    val u = birdEase(((tripLocal - 0.10f) / 0.28f).coerceIn(0f, 1f))
                    val cx = lerpFloat(nestX, gatherX, u)
                    val cy = lerpFloat(nestY - h * 0.01f, gatherY, u) + sin(u * PI.toFloat()) * h * 0.03f
                    drawBirdSilhouette(
                        cx = cx, cy = cy, size = adultSize,
                        facingRight = gatherX > nestX, alpha = fadeScene,
                        flapPhase = local * 13f, flapAmp = 1.15f,
                        color = NestAdultBody, accent = NestAdultAccent,
                    )
                }
                tripLocal < 0.48f -> {
                    // Pick material on ground / brush
                    drawBirdSilhouette(
                        cx = gatherX + sin(local * 6f) * adultSize * 0.2f,
                        cy = gatherY + h * 0.008f,
                        size = adultSize,
                        facingRight = nestX > gatherX,
                        alpha = fadeScene,
                        flapPhase = local * 3f,
                        flapAmp = 0.2f,
                        color = NestAdultBody,
                        accent = NestAdultAccent,
                        carryingTwig = true,
                        softMaterial = softLoad,
                    )
                }
                tripLocal < 0.72f -> {
                    // Return with load
                    val u = birdEase(((tripLocal - 0.48f) / 0.24f).coerceIn(0f, 1f))
                    val cx = lerpFloat(gatherX, nestX, u)
                    val cy = lerpFloat(gatherY, nestY - h * 0.012f, u) - sin(u * PI.toFloat()) * h * 0.04f
                    drawBirdSilhouette(
                        cx = cx, cy = cy, size = adultSize,
                        facingRight = nestX > gatherX, alpha = fadeScene,
                        flapPhase = local * 12f, flapAmp = 1.1f,
                        color = NestAdultBody, accent = NestAdultAccent,
                        carryingTwig = true,
                        softMaterial = softLoad,
                    )
                }
                else -> {
                    // Place and weave into nest rim
                    val u = ((tripLocal - 0.72f) / 0.28f).coerceIn(0f, 1f)
                    val weave = sin(u * PI.toFloat() * 3f + trip) * adultSize * 0.22f
                    drawBirdSilhouette(
                        cx = nestX + nestSide * adultSize * 0.25f + weave,
                        cy = nestY - adultSize * (0.50f - u * 0.08f),
                        size = adultSize,
                        facingRight = nestSide > 0f,
                        alpha = fadeScene,
                        flapPhase = local * 4f,
                        flapAmp = 0.18f,
                        color = NestAdultBody,
                        accent = NestAdultAccent,
                        carryingTwig = u < 0.55f,
                        softMaterial = softLoad,
                    )
                }
            }
        }
        p < 0.58f -> {
            // Finish: circle nest, soft lining, press cup
            val finish = ((p - 0.50f) / 0.08f).coerceIn(0f, 1f)
            drawNest(nestX, nestY, nestFull, fadeScene, progress = 0.92f + 0.08f * finish)
            val orbit = sin(finish * PI.toFloat() * 2f) * adultSize * 0.55f
            val tuck = finish > 0.72f
            drawBirdSilhouette(
                cx = nestX + if (tuck) 0f else nestSide * adultSize * 0.2f + orbit,
                cy = nestY - adultSize * (if (tuck) 0.32f else 0.52f),
                size = adultSize,
                facingRight = nestSide > 0f,
                alpha = fadeScene,
                flapPhase = local * 1.5f,
                flapAmp = if (tuck) 0f else 0.15f,
                color = NestAdultBody,
                accent = if (tuck) null else NestAdultAccent,
                tucked = tuck,
                carryingTwig = finish in 0.15f..0.45f,
                softMaterial = true,
            )
        }
        p < 0.70f -> {
            drawNest(nestX, nestY, nestFull, fadeScene, progress = 1f)
            drawBirdSilhouette(
                cx = nestX,
                cy = nestY - adultSize * 0.35f + sin(local * 1.15f) * h * 0.0012f,
                size = adultSize * 0.95f,
                facingRight = nestSide > 0f,
                alpha = fadeScene,
                flapPhase = 0f,
                flapAmp = 0f,
                color = NestAdultBody,
                accent = null,
                tucked = true,
            )
        }
        p < 0.86f -> {
            val grow = ((p - 0.70f) / 0.16f).coerceIn(0f, 1f)
            drawNest(nestX, nestY, nestFull, fadeScene, progress = 1f)
            val chickN = when {
                grow < 0.28f -> 2
                else -> 3
            }
            val chickScale = 0.50f + 0.50f * birdEase(grow)
            for (i in 0 until chickN) {
                val ox = (i - (chickN - 1) * 0.5f) * chickBase * 1.35f
                drawChick(
                    cx = nestX + ox,
                    cy = nestY - chickBase * 0.35f * chickScale - sizeBase * 0.004f,
                    size = chickBase * chickScale,
                    alpha = fadeScene * (0.55f + 0.45f * grow),
                    bobPhase = local * 3f + i * 1.1f,
                )
            }
            // Adult: mostly beside nest; short feed hop mid-phase
            val feedHop = grow in 0.40f..0.58f
            if (feedHop) {
                val fh = ((grow - 0.40f) / 0.18f).coerceIn(0f, 1f)
                val out = if (fh < 0.5f) fh * 2f else (1f - fh) * 2f
                drawBirdSilhouette(
                    cx = nestX + nestSide * adultSize * (1.1f + out * 2.2f),
                    cy = nestY - adultSize * 0.4f + out * h * 0.02f,
                    size = adultSize,
                    facingRight = (nestSide > 0f) == (fh < 0.5f),
                    alpha = fadeScene,
                    flapPhase = local * 10f,
                    flapAmp = 0.9f,
                    color = NestAdultBody,
                    accent = NestAdultAccent,
                    carryingTwig = fh in 0.35f..0.70f,
                    softMaterial = true,
                )
            } else {
                drawBirdSilhouette(
                    cx = nestX + nestSide * adultSize * 1.1f,
                    cy = nestY - adultSize * 0.55f,
                    size = adultSize,
                    facingRight = nestSide < 0f,
                    alpha = fadeScene,
                    flapPhase = local * 0.8f,
                    flapAmp = 0.1f,
                    color = NestAdultBody,
                    accent = NestAdultAccent,
                )
            }
        }
        else -> {
            val u = birdEase(((p - 0.86f) / 0.14f).coerceIn(0f, 1f))
            val nestFade = fadeScene * (1f - u * 0.85f)
            if (nestFade > 0.04f) {
                drawNest(nestX, nestY, nestFull, nestFade, progress = 1f)
            }
            val goRight = perch.x < 0.5f
            val destX = if (goRight) w * 1.12f else -w * 0.12f
            val destY = h * 0.14f
            val family = 4
            for (i in 0 until family) {
                val lag = i * 0.05f
                val uu = birdEase((u - lag).coerceIn(0f, 1f) / (1f - lag).coerceAtLeast(0.45f))
                if (u < lag) {
                    if (i == 0) {
                        drawBirdSilhouette(
                            cx = nestX, cy = nestY - adultSize * 0.5f,
                            size = adultSize, facingRight = goRight, alpha = fadeScene,
                            flapPhase = local * 10f, flapAmp = 0.8f, color = NestAdultBody, accent = NestAdultAccent,
                        )
                    } else {
                        drawChick(
                            cx = nestX + (i - 2) * chickBase * 0.9f,
                            cy = nestY - chickBase * 0.4f,
                            size = chickBase * 0.95f,
                            alpha = fadeScene,
                            bobPhase = local * 5f + i,
                        )
                    }
                    continue
                }
                val spread = (i - 1.5f) * sizeBase * 0.022f
                val cx = lerpFloat(nestX + spread * 0.3f, destX + spread, uu)
                val cy = lerpFloat(nestY - h * 0.01f, destY + abs(spread) * 0.15f, uu) -
                    sin(uu * PI.toFloat()) * h * 0.06f
                val a = fadeScene * (1f - uu * 0.15f)
                if (i == 0) {
                    drawBirdSilhouette(
                        cx = cx, cy = cy, size = adultSize,
                        facingRight = goRight, alpha = a,
                        flapPhase = local * 14f + i, flapAmp = 1.2f,
                        color = NestAdultBody, accent = NestAdultAccent,
                    )
                } else {
                    drawBirdSilhouette(
                        cx = cx, cy = cy, size = adultSize * 0.62f,
                        facingRight = goRight, alpha = a,
                        flapPhase = local * 16f + i * 1.7f, flapAmp = 1.3f,
                        color = Color(0xFF2A2E34),
                        accent = null,
                    )
                }
            }
        }
    }
}

private fun birdFade(p: Float, inEnd: Float, outStart: Float): Float = when {
    p < inEnd -> (p / inEnd).coerceIn(0f, 1f)
    p > outStart -> ((1f - p) / (1f - outStart).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
    else -> 1f
}

private fun birdEase(u: Float): Float {
    val t = u.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * Progressive nest: loose crossed sticks → partial cup → full bowl → soft lining.
 * [progress] 0..1 drives how complete the nest looks.
 */
private fun DrawScope.drawNest(
    cx: Float,
    cy: Float,
    size: Float,
    alpha: Float,
    progress: Float = 1f,
) {
    val a = alpha.coerceIn(0f, 1f)
    val pr = progress.coerceIn(0f, 1f)
    if (a < 0.02f || size < 1f || pr < 0.02f) return
    val s = size * (0.55f + 0.45f * pr.coerceAtMost(0.85f) / 0.85f)

    // Stage 1: platform of crossed twigs
    val stickN = (2 + (pr * 7f).toInt()).coerceAtMost(8)
    for (i in 0 until stickN) {
        val ang = (i * 0.72f + pr * 0.4f)
        val len = s * (0.55f + (i % 3) * 0.12f)
        val thick = s * (0.045f + (i % 2) * 0.015f)
        val col = if (i % 2 == 0) NestTwig else NestTwigLite
        val dx = cos(ang) * len * 0.5f
        val dy = sin(ang) * len * 0.22f + s * 0.08f
        drawLine(
            color = col.copy(alpha = a * (0.55f + 0.35f * pr)),
            start = Offset(cx - dx, cy - dy * 0.2f),
            end = Offset(cx + dx, cy + dy),
            strokeWidth = thick,
            cap = StrokeCap.Round,
        )
    }
    if (pr < 0.28f) return

    // Stage 2+: cup walls
    val cup = ((pr - 0.28f) / 0.40f).coerceIn(0f, 1f)
    val bowl = Path().apply {
        val half = s * (0.35f + 0.22f * cup)
        moveTo(cx - half, cy - s * 0.02f)
        quadraticTo(cx - half * 0.95f, cy + s * (0.22f + 0.22f * cup), cx, cy + s * (0.20f + 0.24f * cup))
        quadraticTo(cx + half * 0.95f, cy + s * (0.22f + 0.22f * cup), cx + half, cy - s * 0.02f)
        quadraticTo(cx, cy + s * 0.06f * cup, cx - half, cy - s * 0.02f)
        close()
    }
    drawPath(bowl, NestTwig.copy(alpha = a * (0.55f + 0.40f * cup)))
    if (cup > 0.35f) {
        drawPath(
            bowl,
            NestTwigLite.copy(alpha = a * 0.40f * cup),
            style = Stroke(width = s * 0.07f),
        )
    }
    if (pr < 0.62f) return

    // Stage 3: rim + inner hollow
    val line = ((pr - 0.62f) / 0.38f).coerceIn(0f, 1f)
    drawOval(
        color = NestInner.copy(alpha = a * 0.75f * line),
        topLeft = Offset(cx - s * 0.30f, cy - s * 0.01f),
        size = Size(s * 0.60f, s * 0.26f * line),
    )
    // Soft lining fluff
    if (line > 0.45f) {
        val fluffA = a * 0.35f * ((line - 0.45f) / 0.55f).coerceIn(0f, 1f)
        drawOval(
            color = Color(0xFFC8B89A).copy(alpha = fluffA),
            topLeft = Offset(cx - s * 0.22f, cy + s * 0.02f),
            size = Size(s * 0.44f, s * 0.14f),
        )
    }
    // Rim twigs
    drawLine(
        color = NestTwigLite.copy(alpha = a * 0.75f * line),
        start = Offset(cx - s * 0.48f, cy + s * 0.04f),
        end = Offset(cx - s * 0.08f, cy + s * 0.28f),
        strokeWidth = s * 0.055f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = NestTwig.copy(alpha = a * 0.8f * line),
        start = Offset(cx + s * 0.46f, cy),
        end = Offset(cx + s * 0.06f, cy + s * 0.30f),
        strokeWidth = s * 0.05f,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawChick(
    cx: Float,
    cy: Float,
    size: Float,
    alpha: Float,
    bobPhase: Float,
) {
    val a = alpha.coerceIn(0f, 1f)
    if (a < 0.02f) return
    val bob = sin(bobPhase) * size * 0.12f
    drawOval(
        color = ChickDown.copy(alpha = a),
        topLeft = Offset(cx - size * 0.45f, cy - size * 0.35f + bob),
        size = Size(size * 0.90f, size * 0.75f),
    )
    drawCircle(
        color = Color(0xFF1A1A1A).copy(alpha = a),
        radius = size * 0.08f,
        center = Offset(cx + size * 0.12f, cy - size * 0.12f + bob),
    )
    // Beak
    val beak = Path().apply {
        moveTo(cx + size * 0.35f, cy - size * 0.05f + bob)
        lineTo(cx + size * 0.62f, cy + size * 0.02f + bob)
        lineTo(cx + size * 0.32f, cy + size * 0.12f + bob)
        close()
    }
    drawPath(beak, ChickBeak.copy(alpha = a))
}

private fun DrawScope.drawBirdSilhouette(
    cx: Float,
    cy: Float,
    size: Float,
    facingRight: Boolean,
    alpha: Float,
    flapPhase: Float,
    flapAmp: Float,
    color: Color,
    accent: Color?,
    carryingTwig: Boolean = false,
    softMaterial: Boolean = false,
    tucked: Boolean = false,
    forkedTail: Boolean = false,
    plump: Boolean = false,
) {
    val a = alpha.coerceIn(0f, 1f)
    if (a < 0.02f || size < 0.5f) return
    val dir = if (facingRight) 1f else -1f
    val flap = sin(flapPhase) * size * 0.55f * flapAmp
    val bodyW = if (plump) 0.95f else 0.85f
    val bodyH = if (plump) 0.68f else 0.58f

    // Wing
    if (!tucked && flapAmp > 0.05f) {
        val wing = Path().apply {
            moveTo(cx - dir * size * 0.05f, cy)
            quadraticTo(
                cx - dir * size * 0.15f,
                cy - size * (if (forkedTail) 0.72f else 0.55f) - flap,
                cx + dir * size * 0.35f,
                cy - size * 0.15f - flap * 0.4f,
            )
            quadraticTo(cx + dir * size * 0.05f, cy + size * 0.05f, cx - dir * size * 0.05f, cy)
            close()
        }
        drawPath(wing, BirdWing.copy(alpha = a * 0.9f))
    }

    // Body
    drawOval(
        color = color.copy(alpha = a),
        topLeft = Offset(cx - size * bodyW * 0.52f, cy - size * bodyH * 0.55f),
        size = Size(size * bodyW, size * bodyH),
    )
    // Head
    drawCircle(
        color = color.copy(alpha = a),
        radius = size * (if (plump) 0.30f else 0.28f),
        center = Offset(cx + dir * size * 0.38f, cy - size * 0.18f),
    )
    // Beak
    val beakTip = if (forkedTail) 0.92f else 0.82f
    val beak = Path().apply {
        moveTo(cx + dir * size * 0.55f, cy - size * 0.18f)
        lineTo(cx + dir * size * beakTip, cy - size * 0.12f)
        lineTo(cx + dir * size * 0.55f, cy - size * 0.06f)
        close()
    }
    drawPath(beak, (accent ?: Color(0xFFC47848)).copy(alpha = a))
    // Tail
    if (!tucked) {
        if (forkedTail) {
            val fork = Path().apply {
                moveTo(cx - dir * size * 0.30f, cy)
                lineTo(cx - dir * size * 0.95f, cy - size * 0.32f)
                lineTo(cx - dir * size * 0.55f, cy - size * 0.02f)
                lineTo(cx - dir * size * 0.92f, cy + size * 0.28f)
                close()
            }
            drawPath(fork, color.copy(alpha = a * 0.88f))
        } else {
            val tail = Path().apply {
                moveTo(cx - dir * size * 0.35f, cy)
                lineTo(cx - dir * size * 0.85f, cy - size * 0.22f)
                lineTo(cx - dir * size * 0.70f, cy + size * 0.12f)
                close()
            }
            drawPath(tail, color.copy(alpha = a * 0.85f))
        }
    }
    // Accent breast / wing bar
    if (accent != null && !tucked) {
        drawOval(
            color = accent.copy(alpha = a * 0.55f),
            topLeft = Offset(cx - size * 0.15f, cy - size * 0.08f),
            size = Size(size * 0.35f, size * 0.28f),
        )
    }
    // Material in beak: twig early, soft fluff later
    if (carryingTwig) {
        if (softMaterial) {
            drawOval(
                color = Color(0xFFC8B89A).copy(alpha = a * 0.9f),
                topLeft = Offset(cx + dir * size * 0.62f - size * 0.12f, cy - size * 0.22f),
                size = Size(size * 0.42f, size * 0.22f),
            )
            drawOval(
                color = Color(0xFFE8DCC8).copy(alpha = a * 0.55f),
                topLeft = Offset(cx + dir * size * 0.78f - size * 0.08f, cy - size * 0.16f),
                size = Size(size * 0.28f, size * 0.14f),
            )
        } else {
            drawLine(
                color = NestTwig.copy(alpha = a),
                start = Offset(cx + dir * size * 0.70f, cy - size * 0.12f),
                end = Offset(cx + dir * size * 1.15f, cy - size * 0.02f),
                strokeWidth = size * 0.08f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = NestTwigLite.copy(alpha = a * 0.8f),
                start = Offset(cx + dir * size * 0.72f, cy - size * 0.06f),
                end = Offset(cx + dir * size * 1.05f, cy + size * 0.08f),
                strokeWidth = size * 0.05f,
                cap = StrokeCap.Round,
            )
        }
    }
}
