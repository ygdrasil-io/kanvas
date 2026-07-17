package org.graphiks.kanvas.gpu.renderer.capabilities

import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GPUCapabilityContractsTest {
    @Test
    fun `GPU abstraction labels dump to stable public strings`() {
        assertEquals("r16unorm", GPUTextureFormat.R16Unorm.dumpLabel())
        assertEquals("r16snorm", GPUTextureFormat.R16Snorm.dumpLabel())
        assertEquals("rg16unorm", GPUTextureFormat.RG16Unorm.dumpLabel())
        assertEquals("rg16snorm", GPUTextureFormat.RG16Snorm.dumpLabel())
        assertEquals("rgba16unorm", GPUTextureFormat.RGBA16Unorm.dumpLabel())
        assertEquals("rgba16snorm", GPUTextureFormat.RGBA16Snorm.dumpLabel())
        assertEquals("rgba8unorm", GPUTextureFormat.RGBA8Unorm.dumpLabel())
        assertEquals("rgba8unorm-srgb", GPUTextureFormat.RGBA8UnormSrgb.dumpLabel())
        assertEquals("bgra8unorm", GPUTextureFormat.BGRA8Unorm.dumpLabel())
        assertEquals("bgra8unorm-srgb", GPUTextureFormat.BGRA8UnormSrgb.dumpLabel())
        assertEquals("depth24plus-stencil8", GPUTextureFormat.Depth24PlusStencil8.dumpLabel())
        assertEquals("depth32float-stencil8", GPUTextureFormat.Depth32FloatStencil8.dumpLabel())
        assertEquals("bc1-rgba-unorm-srgb", GPUTextureFormat.BC1RGBAUnormSrgb.dumpLabel())
        assertEquals("bc6h-rgb-ufloat", GPUTextureFormat.BC6HRGBUfloat.dumpLabel())
        assertEquals("bc6h-rgb-float", GPUTextureFormat.BC6HRGBFloat.dumpLabel())
        assertEquals("astc-4x4-unorm", GPUTextureFormat.ASTC4x4Unorm.dumpLabel())
        assertEquals("etc2-rgb8unorm", GPUTextureFormat.ETC2RGB8Unorm.dumpLabel())
        assertEquals("etc2-rgb8unorm-srgb", GPUTextureFormat.ETC2RGB8UnormSrgb.dumpLabel())
        assertEquals("eac-r11unorm", GPUTextureFormat.EACR11Unorm.dumpLabel())
        assertEquals("eac-rg11snorm", GPUTextureFormat.EACRG11Snorm.dumpLabel())
        assertEquals("bc6h-rgb-ufloat", GPUTextureFormat.BC6HRGBUfloat.dumpLabel())
        assertEquals("bc6h-rgb-float", GPUTextureFormat.BC6HRGBFloat.dumpLabel())

        val usage = GPUTextureUsage.CopyDst or GPUTextureUsage.TextureBinding or GPUTextureUsage.RenderAttachment
        assertEquals(
            listOf("copy_dst", "texture_binding", "render_attachment"),
            usage.dumpLabels(),
        )

        assertEquals("texture-sampling", GPURendererFeature.TextureSampling.dumpLabel)
        assertEquals("uniform-buffer", GPURendererFeature.UniformBuffer.dumpLabel)
    }

    @Test
    fun `GPU limits validate positive values and nonblank source`() {
        val limits = GPULimits(
            maxTextureDimension2D = 8192L,
            copyBytesPerRowAlignment = 256L,
            minUniformBufferOffsetAlignment = 256L,
            source = "device.limits",
        )

        assertEquals(8192L, limits.maxTextureDimension2D)
        assertEquals(256L, limits.copyBytesPerRowAlignment)
        assertEquals(256L, limits.minUniformBufferOffsetAlignment)
        assertEquals("device.limits", limits.source)
        assertFailsWith<IllegalArgumentException> {
            GPULimits(
                maxTextureDimension2D = 0L,
                copyBytesPerRowAlignment = 256L,
                minUniformBufferOffsetAlignment = 256L,
                source = "device.limits",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPULimits(
                maxTextureDimension2D = 8192L,
                copyBytesPerRowAlignment = 0L,
                minUniformBufferOffsetAlignment = 256L,
                source = "device.limits",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPULimits(
                maxTextureDimension2D = 8192L,
                copyBytesPerRowAlignment = 256L,
                minUniformBufferOffsetAlignment = 0L,
                source = "device.limits",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPULimits(
                maxTextureDimension2D = 8192L,
                copyBytesPerRowAlignment = 256L,
                minUniformBufferOffsetAlignment = 256L,
                source = "",
            )
        }
    }

    @Test
    fun `GPU limits expose stable capability facts`() {
        val facts = GPULimits(
            maxTextureDimension2D = 8192L,
            copyBytesPerRowAlignment = 256L,
            minUniformBufferOffsetAlignment = 256L,
            source = "runtime.conservative",
        ).capabilityFacts(evidenceLabel = "runtime")

        assertEquals(
            listOf(
                "maxTextureDimension2D",
                "copyBytesPerRowAlignment",
                "minUniformBufferOffsetAlignment",
            ),
            facts.map { it.name },
        )
        assertEquals(listOf("8192", "256", "256"), facts.map { it.value })
        assertEquals(setOf("runtime.conservative"), facts.map { it.source }.toSet())
        assertTrue(facts.all { it.affectsValidity })
        assertTrue(facts.all { it.evidenceLabel == "runtime" })
        assertTrue(!facts.joinToString("\n").contains("@"))
    }

    @Test
    fun `GPU limits expose max buffer size only when facade observed it`() {
        val legacyPositional = GPULimits(8192, 256, 256, "legacy-source")
        val unknown = GPULimits(
            maxTextureDimension2D = 8192L,
            copyBytesPerRowAlignment = 256L,
            minUniformBufferOffsetAlignment = 256L,
        )
        val observed = unknown.copy(maxBufferSize = 268_435_456L)

        assertEquals("legacy-source", legacyPositional.source)
        assertEquals(null, legacyPositional.maxBufferSize)
        assertEquals(null, unknown.maxBufferSize)
        assertTrue(unknown.capabilityFacts("unknown").none { it.name == "maxBufferSize" })
        assertEquals(
            GPUCapabilityFact(
                name = "maxBufferSize",
                source = "device.limits",
                value = "268435456",
                affectsValidity = true,
                evidenceLabel = "observed",
            ),
            observed.capabilityFacts("observed").single { it.name == "maxBufferSize" },
        )
        assertFailsWith<IllegalArgumentException> { unknown.copy(maxBufferSize = 0) }
    }

    @Test
    fun `GPU capabilities can carry limits without forcing existing facts`() {
        val limits = GPULimits.conservative(
            maxTextureDimension2D = 8192L,
            copyBytesPerRowAlignment = 256L,
            minUniformBufferOffsetAlignment = 256L,
        )
        val capabilities = GPUCapabilities(
            implementation = GPUImplementationIdentity(
                facadeName = "GPU",
                implementationName = "unit",
                adapterName = "unit-adapter",
                deviceName = "unit-device",
            ),
            facts = emptyList(),
            snapshotId = "unit-snapshot",
            limits = limits,
        )

        assertEquals(limits, capabilities.limits)
        assertEquals(emptyList(), capabilities.facts)
        assertEquals("runtime.conservative", capabilities.limits?.source)
    }

    @Test
    fun `GPU capabilities validate texture format usage size and uniform alignment`() {
        val capabilities = GPUCapabilities(
            implementation = GPUImplementationIdentity(
                facadeName = "GPU",
                implementationName = "native",
                adapterName = "unit-adapter",
                deviceName = "unit-device",
            ),
            facts = emptyList(),
            snapshotId = "unit-snapshot",
            limits = GPULimits.conservative(
                maxTextureDimension2D = 4096,
                copyBytesPerRowAlignment = 256,
                minUniformBufferOffsetAlignment = 256,
            ),
            supportedTextureFormats = setOf(GPUTextureFormat.RGBA8Unorm),
            supportedTextureUsage =
                GPUTextureUsage.CopyDst or
                    GPUTextureUsage.TextureBinding or
                    GPUTextureUsage.RenderAttachment,
            rendererFeatures = setOf(
                GPURendererFeature.TextureSampling,
                GPURendererFeature.UniformBuffer,
            ),
        )

        assertEquals(
            null,
            capabilities.validateTextureRequest(
                GPUTextureFormat.RGBA8Unorm,
                128,
                64,
                GPUTextureUsage.TextureBinding,
            ),
        )
        assertEquals(null, capabilities.validateUniformAlignment(512))
        assertEquals(
            "unsupported.capability.texture_format",
            capabilities.validateTextureRequest(
                GPUTextureFormat.BGRA8Unorm,
                128,
                64,
                GPUTextureUsage.TextureBinding,
            )?.code,
        )
        assertEquals(
            "unsupported.capability.texture_usage",
            capabilities.validateTextureRequest(
                GPUTextureFormat.RGBA8Unorm,
                128,
                64,
                GPUTextureUsage.StorageBinding,
            )?.code,
        )
        assertEquals(
            "unsupported.capability.texture_size",
            capabilities.validateTextureRequest(
                GPUTextureFormat.RGBA8Unorm,
                4097,
                64,
                GPUTextureUsage.TextureBinding,
            )?.code,
        )
        assertEquals(
            "unsupported.capability.uniform_alignment",
            capabilities.validateUniformAlignment(128)?.code,
        )
        assertEquals(
            "unsupported.capability.feature",
            capabilities.validateRendererFeature(GPURendererFeature.Readback)?.code,
        )
    }

    @Test
    fun `GPU capabilities allow finer uniform alignment when observed limits allow it`() {
        val capabilities = GPUCapabilities(
            implementation = GPUImplementationIdentity(
                facadeName = "GPU",
                implementationName = "native",
                adapterName = "unit-adapter",
                deviceName = "unit-device",
            ),
            facts = emptyList(),
            snapshotId = "unit-snapshot-64",
            limits = GPULimits.conservative(
                maxTextureDimension2D = 4096,
                copyBytesPerRowAlignment = 256,
                minUniformBufferOffsetAlignment = 64,
            ),
            supportedTextureFormats = setOf(GPUTextureFormat.RGBA8Unorm),
            supportedTextureUsage = GPUTextureUsage.CopyDst or GPUTextureUsage.TextureBinding,
            rendererFeatures = setOf(GPURendererFeature.UniformBuffer),
        )

        assertEquals(null, capabilities.validateUniformAlignment(64))
        assertEquals(
            "unsupported.capability.uniform_alignment",
            capabilities.validateUniformAlignment(32)?.code,
        )
    }

    @Test
    fun `GPU capabilities treat unknown supported texture usage as non-blocking`() {
        val capabilities = GPUCapabilities(
            implementation = GPUImplementationIdentity(
                facadeName = "GPU",
                implementationName = "native",
                adapterName = "unit-adapter",
                deviceName = "unit-device",
            ),
            facts = emptyList(),
            snapshotId = "unit-snapshot-unknown-usage",
            limits = GPULimits.conservative(
                maxTextureDimension2D = 4096,
                copyBytesPerRowAlignment = 256,
                minUniformBufferOffsetAlignment = 256,
            ),
            supportedTextureFormats = setOf(GPUTextureFormat.RGBA8Unorm),
        )

        assertEquals(
            null,
            capabilities.validateTextureRequest(
                format = GPUTextureFormat.RGBA8Unorm,
                width = 128,
                height = 64,
                usage = GPUTextureUsage.TextureBinding,
            ),
        )
        assertEquals(
            "unsupported.capability.texture_usage",
            capabilities.copy(
                snapshotId = "unit-snapshot-observed-no-usage",
                supportedTextureUsage = GPUTextureUsage.None,
            ).validateTextureRequest(
                format = GPUTextureFormat.RGBA8Unorm,
                width = 128,
                height = 64,
                usage = GPUTextureUsage.TextureBinding,
            )?.code,
        )
    }

    @Test
    fun `GPU capabilities report unknown texture usage bits instead of dropping them`() {
        val futureUsage = textureUsageForRawValue(1uL shl 24)
        val capabilities = GPUCapabilities(
            implementation = GPUImplementationIdentity(
                facadeName = "GPU",
                implementationName = "native",
                adapterName = "unit-adapter",
                deviceName = "unit-device",
            ),
            facts = emptyList(),
            snapshotId = "unit-snapshot-future-usage",
            supportedTextureFormats = setOf(GPUTextureFormat.RGBA8Unorm),
            supportedTextureUsage = GPUTextureUsage.TextureBinding,
        )

        assertEquals(
            listOf("unknown:0x1000000"),
            futureUsage.dumpLabels(),
        )
        assertEquals(
            "unknown:0x1000000",
            capabilities.validateTextureRequest(
                format = GPUTextureFormat.RGBA8Unorm,
                width = 128,
                height = 64,
                usage = GPUTextureUsage.TextureBinding or futureUsage,
            )?.required,
        )
    }

    @Test
    fun `GPU capabilities validate nonblank snapshot`() {
        val implementation = GPUImplementationIdentity(
            facadeName = "GPU",
            implementationName = "native",
            adapterName = "unit-adapter",
            deviceName = "unit-device",
        )

        assertFailsWith<IllegalArgumentException> {
            GPUCapabilities(
                implementation = implementation,
                facts = emptyList(),
                snapshotId = "",
            )
        }
    }

    @Test
    fun `device generation identity is checked and stable`() {
        assertEquals(GPUDeviceGenerationID(7), GPUDeviceGenerationID(7))
        assertEquals(7L, GPUDeviceGenerationID(7).value)
        assertFailsWith<IllegalArgumentException> { GPUDeviceGenerationID(-1) }
    }

    private fun textureUsageForRawValue(value: ULong): GPUTextureUsage {
        val method = GPUTextureUsage::class.java.getDeclaredMethod("box-impl", java.lang.Long.TYPE)
        return method.invoke(null, value.toLong()) as GPUTextureUsage
    }
}
