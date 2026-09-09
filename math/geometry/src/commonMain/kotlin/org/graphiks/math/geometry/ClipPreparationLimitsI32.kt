package org.graphiks.math.geometry

/** Integer resource ceilings for one entry, one clip stack, and one frame. */
public data class ClipPreparationLimitsI32(
    public val maxClipEntryCountPerStackI32: Int = 64,
    public val maxClipEntryCountPerFrameI32: Int = 1_024,
    public val maxAttemptedEdgesPerEntryI32: Int = 65_536,
    public val maxAttemptedEdgesPerStackI32: Int = 262_144,
    public val maxAttemptedEdgesPerFrameI32: Int = 1_048_576,
    public val maxEmittedVertexCountPerEntryI32: Int = 262_144,
    public val maxEmittedVertexCountPerStackI32: Int = 1_048_576,
    public val maxEmittedVertexCountPerFrameI32: Int = 4_194_304,
    public val maxEmittedIndexCountPerEntryI32: Int = 786_432,
    public val maxEmittedIndexCountPerStackI32: Int = 3_145_728,
    public val maxEmittedIndexCountPerFrameI32: Int = 12_582_912,
) {
    init {
        listOf(
            maxClipEntryCountPerStackI32, maxClipEntryCountPerFrameI32,
            maxAttemptedEdgesPerEntryI32, maxAttemptedEdgesPerStackI32, maxAttemptedEdgesPerFrameI32,
            maxEmittedVertexCountPerEntryI32, maxEmittedVertexCountPerStackI32,
            maxEmittedVertexCountPerFrameI32, maxEmittedIndexCountPerEntryI32,
            maxEmittedIndexCountPerStackI32, maxEmittedIndexCountPerFrameI32,
        ).forEach { require(it > 0) }
    }
}
