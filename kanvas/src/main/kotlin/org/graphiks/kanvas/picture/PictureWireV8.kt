package org.graphiks.kanvas.picture

import org.graphiks.kanvas.color.Gamut
import org.graphiks.kanvas.color.TransferFunction
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.PathVerb
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorChannel
import org.graphiks.kanvas.paint.ColorSpaceInterpolation
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Path1DStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.pipeline.ChildType
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.pipeline.UniformType
import org.graphiks.kanvas.pipeline.VertexFormat
import org.graphiks.kanvas.pipeline.VertexStepMode
import org.graphiks.kanvas.types.LatticeFlags
import org.graphiks.kanvas.types.PointMode
import org.graphiks.kanvas.types.VertexMode

// These wire ids deliberately do not depend on declaration order.
internal fun stableFillTypeId(value: FillType): Byte = when (value) {
    FillType.WINDING -> 1
    FillType.EVEN_ODD -> 2
    FillType.INVERSE_WINDING -> 3
    FillType.INVERSE_EVEN_ODD -> 4
}

internal fun stableFillTypeFromId(id: Byte): FillType? = when (id.toInt()) {
    1 -> FillType.WINDING
    2 -> FillType.EVEN_ODD
    3 -> FillType.INVERSE_WINDING
    4 -> FillType.INVERSE_EVEN_ODD
    else -> null
}

internal fun stablePathVerbId(value: PathVerb): Byte = when (value) {
    PathVerb.MOVE -> 1
    PathVerb.LINE -> 2
    PathVerb.QUAD -> 3
    PathVerb.CUBIC -> 4
    PathVerb.ARC_TO -> 5
    PathVerb.CLOSE -> 6
}

internal fun stablePathVerbFromId(id: Byte): PathVerb? = when (id.toInt()) {
    1 -> PathVerb.MOVE
    2 -> PathVerb.LINE
    3 -> PathVerb.QUAD
    4 -> PathVerb.CUBIC
    5 -> PathVerb.ARC_TO
    6 -> PathVerb.CLOSE
    else -> null
}

internal fun stableColorTypeId(value: ColorType): Byte = when (value) {
    ColorType.UNKNOWN -> 1
    ColorType.ALPHA_8 -> 2
    ColorType.RGB_565 -> 3
    ColorType.ARGB_4444 -> 4
    ColorType.RGBA_8888 -> 5
    ColorType.RGB_888X -> 6
    ColorType.BGRA_8888 -> 7
    ColorType.RGBA_1010102 -> 8
    ColorType.BGRA_1010102 -> 9
    ColorType.RGB_101010X -> 10
    ColorType.BGR_101010X -> 11
    ColorType.BGR_101010X_XR -> 12
    ColorType.BGRA_10101010_XR -> 13
    ColorType.RGBA_10X6 -> 14
    ColorType.GRAY_8 -> 15
    ColorType.RGBA_F16_NORM -> 16
    ColorType.RGBA_F16 -> 17
    ColorType.RGB_F16F16F16X -> 18
    ColorType.RGBA_F32 -> 19
    ColorType.R8G8_UNORM -> 20
    ColorType.A16_FLOAT -> 21
    ColorType.R16G16_FLOAT -> 22
    ColorType.A16_UNORM -> 23
    ColorType.R16_UNORM -> 24
    ColorType.R16G16_UNORM -> 25
    ColorType.R16G16B16A16_UNORM -> 26
    ColorType.SRGBA_8888 -> 27
    ColorType.R8_UNORM -> 28
}

internal fun stableColorTypeFromId(id: Byte): ColorType? = when (id.toInt()) {
    1 -> ColorType.UNKNOWN
    2 -> ColorType.ALPHA_8
    3 -> ColorType.RGB_565
    4 -> ColorType.ARGB_4444
    5 -> ColorType.RGBA_8888
    6 -> ColorType.RGB_888X
    7 -> ColorType.BGRA_8888
    8 -> ColorType.RGBA_1010102
    9 -> ColorType.BGRA_1010102
    10 -> ColorType.RGB_101010X
    11 -> ColorType.BGR_101010X
    12 -> ColorType.BGR_101010X_XR
    13 -> ColorType.BGRA_10101010_XR
    14 -> ColorType.RGBA_10X6
    15 -> ColorType.GRAY_8
    16 -> ColorType.RGBA_F16_NORM
    17 -> ColorType.RGBA_F16
    18 -> ColorType.RGB_F16F16F16X
    19 -> ColorType.RGBA_F32
    20 -> ColorType.R8G8_UNORM
    21 -> ColorType.A16_FLOAT
    22 -> ColorType.R16G16_FLOAT
    23 -> ColorType.A16_UNORM
    24 -> ColorType.R16_UNORM
    25 -> ColorType.R16G16_UNORM
    26 -> ColorType.R16G16B16A16_UNORM
    27 -> ColorType.SRGBA_8888
    28 -> ColorType.R8_UNORM
    else -> null
}

