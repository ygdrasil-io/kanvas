package org.graphiks.kanvas.gpu.renderer.images

import org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic

enum class YUVColorSpace { BT601, BT709, BT2020 }

enum class ChromaSubsampling { S_420, S_422, S_444 }

enum class SingleChannelTextureFormat { R8Unorm, R16Unorm }

enum class ChromaSiting { Center, Left, TopLeft }

enum class TransferFunction { SRGB, Linear }

data class PlaneDimensions(
    val width: Int,
    val height: Int,
    val rowBytes: Int,
)

data class PlaneUpload(
    val planeIndex: Int,
    val width: Int,
    val height: Int,
    val rowBytes: Int,
)

data class GPUYUVMultiPlanDescriptor(
    val colorSpace: YUVColorSpace,
    val subsampling: ChromaSubsampling,
    val planeCount: Int,
    val perPlaneDims: List<PlaneDimensions>,
    val bitDepth: Int,
    val chromaSiting: ChromaSiting,
)

data class GPUYUVPlaneUploadPlan(
    val planes: List<PlaneUpload>,
    val format: SingleChannelTextureFormat,
)

@JvmInline
value class WgslModuleId(val value: String)

data class YUVMatrixCoefficients(
    val kr: Double,
    val kg: Double,
    val kb: Double,
)

data class GPUYUVToRGBCoverterPlan(
    val wgslModule: WgslModuleId,
    val matrixCoefficients: YUVMatrixCoefficients,
    val transferFn: TransferFunction,
)

data class UVScaleStrategy(
    val scaleU: Double,
    val scaleV: Double,
)

data class GPUYUVSamplingPlan(
    val chromaSiting: ChromaSiting,
    val uvScale: UVScaleStrategy,
)

sealed interface GPUYUVMultiPlanTextureRoute {
    data class Accepted(
        val descriptor: GPUYUVMultiPlanDescriptor,
        val planeUploadPlan: GPUYUVPlaneUploadPlan,
        val converterPlan: GPUYUVToRGBCoverterPlan,
        val samplingPlan: GPUYUVSamplingPlan,
        val routeKind: String = "gpu-renderer.image.yuv-multi-plan",
    ) : GPUYUVMultiPlanTextureRoute

    data class Refused(
        val diagnostic: RefuseDiagnostic,
        val descriptor: GPUYUVMultiPlanDescriptor,
    ) : GPUYUVMultiPlanTextureRoute
}

object GpuYuvTexture {

    fun planRoute(descriptor: GPUYUVMultiPlanDescriptor): GPUYUVMultiPlanTextureRoute {
        if (descriptor.planeCount < 2 || descriptor.planeCount > 3) {
            return GPUYUVMultiPlanTextureRoute.Refused(
                diagnostic = RefuseDiagnostic(
                    code = "unsupported.image.yuv_plane_count",
                    message = "YUV multi-plan route refused: planeCount=${descriptor.planeCount} is not supported (2-3 supported).",
                    stage = "yuv-multi-plan",
                    terminal = true,
                ),
                descriptor = descriptor,
            )
        }

        if (descriptor.perPlaneDims.size != descriptor.planeCount) {
            return GPUYUVMultiPlanTextureRoute.Refused(
                diagnostic = RefuseDiagnostic(
                    code = "unsupported.image.yuv_plane_dims_mismatch",
                    message = "YUV multi-plan route refused: perPlaneDims.size=${descriptor.perPlaneDims.size} does not match planeCount=${descriptor.planeCount}.",
                    stage = "yuv-multi-plan",
                    terminal = true,
                ),
                descriptor = descriptor,
            )
        }

        if (descriptor.bitDepth != 8 && descriptor.bitDepth != 10) {
            return GPUYUVMultiPlanTextureRoute.Refused(
                diagnostic = RefuseDiagnostic(
                    code = "unsupported.image.yuv_bit_depth",
                    message = "YUV multi-plan route refused: bitDepth=${descriptor.bitDepth} is not supported (8 or 10 supported).",
                    stage = "yuv-multi-plan",
                    terminal = true,
                ),
                descriptor = descriptor,
            )
        }

        if (descriptor.colorSpace == YUVColorSpace.BT2020) {
            return GPUYUVMultiPlanTextureRoute.Refused(
                diagnostic = RefuseDiagnostic(
                    code = "unsupported.image.yuv_color_space",
                    message = "YUV multi-plan route refused: BT.2020 color space requires accepted spec review.",
                    stage = "yuv-multi-plan",
                    terminal = true,
                ),
                descriptor = descriptor,
            )
        }

        if (descriptor.chromaSiting != ChromaSiting.Center) {
            return GPUYUVMultiPlanTextureRoute.Refused(
                diagnostic = RefuseDiagnostic(
                    code = "unsupported.image.yuv_chroma_siting",
                    message = "YUV multi-plan route refused: chroma siting=${descriptor.chromaSiting} is not supported (center only).",
                    stage = "yuv-multi-plan",
                    terminal = true,
                ),
                descriptor = descriptor,
            )
        }

        val format = when (descriptor.bitDepth) {
            8 -> SingleChannelTextureFormat.R8Unorm
            10 -> SingleChannelTextureFormat.R16Unorm
            else -> SingleChannelTextureFormat.R8Unorm
        }

        val planes = descriptor.perPlaneDims.mapIndexed { index, dims ->
            PlaneUpload(
                planeIndex = index,
                width = dims.width,
                height = dims.height,
                rowBytes = dims.rowBytes,
            )
        }

        val planeUploadPlan = GPUYUVPlaneUploadPlan(
            planes = planes,
            format = format,
        )

        val matrixCoefficients = when (descriptor.colorSpace) {
            YUVColorSpace.BT601 -> YUVMatrixCoefficients(kr = 0.299, kg = 0.587, kb = 0.114)
            YUVColorSpace.BT709 -> YUVMatrixCoefficients(kr = 0.2126, kg = 0.7152, kb = 0.0722)
            YUVColorSpace.BT2020 -> YUVMatrixCoefficients(kr = 0.2627, kg = 0.6780, kb = 0.0593)
        }

        val transferFn = when (descriptor.bitDepth) {
            8 -> TransferFunction.SRGB
            10 -> TransferFunction.Linear
            else -> TransferFunction.SRGB
        }

        val converterPlan = GPUYUVToRGBCoverterPlan(
            wgslModule = WgslModuleId("wgsl:yuv-to-rgb:${descriptor.colorSpace.name.lowercase()}"),
            matrixCoefficients = matrixCoefficients,
            transferFn = transferFn,
        )

        val (scaleU, scaleV) = when (descriptor.subsampling) {
            ChromaSubsampling.S_444 -> 1.0 to 1.0
            ChromaSubsampling.S_422 -> 2.0 to 1.0
            ChromaSubsampling.S_420 -> 2.0 to 2.0
        }

        val samplingPlan = GPUYUVSamplingPlan(
            chromaSiting = descriptor.chromaSiting,
            uvScale = UVScaleStrategy(scaleU = scaleU, scaleV = scaleV),
        )

        return GPUYUVMultiPlanTextureRoute.Accepted(
            descriptor = descriptor,
            planeUploadPlan = planeUploadPlan,
            converterPlan = converterPlan,
            samplingPlan = samplingPlan,
        )
    }
}
