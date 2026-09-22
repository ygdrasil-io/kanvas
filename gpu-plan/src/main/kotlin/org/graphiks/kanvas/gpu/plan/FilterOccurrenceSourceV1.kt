package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.LayerDescriptor
import org.graphiks.kanvas.render.ir.SceneSnapshot

/**
 * Captured provenance for one W6b coverage generation.  It retains the actual immutable scene
 * and every enclosing Picture draw; Task 3 may materialize it but must not rediscover a source
 * from the mutable root attachment.
 */
public class FilterOccurrenceSourceV1 internal constructor(
    public val scene: SceneSnapshot,
    public val sourceCommandIndexI32: Int,
    public val sourceDraw: DrawNode?,
    outerPictures: List<DrawNode>,
    public val layerDescriptor: LayerDescriptor? = null,
) {
    private val outerPicturesSnapshot = immutableList(outerPictures)

    init {
        require(sourceCommandIndexI32 >= 0)
        require((sourceDraw == null) == (layerDescriptor != null)) {
            "A W6b occurrence is either a captured draw/Picture or a captured layer."
        }
    }

    public fun outerPictures(): List<DrawNode> = outerPicturesSnapshot
}