internal fun stableAlphaTypeId(value: AlphaType): Byte = when (value) {
    AlphaType.UNKNOWN -> 1
    AlphaType.OPAQUE -> 2
    AlphaType.PREMUL -> 3
    AlphaType.UNPREMUL -> 4
}

internal fun stableAlphaTypeFromId(id: Byte): AlphaType? = when (id.toInt()) {
    1 -> AlphaType.UNKNOWN
    2 -> AlphaType.OPAQUE
    3 -> AlphaType.PREMUL
    4 -> AlphaType.UNPREMUL
    else -> null
}

internal fun stableTransferFunctionId(value: TransferFunction): Byte = when (value) {
    TransferFunction.SRGB -> 1
    TransferFunction.LINEAR -> 2
    TransferFunction.PQ -> 3
    TransferFunction.HLG -> 4
}

internal fun stableTransferFunctionFromId(id: Byte): TransferFunction? = when (id.toInt()) {
    1 -> TransferFunction.SRGB
    2 -> TransferFunction.LINEAR
    3 -> TransferFunction.PQ
    4 -> TransferFunction.HLG
    else -> null
}

internal fun stableGamutId(value: Gamut): Byte = when (value) {
    Gamut.SRGB -> 1
    Gamut.DISPLAY_P3 -> 2
    Gamut.REC2020 -> 3
}

internal fun stableGamutFromId(id: Byte): Gamut? = when (id.toInt()) {
    1 -> Gamut.SRGB
    2 -> Gamut.DISPLAY_P3
    3 -> Gamut.REC2020
    else -> null
}

internal fun stablePaintStyleId(value: PaintStyle): Byte = when (value) {
    PaintStyle.FILL -> 1
    PaintStyle.STROKE -> 2
    PaintStyle.STROKE_AND_FILL -> 3
}

internal fun stablePaintStyleFromId(id: Byte): PaintStyle? = when (id.toInt()) {
    1 -> PaintStyle.FILL
    2 -> PaintStyle.STROKE
    3 -> PaintStyle.STROKE_AND_FILL
    else -> null
}

internal fun stableStrokeCapId(value: StrokeCap): Byte = when (value) {
    StrokeCap.BUTT -> 1
    StrokeCap.ROUND -> 2
    StrokeCap.SQUARE -> 3
}

internal fun stableStrokeCapFromId(id: Byte): StrokeCap? = when (id.toInt()) {
    1 -> StrokeCap.BUTT
    2 -> StrokeCap.ROUND
    3 -> StrokeCap.SQUARE
    else -> null
}

internal fun stableStrokeJoinId(value: StrokeJoin): Byte = when (value) {
    StrokeJoin.MITER -> 1
    StrokeJoin.ROUND -> 2
    StrokeJoin.BEVEL -> 3
}

internal fun stableStrokeJoinFromId(id: Byte): StrokeJoin? = when (id.toInt()) {
    1 -> StrokeJoin.MITER
    2 -> StrokeJoin.ROUND
    3 -> StrokeJoin.BEVEL
    else -> null
}

internal fun stableUniformTypeId(value: UniformType): Byte = when (value) {
    UniformType.FLOAT -> 1
    UniformType.FLOAT2 -> 2
    UniformType.FLOAT3 -> 3
    UniformType.FLOAT4 -> 4
    UniformType.INT1 -> 7
    UniformType.MAT3X3 -> 5
    UniformType.MAT4X4 -> 6
}

internal fun stableUniformTypeFromId(id: Byte): UniformType? = when (id.toInt()) {
    1 -> UniformType.FLOAT
    2 -> UniformType.FLOAT2
    3 -> UniformType.FLOAT3
    4 -> UniformType.FLOAT4
    7 -> UniformType.INT1
    5 -> UniformType.MAT3X3
    6 -> UniformType.MAT4X4
    else -> null
}

internal fun stableVertexFormatId(value: VertexFormat): Byte = when (value) {
    VertexFormat.FLOAT32 -> 1
    VertexFormat.FLOAT32x2 -> 2
    VertexFormat.FLOAT32x3 -> 3
    VertexFormat.FLOAT32x4 -> 4
    VertexFormat.UINT8x4 -> 5
    VertexFormat.SINT16x2 -> 6
    VertexFormat.SINT16x4 -> 7
}

