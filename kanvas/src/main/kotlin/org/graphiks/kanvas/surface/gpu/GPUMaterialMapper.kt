package org.graphiks.kanvas.surface.gpu

import java.util.ArrayDeque
import java.util.Collections
import java.util.IdentityHashMap
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptorAssemblySession
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUPreparedBlenderChildDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUPreparedColorFilterChildDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUPreparedMaterialUnsupportedEvidence
import org.graphiks.kanvas.gpu.renderer.commands.GPUPreparedMaterialUnsupportedReason
import org.graphiks.kanvas.gpu.renderer.commands.GPURuntimeEffectChildDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURuntimeEffectUniformValue
import org.graphiks.kanvas.gpu.renderer.commands.containsUnsupportedMaterial
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.gpu.renderer.materials.CanonicalIdentityEncoder
import org.graphiks.kanvas.gpu.renderer.materials.GradientWgslShaderProvider
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Blender
import org.graphiks.kanvas.paint.BlenderChild
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ColorFilterChild
import org.graphiks.kanvas.paint.MeshProgram
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.ShaderChild
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

/** Exact MeshProgram material mapping; final target blend remains a separate draw fact. */
data class GPUPreparedMeshProgramMapping(
    val descriptor: GPUMaterialDescriptor.RuntimeEffect,
    val paintAlpha: Float,
    val finalTargetBlendMode: GPUBlendMode? = null,
)

/** Typed, deterministic result used by later FP-06 lowering without exceptions or fallback. */
sealed interface GPUPreparedMeshProgramMappingResult {
    data class Ready(
        val mapping: GPUPreparedMeshProgramMapping,
    ) : GPUPreparedMeshProgramMappingResult

    data class Refused(
        val code: String,
        val facts: Map<String, String>,
    ) : GPUPreparedMeshProgramMappingResult
}

internal class GPUPreparedMeshProgramMappingRefusalException(
    val code: String,
    val facts: Map<String, String>,
) : IllegalArgumentException("$code: $facts")

/**
 * Prepared-mapping active traversal safety budget.
 *
 * This bounds hostile graph recursion below JVM stack limits; it is not a
 * rendering capability claim.
 */
internal const val PREPARED_MAPPING_MAX_ACTIVE_GRAPH_DEPTH = 64

internal fun Paint.toPreparedMaterialMapping(): GPUPreparedMaterialMapping =
    toPreparedMaterialMapping(GPUMaterialDescriptorAssemblySession())

