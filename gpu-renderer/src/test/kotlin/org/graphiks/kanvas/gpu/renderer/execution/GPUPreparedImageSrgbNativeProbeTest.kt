package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.BindGroupLayoutDescriptor
import io.ygdrasil.webgpu.BindGroupLayoutEntry
import io.ygdrasil.webgpu.BlendComponent
import io.ygdrasil.webgpu.BlendState
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.Color
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUBlendFactor
import io.ygdrasil.webgpu.GPUBlendOperation
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUFilterMode
import io.ygdrasil.webgpu.GPULoadOp
import io.ygdrasil.webgpu.GPUMapMode
import io.ygdrasil.webgpu.GPUSamplerBindingType
import io.ygdrasil.webgpu.GPUShaderStage
import io.ygdrasil.webgpu.GPUStoreOp
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureSampleType
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.GPUTextureViewDimension
import io.ygdrasil.webgpu.PipelineLayoutDescriptor
import io.ygdrasil.webgpu.PrimitiveState
import io.ygdrasil.webgpu.RenderPassColorAttachment
import io.ygdrasil.webgpu.RenderPassDescriptor
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import io.ygdrasil.webgpu.SamplerBindingLayout
import io.ygdrasil.webgpu.SamplerDescriptor
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import io.ygdrasil.webgpu.TexelCopyBufferInfo
import io.ygdrasil.webgpu.TexelCopyBufferLayout
import io.ygdrasil.webgpu.TexelCopyTextureInfo
import io.ygdrasil.webgpu.TextureBindingLayout
import io.ygdrasil.webgpu.TextureDescriptor
import io.ygdrasil.webgpu.VertexState
import io.ygdrasil.webgpu.beginRenderPass
import io.ygdrasil.webgpu.glfwContextRenderer
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.images.AlphaType
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactFactory
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactResult
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageOrientation
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProfile
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProvenance
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceClass
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceFormat
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPU_PREPARED_IMAGE_TARGET_FORMAT
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageBindingLayoutTopology
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedImageBindingInput
import org.graphiks.kanvas.gpu.renderer.resources.buildPreparedImageFrameResourcePlanFromBindings
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageSampling
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID
import org.junit.jupiter.api.Timeout

class GPUPreparedImageSrgbNativeProbeTest {
    @Test
    @Timeout(30)
    fun `straight sRGB upload and native sRGB store match the independent translucent oracle`() =
        runBlocking {
            val expected = independentOracleBytes()
            val probe = renderNativeCandidates()
            println(probe.evidenceLine(expected))

            assertCandidateMismatch(
                expected = expected,
                actual = probe.bytes.getValue(NativeCandidate.CurrentEncodedPremul),
                label = "current RGBA8Unorm + EncodedPremulSrgb",
            )
            assertCandidateMismatch(
                expected = expected,
                actual = probe.bytes.getValue(NativeCandidate.DirectPremulSrgb),
                label = "direct premultiplied bytes in RGBA8UnormSrgb",
            )
            assertRgbaNear(
                expected = expected,
                actual = probe.bytes.getValue(NativeCandidate.StraightSrgbRepremul),
                tolerance = 1,
                label = "straight RGBA8UnormSrgb + shader premultiply",
            )
            assertRgbaNear(
                expected = expected,
                actual = probe.bytes.getValue(NativeCandidate.LegacyManualTransfer),
                tolerance = 1,
                label = "legacy manual transfer reference",
            )

            val selectedCoverage = probe.bytes.getValue(NativeCandidate.StraightSrgbRepremul)
                .pixel(COVERAGE_PIXEL)
            val recoveredCoverage = selectedCoverage[3] / 255.0 / PAINT_ALPHA
            assertTrue(
                abs(recoveredCoverage - COVERAGE_BYTE / 255.0) <= 1.0 / 255.0 / PAINT_ALPHA,
                "A8 coverage must remain ${COVERAGE_BYTE / 255.0} before tint; " +
                    "native recovered coverage=$recoveredCoverage",
            )

            val production = productionCandidate()
            assertRgbaNear(
                expected = expected,
                actual = probe.bytes.getValue(production),
                tolerance = 1,
                label = "production candidate=$production adapter=${probe.adapter}",
            )
        }

