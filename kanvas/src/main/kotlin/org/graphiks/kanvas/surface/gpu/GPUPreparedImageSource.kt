package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactFactory
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactResult
import org.graphiks.kanvas.gpu.renderer.images.AlphaType as GPUAlphaType
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageOrientation
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProfile
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProvenance
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceClass
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceFormat
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceInput
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.color.ColorSpace

/**
 * Legacy semantic compatibility for whole frames rejected before W5e admission.
 * This factory is not W5e's source authority or a transport for its sealed plans.
 */
internal object GPUPreparedSurfaceImageSource {
    fun prepare(image: Image): GPUPreparedImageArtifactResult {
        if (image.premultiplication != org.graphiks.kanvas.render.ir.ImagePremultiplicationV1.SOURCE_SPACE)
            return GPUPreparedImageArtifactResult.Refused("unsupported.image.prepared.premultiplication",
                mapOf("boundary" to "legacy-pre-admission", "premultiplication" to image.premultiplication.name))
        val format = when (image.colorType) {
            ColorType.RGBA_8888 -> GPUPreparedImageSourceFormat.Rgba8
            ColorType.BGRA_8888 -> GPUPreparedImageSourceFormat.Bgra8
            ColorType.ALPHA_8 -> GPUPreparedImageSourceFormat.A8
            ColorType.RGB_565 -> GPUPreparedImageSourceFormat.Rgb565
            ColorType.ARGB_4444 -> GPUPreparedImageSourceFormat.Argb4444
            ColorType.RGBA_F16 -> GPUPreparedImageSourceFormat.RgbaF16
            ColorType.GRAY_8 -> GPUPreparedImageSourceFormat.Gray8
            else -> GPUPreparedImageSourceFormat.Unsupported
        }
        val sourceRowBytes = image.rowBytesI32.toLong()
        return GPUPreparedImageArtifactFactory.prepare(
            GPUPreparedImageSourceInput(
                GPUPreparedImageSourceClass.DecodedCpu, image.sourceId, image.width, image.height, format,
                image.alphaType.toGpuPreparedAlphaType(), sourceRowBytes,
                if (image.colorSpace == ColorSpace.SRGB) GPUPreparedImageProfile.Srgb else GPUPreparedImageProfile.Other,
                GPUPreparedImageOrientation.AppliedIdentity, GPUPreparedImageProvenance.RegisteredDecode, 0L, image.pixels,
            ),
        )
    }
}

private fun org.graphiks.kanvas.image.AlphaType.toGpuPreparedAlphaType(): GPUAlphaType = when (this) {
    org.graphiks.kanvas.image.AlphaType.OPAQUE -> GPUAlphaType.OPAQUE
    org.graphiks.kanvas.image.AlphaType.PREMUL -> GPUAlphaType.PREMUL
    org.graphiks.kanvas.image.AlphaType.UNPREMUL -> GPUAlphaType.UNPREMUL
    org.graphiks.kanvas.image.AlphaType.UNKNOWN -> GPUAlphaType.UNKNOWN
}
