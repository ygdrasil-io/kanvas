package org.graphiks.kanvas.canvas

import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.Rect

/** Complete save-layer request, including the optional filter over parent content. */
data class SaveLayerRec(
    val bounds: Rect? = null,
    val paint: Paint? = null,
    val backdrop: ImageFilter? = null,
    /**
     * GPU replay detail for a synthetic picture group: its final composite must retain the
     * DrawPicture clip even though the group's children have already been expanded.
     *
     * Ordinary Canvas saveLayer calls leave this null. It is intentionally not serialized as part
     * of the public Picture stream because it is derived from the replay context.
     */
    internal val compositeClip: ClipStack? = null,
)
