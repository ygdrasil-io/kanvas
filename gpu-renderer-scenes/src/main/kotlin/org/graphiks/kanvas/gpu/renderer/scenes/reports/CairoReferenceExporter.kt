package org.graphiks.kanvas.gpu.renderer.scenes.reports

import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererSceneRegistry
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect
import java.io.File

/**
 * Exports GPU renderer scenes to the Cairo reference JSON format.
 *
 * The JSON schema matches what [tools/cairo-reference/render_scene.c] expects:
 * - Clear, FillRect, FillRRect, LinearGradient, Clip, SaveLayer commands
 * - Rectangles expressed as {x, y, w, h}
 * - Colors as {r, g, b, a} floats in 0..1
 * - Gradients use explicit start/end points and color stops
 */
object CairoReferenceExporter {

    private const val CAIRO_SCENES_DIR = "tools/cairo-reference/scenes"

    fun exportAllScenes(repoRoot: File) {
        val outDir = File(repoRoot, CAIRO_SCENES_DIR).also { it.mkdirs() }
        for (scene in GPURendererSceneRegistry.scenes) {
            val json = buildSceneJson(scene)
            val file = File(outDir, "${scene.sceneId.value}.json")
            file.writeText(json)
            println("[cairo-export] ${scene.sceneId.value} -> ${file.relativeTo(repoRoot)}")
        }
        println("[cairo-export] Exported ${GPURendererSceneRegistry.scenes.size} scenes to ${outDir.relativeTo(repoRoot)}")
    }

    private fun buildSceneJson(scene: GPURendererScene<*>): String {
        val w = scene.dimensions.width
        val h = scene.dimensions.height
        val cmdJsons = mutableListOf<String>()
        val saveLayers = scene.commands.filterIsInstance<SceneCommand.SaveLayer>()
        val childLabels = mutableSetOf<String>()

        for (layer in saveLayers.filter { it.groupAlpha < 1f }) {
            childLabels.addAll(childFillLabels(scene.commands, layer))
        }

        for (cmd in scene.commands) {
            when (cmd) {
                is SceneCommand.Clear -> cmdJsons.add(clearJson(cmd))
                is SceneCommand.FillRect -> {
                    if (cmd.label !in childLabels) {
                        cmdJsons.add(fillRectJson(cmd.rect, cmd.color))
                    }
                }
                is SceneCommand.FillRRect -> cmdJsons.add(fillRRectJson(cmd.rect, cmd.radius, cmd.color))
                is SceneCommand.LinearGradientRect -> cmdJsons.add(linearGradientJson(cmd))
                is SceneCommand.Clip -> cmdJsons.add(clipJson(cmd))
                is SceneCommand.SaveLayer -> cmdJsons.add(saveLayerJson(scene.commands, cmd, saveLayers))
                else -> Unit
            }
        }

        return """{
  "width": $w,
  "height": $h,
  "commands": [
${cmdJsons.joinToString(",\n") { indent(it, 4) }}
  ]
}
"""
    }

    private fun childFillLabels(commands: List<SceneCommand>, layer: SceneCommand.SaveLayer): Set<String> {
        val layers = commands.filterIsInstance<SceneCommand.SaveLayer>().sortedBy { it.paintOrder }
        val idx = layers.indexOfFirst { it === layer }
        val next = if (idx + 1 < layers.size) layers[idx + 1].paintOrder else Int.MAX_VALUE
        return commands.filterIsInstance<SceneCommand.FillRect>()
            .filter { it.paintOrder > layer.paintOrder && it.paintOrder < next }
            .map { it.label }
            .toSet()
    }

