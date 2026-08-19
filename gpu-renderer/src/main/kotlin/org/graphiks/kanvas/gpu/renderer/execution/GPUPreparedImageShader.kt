package org.graphiks.kanvas.gpu.renderer.execution

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedAtlasSourceBlend
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageBindingLayoutTopology
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.wgsl.validation.KanvasWGSLReflectionProvider
import org.graphiks.kanvas.gpu.renderer.wgsl.validation.KanvasWGSLValidator

internal data class GPUPreparedImageShaderContract(
    val sourceHash: String,
    val bindingLayoutHash: String,
    val reflectedBindingsHash: String,
)

internal data class GPUPreparedImageBindingLayoutContract(
    val identity: String,
    val reflectedBindingsHash: String,
    val uniformMinBindingSize: Long,
    val group: Int,
    val uniformBinding: Int,
    val textureBinding: Int,
    val samplerBinding: Int,
)

internal sealed interface GPUPreparedImageShaderValidationResult {
    data class Ready(
        val bindingLayout: GPUPreparedImageBindingLayoutContract,
        val shaderContract: GPUPreparedImageShaderContract,
    ) : GPUPreparedImageShaderValidationResult

    data class Refused(
        val code: String,
        val facts: Map<String, String>,
    ) : GPUPreparedImageShaderValidationResult
}

internal data class GPUPreparedImageUniformInput(
    val positions: List<Pair<Float, Float>>,
    val uvs: List<Pair<Float, Float>>,
    val tintPremultipliedRgba: List<Float>,
    val atlasColorPremultipliedRgba: List<Float>?,
    val alphaOnly: Boolean,
    val atlasSourceBlend: GPUPreparedAtlasSourceBlend?,
    val premultipliedSource: Boolean = false,
)

internal object GPUPreparedImageUniformAbi {
    const val BYTE_SIZE: Int =
        GPUPreparedImageBindingLayoutTopology.UNIFORM_MIN_BINDING_SIZE_BYTES

    fun pack(input: GPUPreparedImageUniformInput): ByteArray {
        require(input.positions.size == 4 && input.uvs.size == 4) {
            "Prepared-image ABI requires four positions and four UVs"
        }
        require(input.positions.flattenPairs().all(Float::isFinite) &&
            input.uvs.flattenPairs().all(Float::isFinite)
        ) {
            "Prepared-image position and UV values must be finite"
        }
        require(input.tintPremultipliedRgba.isPremultipliedRgba()) {
            "Prepared-image tint must be finite premultiplied RGBA"
        }
        require(input.atlasColorPremultipliedRgba == null ||
            input.atlasColorPremultipliedRgba.isPremultipliedRgba()
        ) {
            "Prepared-image atlas color must be finite premultiplied RGBA"
        }
        require((input.atlasColorPremultipliedRgba == null) == (input.atlasSourceBlend == null)) {
            "Prepared-image atlas color and source blend must be specified together"
        }
        return ByteBuffer.allocate(BYTE_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            input.positions.zip(input.uvs).forEach { (position, uv) ->
                putFloat(position.first)
                putFloat(position.second)
                putFloat(uv.first)
                putFloat(uv.second)
            }
            input.tintPremultipliedRgba.forEach(::putFloat)
            (input.atlasColorPremultipliedRgba ?: ZERO_RGBA).forEach(::putFloat)
            putInt(
                when {
                    input.alphaOnly -> 1
                    input.premultipliedSource -> 2
                    else -> 0
                },
            )
            putInt(input.atlasSourceBlend?.wireCode ?: 0)
            putInt(0)
            putInt(0)
        }.array()
    }
}

internal fun preparedImageAtlasSourceBlend(mode: GPUBlendMode): GPUPreparedAtlasSourceBlend? =
    when (mode) {
        GPUBlendMode.SRC -> GPUPreparedAtlasSourceBlend.Src
        GPUBlendMode.DST -> GPUPreparedAtlasSourceBlend.Dst
        GPUBlendMode.SRC_OVER -> GPUPreparedAtlasSourceBlend.SrcOver
        GPUBlendMode.PLUS -> GPUPreparedAtlasSourceBlend.Plus
        GPUBlendMode.MODULATE -> GPUPreparedAtlasSourceBlend.Modulate
        else -> null
    }

