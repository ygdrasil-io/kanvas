package org.graphiks.kanvas.surface.gpu

import java.util.Collections
import java.util.IdentityHashMap
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUPreparedMaterialUnsupportedEvidence
import org.graphiks.kanvas.gpu.renderer.commands.GPUPreparedMaterialUnsupportedReason
import org.graphiks.kanvas.gpu.renderer.commands.GPURuntimeEffectUniformValue
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.gpu.renderer.materials.CanonicalIdentityEncoder
import org.graphiks.kanvas.gpu.renderer.materials.GradientWgslShaderProvider
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.pipeline.UniformValue
import org.graphiks.kanvas.types.a
import org.graphiks.kanvas.types.b
import org.graphiks.kanvas.types.g
import org.graphiks.kanvas.types.r
import kotlin.math.pow

data class GPUPreparedMaterialMapping(
    val descriptor: GPUMaterialDescriptor,
    val paintAlpha: Float,
)

internal fun Paint.toPreparedMaterialMapping(): GPUPreparedMaterialMapping {
    val shader = shader
    val base = if (shader == null) {
        GPUMaterialDescriptor.SolidColor(
            r = color.r,
            g = color.g,
            b = color.b,
            a = color.a,
        )
    } else {
        shader.toPreparedMaterial()
    }
    val tinted = if (base is GPUMaterialDescriptor.ImageDraw && base.alphaOnly) {
        base.copy(
            tintR = color.r,
            tintG = color.g,
            tintB = color.b,
            tintA = 1f,
        )
    } else {
        base
    }
    val mapped = colorFilter?.let { filter ->
        tinted.withPreparedColorFilter(filter)
    } ?: tinted
    val descriptor = if (mapped is GPUMaterialDescriptor.ImageDraw && mapped.alphaOnly) {
        mapped.copy(tintA = 1f)
    } else {
        mapped
    }
    val paintAlpha = if (shader == null && descriptor is GPUMaterialDescriptor.SolidColor) {
        1f
    } else {
        color.a
    }
    return GPUPreparedMaterialMapping(
        descriptor = descriptor,
        paintAlpha = paintAlpha,
    )
}

internal fun Paint.toMaterial(): GPUMaterialDescriptor =
    mapMaterial(
        shaderMapper = { shader -> shader.toMaterial() },
        preserveRuntimePayload = false,
    )

