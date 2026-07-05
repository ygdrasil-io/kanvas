package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point

/**
 * Port of Skia's `gm/patch.cpp::patch_image_persp`
 * (`DEF_SIMPLE_GM(patch_image_persp, canvas, 1500, 1100)`).
 *
 * Same as [PatchImageGm] except the image shader is built with a
 * perspective local matrix — `localM[kMPersp0] = 0.00001f` (index 6 in
 * upstream's 9-element row-major array). This exercises the perspective
 * sampling path inside the `SkBitmapShader` / raster device.
 *
 * ```cpp
 * DEF_SIMPLE_GM(patch_image_persp, canvas, 1500, 1100) {
 *     const SkColor colors[SkPatchUtils::kNumCorners] = {
 *         SK_ColorRED, SK_ColorGREEN, SK_ColorBLUE, SK_ColorCYAN
 *     };
 *     SkMatrix localM;
 *     localM.reset();
 *     localM[6] = 0.00001f;    // force perspective
 *     dopatch(canvas, colors,
 *             ToolUtils::GetResourceAsImage("images/mandrill_128.png"),
 *             &localM);
 * }
 * ```
 *
 * The perspective matrix is constructed via `Matrix33.makeAll` with
 * `persp0 = 0.00001f` at row 2, column 0 (index 6 in row-major order).
 *
 * **Out of scope** : `draw_control_points` (see [PatchPrimitiveGm]).
 * @see https://github.com/google/skia/blob/main/gm/patch.cpp
 */
class PatchImagePerspGm : SkiaGm {
    override val name = "patch_image_persp"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 1500
    override val height = 1100

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.fromRGBA(0f, 1f, 1f, 1f))
        val modes = listOf(BlendMode.SRC, BlendMode.DST, BlendMode.COLOR_DODGE)
        val paint = Paint(color = Color.GREEN)

        val (shader, texCoords) = loadImageWithPerspTexCoords()

        canvas.save()
        for (iy in 0 until 3) {
            for (ix in 0 until 4) {
                canvas.save()
                canvas.translate(ix * 350f, iy * 350f)
                when (ix) {
                    0 -> canvas.drawPatch(GCubics, null, null, modes[iy], paint)
                    1 -> canvas.drawPatch(GCubics, colors, null, modes[iy], paint)
                    2 -> canvas.drawPatch(GCubics, null, texCoords, modes[iy], paint.copy(shader = shader))
                    3 -> canvas.drawPatch(GCubics, colors, texCoords, modes[iy], paint.copy(shader = shader))
                }
                canvas.restore()
            }
        }
        canvas.restore()
    }

    private fun loadImageWithPerspTexCoords(): Pair<Shader?, List<Point>> {
        val bytes = this::class.java.classLoader?.getResourceAsStream("images/mandrill_128.png")?.readBytes()
        if (bytes != null) {
            val image = Image.decode(bytes)
            if (image.width > 0) {
                val w = image.width.toFloat()
                val h = image.height.toFloat()
                val tex = listOf(Point(0f, 0f), Point(w, 0f), Point(w, h), Point(0f, h))
                val baseShader = image.makeShader(TileMode.CLAMP, TileMode.CLAMP)
                val perspMatrix = Matrix33.makeAll(1f, 0f, 0f, 0f, 1f, 0f, 0.00001f, 0f, 1f)
                val shader = Shader.WithLocalMatrix(baseShader, perspMatrix)
                return shader to tex
            }
        }
        return null to listOf(Point(0f, 0f), Point(100f, 0f), Point(100f, 100f), Point(0f, 100f))
    }

    private companion object {
        private val GCubics: List<Point> = listOf(
            Point(100f, 100f), Point(150f, 50f), Point(250f, 150f), Point(300f, 100f),
            Point(250f, 150f), Point(350f, 250f),
            Point(300f, 300f), Point(250f, 250f), Point(150f, 350f), Point(100f, 300f),
            Point(50f, 250f), Point(150f, 150f),
        )
    }
}
