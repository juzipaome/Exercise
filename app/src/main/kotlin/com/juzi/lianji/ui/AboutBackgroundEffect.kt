// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package com.juzi.lianji.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.RuntimeShader
import top.yukonga.miuix.kmp.blur.asBrush
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

@Composable
internal fun AboutBackground(
    dark: Boolean,
    alpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    val surface = MiuixTheme.colorScheme.surface
    if (!isRuntimeShaderSupported()) {
        Spacer(modifier.fillMaxSize().background(surface))
        return
    }

    val preset = remember(dark) { AboutBackgroundPreset.forTheme(dark) }
    val painter = remember { AboutBackgroundPainter() }
    val colorStage = remember { Animatable(0f) }
    LaunchedEffect(preset) {
        var targetStage = floor(colorStage.value) + 1f
        while (isActive) {
            delay((preset.colorInterpPeriod * 500).toLong())
            colorStage.animateTo(targetStage, spring(dampingRatio = .9f, stiffness = 35f))
            targetStage += 1f
        }
    }

    Spacer(
        modifier.fillMaxSize().aboutBackgroundDraw(
            painter = painter,
            preset = preset,
            surface = surface,
            colorStage = { colorStage.value },
            alpha = alpha,
        ),
    )
}

private class AboutBackgroundPreset(
    val points: FloatArray,
    val colors1: FloatArray,
    val colors2: FloatArray,
    val colors3: FloatArray,
    val colorInterpPeriod: Float,
    val lightOffset: Float,
    val saturateOffset: Float,
    val pointOffset: Float,
) {
    companion object {
        private val points = floatArrayOf(.8f, .2f, 1f, .8f, .9f, 1f, .2f, .9f, 1f, .2f, .2f, 1f)
        private val light = AboutBackgroundPreset(
            points,
            floatArrayOf(1f, .9f, .94f, 1f, 1f, .84f, .89f, 1f, .97f, .73f, .82f, 1f, .64f, .65f, .98f, 1f),
            floatArrayOf(.58f, .74f, 1f, 1f, 1f, .9f, .93f, 1f, .74f, .76f, 1f, 1f, .97f, .77f, .84f, 1f),
            floatArrayOf(.98f, .86f, .9f, 1f, .6f, .73f, .98f, 1f, .92f, .93f, 1f, 1f, .56f, .69f, 1f, 1f),
            5f, .1f, .2f, .2f,
        )
        private val dark = AboutBackgroundPreset(
            points,
            floatArrayOf(.2f, .06f, .88f, .4f, .3f, .14f, .55f, .5f, 0f, .64f, .96f, .5f, .11f, .16f, .83f, .4f),
            floatArrayOf(.07f, .15f, .79f, .5f, .62f, .21f, .67f, .5f, .06f, .25f, .84f, .5f, 0f, .2f, .78f, .5f),
            floatArrayOf(.58f, .3f, .74f, .4f, .27f, .18f, .6f, .5f, .66f, .26f, .62f, .5f, .12f, .16f, .7f, .6f),
            8f, 0f, .17f, .4f,
        )

        fun forTheme(darkTheme: Boolean) = if (darkTheme) dark else light
    }
}

private class AboutBackgroundPainter {
    private val shader by lazy {
        RuntimeShader(OS3_BACKGROUND_SHADER).also {
            it.setFloatUniform("uTranslateY", 0f)
            it.setFloatUniform("uNoiseScale", 1.5f)
            it.setFloatUniform("uPointRadiusMulti", 1f)
            it.setFloatUniform("uAlphaMulti", 1f)
        }
    }
    val brush: Brush get() = shader.asBrush()
    private val colors = FloatArray(16)
    private val animatedPoints = FloatArray(8)

    fun update(width: Float, height: Float, time: Float, preset: AboutBackgroundPreset, stage: Float) {
        shader.setFloatUniform("uResolution", width, height)
        val drawHeight = height * .8f
        shader.setFloatUniform("uBound", 0f, 1f - drawHeight / height, 1f, drawHeight / height)
        shader.setFloatUniform("uAnimTime", time)
        shader.setFloatUniform("uPoints", preset.points)
        shader.setFloatUniform("uLightOffset", preset.lightOffset)
        shader.setFloatUniform("uSaturateOffset", preset.saturateOffset)

        for (index in 0 until 4) {
            val x = preset.points[index * 3]
            val y = preset.points[index * 3 + 1]
            val animatedX = x + sin(time + y) * preset.pointOffset
            animatedPoints[index * 2] = animatedX
            animatedPoints[index * 2 + 1] = y + cos(time + animatedX) * preset.pointOffset
        }
        shader.setFloatUniform("uPointsAnim", animatedPoints)

        val base = stage.toInt()
        val fraction = stage - base
        val start = preset.colorsFor(base)
        val end = preset.colorsFor(base + 1)
        for (index in colors.indices) colors[index] = start[index] + (end[index] - start[index]) * fraction
        shader.setFloatUniform("uColors", colors)
    }

    private fun AboutBackgroundPreset.colorsFor(index: Int) = when (index.mod(4)) {
        1 -> colors1
        3 -> colors3
        else -> colors2
    }
}