    private fun saveLayerChildren(
        commands: List<SceneCommand>,
        layer: SceneCommand.SaveLayer,
        saveLayers: List<SceneCommand.SaveLayer>,
    ): List<String> {
        val children = mutableListOf<String>()

        if (layer.hasFixturePayload) {
            val sr = layer.shadowRect ?: layer.contentRect!!
            val cr = layer.contentRect!!
            val sc = layer.shadowColor!!
            val cc = layer.contentColor!!
            if (sc.a > 0f) {
                children.add(
                    if (layer.radius > 0f) fillRRectJson(sr, layer.radius, sc)
                    else fillRectJson(sr, sc)
                )
            }
            if (cc.a > 0f) {
                children.add(
                    if (layer.radius > 0f) fillRRectJson(cr, layer.radius, cc)
                    else fillRectJson(cr, cc)
                )
            }
        }

        val layers = saveLayers.sortedBy { it.paintOrder }
        val idx = layers.indexOfFirst { it === layer }
        val next = if (idx + 1 < layers.size) layers[idx + 1].paintOrder else Int.MAX_VALUE
        for (cmd in commands) {
            if (cmd is SceneCommand.FillRect &&
                cmd.paintOrder > layer.paintOrder && cmd.paintOrder < next
            ) {
                children.add(fillRectJson(cmd.rect, cmd.color))
            }
        }

        return children
    }

    private fun clearJson(cmd: SceneCommand.Clear): String {
        return """{ "type": "Clear", "color": ${colorJson(cmd.color)} }"""
    }

    private fun fillRectJson(rect: SceneRect, color: SceneColor): String {
        return """{ "type": "FillRect", "rect": ${rectJson(rect)}, "color": ${colorJson(color)} }"""
    }

    private fun fillRRectJson(rect: SceneRect, radius: Float, color: SceneColor): String {
        return """{ "type": "FillRRect", "rect": ${rectJson(rect)}, "radius": ${formatFloat(radius)}, "color": ${colorJson(color)} }"""
    }

    private fun linearGradientJson(cmd: SceneCommand.LinearGradientRect): String {
        val r = cmd.rect
        return """{
      "type": "LinearGradient",
      "rect": ${rectJson(r)},
      "x1": ${formatFloat(r.left)}, "y1": ${formatFloat(r.top)},
      "x2": ${formatFloat(r.right)}, "y2": ${formatFloat(r.bottom)},
      "stops": [
        { "pos": 0.0, "color": ${colorJson(cmd.startColor)} },
        { "pos": 1.0, "color": ${colorJson(cmd.endColor)} }
      ]
    }"""
    }

    private fun clipJson(cmd: SceneCommand.Clip): String {
        return """{ "type": "Clip", "rect": ${rectJson(cmd.rect)} }"""
    }

    private fun saveLayerJson(
        commands: List<SceneCommand>,
        layer: SceneCommand.SaveLayer,
        saveLayers: List<SceneCommand.SaveLayer>,
    ): String {
        val children = saveLayerChildren(commands, layer, saveLayers)
        if (children.isEmpty()) {
            return """{ "type": "SaveLayer", "groupAlpha": ${formatFloat(layer.groupAlpha)}, "commands": [] }"""
        }
        return """{
      "type": "SaveLayer",
      "groupAlpha": ${formatFloat(layer.groupAlpha)},
      "commands": [
${children.joinToString(",\n") { indent(it, 8) }}
      ]
    }"""
    }

    private fun rectJson(r: SceneRect): String {
        val x = formatFloat(r.left)
        val y = formatFloat(r.top)
        val w = formatFloat(r.right - r.left)
        val h = formatFloat(r.bottom - r.top)
        return """{ "x": $x, "y": $y, "w": $w, "h": $h }"""
    }

    private fun colorJson(c: SceneColor): String {
        return """{ "r": ${formatFloat(c.r)}, "g": ${formatFloat(c.g)}, "b": ${formatFloat(c.b)}, "a": ${formatFloat(c.a)} }"""
    }

    private fun formatFloat(v: Float): String {
        return if (v == v.toInt().toFloat()) "${v.toInt()}.0" else v.toString()
    }

    private fun indent(s: String, spaces: Int): String {
        val prefix = " ".repeat(spaces)
        return s.lines().joinToString("\n") { "$prefix$it" }
    }
}
