package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.materials.GradientWgslShaderProvider
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.types.a
import org.graphiks.kanvas.types.b
import org.graphiks.kanvas.types.g
import org.graphiks.kanvas.types.r
import kotlin.math.pow

internal fun Paint.toMaterial(): GPUMaterialDescriptor {
    val shader = this.shader
    val base = if (shader != null) {
        val material = shader.toMaterial()
        if (material is GPUMaterialDescriptor.ImageDraw && material.alphaOnly) {
            material.copy(
                tintR = this.color.r,
                tintG = this.color.g,
                tintB = this.color.b,
                tintA = this.color.a,
            )
        } else {
            material
        }
    } else {
        GPUMaterialDescriptor.SolidColor(
            r = this.color.r,
            g = this.color.g,
            b = this.color.b,
            a = this.color.a,
        )
    }

    val cf = this.colorFilter
    if (cf is ColorFilter.RuntimeEffect) {
        return GPUMaterialDescriptor.RuntimeEffect(
            effectId = cf.effect.id,
            descriptorVersion = 1,
        )
    }
    if (cf != null) {
        base.withGradientColorFilter(cf)?.let { return it }
    }
    if (cf != null && base is GPUMaterialDescriptor.SolidColor) {
        return cf.applyTo(base)?.toSolidColor() ?: base
    }
    return base
}

internal fun Paint.isStroke(): Boolean = style == PaintStyle.STROKE

