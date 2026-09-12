package org.graphiks.kanvas.gpu.plan

public enum class GradientFamilyV2 { LINEAR, RADIAL, SWEEP, CONICAL }
public enum class GradientTileModeV2 { CLAMP, REPEAT, MIRROR, DECAL }

/** Closed scalar/validity grammar, shared by every gradient family. */
public sealed interface GradientTileOperationNodeV2 {
    public sealed interface ScalarF32 : GradientTileOperationNodeV2
    public sealed interface Flag : GradientTileOperationNodeV2
    public data object InputTF32 : ScalarF32
    public data object InputValidity : Flag
    public data class ConstantF32(public val valueF32: Float) : ScalarF32
    public data class MulF32(public val left: ScalarF32, public val right: ScalarF32) : ScalarF32
    public data class SubF32(public val left: ScalarF32, public val right: ScalarF32) : ScalarF32
    public data class FloorF32(public val input: ScalarF32) : ScalarF32
    public data class AbsF32(public val input: ScalarF32) : ScalarF32
    public data class CompareF32(public val left: ScalarF32, public val right: ScalarF32,
        public val lessOrEqual: Boolean = false) : Flag
    public data class SelectF32(public val otherwise: ScalarF32, public val selected: ScalarF32,
        public val condition: Flag) : ScalarF32
    public data class AndValidity(public val left: Flag, public val right: Flag) : Flag
}

public sealed interface GradientTileOperationGraphV2 {
    public val requestedMode: GradientTileModeV2
    public val effectiveMode: GradientTileModeV2
    public val outputTF32: GradientTileOperationNodeV2.ScalarF32
    public val validity: GradientTileOperationNodeV2.Flag
    public val contractId: String get() = "gradient-tile-v2"
    public companion object {
        public fun clamp(): GradientTileOperationGraphV2 = Clamp
        public fun repeat(): GradientTileOperationGraphV2 = Repeat
        public fun mirror(): GradientTileOperationGraphV2 = Mirror
        public fun decal(): GradientTileOperationGraphV2 = Decal
        internal fun fullCoverageClamp(requestedMode: GradientTileModeV2): GradientTileOperationGraphV2 =
            FullCoverageClamp(requestedMode)

        private val t = GradientTileOperationNodeV2.InputTF32
        private val zero = GradientTileOperationNodeV2.ConstantF32(0f)
        private val one = GradientTileOperationNodeV2.ConstantF32(1f)
        private fun safeClamp(): GradientTileOperationNodeV2.ScalarF32 {
            val lower = GradientTileOperationNodeV2.SelectF32(t, zero, GradientTileOperationNodeV2.CompareF32(t, zero))
            return GradientTileOperationNodeV2.SelectF32(lower, one, GradientTileOperationNodeV2.CompareF32(one, lower))
        }
    }
    private data class FullCoverageClamp(override val requestedMode: GradientTileModeV2) : GradientTileOperationGraphV2 {
        override val effectiveMode: GradientTileModeV2 = GradientTileModeV2.CLAMP
        override val outputTF32: GradientTileOperationNodeV2.ScalarF32 = Clamp.outputTF32
        override val validity: GradientTileOperationNodeV2.Flag = Clamp.validity
    }
    private data object Clamp : GradientTileOperationGraphV2 {
        override val requestedMode: GradientTileModeV2 = GradientTileModeV2.CLAMP
        override val effectiveMode: GradientTileModeV2 = GradientTileModeV2.CLAMP
        override val outputTF32: GradientTileOperationNodeV2.ScalarF32 =
            safeClamp()
        override val validity: GradientTileOperationNodeV2.Flag = GradientTileOperationNodeV2.InputValidity
    }
    private data object Repeat : GradientTileOperationGraphV2 {
        override val requestedMode: GradientTileModeV2 = GradientTileModeV2.REPEAT
        override val effectiveMode: GradientTileModeV2 = requestedMode
        override val outputTF32: GradientTileOperationNodeV2.ScalarF32 =
            GradientTileOperationNodeV2.SubF32(t, GradientTileOperationNodeV2.FloorF32(t))
        override val validity: GradientTileOperationNodeV2.Flag = GradientTileOperationNodeV2.InputValidity
    }
    private data object Mirror : GradientTileOperationGraphV2 {
        override val requestedMode: GradientTileModeV2 = GradientTileModeV2.MIRROR
        override val effectiveMode: GradientTileModeV2 = requestedMode
        private val q = GradientTileOperationNodeV2.SubF32(t, GradientTileOperationNodeV2.MulF32(
            GradientTileOperationNodeV2.ConstantF32(2f), GradientTileOperationNodeV2.FloorF32(
                GradientTileOperationNodeV2.MulF32(t, GradientTileOperationNodeV2.ConstantF32(.5f)))))
        override val outputTF32: GradientTileOperationNodeV2.ScalarF32 =
            GradientTileOperationNodeV2.SubF32(one, GradientTileOperationNodeV2.AbsF32(GradientTileOperationNodeV2.SubF32(q, one)))
        override val validity: GradientTileOperationNodeV2.Flag = GradientTileOperationNodeV2.InputValidity
    }
    private data object Decal : GradientTileOperationGraphV2 {
        override val requestedMode: GradientTileModeV2 = GradientTileModeV2.DECAL
        override val effectiveMode: GradientTileModeV2 = requestedMode
        override val outputTF32: GradientTileOperationNodeV2.ScalarF32 = safeClamp()
        override val validity: GradientTileOperationNodeV2.Flag = GradientTileOperationNodeV2.AndValidity(
            GradientTileOperationNodeV2.InputValidity, GradientTileOperationNodeV2.AndValidity(
                GradientTileOperationNodeV2.CompareF32(zero, t, lessOrEqual = true),
                GradientTileOperationNodeV2.CompareF32(t, one, lessOrEqual = true)))
    }
}

internal fun GradientTileModeV2.operationGraph(fullCoverageClamp: Boolean = false): GradientTileOperationGraphV2 =
    if (fullCoverageClamp) GradientTileOperationGraphV2.fullCoverageClamp(this) else when (this) {
    GradientTileModeV2.CLAMP -> GradientTileOperationGraphV2.clamp()
    GradientTileModeV2.REPEAT -> GradientTileOperationGraphV2.repeat()
    GradientTileModeV2.MIRROR -> GradientTileOperationGraphV2.mirror()
    GradientTileModeV2.DECAL -> GradientTileOperationGraphV2.decal()
}
