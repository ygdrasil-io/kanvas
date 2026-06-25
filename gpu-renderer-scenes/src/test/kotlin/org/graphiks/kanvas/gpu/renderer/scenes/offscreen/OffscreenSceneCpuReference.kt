package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererSceneRegistry
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect

/** Width/height + tightly packed RGBA8 (straight alpha) image. */
data class RefImage(val width: Int, val height: Int, val rgba: ByteArray)

/**
 * KGPU-M28 verification foundation: a minimal, self-contained CPU reference
 * rasterizer for offscreen scenes. It is INDEPENDENT of the GPU pipeline — it
 * draws the INTENDED scene (clear + srcOver rects + filled polygons) so the GPU
 * `render.png` can be diffed against a ground truth.
 *
 * Straight-alpha RGBA8; for opaque scenes there is no premultiply ambiguity vs
 * the GPU readback. Polygon geometry is reused verbatim from the renderer
 * ([generateStarVertices], [generateOctagonVertices]) so a clean reference shape
 * is compared against whatever the GPU actually produced.
 *
 * Only the commands needed to verify the M28 shape/layer scenes are translated;
 * unsupported commands are ignored (documented per call site).
 */
object OffscreenSceneCpuReference {

    fun renderSceneRgba(sceneId: String): RefImage {
        val scene = GPURendererSceneRegistry.registry.requireScene(sceneId)
        val w = scene.dimensions.width
        val h = scene.dimensions.height
        val buf = FloatArray(w * h * 4) // r,g,b,a straight, 0..1

        // Group-alpha saveLayers (groupAlpha < 1) genuinely isolate their children:
        // their child FillRects are NOT drawn directly onto the main buffer; they are
        // composited via the layered path below. Child association mirrors the GPU
        // renderer's paintOrder windowing exactly.
        val layeredChildLabels = scene.commands
            .filterIsInstance<SceneCommand.SaveLayer>()
            .filter { it.groupAlpha < 1f }
            .flatMap { childFillsOf(scene, it).map { child -> child.label } }
            .toSet()

        for (cmd in scene.commands) {
            when (cmd) {
                is SceneCommand.Clear -> clear(buf, cmd.color)
                is SceneCommand.FillRect ->
                    if (cmd.label !in layeredChildLabels) fillRect(buf, w, h, cmd.rect, cmd.color)
                is SceneCommand.SaveLayer -> {
                    if (cmd.groupAlpha < 1f) {
                        compositeGroupAlphaLayer(buf, w, h, scene, cmd)
                    } else if (cmd.hasFixturePayload) {
                        val contentRect = cmd.contentRect!!
                        val shadowRect = cmd.shadowRect!!
                        val contentColor = cmd.contentColor!!
                        val shadowColor = cmd.shadowColor!!
                        fillRect(buf, w, h, shadowRect, shadowColor)
                        fillRect(buf, w, h, contentRect, contentColor)
                    }
                }
                is SceneCommand.PathFillStencil ->
                    fillPolygon(buf, w, h, generateStarVertices(160f, 100f, 80f, 35f, 5), cmd.fillColor)
                is SceneCommand.ConvexFanMesh ->
                    fillPolygon(buf, w, h, generateOctagonVertices(160f, 100f, 60f, 8), cmd.fillColor)
                else -> Unit // unsupported in this reference
            }
        }
        return RefImage(w, h, toRgbaBytes(buf))
    }

    /**
     * Child FillRects belonging to [layer], using the same paintOrder windowing as the
     * GPU renderer: a child is any FillRect whose paintOrder is strictly between this
     * layer's paintOrder and the next saveLayer's paintOrder.
     */
    private fun childFillsOf(
        scene: org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene<SceneCommand>,
        layer: SceneCommand.SaveLayer,
    ): List<SceneCommand.FillRect> {
        val layers = scene.commands.filterIsInstance<SceneCommand.SaveLayer>().sortedBy { it.paintOrder }
        val idx = layers.indexOfFirst { it === layer }
        val nextPaintOrder = if (idx + 1 < layers.size) layers[idx + 1].paintOrder else Int.MAX_VALUE
        return scene.commands.filterIsInstance<SceneCommand.FillRect>()
            .filter { it.paintOrder > layer.paintOrder && it.paintOrder < nextPaintOrder }
            .sortedBy { it.paintOrder }
    }