internal fun Shader.toMaterial(): GPUMaterialDescriptor = when (this) {
    is Shader.SolidColor -> GPUMaterialDescriptor.SolidColor(
        r = this.color.r,
        g = this.color.g,
        b = this.color.b,
        a = this.color.a,
    )
    is Shader.LinearGradient -> {
        val first = this.stops.first()
        val last = this.stops.last()
        val allPos = FloatArray(this.stops.size) { this.stops[it].position }
        val allCol = FloatArray(this.stops.size * 4) { i ->
            val stop = this.stops[i / 4]
            when (i % 4) { 0 -> stop.color.r; 1 -> stop.color.g; 2 -> stop.color.b; else -> stop.color.a }
        }
        val tileMode = when (this.tileMode) {
            org.graphiks.kanvas.paint.TileMode.CLAMP -> "clamp"
            org.graphiks.kanvas.paint.TileMode.REPEAT -> "repeat"
            org.graphiks.kanvas.paint.TileMode.MIRROR -> "mirror"
            org.graphiks.kanvas.paint.TileMode.DECAL -> "decal"
        }
        val desc = GPUMaterialDescriptor.LinearGradient(
            startX = this.start.x, startY = this.start.y,
            endX = this.end.x, endY = this.end.y,
            startR = first.color.r, startG = first.color.g, startB = first.color.b, startA = first.color.a,
            endR = last.color.r, endG = last.color.g, endB = last.color.b, endA = last.color.a,
            tileMode = tileMode,
            allStopPositions = allPos, allStopColors = allCol,
        )
        if (GradientWgslShaderProvider.canHandle(desc)) {
            val hash = GradientWgslShaderProvider.uniformLayoutHashFor(desc)
            desc.copy(snippetSourceHash = hash)
        } else {
            desc
        }
    }
    is Shader.RadialGradient -> {
        val first = this.stops.first()
        val last = this.stops.last()
        val allPos = FloatArray(this.stops.size) { this.stops[it].position }
        val allCol = FloatArray(this.stops.size * 4) { i ->
            val stop = this.stops[i / 4]
            when (i % 4) { 0 -> stop.color.r; 1 -> stop.color.g; 2 -> stop.color.b; else -> stop.color.a }
        }
        val tileMode = when (this.tileMode) {
            org.graphiks.kanvas.paint.TileMode.CLAMP -> "clamp"
            org.graphiks.kanvas.paint.TileMode.REPEAT -> "repeat"
            org.graphiks.kanvas.paint.TileMode.MIRROR -> "mirror"
            org.graphiks.kanvas.paint.TileMode.DECAL -> "decal"
        }
        val desc = GPUMaterialDescriptor.RadialGradient(
            centerX = this.center.x, centerY = this.center.y,
            radius = this.radius,
            startR = first.color.r, startG = first.color.g, startB = first.color.b, startA = first.color.a,
            endR = last.color.r, endG = last.color.g, endB = last.color.b, endA = last.color.a,
            tileMode = tileMode,
            allStopPositions = allPos, allStopColors = allCol,
        )
        if (GradientWgslShaderProvider.canHandle(desc)) {
            val hash = GradientWgslShaderProvider.uniformLayoutHashFor(desc)
            desc.copy(snippetSourceHash = hash)
        } else {
            desc
        }
    }
    is Shader.Image -> {
        val image = this.image
        val filterMode = when (this.sampling) {
            is SamplingOptions.NEAREST -> "nearest"
            is SamplingOptions.LINEAR -> "linear"
            is SamplingOptions.Cubic -> "linear"
        }
        GPUMaterialDescriptor.ImageDraw(
            imageSourceId = image.sourceId,
            imageWidth = image.width,
            imageHeight = image.height,
            rgbaPixels = image.expandToRgba(),
            samplingFilterMode = filterMode,
            alphaOnly = image.colorType == ColorType.ALPHA_8,
        )
    }
    is Shader.Blend -> {
        val dstDesc = this.dst.toMaterial()
        val srcDesc = this.src.toMaterial()
        val modeStr = this.mode.name
        val desc = GPUMaterialDescriptor.BlendShader(
            mode = modeStr,
            dst = dstDesc,
            src = srcDesc,
        )
        if (org.graphiks.kanvas.gpu.renderer.materials.GPUBlendShaderLowering.canHandle(desc)) {
            desc.copy(
                wgslCombined = org.graphiks.kanvas.gpu.renderer.materials.BlendWgslBuilder.buildWgsl(dstDesc, srcDesc, modeStr),
                uniformBytes = org.graphiks.kanvas.gpu.renderer.materials.BlendWgslBuilder.packUniforms(dstDesc, srcDesc, modeStr),
            )
        } else {
            // fallback: use src shader only (drop the blend)
            srcDesc
        }
    }
    is Shader.RuntimeEffect -> {
        val id = this.effect.id
        GPUMaterialDescriptor.RuntimeEffect(effectId = id, descriptorVersion = 1)
    }
    is Shader.WithLocalMatrix -> this.shader.toMaterial()
    is Shader.WithColorFilter -> this.shader.toMaterial().let { material ->
        material.withGradientColorFilter(this.filter) ?: material
    }
    is Shader.SweepGradient -> {
        val first = this.stops.first()
        val last = this.stops.last()
        val allPos = FloatArray(this.stops.size) { this.stops[it].position }
        val allCol = FloatArray(this.stops.size * 4) { i ->
            val stop = this.stops[i / 4]
            when (i % 4) { 0 -> stop.color.r; 1 -> stop.color.g; 2 -> stop.color.b; else -> stop.color.a }
        }
        val tileMode = when (this.tileMode) {
            org.graphiks.kanvas.paint.TileMode.CLAMP -> "clamp"
            org.graphiks.kanvas.paint.TileMode.REPEAT -> "repeat"
            org.graphiks.kanvas.paint.TileMode.MIRROR -> "mirror"
            org.graphiks.kanvas.paint.TileMode.DECAL -> "decal"
        }
        val desc = GPUMaterialDescriptor.SweepGradient(
            centerX = this.center.x, centerY = this.center.y,
            startAngle = this.startAngle, endAngle = this.endAngle,
            startR = first.color.r, startG = first.color.g, startB = first.color.b, startA = first.color.a,
            endR = last.color.r, endG = last.color.g, endB = last.color.b, endA = last.color.a,
            tileMode = tileMode,
            allStopPositions = allPos, allStopColors = allCol,
        )
        if (GradientWgslShaderProvider.canHandle(desc)) {
            val hash = GradientWgslShaderProvider.uniformLayoutHashFor(desc)
            desc.copy(snippetSourceHash = hash)
        } else {
            desc
        }
    }
    is Shader.ConicalGradient -> {
        val first = this.stops.first()
        val last = this.stops.last()
        val allPos = FloatArray(this.stops.size) { this.stops[it].position }
        val allCol = FloatArray(this.stops.size * 4) { i ->
            val stop = this.stops[i / 4]
            when (i % 4) { 0 -> stop.color.r; 1 -> stop.color.g; 2 -> stop.color.b; else -> stop.color.a }
        }
        val tileMode = when (this.tileMode) {
            org.graphiks.kanvas.paint.TileMode.CLAMP -> "clamp"
            org.graphiks.kanvas.paint.TileMode.REPEAT -> "repeat"
            org.graphiks.kanvas.paint.TileMode.MIRROR -> "mirror"
            org.graphiks.kanvas.paint.TileMode.DECAL -> "decal"
        }
        val desc = GPUMaterialDescriptor.ConicalGradient(
            startX = this.start.x, startY = this.start.y,
            endX = this.end.x, endY = this.end.y,
            startRadius = this.startRadius, endRadius = this.endRadius,
            startR = first.color.r, startG = first.color.g, startB = first.color.b, startA = first.color.a,
            endR = last.color.r, endG = last.color.g, endB = last.color.b, endA = last.color.a,
            tileMode = tileMode,
            allStopPositions = allPos, allStopColors = allCol,
        )
        if (GradientWgslShaderProvider.canHandle(desc)) {
            val hash = GradientWgslShaderProvider.uniformLayoutHashFor(desc)
            desc.copy(snippetSourceHash = hash)
        } else {
            desc
        }
    }
    is Shader.PerlinNoise -> GPUMaterialDescriptor.SolidColor(r = 0f, g = 0f, b = 0f, a = 0f)
    is Shader.FractalNoise -> GPUMaterialDescriptor.SolidColor(r = 0f, g = 0f, b = 0f, a = 0f)
    is Shader.WithWorkingColorSpace -> this.shader.toMaterial()
    is Shader.CoordClamp -> this.shader.toMaterial()
}

