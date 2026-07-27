package org.graphiks.kanvas.gpu.renderer.execution

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedAtlasSourceBlend
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageBindingLayoutTopology
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.KanvasWGSLReflectionProvider
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.KanvasWGSLValidator

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

internal data class GPUPreparedImageUniformInput(
    val positions: List<Pair<Float, Float>>,
    val uvs: List<Pair<Float, Float>>,
    val tintPremultipliedRgba: List<Float>,
    val atlasColorPremultipliedRgba: List<Float>?,
    val alphaOnly: Boolean,
    val atlasSourceBlend: GPUPreparedAtlasSourceBlend?,
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
            putInt(if (input.alphaOnly) 1 else 0)
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
    val source = atlasColorPremultipliedRgba.map { it * coverage }
    val destination = tintPremultipliedRgba
    return when (blend) {
        GPUPreparedAtlasSourceBlend.Src -> source
        GPUPreparedAtlasSourceBlend.Dst -> destination
        GPUPreparedAtlasSourceBlend.SrcOver ->
            source.zip(destination).map { (src, dst) -> src + dst * (1f - source[3]) }
        GPUPreparedAtlasSourceBlend.Plus ->
            source.zip(destination).map { (src, dst) -> (src + dst).coerceAtMost(1f) }
        GPUPreparedAtlasSourceBlend.Modulate ->
            source.zip(destination).map { (src, dst) -> src * dst }
    }
}

internal fun preparedImageShaderContract(): GPUPreparedImageShaderContract {
    val bindingLayout = preparedImageBindingLayoutContract()
    return GPUPreparedImageShaderContract(
        sourceHash = sha256(GPU_PREPARED_IMAGE_WGSL.encodeToByteArray()),
        bindingLayoutHash = bindingLayout.identity,
        reflectedBindingsHash = bindingLayout.reflectedBindingsHash,
    )
}

internal fun preparedImageBindingLayoutContract(): GPUPreparedImageBindingLayoutContract =
    PREPARED_IMAGE_BINDING_LAYOUT_CONTRACT

private val PREPARED_IMAGE_BINDING_LAYOUT_CONTRACT: GPUPreparedImageBindingLayoutContract by lazy {
    val parsed = KanvasWGSLValidator().parse(GPU_PREPARED_IMAGE_WGSL)
    require(parsed.syntaxErrors.isEmpty()) {
        "Prepared-image WGSL parser validation failed: ${parsed.syntaxErrors.joinToString()}"
    }
    val reflected = requireNotNull(KanvasWGSLReflectionProvider().reflect(parsed).report) {
        "Prepared-image WGSL requires parser-backed reflection"
    }
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
    require(reflected.bindings.map { Triple(it.group, it.binding, it.resourceKind) } == expected) {
        "Prepared-image WGSL reflected bindings do not match the closed group-0 ABI"
    }
    val uniformMinBindingSize =
        requireNotNull(reflected.bindings.first().minBindingSize).toLong()
    require(
        uniformMinBindingSize ==
            GPUPreparedImageBindingLayoutTopology.UNIFORM_MIN_BINDING_SIZE_BYTES.toLong(),
    ) {
        "Prepared-image WGSL reflected uniform size does not match ABI112"
    }
    val bindingDump = reflected.bindings.joinToString(";") {
        "${it.group}:${it.binding}:${it.name}:${it.resourceKind}:${it.minBindingSize ?: 0}"
    }
    return@lazy GPUPreparedImageBindingLayoutContract(
        identity = GPUPreparedImageBindingLayoutTopology.IDENTITY,
        reflectedBindingsHash = sha256(bindingDump.encodeToByteArray()),
        uniformMinBindingSize = uniformMinBindingSize,
        group = GPUPreparedImageBindingLayoutTopology.GROUP,
        uniformBinding = GPUPreparedImageBindingLayoutTopology.UNIFORM_BINDING,
        textureBinding = GPUPreparedImageBindingLayoutTopology.TEXTURE_BINDING,
        samplerBinding = GPUPreparedImageBindingLayoutTopology.SAMPLER_BINDING,
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
            let source = vec4<f32>(sampled.rgb * sampled.a, sampled.a);
            return vec4<f32>(source.rgb * image.tint.rgb, source.a * image.tint.a);
        }
        let coverage = sampled.r;
        if (image.flags.y == 0u) {
            return image.tint * coverage;
        }
        let source = image.atlas_color * coverage;
        if (image.flags.y == 1u) {
            return source;
        }
        if (image.flags.y == 2u) {
            return image.tint;
        }
        if (image.flags.y == 3u) {
            return atlas_source_over(source, image.tint);
        }
        if (image.flags.y == 4u) {
            return min(source + image.tint, vec4<f32>(1.0));
        }
        return source * image.tint;
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

private fun List<Pair<Float, Float>>.flattenPairs(): List<Float> =
    flatMap { (first, second) -> listOf(first, second) }

private fun List<Float>.isPremultipliedRgba(): Boolean =
    size == 4 && all { it.isFinite() && it in 0f..1f } &&
        this[0] <= this[3] && this[1] <= this[3] && this[2] <= this[3]

private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