    @Test
    fun `independent oracle uses bounded straight recovery and exact IEC 61966-2-1 transfer`() {
        assertContentEquals(
            byteArrayOf(40, 120, 210.toByte(), 160.toByte()),
            recoverStraightEncoded(PREMUL_TRANSLUCENT),
        )

        val expected = independentOracleBytes()
        assertContentEquals(intArrayOf(25, 84, 150, 120), expected.pixel(TRANSLUCENT_PIXEL))
        assertContentEquals(intArrayOf(34, 105, 185, 191), expected.pixel(OPAQUE_PIXEL))
        assertContentEquals(intArrayOf(165, 165, 165, 96), expected.pixel(COVERAGE_PIXEL))
    }

    private suspend fun renderNativeCandidates(): NativeProbeResult {
        val context = glfwContextRenderer(
            width = 1,
            height = 1,
            title = "prepared-image-srgb-native-probe",
            deferredRendering = true,
        )
        val owned = mutableListOf<AutoCloseable>()
        try {
            val device = context.wgpuContext.device
            val queue = device.queue
            val readback = device.createBuffer(
                BufferDescriptor(
                    size = READBACK_SIZE.toULong(),
                    usage = GPUBufferUsage.MapRead or GPUBufferUsage.CopyDst,
                    mappedAtCreation = false,
                    label = "prepared-image-srgb-probe.readback",
                ),
            ).also(owned::add)
            val encoder = device.createCommandEncoder().also(owned::add)

            NativeCandidate.entries.forEachIndexed { index, candidate ->
                val sourceTexture = device.createTexture(
                    TextureDescriptor(
                        size = Extent3D(COLOR_PIXEL_COUNT.toUInt(), 1u),
                        format = candidate.sourceFormat,
                        usage = GPUTextureUsage.TextureBinding or GPUTextureUsage.CopyDst,
                        label = "prepared-image-srgb-probe.$candidate.source",
                    ),
                ).also(owned::add)
                queue.writeTexture(
                    destination = TexelCopyTextureInfo(texture = sourceTexture),
                    data = ArrayBuffer.of(candidate.paddedSourceBytes()),
                    dataLayout = TexelCopyBufferLayout(
                        offset = 0uL,
                        bytesPerRow = COPY_BYTES_PER_ROW.toUInt(),
                        rowsPerImage = 1u,
                    ),
                    size = Extent3D(COLOR_PIXEL_COUNT.toUInt(), 1u),
                )
                val coverageTexture = device.createTexture(
                    TextureDescriptor(
                        size = Extent3D(1u, 1u),
                        format = GPUTextureFormat.RGBA8Unorm,
                        usage = GPUTextureUsage.TextureBinding or GPUTextureUsage.CopyDst,
                        label = "prepared-image-srgb-probe.$candidate.coverage",
                    ),
                ).also(owned::add)
                queue.writeTexture(
                    destination = TexelCopyTextureInfo(texture = coverageTexture),
                    data = ArrayBuffer.of(paddedRow(byteArrayOf(128.toByte(), 128.toByte(), 128.toByte(), 128.toByte()))),
                    dataLayout = TexelCopyBufferLayout(
                        offset = 0uL,
                        bytesPerRow = COPY_BYTES_PER_ROW.toUInt(),
                        rowsPerImage = 1u,
                    ),
                    size = Extent3D(1u, 1u),
                )
                val target = device.createTexture(
                    TextureDescriptor(
                        size = Extent3D(TARGET_PIXEL_COUNT.toUInt(), 1u),
                        format = candidate.targetFormat,
                        usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.CopySrc,
                        label = "prepared-image-srgb-probe.$candidate.target",
                    ),
                ).also(owned::add)
                val sourceView = sourceTexture.createView().also(owned::add)
                val coverageView = coverageTexture.createView().also(owned::add)
                val targetView = target.createView().also(owned::add)
                val sampler = device.createSampler(
                    SamplerDescriptor(
                        magFilter = GPUFilterMode.Nearest,
                        minFilter = GPUFilterMode.Nearest,
                        label = "prepared-image-srgb-probe.$candidate.sampler",
                    ),
                ).also(owned::add)
                val layout = device.createBindGroupLayout(
                    BindGroupLayoutDescriptor(
                        label = "prepared-image-srgb-probe.$candidate.bind-group-layout",
                        entries = listOf(
                            sampledTextureLayoutEntry(0u),
                            sampledTextureLayoutEntry(1u),
                            BindGroupLayoutEntry(
                                binding = 2u,
                                visibility = GPUShaderStage.Fragment,
                                sampler = SamplerBindingLayout(GPUSamplerBindingType.Filtering),
                            ),
                        ),
                    ),
                ).also(owned::add)
                val shader = device.createShaderModule(
                    ShaderModuleDescriptor(
                        label = "prepared-image-srgb-probe.$candidate.shader",
                        code = candidate.shaderSource(),
                    ),
                ).also(owned::add)
                val pipelineLayout = device.createPipelineLayout(
                    PipelineLayoutDescriptor(
                        label = "prepared-image-srgb-probe.$candidate.pipeline-layout",
                        bindGroupLayouts = listOf(layout),
                    ),
                ).also(owned::add)
                val pipeline = device.createRenderPipeline(
                    RenderPipelineDescriptor(
                        label = "prepared-image-srgb-probe.$candidate.pipeline",
                        layout = pipelineLayout,
                        vertex = VertexState(module = shader, entryPoint = "vs_main"),
                        primitive = PrimitiveState(),
                        fragment = FragmentState(
                            module = shader,
                            entryPoint = "fs_main",
                            targets = listOf(
                                ColorTargetState(
                                    format = candidate.targetFormat,
                                    blend = srcOverBlendState(),
                                ),
                            ),
                        ),
                    ),
                ).also(owned::add)
                val bindGroup = device.createBindGroup(
                    BindGroupDescriptor(
                        label = "prepared-image-srgb-probe.$candidate.bind-group",
                        layout = layout,
                        entries = listOf(
                            BindGroupEntry(0u, sourceView),
                            BindGroupEntry(1u, coverageView),
                            BindGroupEntry(2u, sampler),
                        ),
                    ),
                ).also(owned::add)

                encoder.beginRenderPass(
                    RenderPassDescriptor(
                        colorAttachments = listOf(
                            RenderPassColorAttachment(
                                view = targetView,
                                loadOp = GPULoadOp.Clear,
                                clearValue = Color(0.0, 0.0, 0.0, 0.0),
                                storeOp = GPUStoreOp.Store,
                            ),
                        ),
                    ),
                ) {
                    setPipeline(pipeline)
                    setBindGroup(0u, bindGroup)
                    draw(3u, 1u, 0u, 0u)
                    end()
                }
                encoder.copyTextureToBuffer(
                    source = TexelCopyTextureInfo(texture = target),
                    destination = TexelCopyBufferInfo(
                        buffer = readback,
                        offset = (index * COPY_BYTES_PER_ROW).toULong(),
                        bytesPerRow = COPY_BYTES_PER_ROW.toUInt(),
                        rowsPerImage = 1u,
                    ),
                    copySize = Extent3D(TARGET_PIXEL_COUNT.toUInt(), 1u),
                )
            }

            val commandBuffer = encoder.finish().also(owned::add)
            queue.submit(listOf(commandBuffer))
            queue.onSubmittedWorkDone()
            readback.mapAsync(GPUMapMode.Read, 0uL, READBACK_SIZE.toULong()).getOrThrow()
            val mapped = readback.getMappedRange(0uL, READBACK_SIZE.toULong()).toByteArray()
            readback.unmap()

            val bytes = NativeCandidate.entries.associateWith { candidate ->
                val offset = candidate.ordinal * COPY_BYTES_PER_ROW
                mapped.copyOfRange(offset, offset + TARGET_TIGHT_BYTES)
            }
            val info = context.wgpuContext.adapter.info
            return NativeProbeResult(
                bytes = bytes,
                adapter = listOf(info.vendor, info.device, info.architecture, info.description)
                    .filter(String::isNotBlank)
                    .joinToString(" / ")
                    .ifBlank { "unknown-adapter" },
            )
        } finally {
            owned.asReversed().forEach { handle ->
                runCatching(handle::close)
            }
            context.close()
        }
    }

