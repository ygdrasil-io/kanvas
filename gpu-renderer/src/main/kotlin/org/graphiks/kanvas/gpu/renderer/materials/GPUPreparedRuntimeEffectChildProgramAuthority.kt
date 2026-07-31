package org.graphiks.kanvas.gpu.renderer.materials

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedRuntimeEffectChildCpuProgram
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUBlendFormulaProgramLibrary

/** Canonical CPU plus parser-ready WGSL authority for prepared runtime-effect child programs. */
internal object GPUPreparedRuntimeEffectChildProgramAuthority {
    fun compileMatrix(
        name: String,
        values: List<Float>,
    ): GPUPreparedRuntimeEffectChildProgram? {
        if (values.size != 20 || values.any { value -> !value.isFinite() }) return null
        val snapshot = immutableList(values)
        val bytes = snapshot.toFloatByteArray()
        val functionName = childFunctionName("color_filter_matrix", name)
        val source = matrixWgsl(functionName, snapshot)
        val sourceHash = sha256Hex(source.encodeToByteArray())
        return GPUPreparedRuntimeEffectChildProgram(
            name = name,
            role = GPUPreparedRuntimeEffectChildRole.ColorFilter,
            programKey = CanonicalIdentityEncoder(
                "prepared-runtime-effect-matrix-child-v2",
            )
                .text("sourceHash", sourceHash)
                .bytes("uniformBytes", bytes)
                .digestIdentity(),
            abiHash = compiledRuntimeEffectChildAbiHash(
                role = GPUPreparedRuntimeEffectChildRole.ColorFilter,
                sourceHash = sourceHash,
                uniformByteCount = bytes.size,
            ),
            uniformBytes = immutableList(bytes.toUnsignedInts()),
            resourceFacts = emptyList(),
            wgslSource = source,
            evaluationFunction = functionName,
            cpuProgram = GPUPreparedRuntimeEffectChildCpuProgram.Matrix(snapshot),
        )
    }

    fun compileBlendColorFilter(
        name: String,
        rgba: List<Float>,
        mode: GPUBlendMode,
    ): GPUPreparedRuntimeEffectChildProgram? {
        if (rgba.size != 4 || rgba.any { value -> !value.isFinite() || value !in 0f..1f }) {
            return null
        }
        val formulaFunction = childFunctionName("color_filter_blend_formula", name)
        val evaluationFunction = childFunctionName("color_filter_blend", name)
        val formula = runCatching {
            GPUBlendFormulaLibrary.selectedBlendFunctionWgsl(mode, formulaFunction)
        }.getOrNull() ?: return null
        val sourcePremul = immutableList(
            listOf(rgba[0] * rgba[3], rgba[1] * rgba[3], rgba[2] * rgba[3], rgba[3]),
        )
        val source = """
            $formula

            fn $evaluationFunction(color: vec4<f32>) -> vec4<f32> {
                let source = vec4<f32>(${sourcePremul.joinToString(", ") { it.wgslFloat() }});
                return $formulaFunction(source, color);
            }
        """.trimIndent()
        val bytes = rgba.toFloatByteArray()
        val sourceHash = sha256Hex(source.encodeToByteArray())
        return GPUPreparedRuntimeEffectChildProgram(
            name = name,
            role = GPUPreparedRuntimeEffectChildRole.ColorFilter,
            programKey = CanonicalIdentityEncoder(
                "prepared-runtime-effect-blend-color-filter-child-v2",
            )
                .text("mode", mode.gpuLabel)
                .text("sourceHash", sourceHash)
                .bytes("uniformBytes", bytes)
                .digestIdentity(),
            abiHash = compiledRuntimeEffectChildAbiHash(
                role = GPUPreparedRuntimeEffectChildRole.ColorFilter,
                sourceHash = sourceHash,
                uniformByteCount = bytes.size,
            ),
            uniformBytes = immutableList(bytes.toUnsignedInts()),
            resourceFacts = emptyList(),
            wgslSource = source,
            evaluationFunction = evaluationFunction,
            cpuProgram = GPUPreparedRuntimeEffectChildCpuProgram.BlendConstant(
                sourcePremul = sourcePremul,
                modeLabel = mode.gpuLabel,
            ),
        )
    }

