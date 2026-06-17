package org.graphiks.kanvas.gpu.renderer.images

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.dumpLines

class ImageSamplerMaterializationPreimageTest {
    @Test
    fun `sampler boundary derives texture and sampler materialization preimage without live handles`() {
        val boundary = GPUImageSamplerBoundaryPlanner().plan(
            source = checkerPixelsForPreimage,
            sampling = linearClampSamplingForPreimage,
        )

        val preimage = boundary.toTextureSamplerBindingPreimage()

        assertEquals(true, preimage.accepted)
        assertFalse(preimage.nonClaims.adapterBacked)
        assertFalse(preimage.nonClaims.liveHandles)
        assertFalse(preimage.nonClaims.productRoute)
        assertEquals(
            listOf(GPUMaterializedResourceRole.SampledTexture, GPUMaterializedResourceRole.Sampler),
            preimage.resources.map { it.role }.sortedBy { it.name },
        )
        assertEquals(
            listOf(
                "resource-preimage:accepted plan=image-sampler-binding:image-checker-v1 source=gpu-renderer.sampler-boundary resources=image-sampler:${boundary.samplerDescriptorHash.removePrefix("sha256:")},image-texture:image-checker-v1 bindings=group1.binding1,group1.binding2 adapterBacked=false liveHandles=false productRoute=false",
                "resource-preimage:resource label=image-sampler:${boundary.samplerDescriptorHash.removePrefix("sha256:")} role=sampler generation=0 lifetime=pipeline-cache descriptor=${boundary.samplerDescriptorHash} usage=none facts=address=clamp-to-edge/clamp-to-edge;filter=linear/linear;mipmap=none",
                "resource-preimage:resource label=image-texture:image-checker-v1 role=sampled-texture generation=3 lifetime=recording-local descriptor=${boundary.viewDescriptor.textureDescriptorHash} usage=copy_dst,texture_binding facts=source=image:checker:v1;view=2d",
                "resource-preimage:nonclaim adapterBacked=false liveHandles=false productRoute=false providerCalled=false submitCalled=false",
            ),
            preimage.dumpLines(),
        )
        assertFalse(preimage.dumpLines().joinToString("\n").contains("backend-handle", ignoreCase = true))
        assertFalse(preimage.dumpLines().joinToString("\n").contains("texture-handle", ignoreCase = true))
    }

    @Test
    fun `refused sampler boundary derives refused materialization preimage`() {
        val boundary = GPUImageSamplerBoundaryPlanner().plan(
            source = checkerPixelsForPreimage,
            sampling = linearClampSamplingForPreimage.copy(mipmapMode = "linear"),
        )

        val preimage = boundary.toTextureSamplerBindingPreimage()

        assertFalse(preimage.accepted)
        assertEquals("unsupported.texture.mipmap_unavailable", preimage.refusalCode)
        assertEquals(
            listOf(
                "resource-preimage:refused plan=image-sampler-binding:image-checker-v1 source=gpu-renderer.sampler-boundary reason=unsupported.texture.mipmap_unavailable resources=none bindings=none adapterBacked=false liveHandles=false productRoute=false",
                "resource-preimage:nonclaim adapterBacked=false liveHandles=false productRoute=false providerCalled=false submitCalled=false",
            ),
            preimage.dumpLines(),
        )
    }
}

private val checkerPixelsForPreimage = GPUDecodedImagePixelsDescriptor(
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

private val linearClampSamplingForPreimage = GPUDecodedImageSamplingPlan(
    tileModeX = "clamp",
    tileModeY = "clamp",
    filterMode = "linear",
    mipmapMode = "none",
)
