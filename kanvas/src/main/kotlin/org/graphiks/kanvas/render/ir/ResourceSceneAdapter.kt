package org.graphiks.kanvas.render.ir

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.AlphaType

/** Resource conversion boundary. Image ownership is always made explicit here. */
public object ResourceSceneAdapter {
    public fun captureImage(image: Image): ImageResourceSnapshot {
        val format = ImagePixelFormat.valueOf(image.colorType.name)
        val alpha = ImageAlphaType.valueOf(image.alphaType.name)
        val pixels = image.pixels
        return if (pixels == null) {
            ExternalImageReference.of(image.sourceId, image.width, image.height, format, alpha, image.colorSpace)
        } else {
            ImageResourceSnapshot.fromPixels(
                sourceId = image.sourceId,
                width = image.width,
                height = image.height,
                pixelFormat = format,
                alphaType = alpha,
                colorSpace = image.colorSpace,
                rowBytes = Math.multiplyExact(image.width, format.bytesPerPixel),
                pixels = pixels,
            )
        }
    }

    /** Rebuilds an application Image without retaining the captured storage array. */
    public fun toImage(resource: ImageResourceSnapshot): Image = Image(
        width = resource.width,
        height = resource.height,
        colorType = ColorType.valueOf(resource.pixelFormat.name),
        sourceId = resource.sourceId,
        pixels = (resource as? ImageResourceSnapshot.Pixels)?.copyPixels(),
        colorSpace = resource.colorSpace,
        alphaType = AlphaType.valueOf(resource.alphaType.name),
    )
}