    private fun productionCandidate(): NativeCandidate {
        val artifact = (GPUPreparedImageArtifactFactory.prepare(
            GPUPreparedImageSourceInput(
                sourceClass = GPUPreparedImageSourceClass.DecodedCpu,
                sourceId = "native-probe-production",
                width = COLOR_PIXEL_COUNT,
                height = 1,
                sourceFormat = GPUPreparedImageSourceFormat.Rgba8,
                alphaType = AlphaType.PREMUL,
                sourceRowBytes = 8,
                profile = GPUPreparedImageProfile.Srgb,
                orientation = GPUPreparedImageOrientation.AppliedIdentity,
                provenance = GPUPreparedImageProvenance.CallerPixels,
                sourceGeneration = 7,
                pixelBytes = PREMUL_COLOR_FIXTURES,
            ),
        ) as GPUPreparedImageArtifactResult.Ready).artifact
        val plan = buildPreparedImageFrameResourcePlanFromBindings(
            artifact = artifact,
            bindingInputs = listOf(
                GPUPreparedImageBindingInput("native-probe-production", GPUPreparedImageSampling.Nearest),
            ),
            bindingLayoutHash = GPUPreparedImageBindingLayoutTopology.IDENTITY,
            capabilities = probeCapabilities(),
            frameIdentity = "native-probe-production",
            uploadTaskId = GPUTaskID("native-probe-production.upload"),
        )
        val productionRepremultiplies =
            GPU_PREPARED_IMAGE_WGSL.contains("sampled.rgb * sampled.a")
        return NativeCandidate.entries.single { candidate ->
            candidate.sourceFormatLabel.equals(plan.textureDescriptor.format, ignoreCase = true) &&
                candidate.targetFormatLabel.equals(GPU_PREPARED_IMAGE_TARGET_FORMAT, ignoreCase = true) &&
                candidate.sourceBytes.contentEquals(artifact.tightRgba8BytesForUpload()) &&
                candidate.repremultiplies == productionRepremultiplies &&
                !candidate.manualTransfer
        }
    }

