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
     * GPU replay detail for a group restore: its final composite must retain the deferred outer
     * clip even though the layer children are rendered without that clip.
     *
     * Canvas recording sets this for a public saveLayer under a clip; picture replay does the
     * same for a painted DrawPicture. It is serialized with a Picture so a roundtrip retains the
     * group-composite semantics, but remains internal because Canvas derives it from state.
     */
    internal val compositeClip: ClipStack? = null,
)
