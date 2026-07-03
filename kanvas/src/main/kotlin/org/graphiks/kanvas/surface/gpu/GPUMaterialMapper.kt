package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.wgsl.GradientWgslShaderProvider
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.types.a
import org.graphiks.kanvas.types.b
import org.graphiks.kanvas.types.g
import org.graphiks.kanvas.types.r

internal fun Paint.toMaterial(): GPUMaterialDescriptor {
    val shader = this.shader
    if (shader != null) {
        return shader.toMaterial()
    }
    return GPUMaterialDescriptor.SolidColor(
        r = this.color.r,
        g = this.color.g,
        b = this.color.b,
        a = this.color.a,
    )
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
    is Shader.WithColorFilter -> this.shader.toMaterial()
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
            rgba[off] = 0
            rgba[off + 1] = 0
            rgba[off + 2] = 0
            rgba[off + 3] = a
        }
        return rgba
    }
    return pixels
}