internal fun preparedImageA8AtlasOracle(
    coverage: Float,
    tintPremultipliedRgba: List<Float>,
    atlasColorPremultipliedRgba: List<Float>,
    blend: GPUPreparedAtlasSourceBlend,
): List<Float> {
    require(coverage.isFinite() && coverage in 0f..1f)
    require(tintPremultipliedRgba.isPremultipliedRgba())
    require(atlasColorPremultipliedRgba.isPremultipliedRgba())
    val destination = OPAQUE_WHITE_RGBA
    val combined = when (blend) {
        GPUPreparedAtlasSourceBlend.Src -> atlasColorPremultipliedRgba
        GPUPreparedAtlasSourceBlend.Dst -> destination
        GPUPreparedAtlasSourceBlend.SrcOver ->
            atlasColorPremultipliedRgba.zip(destination).map { (src, dst) ->
                src + dst * (1f - atlasColorPremultipliedRgba[3])
            }
        GPUPreparedAtlasSourceBlend.Plus ->
            atlasColorPremultipliedRgba.zip(destination).map { (src, dst) ->
                (src + dst).coerceAtMost(1f)
            }
        GPUPreparedAtlasSourceBlend.Modulate ->
            atlasColorPremultipliedRgba.zip(destination).map { (src, dst) -> src * dst }
    }
    return combined.zip(tintPremultipliedRgba).map { (color, tint) ->
        color * tint * coverage
    }
}

internal fun validatePreparedImageShader(
    source: String,
): GPUPreparedImageShaderValidationResult {
    fun refused(reason: String): GPUPreparedImageShaderValidationResult.Refused =
        GPUPreparedImageShaderValidationResult.Refused(
            code = GPUPreparedImageRefusalCodes.WGSL_VALIDATION,
            facts = mapOf(
                "boundary" to "wgsl-validation",
                "reason" to reason,
            ),
        )

    val parsed = runCatching { KanvasWGSLValidator().parse(source) }
        .getOrElse { failure ->
            return refused("parser_exception:${failure::class.simpleName.orEmpty()}")
        }
    if (parsed.syntaxErrors.isNotEmpty()) {
        return refused("syntax_errors:${parsed.syntaxErrors.size}")
    }
    val reflected = runCatching {
        KanvasWGSLReflectionProvider().reflect(parsed).report
    }.getOrNull() ?: return refused("reflection_unavailable")
    val expected = listOf(
        Triple(
            GPUPreparedImageBindingLayoutTopology.GROUP,
            GPUPreparedImageBindingLayoutTopology.UNIFORM_BINDING,
            "uniformBuffer",
        ),
        Triple(
            GPUPreparedImageBindingLayoutTopology.GROUP,
            GPUPreparedImageBindingLayoutTopology.TEXTURE_BINDING,
            "sampledTexture",
        ),
        Triple(
            GPUPreparedImageBindingLayoutTopology.GROUP,
            GPUPreparedImageBindingLayoutTopology.SAMPLER_BINDING,
            "sampler",
        ),
    )
    if (reflected.bindings.map { Triple(it.group, it.binding, it.resourceKind) } != expected) {
        return refused("binding_layout")
    }
    val uniformMinBindingSize =
        reflected.bindings.first().minBindingSize?.toLong()
            ?: return refused("uniform_size_missing")
    if (
        uniformMinBindingSize !=
        GPUPreparedImageBindingLayoutTopology.UNIFORM_MIN_BINDING_SIZE_BYTES.toLong()
    ) {
        return refused("uniform_size")
    }
    val bindingDump = reflected.bindings.joinToString(";") {
        "${it.group}:${it.binding}:${it.name}:${it.resourceKind}:${it.minBindingSize ?: 0}"
    }
    val bindingLayout = GPUPreparedImageBindingLayoutContract(
        identity = GPUPreparedImageBindingLayoutTopology.IDENTITY,
        reflectedBindingsHash = sha256(bindingDump.encodeToByteArray()),
        uniformMinBindingSize = uniformMinBindingSize,
        group = GPUPreparedImageBindingLayoutTopology.GROUP,
        uniformBinding = GPUPreparedImageBindingLayoutTopology.UNIFORM_BINDING,
        textureBinding = GPUPreparedImageBindingLayoutTopology.TEXTURE_BINDING,
        samplerBinding = GPUPreparedImageBindingLayoutTopology.SAMPLER_BINDING,
    )
    return GPUPreparedImageShaderValidationResult.Ready(
        bindingLayout = bindingLayout,
        shaderContract = GPUPreparedImageShaderContract(
            sourceHash = sha256(source.encodeToByteArray()),
            bindingLayoutHash = bindingLayout.identity,
            reflectedBindingsHash = bindingLayout.reflectedBindingsHash,
        ),
    )
}