    private fun independentOracleBytes(): ByteArray {
        val translucentStraight = recoverStraightEncoded(PREMUL_TRANSLUCENT)
        val translucent = oracleColor(translucentStraight)
        val opaque = oracleColor(OPAQUE_STRAIGHT)
        val coverage = intArrayOf(
            encodeSrgbByte((COVERAGE_BYTE / 255.0) * PAINT_ALPHA),
            encodeSrgbByte((COVERAGE_BYTE / 255.0) * PAINT_ALPHA),
            encodeSrgbByte((COVERAGE_BYTE / 255.0) * PAINT_ALPHA),
            ((COVERAGE_BYTE / 255.0) * PAINT_ALPHA * 255.0).roundToInt(),
        )
        return (translucent + opaque + coverage).map(Int::toByte).toByteArray()
    }

    private fun oracleColor(straightEncoded: ByteArray): IntArray {
        val alpha = straightEncoded[3].unsigned() / 255.0
        val outputAlpha = alpha * PAINT_ALPHA
        return intArrayOf(
            encodeSrgbByte(decodeSrgb(straightEncoded[0].unsigned() / 255.0) * outputAlpha),
            encodeSrgbByte(decodeSrgb(straightEncoded[1].unsigned() / 255.0) * outputAlpha),
            encodeSrgbByte(decodeSrgb(straightEncoded[2].unsigned() / 255.0) * outputAlpha),
            (outputAlpha * 255.0).roundToInt().coerceIn(0, 255),
        )
    }