internal fun Paint.toPreparedMaterialMapping(
    descriptorAssembly: GPUMaterialDescriptorAssemblySession,
): GPUPreparedMaterialMapping {
    val shader = shader
    val paintColorFilter = colorFilter
    val graphRefusalReason = analyzePreparedMaterialGraph(
        shader = shader,
        paintColorFilter = paintColorFilter,
    )
    val colorFilterFingerprinter = PreparedColorFilterFingerprinter()
    val base = if (shader == null) {
        GPUMaterialDescriptor.SolidColor(
            r = color.r,
            g = color.g,
            b = color.b,
            a = color.a,
        )
    } else {
        shader.toPreparedMaterial(colorFilterFingerprinter, descriptorAssembly)
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
    val mapped = paintColorFilter?.let { filter ->
        tinted.withPreparedColorFilter(
            filter,
            colorFilterFingerprinter,
            descriptorAssembly,
        )
    } ?: tinted
    val globallyPrioritized = mapped.withPreparedGraphRefusal(
        graphRefusalReason,
        descriptorAssembly,
    )
    val descriptor = if (
        globallyPrioritized is GPUMaterialDescriptor.ImageDraw &&
        globallyPrioritized.alphaOnly
    ) {
        globallyPrioritized.copy(tintA = 1f)
    } else {
        globallyPrioritized
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

/** Maps one valid MeshProgram or throws a typed refusal for success-oriented callers. */
internal fun MeshProgram.toPreparedMeshProgramMapping(
    paintAlpha: Float,
    descriptorAssembly: GPUMaterialDescriptorAssemblySession =
        GPUMaterialDescriptorAssemblySession(),
): GPUPreparedMeshProgramMapping =
    when (
        val result = toPreparedMeshProgramMappingResult(
            paintAlpha = paintAlpha,
            descriptorAssembly = descriptorAssembly,
        )
    ) {
        is GPUPreparedMeshProgramMappingResult.Ready -> result.mapping
        is GPUPreparedMeshProgramMappingResult.Refused ->
            throw GPUPreparedMeshProgramMappingRefusalException(result.code, result.facts)
    }

/** Fail-closed MeshProgram mapping used by FP-06 lowering before material compilation. */
internal fun MeshProgram.toPreparedMeshProgramMappingResult(
    paintAlpha: Float,
    descriptorAssembly: GPUMaterialDescriptorAssemblySession =
        GPUMaterialDescriptorAssemblySession(),
    finalTargetBlendMode: GPUBlendMode? = null,
): GPUPreparedMeshProgramMappingResult {
    if (effect.id.isBlank()) {
        return meshProgramRefused(
            GPUPreparedVerticesRefusalCodes.MeshProgramUnregistered,
            "blank_effect_id",
        )
    }
    if (!paintAlpha.isFinite() || paintAlpha < 0f || paintAlpha > 1f) {
        return meshProgramRefused(
            GPUPreparedVerticesRefusalCodes.Material,
            "invalid_paint_alpha",
            "paintAlpha" to paintAlpha.toString(),
        )
    }

    val entries = children.entries.toList()
    val seenNames = linkedSetOf<String>()
    entries.forEach { entry ->
        if (entry.name.isBlank()) {
            return meshProgramRefused(
                GPUPreparedVerticesRefusalCodes.MeshProgramChild,
                "blank_child_name",
            )
        }
        if (!seenNames.add(entry.name)) {
            return meshProgramRefused(
                GPUPreparedVerticesRefusalCodes.MeshProgramChild,
                "duplicate_name",
                "childName" to entry.name,
            )
        }
    }

    val childMapper = PreparedMeshProgramChildMapper(descriptorAssembly)
    val mappedChildren = linkedMapOf<String, GPURuntimeEffectChildDescriptor>()
    entries.forEach { entry ->
        when (val mapped = childMapper.map(entry.child)) {
            is PreparedMeshProgramChildMapping.Ready ->
                mappedChildren[entry.name] = mapped.descriptor
            is PreparedMeshProgramChildMapping.Refused ->
                return meshProgramRefused(
                    GPUPreparedVerticesRefusalCodes.MeshProgramChild,
                    mapped.reason,
                    "childName" to entry.name,
                )
        }
    }

    return GPUPreparedMeshProgramMappingResult.Ready(
        GPUPreparedMeshProgramMapping(
            descriptor = descriptorAssembly.runtimeEffectWithChildDescriptors(
                effectId = effect.id,
                descriptorVersion = 1,
                uniforms = uniforms.toGPUUniformValues(),
                childDescriptors = mappedChildren,
            ),
            paintAlpha = paintAlpha,
            finalTargetBlendMode = finalTargetBlendMode,
        ),
    )
}

/** Retains `DrawMesh.blendMode ?: paint.blendMode` outside material identity. */
internal fun DisplayOp.DrawMesh.toPreparedMeshProgramMappingResult(
    descriptorAssembly: GPUMaterialDescriptorAssemblySession =
        GPUMaterialDescriptorAssemblySession(),
): GPUPreparedMeshProgramMappingResult {
    val meshProgram = mesh.program ?: return meshProgramRefused(
        GPUPreparedVerticesRefusalCodes.MeshProgramUnregistered,
        "missing_mesh_program",
    )
    return meshProgram.toPreparedMeshProgramMappingResult(
        paintAlpha = paint.color.a,
        descriptorAssembly = descriptorAssembly,
        finalTargetBlendMode = (blendMode ?: paint.blendMode).toGpuBlendFacts().mode,
    )
}

private fun meshProgramRefused(
    code: String,
    reason: String,
    vararg facts: Pair<String, String>,
): GPUPreparedMeshProgramMappingResult.Refused =
    GPUPreparedMeshProgramMappingResult.Refused(
        code = code,
        facts = Collections.unmodifiableMap(
            linkedMapOf("reason" to reason, *facts),
        ),
    )

private sealed interface PreparedMeshProgramChildMapping {
    data class Ready(
        val descriptor: GPURuntimeEffectChildDescriptor,
    ) : PreparedMeshProgramChildMapping

    data class Refused(val reason: String) : PreparedMeshProgramChildMapping
}

private sealed interface PreparedMeshColorFilterMapping {
    data class Ready(
        val descriptor: GPUPreparedColorFilterChildDescriptor,
        val graphDepth: Int,
    ) : PreparedMeshColorFilterMapping

    data class Refused(val reason: String) : PreparedMeshColorFilterMapping
}

private class PreparedMeshProgramChildMapper(
    private val descriptorAssembly: GPUMaterialDescriptorAssemblySession,
) {
    private val colorFilterActive =
        Collections.newSetFromMap(IdentityHashMap<ColorFilter, Boolean>())
    private val colorFilterMappings =
        IdentityHashMap<ColorFilter, PreparedMeshColorFilterMapping>()

    fun map(child: org.graphiks.kanvas.paint.MeshChild): PreparedMeshProgramChildMapping =
        when (child) {
            is ShaderChild -> mapShader(child.shader)
            is ColorFilterChild -> when (val mapped = mapColorFilter(child.filter, depth = 2)) {
                is PreparedMeshColorFilterMapping.Ready ->
                    PreparedMeshProgramChildMapping.Ready(
                        GPURuntimeEffectChildDescriptor.ColorFilter(mapped.descriptor),
                    )
                is PreparedMeshColorFilterMapping.Refused ->
                    PreparedMeshProgramChildMapping.Refused(mapped.reason)
            }
            is BlenderChild -> mapBlender(child.blender)
        }

    private fun mapShader(shader: Shader): PreparedMeshProgramChildMapping {
        val descriptor = shader.toPreparedMaterial(
            colorFilterFingerprinter = PreparedColorFilterFingerprinter(),
            descriptorAssembly = descriptorAssembly,
        )
        return if (descriptor.containsUnsupportedMaterial()) {
            PreparedMeshProgramChildMapping.Refused("unsupported_shader")
        } else {
            PreparedMeshProgramChildMapping.Ready(
                GPURuntimeEffectChildDescriptor.Shader(descriptor),
            )
        }
    }

    private fun mapBlender(blender: Blender): PreparedMeshProgramChildMapping =
        when (blender) {
            is Blender.Mode -> PreparedMeshProgramChildMapping.Ready(
                GPURuntimeEffectChildDescriptor.Blender(
                    GPUPreparedBlenderChildDescriptor.Mode(
                        blender.mode.toGpuBlendFacts().mode,
                    ),
                ),
            )
            is Blender.Arithmetic ->
                // No registered Kotlin/CPU + WGSL arithmetic authority exists yet.
                PreparedMeshProgramChildMapping.Refused(
                    "arithmetic_blender_unregistered",
                )
        }

    private fun mapColorFilter(
        filter: ColorFilter,
        depth: Int,
    ): PreparedMeshColorFilterMapping {
        if (depth > PREPARED_MAPPING_MAX_ACTIVE_GRAPH_DEPTH) {
            return PreparedMeshColorFilterMapping.Refused("child_graph_depth")
        }
        return when (val mapped = analyzeColorFilter(filter)) {
            is PreparedMeshColorFilterMapping.Ready ->
                if (mapped.graphDepth > PREPARED_MAPPING_MAX_ACTIVE_GRAPH_DEPTH - depth + 1) {
                    PreparedMeshColorFilterMapping.Refused("child_graph_depth")
                } else {
                    mapped
                }
            is PreparedMeshColorFilterMapping.Refused -> mapped
        }
    }

    private fun analyzeColorFilter(
        filter: ColorFilter,
    ): PreparedMeshColorFilterMapping {
        colorFilterMappings[filter]?.let { return it }
        if (!colorFilterActive.add(filter)) {
            return PreparedMeshColorFilterMapping.Refused("child_graph_cycle")
        }
        val result = try {
            analyzeActiveColorFilter(filter)
        } finally {
            colorFilterActive.remove(filter)
        }
        colorFilterMappings[filter] = result
        return result
    }

    private fun analyzeActiveColorFilter(
        filter: ColorFilter,
    ): PreparedMeshColorFilterMapping =
        when (filter) {
            is ColorFilter.Matrix -> {
                val values = filter.values.toList()
                if (values.size != 20 || values.any { !it.isFinite() }) {
                    PreparedMeshColorFilterMapping.Refused("invalid_color_filter_matrix")
                } else {
                    PreparedMeshColorFilterMapping.Ready(
                        GPUPreparedColorFilterChildDescriptor.Matrix(values),
                        graphDepth = 1,
                    )
                }
            }
            is ColorFilter.Blend -> PreparedMeshColorFilterMapping.Ready(
                GPUPreparedColorFilterChildDescriptor.Blend(
                    rgba = listOf(
                        filter.color.r,
                        filter.color.g,
                        filter.color.b,
                        filter.color.a,
                    ),
                    mode = filter.mode.toGpuBlendFacts().mode,
                ),
                graphDepth = 1,
            )
            is ColorFilter.Compose -> {
                val outer = analyzeColorFilter(filter.outer)
                if (outer is PreparedMeshColorFilterMapping.Refused) return outer
                val inner = analyzeColorFilter(filter.inner)
                if (inner is PreparedMeshColorFilterMapping.Refused) return inner
                PreparedMeshColorFilterMapping.Ready(
                    GPUPreparedColorFilterChildDescriptor.Compose(
                        outer = (outer as PreparedMeshColorFilterMapping.Ready).descriptor,
                        inner = (inner as PreparedMeshColorFilterMapping.Ready).descriptor,
                    ),
                    graphDepth = 1 + maxOf(outer.graphDepth, inner.graphDepth),
                )
            }
            is ColorFilter.RuntimeEffect -> analyzeRuntimeColorFilter(filter)
            is ColorFilter.Table,
            is ColorFilter.Lighting,
            ColorFilter.SRGBToLinear,
            ColorFilter.LinearToSRGB,
            is ColorFilter.HSLAMatrix,
            is ColorFilter.Lerp,
            ColorFilter.HighContrast,
            ColorFilter.Luma,
            ColorFilter.Overdraw,
            -> PreparedMeshColorFilterMapping.Refused("unsupported_color_filter")
        }

    private fun analyzeRuntimeColorFilter(
        filter: ColorFilter.RuntimeEffect,
    ): PreparedMeshColorFilterMapping {
        if (filter.effect.id.isBlank()) {
            return PreparedMeshColorFilterMapping.Refused("blank_registered_effect_id")
        }
        val mappedChildren = linkedMapOf<String, GPURuntimeEffectChildDescriptor>()
        var childGraphDepth = 0
        filter.children.entries.toList().forEach { (name, child) ->
            if (name.isBlank()) {
                return PreparedMeshColorFilterMapping.Refused("blank_child_name")
            }
            when (val mapped = analyzeColorFilter(child)) {
                is PreparedMeshColorFilterMapping.Ready -> {
                    mappedChildren[name] =
                        GPURuntimeEffectChildDescriptor.ColorFilter(mapped.descriptor)
                    childGraphDepth = maxOf(childGraphDepth, mapped.graphDepth)
                }
                is PreparedMeshColorFilterMapping.Refused -> return mapped
            }
        }
        val effectDescriptor = descriptorAssembly.runtimeEffectWithChildDescriptors(
            effectId = filter.effect.id,
            descriptorVersion = 1,
            uniforms = filter.uniforms.toGPUUniformValues(),
            childDescriptors = mappedChildren,
        )
        return PreparedMeshColorFilterMapping.Ready(
            GPUPreparedColorFilterChildDescriptor.RegisteredRuntimeEffect(
                effectDescriptor,
            ),
            graphDepth = 1 + childGraphDepth,
        )
    }
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

private fun Shader.toPreparedMaterial(
    colorFilterFingerprinter: PreparedColorFilterFingerprinter,
    descriptorAssembly: GPUMaterialDescriptorAssemblySession,
): GPUMaterialDescriptor =
    PreparedShaderMapper(colorFilterFingerprinter, descriptorAssembly).map(this)

private class PreparedShaderMapper(
    val colorFilterFingerprinter: PreparedColorFilterFingerprinter,
    val descriptorAssembly: GPUMaterialDescriptorAssemblySession,
) {
    private val active =
        Collections.newSetFromMap(IdentityHashMap<Shader, Boolean>())
    private val completed =
        IdentityHashMap<Shader, GPUMaterialDescriptor>()

    fun map(shader: Shader): GPUMaterialDescriptor {
        completed[shader]?.let { return it }
        if (!active.add(shader)) {
            return descriptorAssembly.preparedUnsupported(
                reason = GPUPreparedMaterialUnsupportedReason.SHADER_GRAPH_CYCLE,
                originalKind = shader.materialKind(),
            )
        }
        val descriptor = try {
            if (active.size > PREPARED_MAPPING_MAX_ACTIVE_GRAPH_DEPTH) {
                descriptorAssembly.preparedUnsupported(
                    reason = GPUPreparedMaterialUnsupportedReason.SHADER_GRAPH_DEPTH,
                    originalKind = shader.materialKind(),
                )
            } else {
                shader.toPreparedMaterial(this)
            }
        } finally {
            active.remove(shader)
        }
        completed[shader] = descriptor
        return descriptor
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
            mapper.descriptorAssembly.preparedUnsupported(
                GPUPreparedMaterialUnsupportedReason.GRADIENT_INTERPOLATION,
                GPUMaterialKind.LinearGradient,
            )
        }
    is Shader.RadialGradient ->
        if (interpolation == org.graphiks.kanvas.paint.ColorSpaceInterpolation.SRGB) {
            toMaterial()
        } else {
            mapper.descriptorAssembly.preparedUnsupported(
                GPUPreparedMaterialUnsupportedReason.GRADIENT_INTERPOLATION,
                GPUMaterialKind.RadialGradient,
            )
        }
    is Shader.SweepGradient ->
        if (interpolation == org.graphiks.kanvas.paint.ColorSpaceInterpolation.SRGB) {
            toMaterial()
        } else {
            mapper.descriptorAssembly.preparedUnsupported(
                GPUPreparedMaterialUnsupportedReason.GRADIENT_INTERPOLATION,
                GPUMaterialKind.SweepGradient,
            )
        }
    is Shader.ConicalGradient ->
        if (interpolation == org.graphiks.kanvas.paint.ColorSpaceInterpolation.SRGB) {
            toMaterial()
        } else {
            mapper.descriptorAssembly.preparedUnsupported(
                GPUPreparedMaterialUnsupportedReason.GRADIENT_INTERPOLATION,
                GPUMaterialKind.TwoPointConical,
            )
        }
    is Shader.Image -> toPreparedImageMaterial(mapper.descriptorAssembly)
    is Shader.Blend -> {
        val dstDesc = mapper.map(dst)
        val srcDesc = mapper.map(src)
        listOf(dstDesc, srcDesc).highestPriorityPreparedGraphTraversalRefusal()
            ?: mapper.descriptorAssembly.blendShader(
                mode = mode.name,
                dst = dstDesc,
                src = srcDesc,
            )
    }
    is Shader.RuntimeEffect -> {
        val mappedChildren = children.mapValues { (_, child) -> mapper.map(child) }
        mappedChildren.values.highestPriorityPreparedGraphTraversalRefusal()
            ?: mapper.descriptorAssembly.runtimeEffect(
                effectId = effect.id,
                descriptorVersion = 1,
                uniforms = uniforms.toGPUUniformValues(),
                children = mappedChildren,
            )
    }
    is Shader.WithLocalMatrix -> {
        val source = mapper.map(shader)
        source.preparedGraphTraversalRefusalOrNull()
            ?: mapper.descriptorAssembly.preparedUnsupported(
                reason = GPUPreparedMaterialUnsupportedReason.LOCAL_MATRIX,
                originalKind = shader.materialKind(),
                source = source,
            )
    }
    is Shader.WithColorFilter -> {
        val source = mapper.map(shader)
        source.preparedGraphTraversalRefusalOrNull()
            ?: source.withPreparedColorFilter(
                filter,
                mapper.colorFilterFingerprinter,
                mapper.descriptorAssembly,
            )
    }
    is Shader.WithWorkingColorSpace -> {
        val source = mapper.map(shader)
        source.preparedGraphTraversalRefusalOrNull()
            ?: mapper.descriptorAssembly.preparedUnsupported(
                reason = GPUPreparedMaterialUnsupportedReason.WORKING_COLOR_SPACE,
                originalKind = shader.materialKind(),
                source = source,
            )
    }
    is Shader.CoordClamp -> {
        val source = mapper.map(shader)
        source.preparedGraphTraversalRefusalOrNull()
            ?: mapper.descriptorAssembly.preparedUnsupported(
                reason = GPUPreparedMaterialUnsupportedReason.COORDINATE_CLAMP,
                originalKind = shader.materialKind(),
                source = source,
            )
    }
    is Shader.PerlinNoise -> mapper.descriptorAssembly.preparedUnsupported(
        GPUPreparedMaterialUnsupportedReason.NOISE_SHADER,
        GPUMaterialKind.SolidColor,
    )
    is Shader.FractalNoise -> mapper.descriptorAssembly.preparedUnsupported(
        GPUPreparedMaterialUnsupportedReason.NOISE_SHADER,
        GPUMaterialKind.SolidColor,
    )
}

private fun Shader.Image.toPreparedImageMaterial(
    descriptorAssembly: GPUMaterialDescriptorAssemblySession,
): GPUMaterialDescriptor {
    if (
        tileModeX != org.graphiks.kanvas.paint.TileMode.CLAMP ||
        tileModeY != org.graphiks.kanvas.paint.TileMode.CLAMP
    ) {
        return descriptorAssembly.preparedUnsupported(
            GPUPreparedMaterialUnsupportedReason.IMAGE_TILE_MODE,
            GPUMaterialKind.ImageDraw,
        )
    }
    val filterMode = when (sampling) {
        is SamplingOptions.NEAREST -> "nearest"
        is SamplingOptions.LINEAR -> "linear"
        is SamplingOptions.Cubic ->
            return descriptorAssembly.preparedUnsupported(
                GPUPreparedMaterialUnsupportedReason.IMAGE_CUBIC_SAMPLING,
                GPUMaterialKind.ImageDraw,
            )
    }
    if (
        image.colorType != ColorType.RGBA_8888 &&
        image.colorType != ColorType.BGRA_8888 &&
        image.colorType != ColorType.ALPHA_8
    ) {
        return descriptorAssembly.preparedUnsupported(
            GPUPreparedMaterialUnsupportedReason.IMAGE_COLOR_TYPE,
            GPUMaterialKind.ImageDraw,
        )
    }
    if (image.alphaType == AlphaType.PREMUL || image.alphaType == AlphaType.UNKNOWN) {
        return descriptorAssembly.preparedUnsupported(
            GPUPreparedMaterialUnsupportedReason.IMAGE_ALPHA_TYPE,
            GPUMaterialKind.ImageDraw,
        )
    }
    if (image.colorSpace != org.graphiks.kanvas.types.ColorSpace.SRGB) {
        return descriptorAssembly.preparedUnsupported(
            GPUPreparedMaterialUnsupportedReason.IMAGE_COLOR_SPACE,
            GPUMaterialKind.ImageDraw,
        )
    }
    val rgbaPixels = image.expandToPreparedRgba()
        ?: return descriptorAssembly.preparedUnsupported(
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
    fingerprinter: PreparedColorFilterFingerprinter,
    descriptorAssembly: GPUMaterialDescriptorAssemblySession,
): GPUMaterialDescriptor {
    preparedGraphTraversalRefusalOrNull()?.let { return it }
    if (filter is ColorFilter.RuntimeEffect) {
        return when (
            val result = fingerprinter.runtimeEvidence(filter)
        ) {
            PreparedColorFilterEvidenceResult.Cycle ->
                descriptorAssembly.preparedUnsupported(
                    reason = GPUPreparedMaterialUnsupportedReason.COLOR_FILTER_GRAPH_CYCLE,
                    originalKind = kind,
                    source = this,
                )
            PreparedColorFilterEvidenceResult.Depth ->
                descriptorAssembly.preparedUnsupported(
                    reason = GPUPreparedMaterialUnsupportedReason.COLOR_FILTER_GRAPH_DEPTH,
                    originalKind = kind,
                    source = this,
                )
            is PreparedColorFilterEvidenceResult.Ready ->
                descriptorAssembly.preparedUnsupported(
                    reason =
                        GPUPreparedMaterialUnsupportedReason.RUNTIME_COLOR_FILTER_PLACEMENT,
                    originalKind = kind,
                    source = this,
                    evidence = result.evidence,
            )
        }
    }
    when (fingerprinter.fingerprintResult(filter)) {
        PreparedColorFilterFingerprint.Cycle ->
            return descriptorAssembly.preparedUnsupported(
                reason = GPUPreparedMaterialUnsupportedReason.COLOR_FILTER_GRAPH_CYCLE,
                originalKind = kind,
                source = this,
            )
        PreparedColorFilterFingerprint.Depth ->
            return descriptorAssembly.preparedUnsupported(
                reason = GPUPreparedMaterialUnsupportedReason.COLOR_FILTER_GRAPH_DEPTH,
                originalKind = kind,
                source = this,
            )
        is PreparedColorFilterFingerprint.Ready -> Unit
    }
    if (this is GPUMaterialDescriptor.Unsupported) return this
    withGradientColorFilter(filter)?.let { return it }
    if (this is GPUMaterialDescriptor.SolidColor) {
        filter.applyTo(this)?.let { return it.toSolidColor() }
    }
    return descriptorAssembly.preparedUnsupported(
        reason = GPUPreparedMaterialUnsupportedReason.COLOR_FILTER,
        originalKind = kind,
        source = this,
    )
}

private sealed interface PreparedGraphAnalysis {
    data object Ready : PreparedGraphAnalysis
    data object Cycle : PreparedGraphAnalysis
    data object Depth : PreparedGraphAnalysis
}

private enum class PreparedGraphVisitState {
    Visiting,
    Complete,
}

private data class PreparedGraphFrame<T : Any>(
    val node: T,
    val children: List<T>,
    var nextChildIndex: Int = 0,
    var maxChildDepth: Int = 0,
)

private fun analyzePreparedMaterialGraph(
    shader: Shader?,
    paintColorFilter: ColorFilter?,
): GPUPreparedMaterialUnsupportedReason? {
    val colorFilterRoots = ArrayList<ColorFilter>()
    paintColorFilter?.let(colorFilterRoots::add)
    val shaderAnalysis = if (shader == null) {
        PreparedGraphAnalysis.Ready
    } else {
        analyzePreparedGraph(
            roots = listOf(shader),
            childrenOf = Shader::preparedGraphChildren,
            onDiscovered = { discovered ->
                if (discovered is Shader.WithColorFilter) {
                    colorFilterRoots += discovered.filter
                }
            },
        )
    }
    if (shaderAnalysis == PreparedGraphAnalysis.Cycle) {
        return GPUPreparedMaterialUnsupportedReason.SHADER_GRAPH_CYCLE
    }

    val colorFilterAnalysis = analyzePreparedGraph(
        roots = colorFilterRoots,
        childrenOf = ColorFilter::preparedGraphChildren,
    )
    return when {
        colorFilterAnalysis == PreparedGraphAnalysis.Cycle ->
            GPUPreparedMaterialUnsupportedReason.COLOR_FILTER_GRAPH_CYCLE
        shaderAnalysis == PreparedGraphAnalysis.Depth ->
            GPUPreparedMaterialUnsupportedReason.SHADER_GRAPH_DEPTH
        colorFilterAnalysis == PreparedGraphAnalysis.Depth ->
            GPUPreparedMaterialUnsupportedReason.COLOR_FILTER_GRAPH_DEPTH
        else -> null
    }
}

private fun <T : Any> analyzePreparedGraph(
    roots: Iterable<T>,
    childrenOf: (T) -> List<T>,
    onDiscovered: (T) -> Unit = {},
): PreparedGraphAnalysis {
    val states = IdentityHashMap<T, PreparedGraphVisitState>()
    val depths = IdentityHashMap<T, Int>()
    val stack = ArrayDeque<PreparedGraphFrame<T>>()
    var maxRootDepth = 0

    fun push(node: T) {
        states[node] = PreparedGraphVisitState.Visiting
        onDiscovered(node)
        stack.addLast(
            PreparedGraphFrame(
                node = node,
                children = childrenOf(node),
            ),
        )
    }

    for (root in roots) {
        when (states[root]) {
            PreparedGraphVisitState.Visiting ->
                return PreparedGraphAnalysis.Cycle
            PreparedGraphVisitState.Complete -> {
                maxRootDepth = maxOf(maxRootDepth, depths.getValue(root))
            }
            null -> push(root)
        }
        while (stack.isNotEmpty()) {
            val frame = stack.peekLast()
            if (frame.nextChildIndex < frame.children.size) {
                val child = frame.children[frame.nextChildIndex++]
                when (states[child]) {
                    PreparedGraphVisitState.Visiting ->
                        return PreparedGraphAnalysis.Cycle
                    PreparedGraphVisitState.Complete ->
                        frame.maxChildDepth = maxOf(
                            frame.maxChildDepth,
                            depths.getValue(child),
                        )
                    null -> push(child)
                }
            } else {
                val depth = if (frame.maxChildDepth == Int.MAX_VALUE) {
                    Int.MAX_VALUE
                } else {
                    frame.maxChildDepth + 1
                }
                depths[frame.node] = depth
                states[frame.node] = PreparedGraphVisitState.Complete
                stack.removeLast()
                stack.peekLast()?.let { parent ->
                    parent.maxChildDepth = maxOf(parent.maxChildDepth, depth)
                }
            }
        }
        maxRootDepth = maxOf(maxRootDepth, depths.getValue(root))
    }

    return if (maxRootDepth > PREPARED_MAPPING_MAX_ACTIVE_GRAPH_DEPTH) {
        PreparedGraphAnalysis.Depth
    } else {
        PreparedGraphAnalysis.Ready
    }
}

private fun Shader.preparedGraphChildren(): List<Shader> =
    when (this) {
        is Shader.Blend -> listOf(dst, src)
        is Shader.RuntimeEffect -> children.values.toList()
        is Shader.WithLocalMatrix -> listOf(shader)
        is Shader.WithColorFilter -> listOf(shader)
        is Shader.WithWorkingColorSpace -> listOf(shader)
        is Shader.CoordClamp -> listOf(shader)
        is Shader.SolidColor,
        is Shader.LinearGradient,
        is Shader.RadialGradient,
        is Shader.SweepGradient,
        is Shader.ConicalGradient,
        is Shader.Image,
        is Shader.PerlinNoise,
        is Shader.FractalNoise,
        -> emptyList()
    }

private fun ColorFilter.preparedGraphChildren(): List<ColorFilter> =
    when (this) {
        is ColorFilter.Compose -> listOf(outer, inner)
        is ColorFilter.Lerp -> listOf(dst, src)
        is ColorFilter.RuntimeEffect -> children.values.toList()
        is ColorFilter.Matrix,
        is ColorFilter.Blend,
        is ColorFilter.Table,
        is ColorFilter.Lighting,
        ColorFilter.SRGBToLinear,
        ColorFilter.LinearToSRGB,
        is ColorFilter.HSLAMatrix,
        ColorFilter.HighContrast,
        ColorFilter.Luma,
        ColorFilter.Overdraw,
        -> emptyList()
    }

private fun Shader.materialKind(): GPUMaterialKind {
    var current = this
    while (true) {
        when (val shader = current) {
            is Shader.SolidColor -> return GPUMaterialKind.SolidColor
            is Shader.LinearGradient -> return GPUMaterialKind.LinearGradient
            is Shader.RadialGradient -> return GPUMaterialKind.RadialGradient
            is Shader.SweepGradient -> return GPUMaterialKind.SweepGradient
            is Shader.ConicalGradient -> return GPUMaterialKind.TwoPointConical
            is Shader.Image -> return GPUMaterialKind.ImageDraw
            is Shader.RuntimeEffect -> return GPUMaterialKind.RuntimeEffect
            is Shader.Blend -> return GPUMaterialKind.ShaderBlend
            is Shader.WithLocalMatrix -> current = shader.shader
            is Shader.WithColorFilter -> current = shader.shader
            is Shader.WithWorkingColorSpace -> current = shader.shader
            is Shader.CoordClamp -> current = shader.shader
            is Shader.PerlinNoise,
            is Shader.FractalNoise,
            -> return GPUMaterialKind.SolidColor
        }
    }
}

private fun GPUMaterialDescriptorAssemblySession.preparedUnsupported(
    reason: GPUPreparedMaterialUnsupportedReason,
    originalKind: GPUMaterialKind,
    source: GPUMaterialDescriptor? = null,
    evidence: GPUPreparedMaterialUnsupportedEvidence? = null,
): GPUMaterialDescriptor.Unsupported =
    unsupported(
        reason = reason,
        originalKind = originalKind,
        source = source,
        evidence = evidence,
    )

private fun GPUMaterialDescriptor.withPreparedGraphRefusal(
    reason: GPUPreparedMaterialUnsupportedReason?,
    descriptorAssembly: GPUMaterialDescriptorAssemblySession,
): GPUMaterialDescriptor {
    if (reason == null) return this
    val currentRefusal = preparedGraphTraversalRefusalOrNull()
    if (currentRefusal?.reason == reason) return this
    return descriptorAssembly.preparedUnsupported(
        reason = reason,
        originalKind = kind,
        source = this,
    )
}

private fun GPUMaterialDescriptor.preparedGraphTraversalRefusalOrNull():
    GPUMaterialDescriptor.Unsupported? =
    (this as? GPUMaterialDescriptor.Unsupported)
        ?.takeIf {
            it.reason == GPUPreparedMaterialUnsupportedReason.SHADER_GRAPH_CYCLE ||
                it.reason == GPUPreparedMaterialUnsupportedReason.SHADER_GRAPH_DEPTH ||
                it.reason == GPUPreparedMaterialUnsupportedReason.COLOR_FILTER_GRAPH_CYCLE ||
                it.reason == GPUPreparedMaterialUnsupportedReason.COLOR_FILTER_GRAPH_DEPTH
        }

private fun Collection<GPUMaterialDescriptor>.highestPriorityPreparedGraphTraversalRefusal():
    GPUMaterialDescriptor.Unsupported? {
    var depthRefusal: GPUMaterialDescriptor.Unsupported? = null
    forEach { descriptor ->
        val refusal = descriptor.preparedGraphTraversalRefusalOrNull()
            ?: return@forEach
        when (refusal.reason) {
            GPUPreparedMaterialUnsupportedReason.SHADER_GRAPH_CYCLE,
            GPUPreparedMaterialUnsupportedReason.COLOR_FILTER_GRAPH_CYCLE,
            -> return refusal
            GPUPreparedMaterialUnsupportedReason.SHADER_GRAPH_DEPTH,
            GPUPreparedMaterialUnsupportedReason.COLOR_FILTER_GRAPH_DEPTH,
            -> if (depthRefusal == null) depthRefusal = refusal
            else -> error("Non-traversal refusal escaped the traversal filter")
        }
    }
    return depthRefusal
}

private sealed interface PreparedColorFilterEvidenceResult {
    data class Ready(
        val evidence: GPUPreparedMaterialUnsupportedEvidence.RuntimeColorFilter,
    ) : PreparedColorFilterEvidenceResult

    data object Cycle : PreparedColorFilterEvidenceResult
    data object Depth : PreparedColorFilterEvidenceResult
}

private sealed interface PreparedColorFilterFingerprint {
    data class Ready(val identity: String) : PreparedColorFilterFingerprint
    data object Cycle : PreparedColorFilterFingerprint
    data object Depth : PreparedColorFilterFingerprint
}

private sealed interface PreparedColorFilterChildrenFingerprint {
    data class Ready(
        val identities: Map<String, String>,
    ) : PreparedColorFilterChildrenFingerprint

    data object Cycle : PreparedColorFilterChildrenFingerprint
    data object Depth : PreparedColorFilterChildrenFingerprint
}

@OptIn(ExperimentalUnsignedTypes::class)
private class PreparedColorFilterFingerprinter {
    private val active =
        Collections.newSetFromMap(IdentityHashMap<ColorFilter, Boolean>())
    private val completedFingerprints =
        IdentityHashMap<ColorFilter, PreparedColorFilterFingerprint.Ready>()
    private val completedEvidence =
        IdentityHashMap<
            ColorFilter.RuntimeEffect,
            PreparedColorFilterEvidenceResult.Ready,
            >()

    fun runtimeEvidence(
        filter: ColorFilter.RuntimeEffect,
    ): PreparedColorFilterEvidenceResult {
        completedEvidence[filter]?.let { return it }
        if (!active.add(filter)) return PreparedColorFilterEvidenceResult.Cycle
        val result = try {
            if (active.size > PREPARED_MAPPING_MAX_ACTIVE_GRAPH_DEPTH) {
                PreparedColorFilterEvidenceResult.Depth
            } else {
                val uniforms = filter.uniforms.toGPUUniformValues()
                when (val children = fingerprintChildren(filter.children)) {
                    PreparedColorFilterChildrenFingerprint.Cycle ->
                        PreparedColorFilterEvidenceResult.Cycle
                    PreparedColorFilterChildrenFingerprint.Depth ->
                        PreparedColorFilterEvidenceResult.Depth
                    is PreparedColorFilterChildrenFingerprint.Ready ->
                        PreparedColorFilterEvidenceResult.Ready(
                            GPUPreparedMaterialUnsupportedEvidence.RuntimeColorFilter(
                                effectId = filter.effect.id,
                                uniforms = uniforms,
                                childIdentities = children.identities,
                            ),
                        )
                }
            }
        } finally {
            active.remove(filter)
        }
        if (result is PreparedColorFilterEvidenceResult.Ready) {
            completedEvidence[filter] = result
        }
        return result
    }

    fun fingerprintResult(filter: ColorFilter): PreparedColorFilterFingerprint =
        fingerprint(filter)

    private fun fingerprint(filter: ColorFilter): PreparedColorFilterFingerprint {
        completedFingerprints[filter]?.let { return it }
        if (!active.add(filter)) return PreparedColorFilterFingerprint.Cycle
        val result = try {
            if (active.size > PREPARED_MAPPING_MAX_ACTIVE_GRAPH_DEPTH) {
                PreparedColorFilterFingerprint.Depth
            } else {
                fingerprintActive(filter)
            }
        } finally {
            active.remove(filter)
        }
        if (result is PreparedColorFilterFingerprint.Ready) {
            completedFingerprints[filter] = result
        }
        return result
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
                    PreparedColorFilterChildrenFingerprint.Depth ->
                        PreparedColorFilterFingerprint.Depth
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
        val firstResult = fingerprint(first)
        if (firstResult == PreparedColorFilterFingerprint.Cycle) {
            return PreparedColorFilterFingerprint.Cycle
        }
        val secondResult = fingerprint(second)
        if (secondResult == PreparedColorFilterFingerprint.Cycle) {
            return PreparedColorFilterFingerprint.Cycle
        }
        if (
            firstResult == PreparedColorFilterFingerprint.Depth ||
            secondResult == PreparedColorFilterFingerprint.Depth
        ) {
            return PreparedColorFilterFingerprint.Depth
        }
        val firstIdentity =
            (firstResult as PreparedColorFilterFingerprint.Ready).identity
        val secondIdentity =
            (secondResult as PreparedColorFilterFingerprint.Ready).identity
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
        var depthFound = false
        children.keys.sorted().forEach { name ->
            when (val child = fingerprint(children.getValue(name))) {
                PreparedColorFilterFingerprint.Cycle ->
                    return PreparedColorFilterChildrenFingerprint.Cycle
                PreparedColorFilterFingerprint.Depth ->
                    depthFound = true
                is PreparedColorFilterFingerprint.Ready ->
                    identities[name] = child.identity
            }
        }
        return if (depthFound) {
            PreparedColorFilterChildrenFingerprint.Depth
        } else {
            PreparedColorFilterChildrenFingerprint.Ready(identities)
        }
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