    fun composeColorFilters(
        name: String,
        inner: GPUPreparedRuntimeEffectChildProgram,
        outer: GPUPreparedRuntimeEffectChildProgram,
    ): GPUPreparedRuntimeEffectChildProgram? {
        if (
            inner.role != GPUPreparedRuntimeEffectChildRole.ColorFilter ||
            outer.role != GPUPreparedRuntimeEffectChildRole.ColorFilter ||
            inner.wgslSource.isBlank() || outer.wgslSource.isBlank()
        ) {
            return null
        }
        val evaluationFunction = childFunctionName("color_filter_compose", name)
        val source = mergePreparedRuntimeEffectWgsl(
            listOf(
                inner.wgslSource,
                outer.wgslSource,
                """
            fn $evaluationFunction(color: vec4<f32>) -> vec4<f32> {
                return ${outer.evaluationFunction}(${inner.evaluationFunction}(color));
            }
                """.trimIndent(),
            ),
        )
        val sourceHash = sha256Hex(source.encodeToByteArray())
        return GPUPreparedRuntimeEffectChildProgram(
            name = name,
            role = GPUPreparedRuntimeEffectChildRole.ColorFilter,
            programKey = CanonicalIdentityEncoder(
                "prepared-runtime-effect-compose-color-filter-child-v2",
            )
                .text("inner", inner.programKey)
                .text("outer", outer.programKey)
                .text("sourceHash", sourceHash)
                .digestIdentity(),
            abiHash = compiledRuntimeEffectChildAbiHash(
                role = GPUPreparedRuntimeEffectChildRole.ColorFilter,
                sourceHash = sourceHash,
                uniformByteCount = inner.uniformBytes.size + outer.uniformBytes.size,
            ),
            uniformBytes = immutableList(inner.uniformBytes + outer.uniformBytes),
            resourceFacts = immutableList(
                inner.resourceFacts.map { fact -> "inner.$fact" } +
                    outer.resourceFacts.map { fact -> "outer.$fact" },
            ),
            wgslSource = source,
            evaluationFunction = evaluationFunction,
            cpuProgram = GPUPreparedRuntimeEffectChildCpuProgram.Compose(
                inner = inner.cpuProgram,
                outer = outer.cpuProgram,
            ),
        )
    }

    fun compileModeBlender(
        name: String,
        mode: GPUBlendMode,
    ): GPUPreparedRuntimeEffectChildProgram? {
        val evaluationFunction = childFunctionName("blender_mode", name)
        val source = runCatching {
            GPUBlendFormulaLibrary.selectedBlendFunctionWgsl(mode, evaluationFunction)
        }.getOrNull() ?: return null
        val sourceHash = sha256Hex(source.encodeToByteArray())
        return GPUPreparedRuntimeEffectChildProgram(
            name = name,
            role = GPUPreparedRuntimeEffectChildRole.Blender,
            programKey = CanonicalIdentityEncoder(
                "prepared-runtime-effect-mode-blender-child-v2",
            )
                .text("mode", mode.gpuLabel)
                .text("sourceHash", sourceHash)
                .digestIdentity(),
            abiHash = compiledRuntimeEffectChildAbiHash(
                role = GPUPreparedRuntimeEffectChildRole.Blender,
                sourceHash = sourceHash,
                uniformByteCount = 0,
            ),
            uniformBytes = emptyList(),
            resourceFacts = emptyList(),
            wgslSource = source,
            evaluationFunction = evaluationFunction,
            cpuProgram = GPUPreparedRuntimeEffectChildCpuProgram.ModeBlender(mode.gpuLabel),
        )
    }
}

/**
 * Independent CPU parity oracle for prepared child programs.
 *
 * Mode selection and executable WGSL remain owned by [GPUBlendFormulaLibrary]; this evaluator
 * deliberately does not generate WGSL or feed the selected shader formula back into CPU results.
 */
internal object GPUPreparedRuntimeEffectChildProgramExecutor {
    fun evaluateColorFilter(
        program: GPUPreparedRuntimeEffectChildProgram,
        input: List<Float>,
    ): List<Float> {
        require(program.role == GPUPreparedRuntimeEffectChildRole.ColorFilter)
        require(input.size == 4 && input.all(Float::isFinite))
        return evaluateColorFilter(program.cpuProgram, input)
    }