/**
 * Expands non-RGBA image pixels to RGBA for GPU upload.
 * ALPHA_8 (1 byte/pixel) → RGBA (4 bytes/pixel, R=G=B=0, A=alpha).
 * RGBA formats pass through unchanged.
 */
private fun org.graphiks.kanvas.image.Image.expandToRgba(): ByteArray {
    val pixels = this.pixels ?: return byteArrayOf()
    if (colorType == ColorType.RGBA_8888 || colorType == ColorType.BGRA_8888) return pixels
    if (colorType == ColorType.ALPHA_8) {
        val rgba = ByteArray(width * height * 4)
        for (i in 0 until width * height) {
            val a = pixels[i]
            val off = i * 4
            rgba[off] = a
            rgba[off + 1] = a
            rgba[off + 2] = a
            rgba[off + 3] = a
        }
        return rgba
    }
    return pixels
}

private data class Rgba(
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float,
) {
    fun clamped(): Rgba = Rgba(
        r = r.coerceIn(0f, 1f),
        g = g.coerceIn(0f, 1f),
        b = b.coerceIn(0f, 1f),
        a = a.coerceIn(0f, 1f),
    )

    fun toSolidColor(): GPUMaterialDescriptor.SolidColor {
        val c = clamped()
        return GPUMaterialDescriptor.SolidColor(c.r, c.g, c.b, c.a)
    }
}

private fun GPUMaterialDescriptor.SolidColor.toRgba(): Rgba = Rgba(r, g, b, a)

private fun ColorFilter.applyTo(input: GPUMaterialDescriptor.SolidColor): Rgba? =
    applyTo(input.toRgba())

private fun ColorFilter.applyTo(input: Rgba): Rgba? = when (this) {
    is ColorFilter.Matrix -> values.applyColorMatrix(input)
    is ColorFilter.HSLAMatrix -> null
    is ColorFilter.Table -> table.applyTable(input)
    is ColorFilter.Lighting -> Rgba(
        r = input.r * mul.r + add.r,
        g = input.g * mul.g + add.g,
        b = input.b * mul.b + add.b,
        a = input.a,
    ).clamped()
    is ColorFilter.Blend -> blendColorFilter(color.toRgba(), input, mode)
    is ColorFilter.Compose -> inner.applyTo(input)?.let { outer.applyTo(it) }
    is ColorFilter.Lerp -> {
        val dstColor = dst.applyTo(input) ?: return null
        val srcColor = src.applyTo(input) ?: return null
        lerp(dstColor, srcColor, t)
    }
    ColorFilter.Luma -> {
        val luma = 0.2126f * input.r + 0.7152f * input.g + 0.0722f * input.b
        Rgba(0f, 0f, 0f, luma * input.a).clamped()
    }
    ColorFilter.SRGBToLinear -> Rgba(
        r = srgbToLinear(input.r),
        g = srgbToLinear(input.g),
        b = srgbToLinear(input.b),
        a = input.a,
    )
    ColorFilter.LinearToSRGB -> Rgba(
        r = linearToSrgb(input.r),
        g = linearToSrgb(input.g),
        b = linearToSrgb(input.b),
        a = input.a,
    )
    ColorFilter.HighContrast,
    ColorFilter.Overdraw,
    is ColorFilter.RuntimeEffect -> null
}