    private fun recoverStraightEncoded(premultiplied: ByteArray): ByteArray {
        val alpha = premultiplied[3].unsigned()
        return byteArrayOf(
            recoverStraightChannel(premultiplied[0].unsigned(), alpha).toByte(),
            recoverStraightChannel(premultiplied[1].unsigned(), alpha).toByte(),
            recoverStraightChannel(premultiplied[2].unsigned(), alpha).toByte(),
            alpha.toByte(),
        )
    }

    private fun recoverStraightChannel(channel: Int, alpha: Int): Int =
        if (alpha == 0) 0 else (channel * 255.0 / alpha).roundToInt().coerceIn(0, 255)

    private fun decodeSrgb(encoded: Double): Double =
        if (encoded <= 0.04045) encoded / 12.92
        else ((encoded + 0.055) / 1.055).pow(2.4)

    private fun encodeSrgb(linear: Double): Double =
        if (linear <= 0.0031308) linear * 12.92
        else 1.055 * linear.pow(1.0 / 2.4) - 0.055

    private fun encodeSrgbByte(linear: Double): Int =
        (encodeSrgb(linear.coerceIn(0.0, 1.0)) * 255.0).roundToInt().coerceIn(0, 255)

    private fun assertCandidateMismatch(
        expected: ByteArray,
        actual: ByteArray,
        label: String,
    ) {
        assertEquals(expected.size, actual.size, label)
        val translucentDeltas = (0 until 4).map { channel ->
            abs(expected[channel].unsigned() - actual[channel].unsigned())
        }
        assertTrue(
            translucentDeltas.any { it > 1 },
            "$label must reproduce the translucent mismatch; deltas=$translucentDeltas",
        )
    }

    private fun assertRgbaNear(
        expected: ByteArray,
        actual: ByteArray,
        tolerance: Int,
        label: String,
    ) {
        assertEquals(expected.size, actual.size, label)
        expected.indices.forEach { index ->
            val delta = abs(expected[index].unsigned() - actual[index].unsigned())
            assertTrue(
                delta <= tolerance,
                "$label byte[$index] expected=${expected[index].unsigned()} " +
                    "actual=${actual[index].unsigned()} delta=$delta tolerance=$tolerance",
            )
        }
    }

    private fun ByteArray.pixel(index: Int): IntArray =
        IntArray(4) { channel -> this[index * 4 + channel].unsigned() }

    private fun Byte.unsigned(): Int = toInt() and 0xff

    private fun NativeProbeResult.evidenceLine(expected: ByteArray): String =
        buildString {
            append("prepared-image-srgb-native-probe runtime=wgpu4k-native")
            append(" os=").append(System.getProperty("os.name"))
            append(" adapter=").append(adapter)
            append(" expected=").append(expected.unsignedCsv())
            NativeCandidate.entries.forEach { candidate ->
                val actual = bytes.getValue(candidate)
                append(" candidate=").append(candidate.name)
                append(":bytes=").append(actual.unsignedCsv())
                append(":deltas=").append(
                    expected.indices.joinToString(",") { index ->
                        abs(expected[index].unsigned() - actual[index].unsigned()).toString()
                    },
                )
            }
        }

    private fun ByteArray.unsignedCsv(): String = joinToString(",") { it.unsigned().toString() }

