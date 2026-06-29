package org.graphiks.kanvas.gpu.renderer.images

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GpuYuvTextureTest {

    @Test
    fun `BT601 444 YUV with center siting builds accepted multi-plan texture route`() {
        val descriptor = GPUYUVMultiPlanDescriptor(
            colorSpace = YUVColorSpace.BT601,
            subsampling = ChromaSubsampling.S_444,
            planeCount = 3,
            perPlaneDims = listOf(
                PlaneDimensions(640, 480, 640),
                PlaneDimensions(640, 480, 640),
                PlaneDimensions(640, 480, 640),
            ),
            bitDepth = 8,
            chromaSiting = ChromaSiting.Center,
        )

        val route = GPUYuvTexture.planRoute(descriptor)

        val accepted = assertIs<GPUYUVMultiPlanTextureRoute.Accepted>(route)
        assertEquals(3, accepted.planeUploadPlan.planes.size)
        assertEquals(SingleChannelTextureFormat.R8Unorm, accepted.planeUploadPlan.format)
        assertEquals(0, accepted.planeUploadPlan.planes[0].planeIndex)
        assertEquals(640, accepted.planeUploadPlan.planes[0].width)
        assertEquals(480, accepted.planeUploadPlan.planes[0].height)
        assertEquals(1, accepted.planeUploadPlan.planes[1].planeIndex)
        assertEquals(2, accepted.planeUploadPlan.planes[2].planeIndex)
        assertEquals("gpu-renderer.image.yuv-multi-plan", accepted.routeKind)
        assertEquals(0.299, accepted.converterPlan.matrixCoefficients.kr)
        assertEquals(0.114, accepted.converterPlan.matrixCoefficients.kb)
        assertEquals(TransferFunction.SRGB, accepted.converterPlan.transferFn)
        assertEquals(ChromaSiting.Center, accepted.samplingPlan.chromaSiting)
        assertEquals(1.0, accepted.samplingPlan.uvScale.scaleU)
        assertEquals(1.0, accepted.samplingPlan.uvScale.scaleV)
    }

    @Test
    fun `BT709 420 YUV with center siting builds accepted route with scaled UV planes`() {
        val descriptor = GPUYUVMultiPlanDescriptor(
            colorSpace = YUVColorSpace.BT709,
            subsampling = ChromaSubsampling.S_420,
            planeCount = 3,
            perPlaneDims = listOf(
                PlaneDimensions(640, 480, 640),
                PlaneDimensions(320, 240, 320),
                PlaneDimensions(320, 240, 320),
            ),
            bitDepth = 8,
            chromaSiting = ChromaSiting.Center,
        )

        val route = GPUYuvTexture.planRoute(descriptor)

        val accepted = assertIs<GPUYUVMultiPlanTextureRoute.Accepted>(route)
        assertEquals(3, accepted.planeUploadPlan.planes.size)
        assertEquals(SingleChannelTextureFormat.R8Unorm, accepted.planeUploadPlan.format)
        assertEquals(640, accepted.planeUploadPlan.planes[0].width)
        assertEquals(480, accepted.planeUploadPlan.planes[0].height)
        assertEquals(320, accepted.planeUploadPlan.planes[1].width)
        assertEquals(240, accepted.planeUploadPlan.planes[1].height)
        assertEquals(0.2126, accepted.converterPlan.matrixCoefficients.kr)
        assertEquals(0.0722, accepted.converterPlan.matrixCoefficients.kb)
        assertEquals(TransferFunction.SRGB, accepted.converterPlan.transferFn)
        assertEquals(2.0, accepted.samplingPlan.uvScale.scaleU)
        assertEquals(2.0, accepted.samplingPlan.uvScale.scaleV)
    }

    @Test
    fun `BT2020 color space emits terminal refusal diagnostic`() {
        val descriptor = GPUYUVMultiPlanDescriptor(
            colorSpace = YUVColorSpace.BT2020,
            subsampling = ChromaSubsampling.S_444,
            planeCount = 3,
            perPlaneDims = listOf(
                PlaneDimensions(640, 480, 640),
                PlaneDimensions(640, 480, 640),
                PlaneDimensions(640, 480, 640),
            ),
            bitDepth = 8,
            chromaSiting = ChromaSiting.Center,
        )

        val route = GPUYuvTexture.planRoute(descriptor)

        val refused = assertIs<GPUYUVMultiPlanTextureRoute.Refused>(route)
        assertEquals("unsupported.image.yuv_color_space", refused.diagnostic.code)
        assertTrue(refused.diagnostic.terminal)
        assertTrue(refused.diagnostic.message.contains("BT.2020", ignoreCase = true))
    }

    @Test
    fun `plane count greater than 3 emits terminal refusal diagnostic`() {
        val descriptor = GPUYUVMultiPlanDescriptor(
            colorSpace = YUVColorSpace.BT601,
            subsampling = ChromaSubsampling.S_444,
            planeCount = 4,
            perPlaneDims = listOf(
                PlaneDimensions(640, 480, 640),
                PlaneDimensions(640, 480, 640),
                PlaneDimensions(640, 480, 640),
                PlaneDimensions(640, 480, 640),
            ),
            bitDepth = 8,
            chromaSiting = ChromaSiting.Center,
        )

        val route = GPUYuvTexture.planRoute(descriptor)

        val refused = assertIs<GPUYUVMultiPlanTextureRoute.Refused>(route)
        assertEquals("unsupported.image.yuv_plane_count", refused.diagnostic.code)
        assertTrue(refused.diagnostic.terminal)
        assertTrue(refused.diagnostic.message.contains("4", ignoreCase = false))
    }

    @Test
    fun `plane count less than 2 emits terminal refusal diagnostic`() {
        val descriptor = GPUYUVMultiPlanDescriptor(
            colorSpace = YUVColorSpace.BT601,
            subsampling = ChromaSubsampling.S_444,
            planeCount = 1,
            perPlaneDims = listOf(
                PlaneDimensions(640, 480, 640),
            ),
            bitDepth = 8,
            chromaSiting = ChromaSiting.Center,
        )

        val route = GPUYuvTexture.planRoute(descriptor)

        val refused = assertIs<GPUYUVMultiPlanTextureRoute.Refused>(route)
        assertEquals("unsupported.image.yuv_plane_count", refused.diagnostic.code)
        assertTrue(refused.diagnostic.terminal)
    }

    @Test
    fun `plane dimensions list size mismatch with planeCount emits terminal refusal`() {
        val descriptor = GPUYUVMultiPlanDescriptor(
            colorSpace = YUVColorSpace.BT601,
            subsampling = ChromaSubsampling.S_444,
            planeCount = 3,
            perPlaneDims = listOf(
                PlaneDimensions(640, 480, 640),
                PlaneDimensions(640, 480, 640),
            ),
            bitDepth = 8,
            chromaSiting = ChromaSiting.Center,
        )

        val route = GPUYuvTexture.planRoute(descriptor)

        val refused = assertIs<GPUYUVMultiPlanTextureRoute.Refused>(route)
        assertEquals("unsupported.image.yuv_plane_dims_mismatch", refused.diagnostic.code)
        assertTrue(refused.diagnostic.terminal)
    }

    @Test
    fun `bit depth other than 8 or 10 emits terminal refusal`() {
        val descriptor = GPUYUVMultiPlanDescriptor(
            colorSpace = YUVColorSpace.BT601,
            subsampling = ChromaSubsampling.S_444,
            planeCount = 3,
            perPlaneDims = listOf(
                PlaneDimensions(640, 480, 640),
                PlaneDimensions(640, 480, 640),
                PlaneDimensions(640, 480, 640),
            ),
            bitDepth = 12,
            chromaSiting = ChromaSiting.Center,
        )

        val route = GPUYuvTexture.planRoute(descriptor)

        val refused = assertIs<GPUYUVMultiPlanTextureRoute.Refused>(route)
        assertEquals("unsupported.image.yuv_bit_depth", refused.diagnostic.code)
        assertTrue(refused.diagnostic.terminal)
    }

    @Test
    fun `unsupported chroma siting left emits terminal refusal`() {
        val descriptor = GPUYUVMultiPlanDescriptor(
            colorSpace = YUVColorSpace.BT601,
            subsampling = ChromaSubsampling.S_444,
            planeCount = 3,
            perPlaneDims = listOf(
                PlaneDimensions(640, 480, 640),
                PlaneDimensions(640, 480, 640),
                PlaneDimensions(640, 480, 640),
            ),
            bitDepth = 8,
            chromaSiting = ChromaSiting.Left,
        )

        val route = GPUYuvTexture.planRoute(descriptor)

        val refused = assertIs<GPUYUVMultiPlanTextureRoute.Refused>(route)
        assertEquals("unsupported.image.yuv_chroma_siting", refused.diagnostic.code)
        assertTrue(refused.diagnostic.terminal)
    }

    @Test
    fun `unvalidated converter WGSL module emits terminal refusal`() {
        val descriptor = GPUYUVMultiPlanDescriptor(
            colorSpace = YUVColorSpace.BT601,
            subsampling = ChromaSubsampling.S_444,
            planeCount = 3,
            perPlaneDims = listOf(
                PlaneDimensions(640, 480, 640),
                PlaneDimensions(640, 480, 640),
                PlaneDimensions(640, 480, 640),
            ),
            bitDepth = 8,
            chromaSiting = ChromaSiting.Center,
        )

        val route = GPUYuvTexture.planRoute(descriptor, validatedConverterModules = emptySet())

        val refused = assertIs<GPUYUVMultiPlanTextureRoute.Refused>(route)
        assertEquals("unsupported.image.yuv_converter_wgsl_unvalidated", refused.diagnostic.code)
        assertTrue(refused.diagnostic.terminal)
    }

    @Test
    fun `default validated converter modules accept BT601 and BT709 routes`() {
        val bt601 = GPUYUVMultiPlanDescriptor(
            colorSpace = YUVColorSpace.BT601,
            subsampling = ChromaSubsampling.S_444,
            planeCount = 3,
            perPlaneDims = listOf(
                PlaneDimensions(8, 8, 8),
                PlaneDimensions(8, 8, 8),
                PlaneDimensions(8, 8, 8),
            ),
            bitDepth = 8,
            chromaSiting = ChromaSiting.Center,
        )

        val accepted = assertIs<GPUYUVMultiPlanTextureRoute.Accepted>(GPUYuvTexture.planRoute(bt601))
        assertEquals(WgslModuleId("wgsl:yuv-to-rgb:bt601"), accepted.converterPlan.wgslModule)
    }
}
