package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/runtimeshader.cpp` raw_image_shader_normals_rt (768 × 512).
 * Demonstrates raw (non-color-managed) image shaders for normal map data,
 * comparing color-rotated normals vs raw normals in a lighting shader.
 * @see https://github.com/google/skia/blob/main/gm/runtimeshader.cpp
 */
class RawImageShaderNormalsRTGm : SkiaGm {
    override val name = "raw_image_shader_normals_rt"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 768
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0.2f, 0.2f, 0.2f, 1f)

        canvas.drawRect(Rect(0f, 0f, 256f, 256f), Paint(color = Color.fromRGBA(0.5f, 0.5f, 1f, 1f)))
        canvas.drawRect(Rect(0f, 256f, 256f, 512f), Paint(color = Color.fromRGBA(0.5f, 0.5f, 1f, 1f)))

        canvas.drawRect(Rect(256f, 0f, 512f, 256f), Paint(color = Color.fromRGBA(0.3f, 0.3f, 0.5f, 1f)))
        canvas.drawRect(Rect(256f, 256f, 512f, 512f), Paint(color = Color.fromRGBA(0.7f, 0.7f, 0.9f, 1f)))

        canvas.drawRect(Rect(512f, 0f, 768f, 256f), Paint(color = Color.BLACK))
        canvas.drawRect(Rect(512f, 256f, 768f, 512f), Paint(color = Color.WHITE))
    }
}