private fun Modifier.aboutBackgroundDraw(
    painter: AboutBackgroundPainter,
    preset: AboutBackgroundPreset,
    surface: Color,
    colorStage: () -> Float,
    alpha: () -> Float,
) = this then AboutBackgroundElement(painter, preset, surface, colorStage, alpha)

private data class AboutBackgroundElement(
    val painter: AboutBackgroundPainter,
    val preset: AboutBackgroundPreset,
    val surface: Color,
    val colorStage: () -> Float,
    val alpha: () -> Float,
) : ModifierNodeElement<AboutBackgroundNode>() {
    override fun create() = AboutBackgroundNode(painter, preset, surface, colorStage, alpha)
    override fun update(node: AboutBackgroundNode) = node.update(painter, preset, surface, colorStage, alpha)
}

private class AboutBackgroundNode(
    private var painter: AboutBackgroundPainter,
    private var preset: AboutBackgroundPreset,
    private var surface: Color,
    private var colorStage: () -> Float,
    private var alpha: () -> Float,
) : Modifier.Node(), DrawModifierNode {
    private var animationJob: Job? = null
    private var animationTime = 0f

    override fun onAttach() = startAnimation()
    override fun onDetach() { animationJob?.cancel() }

    fun update(
        painter: AboutBackgroundPainter,
        preset: AboutBackgroundPreset,
        surface: Color,
        colorStage: () -> Float,
        alpha: () -> Float,
    ) {
        this.painter = painter
        this.preset = preset
        this.surface = surface
        this.colorStage = colorStage
        this.alpha = alpha
        invalidateDraw()
    }

    private fun startAnimation() {
        animationJob?.cancel()
        val startOffset = animationTime
        animationJob = coroutineScope.launch {
            val origin = withFrameNanos { it }
            while (isActive) {
                val now = withFrameNanos { it }
                animationTime = startOffset + (now - origin) / 1_000_000_000f
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawRect(surface)
        val effectAlpha = alpha()
        if (effectAlpha > 0f) {
            painter.update(size.width, size.height, animationTime, preset, colorStage())
            drawRect(painter.brush, alpha = effectAlpha)
        }
        drawContent()
    }
}

private const val OS3_BACKGROUND_SHADER = """
    uniform vec2 uResolution;
    uniform float uAnimTime;
    uniform vec4 uBound;
    uniform float uTranslateY;
    uniform vec3 uPoints[4];
    uniform vec2 uPointsAnim[4];
    uniform vec4 uColors[4];
    uniform float uAlphaMulti;
    uniform float uNoiseScale;
    uniform float uPointRadiusMulti;
    uniform float uSaturateOffset;
    uniform float uLightOffset;

    vec3 rgb2hsv(vec3 c) {
        vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
        vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
        vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
        float d = q.x - min(q.w, q.y);
        float e = 1.0e-10;
        return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
    }

    vec3 hsv2rgb(vec3 c) {
        vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
        vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
        return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
    }

    float hash(vec2 p) {
        vec3 p3 = fract(vec3(p.xyx) * 0.13);
        p3 += dot(p3, p3.yzx + 3.333);
        return fract((p3.x + p3.y) * p3.z);
    }

    float perlin(vec2 x) {
        vec2 i = floor(x); vec2 f = fract(x);
        float a = hash(i); float b = hash(i + vec2(1.0, 0.0));
        float c = hash(i + vec2(0.0, 1.0)); float d = hash(i + vec2(1.0, 1.0));
        vec2 u = f * f * (3.0 - 2.0 * f);
        return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
    }

    float gradientNoise(in vec2 uv) {
        return fract(52.9829189 * fract(dot(uv, vec2(0.06711056, 0.00583715))));
    }

    vec4 main(vec2 fragCoord) {
        vec2 vUv = fragCoord / uResolution;
        vUv.y = 1.0 - vUv.y;
        vec2 uv = vUv;
        uv -= vec2(0.0, uTranslateY);
        uv.xy -= uBound.xy;
        uv.xy /= uBound.zw;

        vec4 color = vec4(0.0);
        float noiseValue = perlin(vUv * uNoiseScale + vec2(-uAnimTime, -uAnimTime));
        for (int i = 0; i < 4; i++) {
            vec4 pointColor = uColors[i];
            pointColor.rgb *= pointColor.a;
            vec2 point = uPointsAnim[i];
            float pct = smoothstep(uPoints[i].z * uPointRadiusMulti, 0.0, distance(uv, point));
            color.rgb = mix(color.rgb, pointColor.rgb, pct);
            color.a = mix(color.a, pointColor.a, pct);
        }

        float oppositeNoise = smoothstep(0.0, 1.0, noiseValue);
        color.rgb /= color.a;
        vec3 hsv = rgb2hsv(color.rgb);
        hsv.y = mix(hsv.y, 0.0, oppositeNoise * uSaturateOffset);
        color.rgb = hsv2rgb(hsv);
        color.rgb += oppositeNoise * uLightOffset;
        color.a = clamp(color.a, 0.0, 1.0) * uAlphaMulti;
        color += (10.0 / 255.0) * gradientNoise(fragCoord.xy) - (5.0 / 255.0);
        return vec4(color.rgb * color.a, color.a);
    }
"""
