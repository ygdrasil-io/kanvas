package org.graphiks.kanvas.skia

import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.Image

internal fun Bitmap.toImageForGm(sourceId: String = "bitmap"): Image =
    requireNotNull(toImageOrNull()) {
        "GM bitmap uses unsupported image color profile: ${colorSpace.profileRefusalCode}"
    }.copy(sourceId = sourceId)