private fun Paint.mapMaterial(
    shaderMapper: (Shader) -> GPUMaterialDescriptor,
    preserveRuntimePayload: Boolean,
): GPUMaterialDescriptor {
    val shader = this.shader
    val base = if (shader != null) {
        val material = shaderMapper(shader)
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
            uniforms = if (preserveRuntimePayload) {
                cf.uniforms.toGPUUniformValues()
            } else {
                emptyMap()
            },
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

private fun Shader.toPreparedMaterial(): GPUMaterialDescriptor =
    PreparedShaderMapper().map(this)

private class PreparedShaderMapper {
    private val active =
        Collections.newSetFromMap(IdentityHashMap<Shader, Boolean>())

    fun map(shader: Shader): GPUMaterialDescriptor {
        if (!active.add(shader)) {
            return preparedUnsupported(
                reason = GPUPreparedMaterialUnsupportedReason.SHADER_GRAPH_CYCLE,
                originalKind = shader.materialKind(),
            )
        }
        return try {
            shader.toPreparedMaterial(this)
        } finally {
            active.remove(shader)
        }
    }
}

private fun Shader.toPreparedMaterial(
    mapper: PreparedShaderMapper,
): GPUMaterialDescriptor = when (this) {
    is Shader.SolidColor -> toMaterial()
    is Shader.LinearGradient ->
        if (interpolation == org.graphiks.kanvas.paint.ColorSpaceInterpolation.SRGB) {
            toMaterial()
        } else {
            preparedUnsupported(
                GPUPreparedMaterialUnsupportedReason.GRADIENT_INTERPOLATION,
                GPUMaterialKind.LinearGradient,
            )
        }
    is Shader.RadialGradient ->
        if (interpolation == org.graphiks.kanvas.paint.ColorSpaceInterpolation.SRGB) {
            toMaterial()
        } else {
            preparedUnsupported(
                GPUPreparedMaterialUnsupportedReason.GRADIENT_INTERPOLATION,
                GPUMaterialKind.RadialGradient,
            )
        }
    is Shader.SweepGradient ->
        if (interpolation == org.graphiks.kanvas.paint.ColorSpaceInterpolation.SRGB) {
            toMaterial()
        } else {
            preparedUnsupported(
                GPUPreparedMaterialUnsupportedReason.GRADIENT_INTERPOLATION,
                GPUMaterialKind.SweepGradient,
            )
        }
    is Shader.ConicalGradient ->
        if (interpolation == org.graphiks.kanvas.paint.ColorSpaceInterpolation.SRGB) {
            toMaterial()
        } else {
            preparedUnsupported(
                GPUPreparedMaterialUnsupportedReason.GRADIENT_INTERPOLATION,
                GPUMaterialKind.TwoPointConical,
            )
        }
    is Shader.Image -> toPreparedImageMaterial()
    is Shader.Blend -> {
        val dstDesc = mapper.map(dst)
        val srcDesc = mapper.map(src)
        listOf(dstDesc, srcDesc).firstPreparedGraphCycle()
            ?: GPUMaterialDescriptor.BlendShader(
                mode = mode.name,
                dst = dstDesc,
                src = srcDesc,
            )
    }
    is Shader.RuntimeEffect -> {
        val mappedChildren = children.mapValues { (_, child) -> mapper.map(child) }
        mappedChildren.values.firstPreparedGraphCycle()
            ?: GPUMaterialDescriptor.RuntimeEffect(
                effectId = effect.id,
                descriptorVersion = 1,
                uniforms = uniforms.toGPUUniformValues(),
                children = mappedChildren,
            )
    }
    is Shader.WithLocalMatrix -> {
        val source = mapper.map(shader)
        source.preparedGraphCycleOrNull()
            ?: preparedUnsupported(
                reason = GPUPreparedMaterialUnsupportedReason.LOCAL_MATRIX,
                originalKind = shader.materialKind(),
                source = source,
            )
    }
    is Shader.WithColorFilter -> {
        val source = mapper.map(shader)
        source.preparedGraphCycleOrNull()
            ?: source.withPreparedColorFilter(filter)
    }
    is Shader.WithWorkingColorSpace -> {
        val source = mapper.map(shader)
        source.preparedGraphCycleOrNull()
            ?: preparedUnsupported(
                reason = GPUPreparedMaterialUnsupportedReason.WORKING_COLOR_SPACE,
                originalKind = shader.materialKind(),
                source = source,
            )
    }
    is Shader.CoordClamp -> {
        val source = mapper.map(shader)
        source.preparedGraphCycleOrNull()
            ?: preparedUnsupported(
                reason = GPUPreparedMaterialUnsupportedReason.COORDINATE_CLAMP,
                originalKind = shader.materialKind(),
                source = source,
            )
    }
    is Shader.PerlinNoise -> preparedUnsupported(
        GPUPreparedMaterialUnsupportedReason.NOISE_SHADER,
        GPUMaterialKind.SolidColor,
    )
    is Shader.FractalNoise -> preparedUnsupported(
        GPUPreparedMaterialUnsupportedReason.NOISE_SHADER,
        GPUMaterialKind.SolidColor,
    )
}

private fun Shader.Image.toPreparedImageMaterial(): GPUMaterialDescriptor {
    if (
        tileModeX != org.graphiks.kanvas.paint.TileMode.CLAMP ||
        tileModeY != org.graphiks.kanvas.paint.TileMode.CLAMP
    ) {
        return preparedUnsupported(
            GPUPreparedMaterialUnsupportedReason.IMAGE_TILE_MODE,
            GPUMaterialKind.ImageDraw,
        )
    }
    val filterMode = when (sampling) {
        is SamplingOptions.NEAREST -> "nearest"
        is SamplingOptions.LINEAR -> "linear"
        is SamplingOptions.Cubic ->
            return preparedUnsupported(
                GPUPreparedMaterialUnsupportedReason.IMAGE_CUBIC_SAMPLING,
                GPUMaterialKind.ImageDraw,
            )
    }
    if (
        image.colorType != ColorType.RGBA_8888 &&
        image.colorType != ColorType.BGRA_8888 &&
        image.colorType != ColorType.ALPHA_8
    ) {
        return preparedUnsupported(
            GPUPreparedMaterialUnsupportedReason.IMAGE_COLOR_TYPE,
            GPUMaterialKind.ImageDraw,
        )
    }
    if (image.alphaType == AlphaType.PREMUL || image.alphaType == AlphaType.UNKNOWN) {
        return preparedUnsupported(
            GPUPreparedMaterialUnsupportedReason.IMAGE_ALPHA_TYPE,
            GPUMaterialKind.ImageDraw,
        )
    }
    if (image.colorSpace != org.graphiks.kanvas.types.ColorSpace.SRGB) {
        return preparedUnsupported(
            GPUPreparedMaterialUnsupportedReason.IMAGE_COLOR_SPACE,
            GPUMaterialKind.ImageDraw,
        )
    }
    val rgbaPixels = image.expandToPreparedRgba()
        ?: return preparedUnsupported(
            GPUPreparedMaterialUnsupportedReason.IMAGE_PIXEL_PAYLOAD,
            GPUMaterialKind.ImageDraw,
        )
    return GPUMaterialDescriptor.ImageDraw(
        imageSourceId = image.sourceId,
        imageWidth = image.width,
        imageHeight = image.height,
        rgbaPixels = rgbaPixels,
        samplingFilterMode = filterMode,
        alphaOnly = image.colorType == ColorType.ALPHA_8,
    )
}

private fun GPUMaterialDescriptor.withPreparedColorFilter(
    filter: ColorFilter,
): GPUMaterialDescriptor {
    preparedGraphCycleOrNull()?.let { return it }
    if (filter is ColorFilter.RuntimeEffect) {
        return when (
            val result = PreparedColorFilterFingerprinter().runtimeEvidence(filter)
        ) {
            PreparedColorFilterEvidenceResult.Cycle ->
                preparedUnsupported(
                    reason = GPUPreparedMaterialUnsupportedReason.COLOR_FILTER_GRAPH_CYCLE,
                    originalKind = kind,
                    source = this,
                )
            is PreparedColorFilterEvidenceResult.Ready ->
                preparedUnsupported(
                    reason =
                        GPUPreparedMaterialUnsupportedReason.RUNTIME_COLOR_FILTER_PLACEMENT,
                    originalKind = kind,
                    source = this,
                    evidence = result.evidence,
                )
        }
    }
    if (PreparedColorFilterFingerprinter().containsCycle(filter)) {
        return preparedUnsupported(
            reason = GPUPreparedMaterialUnsupportedReason.COLOR_FILTER_GRAPH_CYCLE,
            originalKind = kind,
            source = this,
        )
    }
    if (this is GPUMaterialDescriptor.Unsupported) return this
    withGradientColorFilter(filter)?.let { return it }
    if (this is GPUMaterialDescriptor.SolidColor) {
        filter.applyTo(this)?.let { return it.toSolidColor() }
    }
    return preparedUnsupported(
        reason = GPUPreparedMaterialUnsupportedReason.COLOR_FILTER,
        originalKind = kind,
        source = this,
    )
}

private fun Shader.materialKind(): GPUMaterialKind = when (this) {
    is Shader.SolidColor -> GPUMaterialKind.SolidColor
    is Shader.LinearGradient -> GPUMaterialKind.LinearGradient
    is Shader.RadialGradient -> GPUMaterialKind.RadialGradient
    is Shader.SweepGradient -> GPUMaterialKind.SweepGradient
    is Shader.ConicalGradient -> GPUMaterialKind.TwoPointConical
    is Shader.Image -> GPUMaterialKind.ImageDraw
    is Shader.RuntimeEffect -> GPUMaterialKind.RuntimeEffect
    is Shader.Blend -> GPUMaterialKind.ShaderBlend
    is Shader.WithLocalMatrix -> shader.materialKind()
    is Shader.WithColorFilter -> shader.materialKind()
    is Shader.WithWorkingColorSpace -> shader.materialKind()
    is Shader.CoordClamp -> shader.materialKind()
    is Shader.PerlinNoise,
    is Shader.FractalNoise,
    -> GPUMaterialKind.SolidColor
}

private fun preparedUnsupported(
    reason: GPUPreparedMaterialUnsupportedReason,
    originalKind: GPUMaterialKind,
    source: GPUMaterialDescriptor? = null,
    evidence: GPUPreparedMaterialUnsupportedEvidence? = null,
): GPUMaterialDescriptor.Unsupported =
    GPUMaterialDescriptor.Unsupported(
        reason = reason,
        originalKind = originalKind,
        source = source,
        evidence = evidence,
    )

private fun GPUMaterialDescriptor.preparedGraphCycleOrNull():
    GPUMaterialDescriptor.Unsupported? =
    (this as? GPUMaterialDescriptor.Unsupported)
        ?.takeIf {
            it.reason == GPUPreparedMaterialUnsupportedReason.SHADER_GRAPH_CYCLE ||
                it.reason == GPUPreparedMaterialUnsupportedReason.COLOR_FILTER_GRAPH_CYCLE
        }

private fun Collection<GPUMaterialDescriptor>.firstPreparedGraphCycle():
    GPUMaterialDescriptor.Unsupported? =
    firstNotNullOfOrNull { it.preparedGraphCycleOrNull() }

private sealed interface PreparedColorFilterEvidenceResult {
    data class Ready(
        val evidence: GPUPreparedMaterialUnsupportedEvidence.RuntimeColorFilter,
    ) : PreparedColorFilterEvidenceResult

    data object Cycle : PreparedColorFilterEvidenceResult
}

private sealed interface PreparedColorFilterFingerprint {
    data class Ready(val identity: String) : PreparedColorFilterFingerprint
    data object Cycle : PreparedColorFilterFingerprint
}

private sealed interface PreparedColorFilterChildrenFingerprint {
    data class Ready(
        val identities: Map<String, String>,
    ) : PreparedColorFilterChildrenFingerprint

    data object Cycle : PreparedColorFilterChildrenFingerprint
}

@OptIn(ExperimentalUnsignedTypes::class)
private class PreparedColorFilterFingerprinter {
    private val active =
        Collections.newSetFromMap(IdentityHashMap<ColorFilter, Boolean>())

    fun runtimeEvidence(
        filter: ColorFilter.RuntimeEffect,
    ): PreparedColorFilterEvidenceResult {
        if (!active.add(filter)) return PreparedColorFilterEvidenceResult.Cycle
        return try {
            val uniforms = filter.uniforms.toGPUUniformValues()
            when (val children = fingerprintChildren(filter.children)) {
                PreparedColorFilterChildrenFingerprint.Cycle ->
                    PreparedColorFilterEvidenceResult.Cycle
                is PreparedColorFilterChildrenFingerprint.Ready ->
                    PreparedColorFilterEvidenceResult.Ready(
                        GPUPreparedMaterialUnsupportedEvidence.RuntimeColorFilter(
                            effectId = filter.effect.id,
                            uniforms = uniforms,
                            childIdentities = children.identities,
                        ),
                    )
            }
        } finally {
            active.remove(filter)
        }
    }

    fun containsCycle(filter: ColorFilter): Boolean =
        fingerprint(filter) == PreparedColorFilterFingerprint.Cycle

    private fun fingerprint(filter: ColorFilter): PreparedColorFilterFingerprint {
        if (!active.add(filter)) return PreparedColorFilterFingerprint.Cycle
        return try {
            fingerprintActive(filter)
        } finally {
            active.remove(filter)
        }
    }

    private fun fingerprintActive(
        filter: ColorFilter,
    ): PreparedColorFilterFingerprint =
        when (filter) {
            is ColorFilter.Matrix ->
                readyIdentity("Matrix") {
                    floats("values", filter.values)
                }
            is ColorFilter.Blend ->
                readyIdentity("Blend") {
                    long("color", filter.color.packed.toLong())
                    text("mode", filter.mode.name)
                }
            is ColorFilter.Compose ->
                fingerprintBinary(
                    type = "Compose",
                    firstName = "outer",
                    first = filter.outer,
                    secondName = "inner",
                    second = filter.inner,
                )
            is ColorFilter.Table ->
                readyIdentity("Table") {
                    bytes(
                        "table",
                        ByteArray(filter.table.size) { index ->
                            filter.table[index].toByte()
                        },
                    )
                }
            is ColorFilter.Lighting ->
                readyIdentity("Lighting") {
                    long("mul", filter.mul.packed.toLong())
                    long("add", filter.add.packed.toLong())
                }
            ColorFilter.SRGBToLinear -> readyIdentity("SRGBToLinear")
            ColorFilter.LinearToSRGB -> readyIdentity("LinearToSRGB")
            is ColorFilter.HSLAMatrix ->
                readyIdentity("HSLAMatrix") {
                    floats("values", filter.values)
                }
            is ColorFilter.Lerp ->
                fingerprintBinary(
                    type = "Lerp",
                    firstName = "dst",
                    first = filter.dst,
                    secondName = "src",
                    second = filter.src,
                ) { floatBits("t", filter.t) }
            ColorFilter.HighContrast -> readyIdentity("HighContrast")
            ColorFilter.Luma -> readyIdentity("Luma")
            ColorFilter.Overdraw -> readyIdentity("Overdraw")
            is ColorFilter.RuntimeEffect -> {
                val uniforms = filter.uniforms.toGPUUniformValues()
                when (val children = fingerprintChildren(filter.children)) {
                    PreparedColorFilterChildrenFingerprint.Cycle ->
                        PreparedColorFilterFingerprint.Cycle
                    is PreparedColorFilterChildrenFingerprint.Ready ->
                        readyIdentity("RuntimeEffect") {
                            text("effectId", filter.effect.id)
                            typedUniforms("uniforms", uniforms)
                            childIdentities("children", children.identities)
                        }
                }
            }
        }

    private fun fingerprintBinary(
        type: String,
        firstName: String,
        first: ColorFilter,
        secondName: String,
        second: ColorFilter,
        additionalFields: CanonicalIdentityEncoder.() -> Unit = {},
    ): PreparedColorFilterFingerprint {
        val firstIdentity =
            (fingerprint(first) as? PreparedColorFilterFingerprint.Ready)?.identity
                ?: return PreparedColorFilterFingerprint.Cycle
        val secondIdentity =
            (fingerprint(second) as? PreparedColorFilterFingerprint.Ready)?.identity
                ?: return PreparedColorFilterFingerprint.Cycle
        return readyIdentity(type) {
            additionalFields()
            text(firstName, firstIdentity)
            text(secondName, secondIdentity)
        }
    }

    private fun fingerprintChildren(
        children: Map<String, ColorFilter>,
    ): PreparedColorFilterChildrenFingerprint {
        val identities = linkedMapOf<String, String>()
        children.keys.sorted().forEach { name ->
            when (val child = fingerprint(children.getValue(name))) {
                PreparedColorFilterFingerprint.Cycle ->
                    return PreparedColorFilterChildrenFingerprint.Cycle
                is PreparedColorFilterFingerprint.Ready ->
                    identities[name] = child.identity
            }
        }
        return PreparedColorFilterChildrenFingerprint.Ready(identities)
    }

    private fun readyIdentity(
        type: String,
        fields: CanonicalIdentityEncoder.() -> Unit = {},
    ): PreparedColorFilterFingerprint.Ready {
        val encoder =
            CanonicalIdentityEncoder(PREPARED_COLOR_FILTER_IDENTITY_DOMAIN)
                .text("type", type)
        encoder.fields()
        return PreparedColorFilterFingerprint.Ready(encoder.digestIdentity())
    }
}

private fun CanonicalIdentityEncoder.floats(
    name: String,
    values: FloatArray,
): CanonicalIdentityEncoder {
    int("$name.count", values.size)
    values.forEachIndexed { index, value ->
        floatBits("$name[$index]", value)
    }
    return this
}

private fun CanonicalIdentityEncoder.typedUniforms(
    name: String,
    uniforms: Map<String, GPURuntimeEffectUniformValue>,
): CanonicalIdentityEncoder {
    int("$name.count", uniforms.size)
    uniforms.keys.sorted().forEachIndexed { index, uniformName ->
        val field = "$name[$index]"
        text("$field.name", uniformName)
        when (val value = uniforms.getValue(uniformName)) {
            is GPURuntimeEffectUniformValue.Float1 -> {
                text("$field.type", "Float1")
                floatBits("$field.value", value.value)
            }
            is GPURuntimeEffectUniformValue.Float2 -> {
                text("$field.type", "Float2")
                floatBits("$field.x", value.x)
                floatBits("$field.y", value.y)
            }
            is GPURuntimeEffectUniformValue.Float3 -> {
                text("$field.type", "Float3")
                floatBits("$field.x", value.x)
                floatBits("$field.y", value.y)
                floatBits("$field.z", value.z)
            }
            is GPURuntimeEffectUniformValue.Float4 -> {
                text("$field.type", "Float4")
                floatBits("$field.x", value.x)
                floatBits("$field.y", value.y)
                floatBits("$field.z", value.z)
                floatBits("$field.w", value.w)
            }
            is GPURuntimeEffectUniformValue.Int1 -> {
                text("$field.type", "Int1")
                int("$field.value", value.value)
            }
            is GPURuntimeEffectUniformValue.Matrix3x3 -> {
                text("$field.type", "Matrix3x3")
                value.values.forEachIndexed { valueIndex, component ->
                    floatBits("$field.value[$valueIndex]", component)
                }
            }
            is GPURuntimeEffectUniformValue.Matrix4x4 -> {
                text("$field.type", "Matrix4x4")
                value.values.forEachIndexed { valueIndex, component ->
                    floatBits("$field.value[$valueIndex]", component)
                }
            }
        }
    }
    return this
}

private fun CanonicalIdentityEncoder.childIdentities(
    name: String,
    identities: Map<String, String>,
): CanonicalIdentityEncoder {
    int("$name.count", identities.size)
    identities.keys.sorted().forEachIndexed { index, childName ->
        text("$name[$index].name", childName)
        text("$name[$index].identity", identities.getValue(childName))
    }
    return this
}

private fun UniformBlock.toGPUUniformValues(): Map<String, GPURuntimeEffectUniformValue> =
    entries.mapValues { (_, value) ->
        when (value) {
            is UniformValue.F1 -> GPURuntimeEffectUniformValue.Float1(value.v)
            is UniformValue.F2 -> GPURuntimeEffectUniformValue.Float2(value.x, value.y)
            is UniformValue.F3 ->
                GPURuntimeEffectUniformValue.Float3(value.x, value.y, value.z)
            is UniformValue.F4 ->
                GPURuntimeEffectUniformValue.Float4(value.x, value.y, value.z, value.w)
            is UniformValue.I1 -> GPURuntimeEffectUniformValue.Int1(value.v)
            is UniformValue.M3 ->
                GPURuntimeEffectUniformValue.Matrix3x3(
                    listOf(
                        value.m.scaleX,
                        value.m.skewX,
                        value.m.transX,
                        value.m.skewY,
                        value.m.scaleY,
                        value.m.transY,
                        value.m.persp0,
                        value.m.persp1,
                        value.m.persp2,
                    ),
                )
            is UniformValue.M4 ->
                GPURuntimeEffectUniformValue.Matrix4x4(value.values.toList())
        }
    }

private const val PREPARED_COLOR_FILTER_IDENTITY_DOMAIN =
    "prepared-runtime-color-filter-child-v1"

/**
 * Expands non-RGBA image pixels to RGBA for GPU upload.
 * ALPHA_8 (1 byte/pixel) → RGBA (4 bytes/pixel, R=G=B=0, A=alpha).
 * RGBA passes through unchanged; BGRA is swizzled to RGBA.
 */
private fun org.graphiks.kanvas.image.Image.expandToRgba(): ByteArray {
    val pixels = this.pixels ?: return byteArrayOf()
    if (colorType == ColorType.RGBA_8888) return pixels
    if (colorType == ColorType.BGRA_8888) {
        return pixels.copyOf().also { rgba ->
            for (offset in rgba.indices step 4) {
                val blue = rgba[offset]
                rgba[offset] = rgba[offset + 2]
                rgba[offset + 2] = blue
            }
        }
    }
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

private fun org.graphiks.kanvas.image.Image.expandToPreparedRgba(): ByteArray? {
    val source = pixels ?: return null
    val expectedSourceSize = exactImageByteCount(width, height, colorType.bytesPerPixel)
        ?: return null
    val expectedOutputSize = exactImageByteCount(width, height, 4)
        ?: return null
    if (source.size != expectedSourceSize) return null

    val rgba = when (colorType) {
        ColorType.RGBA_8888 -> source.copyOf()
        ColorType.BGRA_8888 -> source.copyOf().also { output ->
            for (offset in output.indices step 4) {
                val blue = output[offset]
                output[offset] = output[offset + 2]
                output[offset + 2] = blue
            }
        }
        ColorType.ALPHA_8 -> ByteArray(expectedOutputSize).also { output ->
            for (index in source.indices) {
                val outputOffset = index * 4
                output[outputOffset] = 0
                output[outputOffset + 1] = 0
                output[outputOffset + 2] = 0
                output[outputOffset + 3] = source[index]
            }
        }
        else -> return null
    }
    if (alphaType == AlphaType.OPAQUE) {
        for (offset in 3 until rgba.size step 4) {
            rgba[offset] = 0xff.toByte()
        }
    }
    return rgba
}

private fun exactImageByteCount(
    width: Int,
    height: Int,
    bytesPerPixel: Int,
): Int? {
    if (width <= 0 || height <= 0 || bytesPerPixel <= 0) return null
    val count = try {
        Math.multiplyExact(
            Math.multiplyExact(width.toLong(), height.toLong()),
            bytesPerPixel.toLong(),
        )
    } catch (_: ArithmeticException) {
        return null
    }
    if (count > Int.MAX_VALUE) return null
    return count.toInt()
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