    fun evaluateBlender(
        program: GPUPreparedRuntimeEffectChildProgram,
        source: List<Float>,
        destination: List<Float>,
    ): List<Float> {
        require(program.role == GPUPreparedRuntimeEffectChildRole.Blender)
        require(source.size == 4 && source.all(Float::isFinite))
        require(destination.size == 4 && destination.all(Float::isFinite))
        val cpu = program.cpuProgram as? GPUPreparedRuntimeEffectChildCpuProgram.ModeBlender
            ?: error("Prepared child has no registered CPU blender semantics")
        return blendPremul(cpu.modeLabel.toBlendMode(), source, destination)
    }

    private fun evaluateColorFilter(
        cpu: GPUPreparedRuntimeEffectChildCpuProgram,
        input: List<Float>,
    ): List<Float> = when (cpu) {
            is GPUPreparedRuntimeEffectChildCpuProgram.Matrix -> {
                val m = cpu.values
                List(4) { row ->
                    val offset = row * 5
                    (
                        m[offset] * input[0] +
                            m[offset + 1] * input[1] +
                            m[offset + 2] * input[2] +
                            m[offset + 3] * input[3] +
                            m[offset + 4]
                        ).coerceIn(0f, 1f)
                }
            }
            is GPUPreparedRuntimeEffectChildCpuProgram.BlendConstant -> blendPremul(
                mode = cpu.modeLabel.toBlendMode(),
                source = cpu.sourcePremul,
                destination = input,
            )
            is GPUPreparedRuntimeEffectChildCpuProgram.Compose -> evaluateColorFilter(
                cpu = cpu.outer,
                input = evaluateColorFilter(cpu.inner, input),
            )
            is GPUPreparedRuntimeEffectChildCpuProgram.ModeBlender ->
                error("Prepared blender semantics cannot execute as a color filter")
            is GPUPreparedRuntimeEffectChildCpuProgram.Shader ->
                error("Prepared shader semantics cannot execute as a color filter")
        }
}

/** Merges callable programs while emitting the canonical advanced-blend helper module once. */
internal fun mergePreparedRuntimeEffectWgsl(sources: List<String>): String {
    val helperModule = GPUBlendFormulaProgramLibrary.advancedHelpersWgsl
    var needsHelperModule = false
    val bodies = sources.asSequence()
        .filter(String::isNotBlank)
        .map { source ->
            if (helperModule in source) {
                needsHelperModule = true
                source.replace(helperModule, "").trim()
            } else {
                source.trim()
            }
        }
        .filter(String::isNotBlank)
        .toList()
    return (listOfNotNull(helperModule.takeIf { needsHelperModule }) + bodies)
        .joinToString("\n\n")
}

internal fun compiledRuntimeEffectChildAbiHash(
    role: GPUPreparedRuntimeEffectChildRole,
    sourceHash: String,
    uniformByteCount: Int,
): String = CanonicalIdentityEncoder("prepared-runtime-effect-child-program-abi-v1")
    .text("invocationAbi", preparedRuntimeEffectChildAbiHash(role))
    .text("sourceHash", sourceHash)
    .int("uniformByteCount", uniformByteCount)
    .digestIdentity()

private fun childFunctionName(kind: String, name: String): String =
    "kanvas_${kind}_${sha256Hex(name.encodeToByteArray())}"

private fun matrixWgsl(
    functionName: String,
    values: List<Float>,
): String {
    fun row(row: Int): String {
        val offset = row * 5
        return (0..3).joinToString(", ") { column -> values[offset + column].wgslFloat() }
    }
    val translate = (0..3).joinToString(", ") { row -> values[row * 5 + 4].wgslFloat() }
    return """
        fn $functionName(color: vec4<f32>) -> vec4<f32> {
            let row0 = vec4<f32>(${row(0)});
            let row1 = vec4<f32>(${row(1)});
            let row2 = vec4<f32>(${row(2)});
            let row3 = vec4<f32>(${row(3)});
            let translate = vec4<f32>($translate);
            return clamp(
                vec4<f32>(
                    dot(row0, color),
                    dot(row1, color),
                    dot(row2, color),
                    dot(row3, color),
                ) + translate,
                vec4<f32>(0.0),
                vec4<f32>(1.0),
            );
        }
    """.trimIndent()
}

private fun Float.wgslFloat(): String = toString().lowercase().let { literal ->
    if ('.' in literal || 'e' in literal) literal else "$literal.0"
}