private fun org.graphiks.kanvas.types.Color.toRgba(): Rgba = Rgba(r, g, b, a)

private fun FloatArray.applyColorMatrix(input: Rgba): Rgba? {
    if (size < 20) return null
    return Rgba(
        r = this[0] * input.r + this[1] * input.g + this[2] * input.b + this[3] * input.a + this[4],
        g = this[5] * input.r + this[6] * input.g + this[7] * input.b + this[8] * input.a + this[9],
        b = this[10] * input.r + this[11] * input.g + this[12] * input.b + this[13] * input.a + this[14],
        a = this[15] * input.r + this[16] * input.g + this[17] * input.b + this[18] * input.a + this[19],
    ).clamped()
}

private fun UByteArray.applyTable(input: Rgba): Rgba? {
    if (size < 256) return null
    fun sample(v: Float): Float = this[(v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()].toInt() / 255f
    return Rgba(sample(input.r), sample(input.g), sample(input.b), sample(input.a))
}

private fun blendColorFilter(src: Rgba, dst: Rgba, mode: BlendMode): Rgba? {
    val sp = src.premultiplied()
    val dp = dst.premultiplied()
    val out = when (mode) {
        BlendMode.CLEAR -> Premul(0f, 0f, 0f, 0f)
        BlendMode.SRC -> sp
        BlendMode.DST -> dp
        BlendMode.SRC_OVER -> sp + dp * (1f - sp.a)
        BlendMode.DST_OVER -> dp + sp * (1f - dp.a)
        BlendMode.SRC_IN -> sp * dp.a
        BlendMode.DST_IN -> dp * sp.a
        BlendMode.SRC_OUT -> sp * (1f - dp.a)
        BlendMode.DST_OUT -> dp * (1f - sp.a)
        BlendMode.SRC_ATOP -> sp * dp.a + dp * (1f - sp.a)
        BlendMode.DST_ATOP -> dp * sp.a + sp * (1f - dp.a)
        BlendMode.XOR -> sp * (1f - dp.a) + dp * (1f - sp.a)
        BlendMode.PLUS -> (sp + dp).clamped()
        BlendMode.MODULATE -> Premul(
            r = sp.r * dp.r,
            g = sp.g * dp.g,
            b = sp.b * dp.b,
            a = sp.a * dp.a,
        )
        else -> return null
    }
    return out.toUnpremultiplied()
}

private data class Premul(
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float,
) {
    operator fun plus(other: Premul): Premul =
        Premul(r + other.r, g + other.g, b + other.b, a + other.a)

    operator fun times(scale: Float): Premul =
        Premul(r * scale, g * scale, b * scale, a * scale)

    fun clamped(): Premul = Premul(
        r = r.coerceIn(0f, 1f),
        g = g.coerceIn(0f, 1f),
        b = b.coerceIn(0f, 1f),
        a = a.coerceIn(0f, 1f),
    )

    fun toUnpremultiplied(): Rgba {
        val c = clamped()
        if (c.a <= 0f) return Rgba(0f, 0f, 0f, 0f)
        return Rgba(c.r / c.a, c.g / c.a, c.b / c.a, c.a).clamped()
    }
}

private fun Rgba.premultiplied(): Premul {
    val c = clamped()
    return Premul(c.r * c.a, c.g * c.a, c.b * c.a, c.a)
}

private fun lerp(dst: Rgba, src: Rgba, t: Float): Rgba {
    val u = t.coerceIn(0f, 1f)
    return Rgba(
        r = dst.r * (1f - u) + src.r * u,
        g = dst.g * (1f - u) + src.g * u,
        b = dst.b * (1f - u) + src.b * u,
        a = dst.a * (1f - u) + src.a * u,
    ).clamped()
}

private fun linearToSrgb(c: Float): Float {
    val v = c.coerceIn(0f, 1f)
    return if (v <= 0.0031308f) {
        v * 12.92f
    } else {
        1.055f * v.pow(1f / 2.4f) - 0.055f
    }
}