    private fun sampledTextureLayoutEntry(binding: UInt): BindGroupLayoutEntry =
        BindGroupLayoutEntry(
            binding = binding,
            visibility = GPUShaderStage.Fragment,
            texture = TextureBindingLayout(
                sampleType = GPUTextureSampleType.Float,
                viewDimension = GPUTextureViewDimension.TwoD,
                multisampled = false,
            ),
        )

    private fun srcOverBlendState(): BlendState = BlendState(
        color = BlendComponent(
            GPUBlendOperation.Add,
            GPUBlendFactor.One,
            GPUBlendFactor.OneMinusSrcAlpha,
        ),
        alpha = BlendComponent(
            GPUBlendOperation.Add,
            GPUBlendFactor.One,
            GPUBlendFactor.OneMinusSrcAlpha,
        ),
    )

    private fun probeCapabilities(): GPUCapabilities = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "wgpu4k", "native", "native"),
        facts = emptyList(),
        snapshotId = "prepared-image-srgb-native-probe",
        limits = GPULimits(
            maxTextureDimension2D = 8_192,
            copyBytesPerRowAlignment = COPY_BYTES_PER_ROW.toLong(),
            minUniformBufferOffsetAlignment = 256,
            maxBufferSize = 1L shl 30,
        ),
    )

    private enum class NativeCandidate(
        val sourceFormat: GPUTextureFormat,
        val sourceFormatLabel: String,
        val targetFormat: GPUTextureFormat,
        val targetFormatLabel: String,
        val sourceBytes: ByteArray,
        val repremultiplies: Boolean,
        val manualTransfer: Boolean,
    ) {
        CurrentEncodedPremul(
            GPUTextureFormat.RGBA8Unorm,
            "RGBA8Unorm",
            GPUTextureFormat.RGBA8Unorm,
            "RGBA8Unorm",
            PREMUL_COLOR_FIXTURES,
            repremultiplies = false,
            manualTransfer = false,
        ),
        DirectPremulSrgb(
            GPUTextureFormat.RGBA8UnormSrgb,
            "RGBA8UnormSrgb",
            GPUTextureFormat.RGBA8UnormSrgb,
            "RGBA8UnormSrgb",
            PREMUL_COLOR_FIXTURES,
            repremultiplies = false,
            manualTransfer = false,
        ),
        StraightSrgbRepremul(
            GPUTextureFormat.RGBA8UnormSrgb,
            "rgba8unorm-srgb",
            GPUTextureFormat.RGBA8UnormSrgb,
            "RGBA8UnormSrgb",
            STRAIGHT_COLOR_FIXTURES,
            repremultiplies = true,
            manualTransfer = false,
        ),
        LegacyManualTransfer(
            GPUTextureFormat.RGBA8Unorm,
            "RGBA8Unorm",
            GPUTextureFormat.RGBA8Unorm,
            "RGBA8Unorm",
            STRAIGHT_COLOR_FIXTURES,
            repremultiplies = true,
            manualTransfer = true,
        ),
        ;

        fun paddedSourceBytes(): ByteArray = paddedRow(sourceBytes)

        fun shaderSource(): String {
            val colorTransform = when {
                manualTransfer ->
                    "vec4<f32>(srgb_to_linear(sampled.rgb) * sampled.a, sampled.a)"
                repremultiplies ->
                    "vec4<f32>(sampled.rgb * sampled.a, sampled.a)"
                else -> "sampled"
            }
            val colorStore =
                if (manualTransfer) "vec4<f32>(linear_to_srgb(painted.rgb), painted.a)"
                else "painted"
            val coverageStore =
                if (manualTransfer) {
                    "vec4<f32>(linear_to_srgb(vec3<f32>(coverage * $PAINT_ALPHA)), coverage * $PAINT_ALPHA)"
                } else {
                    "vec4<f32>(coverage * $PAINT_ALPHA)"
                }
            return """
                @group(0) @binding(0) var color_texture: texture_2d<f32>;
                @group(0) @binding(1) var coverage_texture: texture_2d<f32>;
                @group(0) @binding(2) var nearest_sampler: sampler;

                struct VertexOutput {
                    @builtin(position) position: vec4<f32>,
                }

                @vertex
                fn vs_main(@builtin(vertex_index) vertex_index: u32) -> VertexOutput {
                    var positions = array<vec2<f32>, 3>(
                        vec2<f32>(-1.0, -1.0),
                        vec2<f32>(3.0, -1.0),
                        vec2<f32>(-1.0, 3.0),
                    );
                    var output: VertexOutput;
                    output.position = vec4<f32>(positions[vertex_index], 0.0, 1.0);
                    return output;
                }

                fn srgb_to_linear_channel(value: f32) -> f32 {
                    if (value <= 0.04045) {
                        return value / 12.92;
                    }
                    return pow((value + 0.055) / 1.055, 2.4);
                }

                fn srgb_to_linear(value: vec3<f32>) -> vec3<f32> {
                    return vec3<f32>(
                        srgb_to_linear_channel(value.r),
                        srgb_to_linear_channel(value.g),
                        srgb_to_linear_channel(value.b),
                    );
                }

                fn linear_to_srgb_channel(value: f32) -> f32 {
                    if (value <= 0.0031308) {
                        return value * 12.92;
                    }
                    return 1.055 * pow(value, 1.0 / 2.4) - 0.055;
                }

                fn linear_to_srgb(value: vec3<f32>) -> vec3<f32> {
                    return vec3<f32>(
                        linear_to_srgb_channel(value.r),
                        linear_to_srgb_channel(value.g),
                        linear_to_srgb_channel(value.b),
                    );
                }

                @fragment
                fn fs_main(input: VertexOutput) -> @location(0) vec4<f32> {
                    if (input.position.x >= 2.0) {
                        let coverage = textureSampleLevel(
                            coverage_texture,
                            nearest_sampler,
                            vec2<f32>(0.5, 0.5),
                            0.0,
                        ).r;
                        return $coverageStore;
                    }
                    let color_index = u32(input.position.x);
                    let uv = vec2<f32>((f32(color_index) + 0.5) / 2.0, 0.5);
                    let sampled = textureSampleLevel(color_texture, nearest_sampler, uv, 0.0);
                    let source = $colorTransform;
                    let painted = vec4<f32>(
                        source.rgb * $PAINT_ALPHA,
                        source.a * $PAINT_ALPHA,
                    );
                    return $colorStore;
                }
            """.trimIndent()
        }
    }

    private data class NativeProbeResult(
        val bytes: Map<NativeCandidate, ByteArray>,
        val adapter: String,
    )

    private companion object {
        private const val COLOR_PIXEL_COUNT = 2
        private const val TARGET_PIXEL_COUNT = 3
        private const val TARGET_TIGHT_BYTES = TARGET_PIXEL_COUNT * 4
        private const val COPY_BYTES_PER_ROW = 256
        private const val READBACK_SIZE = COPY_BYTES_PER_ROW * 4
        private const val PAINT_ALPHA = 0.75
        private const val COVERAGE_BYTE = 128
        private const val TRANSLUCENT_PIXEL = 0
        private const val OPAQUE_PIXEL = 1
        private const val COVERAGE_PIXEL = 2
        private val PREMUL_TRANSLUCENT = byteArrayOf(25, 75, 132.toByte(), 160.toByte())
        private val OPAQUE_STRAIGHT = byteArrayOf(40, 120, 210.toByte(), 255.toByte())
        private val PREMUL_COLOR_FIXTURES = PREMUL_TRANSLUCENT + OPAQUE_STRAIGHT
        private val STRAIGHT_COLOR_FIXTURES =
            byteArrayOf(40, 120, 210.toByte(), 160.toByte()) + OPAQUE_STRAIGHT

        private fun paddedRow(tight: ByteArray): ByteArray =
            ByteArray(COPY_BYTES_PER_ROW).also { tight.copyInto(it) }
    }
}
