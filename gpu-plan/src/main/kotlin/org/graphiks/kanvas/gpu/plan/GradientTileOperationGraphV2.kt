package org.graphiks.kanvas.gpu.plan

public enum class GradientFamilyV2 { LINEAR, RADIAL, SWEEP, CONICAL }
public enum class GradientTileModeV2 { CLAMP, REPEAT, MIRROR, DECAL }

/** Closed typed inputs and outputs; later tile modes extend this grammar. */
public sealed interface GradientTileOperationNodeV2 {
    public sealed interface ScalarF32 : GradientTileOperationNodeV2
    public sealed interface Flag : GradientTileOperationNodeV2
    public data object InputTF32 : ScalarF32
    public data object InputValidity : Flag
    public data class ClampF32(public val input: ScalarF32) : ScalarF32
}

public sealed interface GradientTileOperationGraphV2 {
    public val requestedMode: GradientTileModeV2
    public val effectiveMode: GradientTileModeV2
    public val outputTF32: GradientTileOperationNodeV2.ScalarF32
    public val validity: GradientTileOperationNodeV2.Flag
    public val contractId: String get() = "gradient-tile-v2"
    public companion object {
        public fun clamp(): GradientTileOperationGraphV2 = Clamp
    }
    private data object Clamp : GradientTileOperationGraphV2 {
        override val requestedMode: GradientTileModeV2 = GradientTileModeV2.CLAMP
        override val effectiveMode: GradientTileModeV2 = GradientTileModeV2.CLAMP
        override val outputTF32: GradientTileOperationNodeV2.ScalarF32 =
            GradientTileOperationNodeV2.ClampF32(GradientTileOperationNodeV2.InputTF32)
        override val validity: GradientTileOperationNodeV2.Flag = GradientTileOperationNodeV2.InputValidity
    }
}
