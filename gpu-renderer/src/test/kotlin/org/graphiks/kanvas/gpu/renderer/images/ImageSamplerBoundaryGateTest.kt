package org.graphiks.kanvas.gpu.renderer.images

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class ImageSamplerBoundaryGateTest {
    @Test
    fun `sampler boundary dumps tile mip sampler and key facts without promotion`() {
        val plan = GPUImageSamplerBoundaryPlanner().plan(
            source = checkerPixels,
            sampling = linearClampSampling,
        )

        assertEquals("GPUNative", plan.routeKind)
        assertEquals("TargetNative", plan.classification)
        assertFalse(plan.promoted)
        assertEquals("clamp-to-edge", plan.samplerDescriptor.addressModeU)
        assertEquals("clamp-to-edge", plan.samplerDescriptor.addressModeV)
        assertEquals("linear", plan.samplerDescriptor.magFilter)
        assertEquals("none", plan.samplerDescriptor.mipmapFilter)
        assertEquals(
            listOf(
                "sampler-boundary:accepted row=gpu-renderer.sampler-boundary routeKind=GPUNative classification=TargetNative promoted=false productActivation=false source=image:checker:v1",
                "sampler-boundary:sampler descriptor=${plan.samplerDescriptorHash} address=clamp-to-edge/clamp-to-edge filter=linear/linear mipmap=none lod=0..0 compare=none anisotropy=1 capabilities=none",
                "sampler-boundary:tile x=clamp y=clamp address=clamp-to-edge/clamp-to-edge wgsl=hardware-address-mode broadTileSupport=false",
                "sampler-boundary:mip requested=none availableLevels=1 viewMipRange=0..0 policy=no-mipmap support=false",
                "sampler-boundary:key sampler=${plan.samplerBehaviorKey} includes=tile-mode,filter-mode,mipmap-mode,lod-clamp,compare-mode,anisotropy,coordinate-transform excludes=texture-handle,upload-artifact-key,pixel-content,row-bytes,sampler-object",
                "sampler-boundary:pipelineKey=${plan.pipelineKey} includes=binding-layout,sample-type,coordinate-transform-class excludes=address-mode,filter-mode,mipmap-mode,lod-clamp,anisotropy,resource-handle,artifact-key,pixel-content",
                "nonclaim:no-product-activation no-adapter-backed-execution no-native-sampler-support no-mipmap-support no-broad-tile-mode-support no-perspective-sampling no-cpu-rendered-compat-texture",
            ),
            plan.dumpLines(),
        )
    }

    @Test
    fun `sampler behavior key changes for sampler facts while pipeline key excludes ordinary sampler values`() {
        val linear = GPUImageSamplerBoundaryPlanner().plan(
            source = checkerPixels,
            sampling = linearClampSampling,
        )
        val nearest = GPUImageSamplerBoundaryPlanner().plan(
            source = checkerPixels.copy(
                generation = 4,
                contentHash = "sha256:changed-pixels",
                rowBytes = 16,
            ),
            sampling = linearClampSampling.copy(filterMode = "nearest"),
        )

        assertNotEquals(linear.samplerBehaviorKey, nearest.samplerBehaviorKey)
        assertEquals(linear.pipelineKey, nearest.pipelineKey)
        assertFalse(linear.pipelineKey.contains("linear"))
        assertFalse(nearest.pipelineKey.contains("nearest"))
        assertFalse(nearest.pipelineKey.contains("changed-pixels"))
        assertFalse(nearest.samplerBehaviorKey.contains("changed-pixels"))
        assertFalse(nearest.samplerBehaviorKey.contains("row16"))
    }

    @Test
    fun `unsupported sampler boundary variants refuse with stable diagnostics`() {
        val cases = listOf(
            BoundaryRefusalCase(
                expectedCode = "unsupported.texture.mipmap_unavailable",
                sampling = linearClampSampling.copy(mipmapMode = "linear"),
            ),
            BoundaryRefusalCase(
                expectedCode = "unsupported.image.sampling_cubic",
                sampling = linearClampSampling.copy(filterMode = "cubic"),
            ),
            BoundaryRefusalCase(
                expectedCode = "unsupported.image.sampler_anisotropy",
                sampling = linearClampSampling.copy(anisotropy = 0),
            ),
            BoundaryRefusalCase(
                expectedCode = "unsupported.image.sampling_anisotropic",
                sampling = linearClampSampling.copy(anisotropy = 8),
            ),
            BoundaryRefusalCase(
                expectedCode = "unsupported.image.sampler_lod_clamp",
                sampling = linearClampSampling.copy(lodMinClamp = "pointer:0xbeef"),
            ),
            BoundaryRefusalCase(
                expectedCode = "unsupported.image.sampler_lod_clamp",
                sampling = linearClampSampling.copy(lodMaxClamp = "1"),
            ),
            BoundaryRefusalCase(
                expectedCode = "unsupported.image.perspective_sampling",
                sampling = linearClampSampling.copy(coordinateTransformClass = "perspective"),
            ),
        )

        for (case in cases) {
            val plan = GPUImageSamplerBoundaryPlanner().plan(
                source = checkerPixels,
                sampling = case.sampling,
            )

            assertEquals("RefuseDiagnostic", plan.routeKind)
            assertEquals(case.expectedCode, plan.diagnostics.single().code)
            assertContains(plan.dumpLines().first(), "reason=${case.expectedCode}")
            assertContains(plan.dumpLines().last(), "no-native-sampler-support")
        }
    }
}

private data class BoundaryRefusalCase(
    val expectedCode: String,
    val sampling: GPUDecodedImageSamplingPlan,
)

private val checkerPixels = GPUDecodedImagePixelsDescriptor(
    sourceId = "image:checker:v1",
    width = 2,
    height = 2,
    pixelFormat = "RGBA8Unorm",
    rowBytes = 8,
    alphaType = "Premul",
    colorProfileLabel = "srgb",
    orientationState = "Applied",
    generation = 3,
    contentHash = "sha256:checker-pixels-v1",
    provenance = "unit-test",
)

private val linearClampSampling = GPUDecodedImageSamplingPlan(
    tileModeX = "clamp",
    tileModeY = "clamp",
    filterMode = "linear",
    mipmapMode = "none",
)