    /**
     * True layered compositing: render the layer's content into a SEPARATE transparent
     * RGBA layer buffer via srcOver, then composite that layer onto the main buffer
     * where each layer pixel's alpha is scaled by [SceneCommand.SaveLayer.groupAlpha].
     * This is genuine layer isolation, NOT a direct draw — the whole layer fades by
     * group alpha as one unit, so overlapping opaque children blend uniformly.
     */
    private fun compositeGroupAlphaLayer(
        main: FloatArray,
        w: Int,
        h: Int,
        scene: org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene<SceneCommand>,
        layer: SceneCommand.SaveLayer,
    ) {
        val layerBuf = FloatArray(w * h * 4) // transparent (0,0,0,0)
        if (layer.hasFixturePayload) {
            fillRect(layerBuf, w, h, layer.shadowRect!!, layer.shadowColor!!)
            fillRect(layerBuf, w, h, layer.contentRect!!, layer.contentColor!!)
        }
        childFillsOf(scene, layer).forEach { child ->
            fillRect(layerBuf, w, h, child.rect, child.color)
        }
        compositeLayerOver(main, layerBuf, layer.groupAlpha)
    }

    /** srcOver of [layer] (straight RGBA) over [main], scaling the layer alpha by [groupAlpha]. */
    private fun compositeLayerOver(main: FloatArray, layer: FloatArray, groupAlpha: Float) {
        var i = 0
        while (i < main.size) {
            val sa = (layer[i + 3] * groupAlpha).coerceIn(0f, 1f)
            if (sa > 0f) {
                val sr = layer[i]; val sg = layer[i + 1]; val sb = layer[i + 2]
                val dr = main[i]; val dg = main[i + 1]; val db = main[i + 2]; val da = main[i + 3]
                val outA = sa + da * (1f - sa)
                if (outA <= 0f) {
                    main[i] = 0f; main[i + 1] = 0f; main[i + 2] = 0f; main[i + 3] = 0f
                } else {
                    main[i] = (sr * sa + dr * da * (1f - sa)) / outA
                    main[i + 1] = (sg * sa + dg * da * (1f - sa)) / outA
                    main[i + 2] = (sb * sa + db * da * (1f - sa)) / outA
                    main[i + 3] = outA
                }
            }
            i += 4
        }
    }

    private fun clear(buf: FloatArray, c: SceneColor) {
        var i = 0
        while (i < buf.size) {
            buf[i] = c.r; buf[i + 1] = c.g; buf[i + 2] = c.b; buf[i + 3] = c.a
            i += 4
        }
    }

    private fun fillRect(buf: FloatArray, w: Int, h: Int, rect: SceneRect, c: SceneColor) {
        val x0 = rect.left.coerceIn(0f, w.toFloat()).toInt()
        val y0 = rect.top.coerceIn(0f, h.toFloat()).toInt()
        val x1 = rect.right.coerceIn(0f, w.toFloat()).toInt()
        val y1 = rect.bottom.coerceIn(0f, h.toFloat()).toInt()
        for (y in y0 until y1) {
            for (x in x0 until x1) srcOver(buf, (y * w + x) * 4, c)
        }
    }

    private fun fillPolygon(buf: FloatArray, w: Int, h: Int, poly: List<Pair<Float, Float>>, c: SceneColor) {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for ((px, py) in poly) {
            if (px < minX) minX = px; if (py < minY) minY = py
            if (px > maxX) maxX = px; if (py > maxY) maxY = py
        }
        val y0 = minY.coerceIn(0f, h.toFloat()).toInt()
        val y1 = maxY.coerceIn(0f, (h - 1).toFloat()).toInt()
        val x0 = minX.coerceIn(0f, w.toFloat()).toInt()
        val x1 = maxX.coerceIn(0f, (w - 1).toFloat()).toInt()
        for (y in y0..y1) {
            val cy = y + 0.5f
            for (x in x0..x1) {
                if (pointInPolygon(x + 0.5f, cy, poly)) srcOver(buf, (y * w + x) * 4, c)
            }
        }
    }

    private fun pointInPolygon(x: Float, y: Float, poly: List<Pair<Float, Float>>): Boolean {
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val (xi, yi) = poly[i]
            val (xj, yj) = poly[j]
            if (((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)) inside = !inside
            j = i
        }
        return inside
    }

    private fun srcOver(buf: FloatArray, base: Int, s: SceneColor) {
        val sa = s.a
        val dr = buf[base]; val dg = buf[base + 1]; val db = buf[base + 2]; val da = buf[base + 3]
        val outA = sa + da * (1f - sa)
        if (outA <= 0f) {
            buf[base] = 0f; buf[base + 1] = 0f; buf[base + 2] = 0f; buf[base + 3] = 0f
            return
        }
        buf[base] = (s.r * sa + dr * da * (1f - sa)) / outA
        buf[base + 1] = (s.g * sa + dg * da * (1f - sa)) / outA
        buf[base + 2] = (s.b * sa + db * da * (1f - sa)) / outA
        buf[base + 3] = outA
    }

    private fun toRgbaBytes(buf: FloatArray): ByteArray {
        val out = ByteArray(buf.size)
        for (i in buf.indices) out[i] = (buf[i].coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()
        return out
    }
}