internal val GPU_PREPARED_IMAGE_WGSL: String = """
    struct PreparedImageUniforms {
        vertex0: vec4<f32>,
        vertex1: vec4<f32>,
        vertex2: vec4<f32>,
        vertex3: vec4<f32>,
        tint: vec4<f32>,
        atlas_color: vec4<f32>,
        flags: vec4<u32>,
    }

    struct PreparedImageVertexOutput {
        @builtin(position) position: vec4<f32>,
        @location(0) uv: vec2<f32>,
    }

    @group(0) @binding(0) var<uniform> image: PreparedImageUniforms;
    @group(0) @binding(1) var image_texture: texture_2d<f32>;
    @group(0) @binding(2) var image_sampler: sampler;

    @vertex
    fn vs_main(@builtin(vertex_index) vertex_index: u32) -> PreparedImageVertexOutput {
        var vertices = array<vec4<f32>, 4>(
            image.vertex0, image.vertex1, image.vertex2, image.vertex3,
        );
        var indices = array<u32, 6>(0u, 1u, 2u, 0u, 2u, 3u);
        let vertex = vertices[indices[vertex_index]];
        var output: PreparedImageVertexOutput;
        output.position = vec4<f32>(vertex.xy, 0.0, 1.0);
        output.uv = vertex.zw;
        return output;
    }

    fn atlas_source_over(source: vec4<f32>, destination: vec4<f32>) -> vec4<f32> {
        return source + destination * (1.0 - source.a);
    }

    @fragment
    fn fs_main(input: PreparedImageVertexOutput) -> @location(0) vec4<f32> {
        let sampled = textureSample(image_texture, image_sampler, input.uv);
        if (image.flags.x == 0u) {
            let sampled_source = vec4<f32>(sampled.rgb * sampled.a, sampled.a);
            var combined = sampled_source;
            if (image.flags.y == 1u) {
                combined = image.atlas_color;
            }
            if (image.flags.y == 2u) {
                combined = sampled_source;
            }
            if (image.flags.y == 3u) {
                combined = atlas_source_over(image.atlas_color, sampled_source);
            }
            if (image.flags.y == 4u) {
                combined = min(image.atlas_color + sampled_source, vec4<f32>(1.0));
            }
            if (image.flags.y == 5u) {
                combined = image.atlas_color * sampled_source;
            }
            return vec4<f32>(combined.rgb * image.tint.rgb, combined.a * image.tint.a);
        }
        if (image.flags.x == 2u) {
            // Premultiplied source: the texture already holds linear premultiplied
            // colors (layer children render through the premul core pipelines), so
            // no straight-alpha conversion is applied before the premul srcOver.
            let sampled_source = sampled;
            var combined = sampled_source;
            if (image.flags.y == 1u) {
                combined = image.atlas_color;
            }
            if (image.flags.y == 2u) {
                combined = sampled_source;
            }
            if (image.flags.y == 3u) {
                combined = atlas_source_over(image.atlas_color, sampled_source);
            }
            if (image.flags.y == 4u) {
                combined = min(image.atlas_color + sampled_source, vec4<f32>(1.0));
            }
            if (image.flags.y == 5u) {
                combined = image.atlas_color * sampled_source;
            }
            return vec4<f32>(combined.rgb * image.tint.rgb, combined.a * image.tint.a);
        }
        let coverage = sampled.r;
        var combined = vec4<f32>(1.0);
        if (image.flags.y == 1u) {
            combined = image.atlas_color;
        }
        if (image.flags.y == 2u) {
            combined = vec4<f32>(1.0);
        }
        if (image.flags.y == 3u) {
            combined = atlas_source_over(image.atlas_color, vec4<f32>(1.0));
        }
        if (image.flags.y == 4u) {
            combined = min(image.atlas_color + vec4<f32>(1.0), vec4<f32>(1.0));
        }
        if (image.flags.y == 5u) {
            combined = image.atlas_color * vec4<f32>(1.0);
        }
        return vec4<f32>(
            combined.rgb * image.tint.rgb * coverage,
            combined.a * image.tint.a * coverage,
        );
    }
""".trimIndent()

private val GPUPreparedAtlasSourceBlend.wireCode: Int
    get() = when (this) {
        GPUPreparedAtlasSourceBlend.Src -> 1
        GPUPreparedAtlasSourceBlend.Dst -> 2
        GPUPreparedAtlasSourceBlend.SrcOver -> 3
        GPUPreparedAtlasSourceBlend.Plus -> 4
        GPUPreparedAtlasSourceBlend.Modulate -> 5
    }

private val ZERO_RGBA = listOf(0f, 0f, 0f, 0f)
private val OPAQUE_WHITE_RGBA = listOf(1f, 1f, 1f, 1f)

private fun List<Pair<Float, Float>>.flattenPairs(): List<Float> =
    flatMap { (first, second) -> listOf(first, second) }

private fun List<Float>.isPremultipliedRgba(): Boolean =
    size == 4 && all { it.isFinite() && it in 0f..1f } &&
        this[0] <= this[3] && this[1] <= this[3] && this[2] <= this[3]

private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