private fun List<Float>.toFloatByteArray(): ByteArray =
    ByteBuffer.allocate(size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
        forEach(::putFloat)
    }.array()

private fun ByteArray.toUnsignedInts(): List<Int> = map { byte -> byte.toInt() and 0xff }

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun String.toBlendMode(): GPUBlendMode =
    requireNotNull(GPUBlendMode.entries.singleOrNull { mode -> mode.gpuLabel == this }) {
        "Prepared child blend mode is not canonical: $this"
    }

private fun blendPremul(
    mode: GPUBlendMode,
    source: List<Float>,
    destination: List<Float>,
): List<Float> {
    val result = when (mode) {
        GPUBlendMode.CLEAR -> List(4) { 0f }
        GPUBlendMode.SRC -> source
        GPUBlendMode.DST -> destination
        GPUBlendMode.SRC_OVER -> combine(source, destination) { sourceChannel, destinationChannel ->
            sourceChannel + destinationChannel * (1f - source[3])
        }
        GPUBlendMode.DST_OVER -> combine(source, destination) { sourceChannel, destinationChannel ->
            destinationChannel + sourceChannel * (1f - destination[3])
        }
        GPUBlendMode.SRC_IN -> List(4) { channel -> source[channel] * destination[3] }
        GPUBlendMode.DST_IN -> List(4) { channel -> destination[channel] * source[3] }
        GPUBlendMode.SRC_OUT -> List(4) { channel -> source[channel] * (1f - destination[3]) }
        GPUBlendMode.DST_OUT -> List(4) { channel -> destination[channel] * (1f - source[3]) }
        GPUBlendMode.SRC_ATOP -> combine(source, destination) { sourceChannel, destinationChannel ->
            sourceChannel * destination[3] + destinationChannel * (1f - source[3])
        }
        GPUBlendMode.DST_ATOP -> combine(source, destination) { sourceChannel, destinationChannel ->
            destinationChannel * source[3] + sourceChannel * (1f - destination[3])
        }
        GPUBlendMode.XOR -> combine(source, destination) { sourceChannel, destinationChannel ->
            sourceChannel * (1f - destination[3]) + destinationChannel * (1f - source[3])
        }
        GPUBlendMode.PLUS -> combine(source, destination) { sourceChannel, destinationChannel ->
            min(1f, sourceChannel + destinationChannel)
        }
        GPUBlendMode.MODULATE -> combine(source, destination, Float::times)
        GPUBlendMode.SCREEN,
        GPUBlendMode.MULTIPLY,
        GPUBlendMode.OVERLAY,
        GPUBlendMode.DARKEN,
        GPUBlendMode.LIGHTEN,
        GPUBlendMode.COLOR_DODGE,
        GPUBlendMode.COLOR_BURN,
        GPUBlendMode.HARD_LIGHT,
        GPUBlendMode.SOFT_LIGHT,
        GPUBlendMode.DIFFERENCE,
        GPUBlendMode.EXCLUSION,
        GPUBlendMode.HUE,
        GPUBlendMode.SATURATION,
        GPUBlendMode.COLOR,
        GPUBlendMode.LUMINOSITY,
        -> advancedBlend(mode, source, destination)
    }
    return result.map { channel -> channel.coerceIn(0f, 1f) }
}

private fun advancedBlend(
    mode: GPUBlendMode,
    source: List<Float>,
    destination: List<Float>,
): List<Float> {
    if (source[3] == 0f) return destination
    val sourceColor = unpremul(source)
    val destinationColor = unpremul(destination)
    val blended = blendColor(mode, sourceColor, destinationColor)
    return listOf(
        source[0] * (1f - destination[3]) +
            destination[0] * (1f - source[3]) + source[3] * destination[3] * blended[0],
        source[1] * (1f - destination[3]) +
            destination[1] * (1f - source[3]) + source[3] * destination[3] * blended[1],
        source[2] * (1f - destination[3]) +
            destination[2] * (1f - source[3]) + source[3] * destination[3] * blended[2],
        source[3] + destination[3] * (1f - source[3]),
    )
}