internal fun stableVertexFormatFromId(id: Byte): VertexFormat? = when (id.toInt()) {
    1 -> VertexFormat.FLOAT32
    2 -> VertexFormat.FLOAT32x2
    3 -> VertexFormat.FLOAT32x3
    4 -> VertexFormat.FLOAT32x4
    5 -> VertexFormat.UINT8x4
    6 -> VertexFormat.SINT16x2
    7 -> VertexFormat.SINT16x4
    else -> null
}

internal fun stableVertexStepModeId(value: VertexStepMode): Byte = when (value) {
    VertexStepMode.VERTEX -> 1
    VertexStepMode.INSTANCE -> 2
}

internal fun stableVertexStepModeFromId(id: Byte): VertexStepMode? = when (id.toInt()) {
    1 -> VertexStepMode.VERTEX
    2 -> VertexStepMode.INSTANCE
    else -> null
}

internal fun stableChildTypeId(value: ChildType): Byte = when (value) {
    ChildType.SHADER -> 1
    ChildType.COLOR_FILTER -> 2
    ChildType.BLENDER -> 3
}

internal fun stableChildTypeFromId(id: Byte): ChildType? = when (id.toInt()) {
    1 -> ChildType.SHADER
    2 -> ChildType.COLOR_FILTER
    3 -> ChildType.BLENDER
    else -> null
}

internal fun stablePath1DStyleId(value: Path1DStyle): Byte = when (value) {
    Path1DStyle.TRANSLATE -> 1
    Path1DStyle.ROTATE -> 2
    Path1DStyle.MORPH -> 3
}

internal fun stablePath1DStyleFromId(id: Byte): Path1DStyle? = when (id.toInt()) {
    1 -> Path1DStyle.TRANSLATE
    2 -> Path1DStyle.ROTATE
    3 -> Path1DStyle.MORPH
    else -> null
}

internal fun stableBlendModeId(value: BlendMode): Byte = when (value) {
    BlendMode.CLEAR -> 1
    BlendMode.SRC -> 2
    BlendMode.DST -> 3
    BlendMode.SRC_OVER -> 4
    BlendMode.DST_OVER -> 5
    BlendMode.SRC_IN -> 6
    BlendMode.DST_IN -> 7
    BlendMode.SRC_OUT -> 8
    BlendMode.DST_OUT -> 9
    BlendMode.SRC_ATOP -> 10
    BlendMode.DST_ATOP -> 11
    BlendMode.XOR -> 12
    BlendMode.PLUS -> 13
    BlendMode.MODULATE -> 14
    BlendMode.MULTIPLY -> 15
    BlendMode.SCREEN -> 16
    BlendMode.OVERLAY -> 17
    BlendMode.DARKEN -> 18
    BlendMode.LIGHTEN -> 19
    BlendMode.COLOR_DODGE -> 20
    BlendMode.COLOR_BURN -> 21
    BlendMode.HARD_LIGHT -> 22
    BlendMode.SOFT_LIGHT -> 23
    BlendMode.DIFFERENCE -> 24
    BlendMode.EXCLUSION -> 25
    BlendMode.HUE -> 26
    BlendMode.SATURATION -> 27
    BlendMode.COLOR -> 28
    BlendMode.LUMINOSITY -> 29
}

internal fun stableBlendModeFromId(id: Byte): BlendMode? = when (id.toInt()) {
    1 -> BlendMode.CLEAR
    2 -> BlendMode.SRC
    3 -> BlendMode.DST
    4 -> BlendMode.SRC_OVER
    5 -> BlendMode.DST_OVER
    6 -> BlendMode.SRC_IN
    7 -> BlendMode.DST_IN
    8 -> BlendMode.SRC_OUT
    9 -> BlendMode.DST_OUT
    10 -> BlendMode.SRC_ATOP
    11 -> BlendMode.DST_ATOP
    12 -> BlendMode.XOR
    13 -> BlendMode.PLUS
    14 -> BlendMode.MODULATE
    15 -> BlendMode.MULTIPLY
    16 -> BlendMode.SCREEN
    17 -> BlendMode.OVERLAY
    18 -> BlendMode.DARKEN
    19 -> BlendMode.LIGHTEN
    20 -> BlendMode.COLOR_DODGE
    21 -> BlendMode.COLOR_BURN
    22 -> BlendMode.HARD_LIGHT
    23 -> BlendMode.SOFT_LIGHT
    24 -> BlendMode.DIFFERENCE
    25 -> BlendMode.EXCLUSION
    26 -> BlendMode.HUE
    27 -> BlendMode.SATURATION
    28 -> BlendMode.COLOR
    29 -> BlendMode.LUMINOSITY
    else -> null
}

