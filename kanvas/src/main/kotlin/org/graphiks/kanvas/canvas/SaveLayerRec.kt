package org.graphiks.kanvas.canvas

import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.Rect

/** Complete save-layer request, including the optional filter over parent content. */
data class SaveLayerRec(
    val bounds: Rect? = null,
    val paint: Paint? = null,
    val backdrop: ImageFilter? = null,
)
