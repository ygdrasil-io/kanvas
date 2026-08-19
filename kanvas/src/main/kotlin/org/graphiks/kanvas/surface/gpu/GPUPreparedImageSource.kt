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
import org.graphiks.kanvas.types.ColorSpace

object GPUPreparedSurfaceImageSource {
    fun prepare(image: Image): GPUPreparedImageArtifactResult {
        val format = when (image.colorType) {
            ColorType.RGBA_8888 -> GPUPreparedImageSourceFormat.Rgba8
            ColorType.BGRA_8888 -> GPUPreparedImageSourceFormat.Bgra8
            ColorType.ALPHA_8 -> GPUPreparedImageSourceFormat.A8
            else -> GPUPreparedImageSourceFormat.Unsupported
        }
        val sourceRowBytes = image.width.toLong() * image.colorType.bytesPerPixel
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