internal fun stableTileModeId(value: TileMode): Byte = when (value) {
    TileMode.CLAMP -> 1
    TileMode.REPEAT -> 2
    TileMode.MIRROR -> 3
    TileMode.DECAL -> 4
}

internal fun stableTileModeFromId(id: Byte): TileMode? = when (id.toInt()) {
    1 -> TileMode.CLAMP
    2 -> TileMode.REPEAT
    3 -> TileMode.MIRROR
    4 -> TileMode.DECAL
    else -> null
}

internal fun stableBlurStyleId(value: BlurStyle): Byte = when (value) {
    BlurStyle.NORMAL -> 1
    BlurStyle.SOLID -> 2
    BlurStyle.OUTER -> 3
    BlurStyle.INNER -> 4
}

internal fun stableBlurStyleFromId(id: Byte): BlurStyle? = when (id.toInt()) {
    1 -> BlurStyle.NORMAL
    2 -> BlurStyle.SOLID
    3 -> BlurStyle.OUTER
    4 -> BlurStyle.INNER
    else -> null
}

internal fun stableColorChannelId(value: ColorChannel): Byte = when (value) {
    ColorChannel.R -> 1
    ColorChannel.G -> 2
    ColorChannel.B -> 3
    ColorChannel.A -> 4
}

internal fun stableColorChannelFromId(id: Byte): ColorChannel? = when (id.toInt()) {
    1 -> ColorChannel.R
    2 -> ColorChannel.G
    3 -> ColorChannel.B
    4 -> ColorChannel.A
    else -> null
}

internal fun stableColorSpaceInterpolationId(value: ColorSpaceInterpolation): Byte = when (value) {
    ColorSpaceInterpolation.SRGB -> 1
    ColorSpaceInterpolation.LINEAR -> 2
    ColorSpaceInterpolation.OKLAB -> 3
    ColorSpaceInterpolation.HSL -> 4
    ColorSpaceInterpolation.OKLCH -> 5
}

internal fun stableColorSpaceInterpolationFromId(id: Byte): ColorSpaceInterpolation? = when (id.toInt()) {
    1 -> ColorSpaceInterpolation.SRGB
    2 -> ColorSpaceInterpolation.LINEAR
    3 -> ColorSpaceInterpolation.OKLAB
    4 -> ColorSpaceInterpolation.HSL
    5 -> ColorSpaceInterpolation.OKLCH
    else -> null
}

internal fun stablePointModeId(value: PointMode): Byte = when (value) {
    PointMode.POINTS -> 1
    PointMode.LINES -> 2
    PointMode.POLYGON -> 3
}

internal fun stablePointModeFromId(id: Byte): PointMode? = when (id.toInt()) {
    1 -> PointMode.POINTS
    2 -> PointMode.LINES
    3 -> PointMode.POLYGON
    else -> null
}

internal fun stableVertexModeId(value: VertexMode): Byte = when (value) {
    VertexMode.TRIANGLES -> 1
    VertexMode.TRIANGLE_STRIP -> 2
    VertexMode.TRIANGLE_FAN -> 3
}

internal fun stableVertexModeFromId(id: Byte): VertexMode? = when (id.toInt()) {
    1 -> VertexMode.TRIANGLES
    2 -> VertexMode.TRIANGLE_STRIP
    3 -> VertexMode.TRIANGLE_FAN
    else -> null
}

internal fun stableLatticeFlagsId(value: LatticeFlags): Byte = when (value) {
    LatticeFlags.DEFAULT -> 1
    LatticeFlags.TRANSPARENT -> 2
    LatticeFlags.FIXED_COLOR -> 3
}

internal fun stableLatticeFlagsFromId(id: Byte): LatticeFlags? = when (id.toInt()) {
    1 -> LatticeFlags.DEFAULT
    2 -> LatticeFlags.TRANSPARENT
    3 -> LatticeFlags.FIXED_COLOR
    else -> null
}

internal fun stableClipOpId(value: ClipOp): Byte = when (value) {
    ClipOp.INTERSECT -> 1
    ClipOp.DIFFERENCE -> 2
}

internal fun stableClipOpFromId(id: Byte): ClipOp? = when (id.toInt()) {
    1 -> ClipOp.INTERSECT
    2 -> ClipOp.DIFFERENCE
    else -> null
}