private fun blendColor(
    mode: GPUBlendMode,
    source: List<Float>,
    destination: List<Float>,
): List<Float> {
    val separable = List(3) { channel ->
        val sourceChannel = source[channel]
        val destinationChannel = destination[channel]
        when (mode) {
            GPUBlendMode.MULTIPLY -> sourceChannel * destinationChannel
            GPUBlendMode.SCREEN -> sourceChannel + destinationChannel - sourceChannel * destinationChannel
            GPUBlendMode.OVERLAY -> if (destinationChannel <= .5f) {
                2f * sourceChannel * destinationChannel
            } else {
                1f - 2f * (1f - sourceChannel) * (1f - destinationChannel)
            }
            GPUBlendMode.DARKEN -> min(sourceChannel, destinationChannel)
            GPUBlendMode.LIGHTEN -> max(sourceChannel, destinationChannel)
            GPUBlendMode.COLOR_DODGE -> when {
                destinationChannel == 0f -> 0f
                sourceChannel == 1f -> 1f
                else -> min(1f, destinationChannel / (1f - sourceChannel))
            }
            GPUBlendMode.COLOR_BURN -> when {
                destinationChannel == 1f -> 1f
                sourceChannel == 0f -> 0f
                else -> 1f - min(1f, (1f - destinationChannel) / sourceChannel)
            }
            GPUBlendMode.HARD_LIGHT -> if (sourceChannel <= .5f) {
                2f * sourceChannel * destinationChannel
            } else {
                1f - 2f * (1f - sourceChannel) * (1f - destinationChannel)
            }
            GPUBlendMode.SOFT_LIGHT -> softLight(destinationChannel, sourceChannel)
            GPUBlendMode.DIFFERENCE -> abs(destinationChannel - sourceChannel)
            GPUBlendMode.EXCLUSION -> sourceChannel + destinationChannel - 2f * sourceChannel * destinationChannel
            GPUBlendMode.HUE,
            GPUBlendMode.SATURATION,
            GPUBlendMode.COLOR,
            GPUBlendMode.LUMINOSITY,
            -> 0f
            else -> error("$mode is not an advanced blend mode")
        }
    }
    return when (mode) {
        GPUBlendMode.HUE -> setLum(setSat(source, sat(destination)), lum(destination))
        GPUBlendMode.SATURATION -> setLum(setSat(destination, sat(source)), lum(destination))
        GPUBlendMode.COLOR -> setLum(source, lum(destination))
        GPUBlendMode.LUMINOSITY -> setLum(destination, lum(source))
        else -> separable
    }
}

private fun unpremul(color: List<Float>): List<Float> =
    if (color[3] == 0f) List(3) { 0f } else List(3) { channel -> color[channel] / color[3] }

private fun softLight(backdrop: Float, source: Float): Float = if (source <= .5f) {
    backdrop - (1f - 2f * source) * backdrop * (1f - backdrop)
} else {
    val d = if (backdrop <= .25f) {
        ((16f * backdrop - 12f) * backdrop + 4f) * backdrop
    } else {
        sqrt(backdrop)
    }
    backdrop + (2f * source - 1f) * (d - backdrop)
}

private fun lum(color: List<Float>): Float = .3f * color[0] + .59f * color[1] + .11f * color[2]

private fun sat(color: List<Float>): Float = color.maxOrNull()!! - color.minOrNull()!!

private fun setSat(color: List<Float>, saturation: Float): List<Float> {
    val low = color.minOrNull()!!
    val high = color.maxOrNull()!!
    if (high == low) return List(3) { 0f }
    return List(3) { channel -> (color[channel] - low) * saturation / (high - low) }
}

private fun setLum(color: List<Float>, luminosity: Float): List<Float> =
    clipColor(List(3) { channel -> color[channel] + luminosity - lum(color) })

private fun clipColor(color: List<Float>): List<Float> {
    val luminosity = lum(color)
    val low = color.minOrNull()!!
    val high = color.maxOrNull()!!
    var result = color
    if (low < 0f && luminosity != low) {
        result = List(3) { channel ->
            luminosity + (result[channel] - luminosity) * luminosity / (luminosity - low)
        }
    }
    if (high > 1f && high != luminosity) {
        result = List(3) { channel ->
            luminosity + (result[channel] - luminosity) * (1f - luminosity) / (high - luminosity)
        }
    }
    return result
}

private inline fun combine(
    source: List<Float>,
    destination: List<Float>,
    operation: (Float, Float) -> Float,
): List<Float> = List(4) { channel -> operation(source[channel], destination[channel]) }
