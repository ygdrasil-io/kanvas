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

private fun requireOptionalField(value: String?, fieldName: String) {
    require(value == null || value.isNotBlank()) { "$fieldName must not be blank" }
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

    fun offset(dx: Float, dy: Float): SceneRect =
        SceneRect(left = left + dx, top = top + dy, right = right + dx, bottom = bottom + dy)

    fun contains(other: SceneRect): Boolean =
        other.left >= left &&
            other.top >= top &&
            other.right <= right &&
            other.bottom <= bottom
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

enum class SceneFilterKind(val wireName: String) {
    LumaTint("luma-tint"),
    DropShadow("drop-shadow"),
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

    data class SaveLayer(
        override val label: String,
        val bounds: SceneRect? = null,
        val contentRect: SceneRect? = null,
        val radius: Float = 0f,
        val contentColor: SceneColor? = null,
        val shadowColor: SceneColor? = null,
        val shadowOffsetX: Float = 8f,
        val shadowOffsetY: Float = 10f,
        val paintOrder: Int = 0,
    ) : SceneCommand {
        override val family: String = "save-layer"
        val layerKind: String = "bounded-shadow-card"
        val hasFixturePayload: Boolean =
            bounds != null && contentRect != null && contentColor != null && shadowColor != null
        val shadowRect: SceneRect? = contentRect?.offset(shadowOffsetX, shadowOffsetY)

        init {
            requireSceneCommandLabel(label)
            val payloadFieldCount = listOf(bounds, contentRect, contentColor, shadowColor).count { it != null }
            require(payloadFieldCount == 0 || payloadFieldCount == 4) {
                "SceneCommand.SaveLayer fixture payload requires bounds, contentRect, contentColor, and shadowColor"
            }
            require(!radius.isNaN() && !radius.isInfinite()) {
                "SceneCommand.SaveLayer.radius must be finite"
            }
            require(radius >= 0f) { "SceneCommand.SaveLayer.radius must be non-negative" }
            require(!shadowOffsetX.isNaN() && !shadowOffsetX.isInfinite()) {
                "SceneCommand.SaveLayer.shadowOffsetX must be finite"
            }
            require(!shadowOffsetY.isNaN() && !shadowOffsetY.isInfinite()) {
                "SceneCommand.SaveLayer.shadowOffsetY must be finite"
            }
            require(paintOrder >= 0) { "SceneCommand.SaveLayer.paintOrder must be non-negative" }
            if (hasFixturePayload) {
                val layerBounds = bounds ?: error("SaveLayer requires bounds fixture payload: $label")
                val content = contentRect ?: error("SaveLayer requires contentRect fixture payload: $label")
                val shadow = shadowRect ?: error("SaveLayer requires shadowRect fixture payload: $label")
                require(layerBounds.contains(content)) {
                    "SceneCommand.SaveLayer.contentRect must be inside bounds: $label"
                }
                require(layerBounds.contains(shadow)) {
                    "SceneCommand.SaveLayer.shadowRect must be inside bounds: $label"
                }
            }
        }
    }

    data class FilterNode(
        override val label: String,
        val inputLabel: String? = null,
        val kind: SceneFilterKind? = null,
        val strength: Float = 1f,
    ) : SceneCommand {
        override val family: String = "filter-node"
        val hasFixturePayload: Boolean = inputLabel != null && kind != null

        init {
            requireSceneCommandLabel(label)
            require((inputLabel == null) == (kind == null)) {
                "SceneCommand.FilterNode fixture payload requires both inputLabel and kind"
            }
            inputLabel?.let(::requireSceneCommandLabel)
            require(!strength.isNaN() && !strength.isInfinite() && strength in 0f..1f) {
                "SceneCommand.FilterNode.strength must be finite and normalized in 0..1"
            }
            require(hasFixturePayload || strength == 1f) {
                "SceneCommand.FilterNode.strength requires fixture payload"
            }
        }
    }

    data class TextRun(override val label: String) : SceneCommand {
        override val family: String = "text-run"

        init {
            requireSceneCommandLabel(label)
        }
    }

    data class RuntimeEffectTile(
        override val label: String,
        val rect: SceneRect? = null,
        val stableId: String? = null,
        val wgslImplementationId: String? = null,
        val uniformColor: SceneColor? = null,
        val paintOrder: Int = 0,
        val cpuImplementationId: String = "kotlin/simple_rt",
        val uniformName: String = "gColor",
        val uniformType: String = "kFloat4",
        val uniformOffset: Int = 0,
        val uniformSize: Int = 16,
        val pipelineKey: String = "runtimeEffect=SimpleRT descriptor=runtime_simple_rt.wgsl state=[blendMode=kSrcOver]",
    ) : SceneCommand {
        override val family: String = "runtime-effect"
        val hasFixturePayload: Boolean =
            rect != null && stableId != null && wgslImplementationId != null && uniformColor != null
        val isRegisteredSimpleRt: Boolean =
            hasFixturePayload &&
                stableId == "runtime.simple_rt" &&
                wgslImplementationId == "wgsl/runtime_simple_rt" &&
                cpuImplementationId == "kotlin/simple_rt" &&
                uniformName == "gColor" &&
                uniformType == "kFloat4" &&
                uniformOffset == 0 &&
                uniformSize == 16 &&
                pipelineKey == "runtimeEffect=SimpleRT descriptor=runtime_simple_rt.wgsl state=[blendMode=kSrcOver]"
        val uniformLayout: String = "$uniformName@$uniformOffset:$uniformSize"

        init {
            requireSceneCommandLabel(label)
            val payloadFieldCount = listOf(rect, stableId, wgslImplementationId, uniformColor).count { it != null }
            require(payloadFieldCount == 0 || payloadFieldCount == 4) {
                "SceneCommand.RuntimeEffectTile fixture payload requires rect, stableId, wgslImplementationId, and uniformColor"
            }
            requireOptionalField(stableId, "SceneCommand.RuntimeEffectTile.stableId")
            requireOptionalField(wgslImplementationId, "SceneCommand.RuntimeEffectTile.wgslImplementationId")
            require(cpuImplementationId.isNotBlank()) {
                "SceneCommand.RuntimeEffectTile.cpuImplementationId must not be blank"
            }
            require(uniformName.isNotBlank()) { "SceneCommand.RuntimeEffectTile.uniformName must not be blank" }
            require(uniformType.isNotBlank()) { "SceneCommand.RuntimeEffectTile.uniformType must not be blank" }
            require(uniformOffset >= 0) { "SceneCommand.RuntimeEffectTile.uniformOffset must be non-negative" }
            require(uniformSize > 0) { "SceneCommand.RuntimeEffectTile.uniformSize must be positive" }
            require(pipelineKey.isNotBlank()) { "SceneCommand.RuntimeEffectTile.pipelineKey must not be blank" }
            require(paintOrder >= 0) { "SceneCommand.RuntimeEffectTile.paintOrder must be non-negative" }
        }
    }

    data class MeshRibbon(
        override val label: String,
        val bounds: SceneRect? = null,
        val startColor: SceneColor? = null,
        val endColor: SceneColor? = null,
        val thickness: Float = 24f,
        val paintOrder: Int = 0,
    ) : SceneCommand {
        override val family: String = "vertices"
        val meshKind: String = "bounded-ribbon-strip"
        val hasFixturePayload: Boolean = bounds != null && startColor != null && endColor != null

        init {
            requireSceneCommandLabel(label)
            val payloadFieldCount = listOf(bounds, startColor, endColor).count { it != null }
            require(payloadFieldCount == 0 || payloadFieldCount == 3) {
                "SceneCommand.MeshRibbon fixture payload requires bounds, startColor, and endColor"
            }
            require(!thickness.isNaN() && !thickness.isInfinite()) {
                "SceneCommand.MeshRibbon.thickness must be finite"
            }
            require(thickness > 0f) { "SceneCommand.MeshRibbon.thickness must be positive" }
            require(paintOrder >= 0) { "SceneCommand.MeshRibbon.paintOrder must be non-negative" }
        }
    }
}
