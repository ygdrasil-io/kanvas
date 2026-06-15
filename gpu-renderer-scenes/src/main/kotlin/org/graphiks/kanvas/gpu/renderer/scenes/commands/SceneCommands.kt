package org.graphiks.kanvas.gpu.renderer.scenes.commands

import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand

private fun requireSceneCommandLabel(label: String) {
    require(label.isNotBlank()) { "SceneCommand.label must not be blank" }
}

data class SceneTarget(val width: Int, val height: Int, val colorFormat: String = "rgba8unorm") {
    init {
        require(width > 0) { "SceneTarget.width must be positive" }
        require(height > 0) { "SceneTarget.height must be positive" }
        require(colorFormat.isNotBlank()) { "SceneTarget.colorFormat must not be blank" }
    }

    fun toGpuTargetFacts(): GPUTargetFacts =
        GPUTargetFacts(width = width, height = height, colorFormat = colorFormat)
}

data class SceneRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        listOf(left, top, right, bottom).forEach { value ->
            require(!value.isNaN() && !value.isInfinite()) {
                "SceneRect coordinates must be finite: $this"
            }
        }
        require(right > left) { "SceneRect.right must be greater than left" }
        require(bottom > top) { "SceneRect.bottom must be greater than top" }
    }

    fun toGpuRect(): GPURect = GPURect(left = left, top = top, right = right, bottom = bottom)
}

data class SceneColor(val r: Float, val g: Float, val b: Float, val a: Float = 1f) {
    init {
        listOf(r, g, b, a).forEach { value ->
            require(value in 0f..1f) { "SceneColor channels must be in 0..1: $this" }
        }
    }

    fun toMaterial(): GPUMaterialDescriptor.SolidColor =
        GPUMaterialDescriptor.SolidColor(r = r, g = g, b = b, a = a)

    companion object {
        fun red(): SceneColor = SceneColor(0.92f, 0.18f, 0.16f, 1f)
        fun blue(alpha: Float = 1f): SceneColor = SceneColor(0.12f, 0.30f, 0.78f, alpha)
        fun green(alpha: Float = 1f): SceneColor = SceneColor(0.18f, 0.70f, 0.42f, alpha)
        fun amber(alpha: Float = 1f): SceneColor = SceneColor(0.96f, 0.68f, 0.10f, alpha)
    }
}

data class SceneBitmapSource(
    val topLeft: SceneColor,
    val topRight: SceneColor,
    val bottomLeft: SceneColor,
    val bottomRight: SceneColor,
)

enum class SceneBitmapSampling {
    Nearest,
    Linear,
}

sealed interface SceneCommand {
    val label: String
    val family: String

    data class Clear(val color: SceneColor) : SceneCommand {
        override val label: String = "background-clear"
        override val family: String = "clear"
    }

    data class FillRect(
        override val label: String,
        val rect: SceneRect,
        val color: SceneColor,
        val paintOrder: Int = 0,
    ) : SceneCommand {
        override val family: String = "fill-rect"

        init {
            requireSceneCommandLabel(label)
            require(paintOrder >= 0) { "SceneCommand.FillRect.paintOrder must be non-negative" }
        }

        fun toNormalizedFillRect(commandIndex: Int, target: SceneTarget): NormalizedDrawCommand.FillRect =
            GPUFillRectCommandBuilder.build(
                commandId = GPUDrawCommandID(commandIndex),
                rect = rect.toGpuRect(),
                target = target.toGpuTargetFacts(),
                material = color.toMaterial(),
                paintOrder = paintOrder,
                source = GPUCommandSource(adapter = "gpu-renderer-scenes", operation = label),
            )
    }

    data class FillRRect(
        override val label: String,
        val rect: SceneRect,
        val radius: Float,
        val color: SceneColor,
        val paintOrder: Int = 0,
    ) : SceneCommand {
        override val family: String = "fill-rrect"

        init {
            requireSceneCommandLabel(label)
            require(!radius.isNaN() && !radius.isInfinite()) {
                "SceneCommand.FillRRect.radius must be finite"
            }
            require(radius >= 0f) { "SceneCommand.FillRRect.radius must be non-negative" }
            require(paintOrder >= 0) { "SceneCommand.FillRRect.paintOrder must be non-negative" }
        }
    }

    data class LinearGradientRect(
        override val label: String,
        val rect: SceneRect,
        val startColor: SceneColor,
        val endColor: SceneColor,
        val paintOrder: Int = 0,
    ) : SceneCommand {
        override val family: String = "linear-gradient-rect"

        init {
            requireSceneCommandLabel(label)
            require(paintOrder >= 0) { "SceneCommand.LinearGradientRect.paintOrder must be non-negative" }
        }
    }

    data class Clip(
        override val label: String,
        val rect: SceneRect,
    ) : SceneCommand {
        override val family: String = "clip"

        init {
            requireSceneCommandLabel(label)
        }
    }

    data class BitmapRect(
        override val label: String,
        val rect: SceneRect? = null,
        val source: SceneBitmapSource? = null,
        val sampling: SceneBitmapSampling = SceneBitmapSampling.Nearest,
        val paintOrder: Int = 0,
    ) : SceneCommand {
        override val family: String = "bitmap-rect"
        val hasFixturePayload: Boolean = rect != null && source != null

        init {
            requireSceneCommandLabel(label)
            require((rect == null) == (source == null)) {
                "SceneCommand.BitmapRect fixture payload requires both rect and source"
            }
            require(paintOrder >= 0) { "SceneCommand.BitmapRect.paintOrder must be non-negative" }
        }
    }

    data class SaveLayer(override val label: String) : SceneCommand {
        override val family: String = "save-layer"

        init {
            requireSceneCommandLabel(label)
        }
    }

    data class FilterNode(override val label: String) : SceneCommand {
        override val family: String = "filter-node"

        init {
            requireSceneCommandLabel(label)
        }
    }

    data class TextRun(override val label: String) : SceneCommand {
        override val family: String = "text-run"

        init {
            requireSceneCommandLabel(label)
        }
    }

    data class RuntimeEffectTile(override val label: String) : SceneCommand {
        override val family: String = "runtime-effect"

        init {
            requireSceneCommandLabel(label)
        }
    }

    data class MeshRibbon(override val label: String) : SceneCommand {
        override val family: String = "vertices"

        init {
            requireSceneCommandLabel(label)
        }
    }
}
