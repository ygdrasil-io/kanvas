package org.graphiks.kanvas.paint

import org.graphiks.kanvas.types.Color

data class Paint(
    val color: Color = Color.BLACK,
    val shader: Shader? = null,
    val blendMode: BlendMode = BlendMode.SRC_OVER,
    val colorFilter: ColorFilter? = null,
    val maskFilter: MaskFilter? = null,
    val pathEffect: PathEffect? = null,
    val imageFilter: ImageFilter? = null,
    val blender: Blender? = null,
    val style: PaintStyle = PaintStyle.FILL,
    val strokeWidth: Float = 0f,
    val strokeCap: StrokeCap = StrokeCap.BUTT,
    val strokeJoin: StrokeJoin = StrokeJoin.MITER,
    val strokeMiter: Float = 4f,
    val antiAlias: Boolean = true,
) {
    companion object {
        fun fill(color: Color) = Paint(color = color)
        fun stroke(color: Color, width: Float) = Paint(
            color = color, style = PaintStyle.STROKE, strokeWidth = width,
        )
    }
}
