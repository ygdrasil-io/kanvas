package org.graphiks.kanvas.surface.gpu

import java.util.ArrayDeque
import java.util.IdentityHashMap
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.DrawPathSourceOperation
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.surface.GPUColorFormat
import org.graphiks.kanvas.surface.RenderConfig

/** Cheap composition admission only: it intentionally has no Scene or backend dependency. */
internal object GPUPlanSurfaceCandidateGate {
    /** Layers have their own terminal planner ownership before any format or effect filtering. */
    fun ownsW6aLayers(operations: List<DisplayOp>): Boolean = operations.any {
        it is DisplayOp.BeginLayer || it is DisplayOp.EndLayer
    }

    /**
     * Captured W6b filters select W6 even when they are recorded inside a nested Picture.
     * Repeated picture instances are inspected once by identity and the traversal fails closed
     * at the same bounded graph scale as capture, rather than risking a legacy continuation.
     */
    fun ownsW6bFilters(operations: List<DisplayOp>): Boolean {
        val pending = ArrayDeque<List<DisplayOp>>()
        pending.addLast(operations)
        val seenPictures = IdentityHashMap<org.graphiks.kanvas.picture.Picture, Boolean>()
        var inspectedI32 = 0
        while (pending.isNotEmpty()) {
            pending.removeLast().forEach { operation ->
                inspectedI32 = try {
                    Math.addExact(inspectedI32, 1)
                } catch (_: ArithmeticException) {
                    return true
                }
                if (inspectedI32 > W6B_PICTURE_VISIT_LIMIT_I32) return true
                if (operation.paintOrNull()?.let { it.imageFilter != null || it.maskFilter != null } == true ||
                    (operation as? DisplayOp.BeginLayer)?.rec?.backdrop != null
                ) return true
                if (operation is DisplayOp.DrawPicture && seenPictures.put(operation.picture, true) == null) {
                    pending.addLast(operation.picture.ops)
                }
            }
        }
        return false
    }

    /**
     * Exact W6d capability ownership is deliberately narrower than generic W6b filter
     * admission. It is used only to reject a declared-but-unimplemented target format
     * before capture, plan publication, or native submission.
     */
    fun ownsW6dAdvancedFilters(operations: List<DisplayOp>): Boolean {
        val pending = ArrayDeque<List<DisplayOp>>()
        pending.addLast(operations)
        val seenPictures = IdentityHashMap<org.graphiks.kanvas.picture.Picture, Boolean>()
        var inspectedI32 = 0
        while (pending.isNotEmpty()) {
            pending.removeLast().forEach { operation ->
                inspectedI32 = try {
                    Math.addExact(inspectedI32, 1)
                } catch (_: ArithmeticException) {
                    return true
                }
                if (inspectedI32 > W6B_PICTURE_VISIT_LIMIT_I32) return true
                if (operation.paintOrNull()?.imageFilter.containsW6dAdvancedArm() ||
                    (operation as? DisplayOp.BeginLayer)?.rec?.backdrop.containsW6dAdvancedArm()
                ) return true
                if (operation is DisplayOp.DrawPicture && seenPictures.put(operation.picture, true) == null) {
                    pending.addLast(operation.picture.ops)
                }
            }
        }
        return false
    }

    private fun ImageFilter?.containsW6dAdvancedArm(): Boolean = when (this) {
        null -> false
        is ImageFilter.DistantLitDiffuse,
        is ImageFilter.PointLitDiffuse,
        is ImageFilter.SpotLitDiffuse,
        is ImageFilter.DistantLitSpecular,
        is ImageFilter.PointLitSpecular,
        is ImageFilter.SpotLitSpecular,
        is ImageFilter.DisplacementMap,
        is ImageFilter.Picture,
        is ImageFilter.Magnifier,
        is ImageFilter.MatrixConvolution,
        is ImageFilter.RuntimeEffect -> true
        is ImageFilter.Crop -> input.containsW6dAdvancedArm()
        is ImageFilter.Blur -> input.containsW6dAdvancedArm()
        is ImageFilter.DropShadow -> input.containsW6dAdvancedArm()
        is ImageFilter.ColorFilter -> input.containsW6dAdvancedArm()
        is ImageFilter.Compose -> outer.containsW6dAdvancedArm() || inner.containsW6dAdvancedArm()
        is ImageFilter.Blend -> background.containsW6dAdvancedArm() || foreground.containsW6dAdvancedArm()
        is ImageFilter.Dilate -> input.containsW6dAdvancedArm()
        is ImageFilter.Erode -> input.containsW6dAdvancedArm()
        is ImageFilter.Offset -> input.containsW6dAdvancedArm()
        is ImageFilter.Tile -> input.containsW6dAdvancedArm()
        is ImageFilter.Merge -> inputs.any { input -> input.containsW6dAdvancedArm() }
    }

    private fun DisplayOp.paintOrNull(): org.graphiks.kanvas.paint.Paint? = when (this) {
        is DisplayOp.DrawRect -> paint
        is DisplayOp.DrawRRect -> paint
        is DisplayOp.DrawPath -> paint
        is DisplayOp.DrawImage -> paint
        is DisplayOp.DrawText -> paint
        is DisplayOp.BeginLayer -> rec.paint
        is DisplayOp.DrawPoint -> paint
        is DisplayOp.DrawPoints -> paint
        is DisplayOp.DrawDRRect -> paint
        is DisplayOp.DrawImageNine -> paint
        is DisplayOp.DrawImageLattice -> paint
        is DisplayOp.DrawPicture -> paint
        is DisplayOp.DrawVertices -> paint
        is DisplayOp.DrawMesh -> paint
        is DisplayOp.DrawAtlas -> paint
        else -> null
    }

    /** Recognition routes pending composed geometry to its owned planner refusal. */
    private fun DisplayOp.hasComposedSource(): Boolean {
        var source = when (this) {
            is DisplayOp.DrawRect -> paint.shader
            is DisplayOp.DrawRRect -> paint.shader
            is DisplayOp.DrawPath -> paint.shader
            is DisplayOp.DrawPoint -> paint.shader
            is DisplayOp.DrawPoints -> paint.shader
            is DisplayOp.DrawVertices -> paint.shader
            is DisplayOp.DrawMesh -> paint.shader
            else -> null
        }
        repeat(org.graphiks.kanvas.render.ir.GraphLimits().maxDepth) {
            source = when (val node = source) {
                is org.graphiks.kanvas.paint.Shader.Blend,
                is org.graphiks.kanvas.paint.Shader.PerlinNoise,
                is org.graphiks.kanvas.paint.Shader.FractalNoise -> return true
                is org.graphiks.kanvas.paint.Shader.Opacity -> node.shader
                is org.graphiks.kanvas.paint.Shader.WithColorFilter -> node.shader
                is org.graphiks.kanvas.paint.Shader.WithWorkingColorSpace -> node.shader
                is org.graphiks.kanvas.paint.Shader.WithLocalMatrix -> node.shader
                is org.graphiks.kanvas.paint.Shader.CoordClamp -> node.shader
                else -> return false
            }
        }
        return false
    }
    /** Shared whole-frame admission; excluded direct images keep their legacy owner. */
    fun ownsW5eDirectImage(operation: DisplayOp.DrawImage): Boolean =
        operation.image.pixels != null &&
            operation.image.colorType in setOf(org.graphiks.kanvas.image.ColorType.RGBA_8888,
                org.graphiks.kanvas.image.ColorType.BGRA_8888, org.graphiks.kanvas.image.ColorType.SRGBA_8888,
                org.graphiks.kanvas.image.ColorType.ALPHA_8) &&
            operation.image.alphaType in setOf(org.graphiks.kanvas.image.AlphaType.OPAQUE,
                org.graphiks.kanvas.image.AlphaType.PREMUL, org.graphiks.kanvas.image.AlphaType.UNPREMUL) &&
            (operation.sampling in setOf(org.graphiks.kanvas.paint.SamplingOptions.NEAREST,
                org.graphiks.kanvas.paint.SamplingOptions.LINEAR) || operation.sampling is org.graphiks.kanvas.paint.SamplingOptions.Cubic) &&
            operation.paint.let { it == null || it.blender == null &&
                it.maskFilter == null && it.imageFilter == null &&
                it.pathEffect == null && it.style == org.graphiks.kanvas.paint.PaintStyle.FILL }

    fun ownsW5eImages(operations: List<DisplayOp>): Boolean =
        operations.any { it is DisplayOp.DrawImage || it is DisplayOp.DrawImageNine || it is DisplayOp.DrawImageLattice || it is DisplayOp.DrawAtlas || it.isW5eShaderOperation() } && operations.all { operation ->
            when (operation) {
                is DisplayOp.DrawImage -> ownsW5eDirectImage(operation)
                is DisplayOp.DrawImageNine -> operation.image.pixels != null &&
                    operation.image.colorType in setOf(org.graphiks.kanvas.image.ColorType.RGBA_8888,
                        org.graphiks.kanvas.image.ColorType.BGRA_8888, org.graphiks.kanvas.image.ColorType.SRGBA_8888,
                        org.graphiks.kanvas.image.ColorType.ALPHA_8) &&
                    operation.image.alphaType in setOf(org.graphiks.kanvas.image.AlphaType.OPAQUE,
                        org.graphiks.kanvas.image.AlphaType.PREMUL, org.graphiks.kanvas.image.AlphaType.UNPREMUL) &&
                    operation.paint.let { it == null || it.blender == null &&
                        it.maskFilter == null && it.imageFilter == null &&
                        it.pathEffect == null && it.style == org.graphiks.kanvas.paint.PaintStyle.FILL }
                is DisplayOp.DrawRect -> operation.isW5eShaderOperation() || operation.paint.shader?.imageLeafW5e() == null
                is DisplayOp.DrawImageLattice -> operation.image.pixels != null &&
                    operation.image.colorType in setOf(org.graphiks.kanvas.image.ColorType.RGBA_8888,
                        org.graphiks.kanvas.image.ColorType.BGRA_8888, org.graphiks.kanvas.image.ColorType.SRGBA_8888,
                        org.graphiks.kanvas.image.ColorType.ALPHA_8) &&
                    operation.image.alphaType in setOf(org.graphiks.kanvas.image.AlphaType.OPAQUE,
                        org.graphiks.kanvas.image.AlphaType.PREMUL, org.graphiks.kanvas.image.AlphaType.UNPREMUL) &&
                    (operation.sampling in setOf(org.graphiks.kanvas.paint.SamplingOptions.NEAREST,
                        org.graphiks.kanvas.paint.SamplingOptions.LINEAR) || operation.sampling is org.graphiks.kanvas.paint.SamplingOptions.Cubic) &&
                    operation.paint.let { it == null || it.blender == null && it.maskFilter == null &&
                        it.imageFilter == null && it.pathEffect == null && it.style == org.graphiks.kanvas.paint.PaintStyle.FILL }
                is DisplayOp.DrawAtlas -> operation.atlas.pixels != null &&
                    operation.atlas.colorType in setOf(org.graphiks.kanvas.image.ColorType.RGBA_8888,
                        org.graphiks.kanvas.image.ColorType.BGRA_8888, org.graphiks.kanvas.image.ColorType.SRGBA_8888,
                        org.graphiks.kanvas.image.ColorType.ALPHA_8) &&
                    operation.atlas.alphaType in setOf(org.graphiks.kanvas.image.AlphaType.OPAQUE,
                        org.graphiks.kanvas.image.AlphaType.PREMUL, org.graphiks.kanvas.image.AlphaType.UNPREMUL) &&
                    operation.paint.let { it == null || it.blender == null && it.maskFilter == null &&
                        it.imageFilter == null && it.pathEffect == null && it.style == org.graphiks.kanvas.paint.PaintStyle.FILL }
                is DisplayOp.DrawPath -> operation.sourceOperation == DrawPathSourceOperation.DRAW_PATH.stableName &&
                    (operation.isW5eShaderOperation() || operation.paint.shader?.imageLeafW5e() == null)
                is DisplayOp.DrawRRect -> operation.paint.shader?.imageLeafW5e() == null
                is DisplayOp.SetTransform, is DisplayOp.SetClip, is DisplayOp.Annotation -> true
                else -> false
            }
        }

    private fun DisplayOp.isW5eShaderOperation(): Boolean {
        val paint = when (this) {
            is DisplayOp.DrawRect -> paint
            is DisplayOp.DrawPath -> if (sourceOperation == DrawPathSourceOperation.DRAW_PATH.stableName) paint else return false
            else -> return false
        }
        if (paint.style != org.graphiks.kanvas.paint.PaintStyle.FILL || paint.blender != null ||
            paint.maskFilter != null || paint.imageFilter != null || paint.pathEffect != null) return false
        val leaf = paint.shader?.imageLeafW5e() ?: return false
        return leaf.image.pixels != null && (leaf.sampling in setOf(org.graphiks.kanvas.paint.SamplingOptions.NEAREST,
            org.graphiks.kanvas.paint.SamplingOptions.LINEAR) || leaf.sampling is org.graphiks.kanvas.paint.SamplingOptions.Cubic) &&
            leaf.image.colorType in setOf(org.graphiks.kanvas.image.ColorType.RGBA_8888, org.graphiks.kanvas.image.ColorType.BGRA_8888,
                org.graphiks.kanvas.image.ColorType.SRGBA_8888, org.graphiks.kanvas.image.ColorType.ALPHA_8) &&
            leaf.image.alphaType in setOf(org.graphiks.kanvas.image.AlphaType.OPAQUE, org.graphiks.kanvas.image.AlphaType.PREMUL,
                org.graphiks.kanvas.image.AlphaType.UNPREMUL)
    }

    private fun org.graphiks.kanvas.paint.Shader.imageLeafW5e(): org.graphiks.kanvas.paint.Shader.Image? {
        var source = this
        repeat(65) {
            source = when (val node = source) {
                is org.graphiks.kanvas.paint.Shader.WithLocalMatrix -> node.shader
                is org.graphiks.kanvas.paint.Shader.Opacity -> node.shader
                is org.graphiks.kanvas.paint.Shader.WithColorFilter -> node.shader
                is org.graphiks.kanvas.paint.Shader.WithWorkingColorSpace -> node.shader
                else -> return node as? org.graphiks.kanvas.paint.Shader.Image
            }
        }
        return null
    }
    fun accepts(operations: List<DisplayOp>, config: RenderConfig): Boolean =
        config.gpuColorFormat == GPUColorFormat.RGBA8_UNORM_SRGB &&
            (ownsW5eImages(operations) || operations.all { operation ->
                if (operation is DisplayOp.DrawRRect && !operation.paint.isStroke() ||
                    operation is DisplayOp.DrawPath && operation.paint.style == org.graphiks.kanvas.paint.PaintStyle.STROKE &&
                    operation.sourceOperation in setOf(DrawPathSourceOperation.DRAW_PATH.stableName,
                        "drawPoints.lines", "drawPoints.polygon")) return@all true
                if (operation.hasComposedSource()) return@all true
                if ((operation is DisplayOp.DrawRect || operation is DisplayOp.DrawRRect ||
                        operation is DisplayOp.DrawPath) &&
                    !operation.isW5dGradientCandidateV2(allowNonGradient = true)) return@all false
                operation is DisplayOp.DrawRect ||
                    operation is DisplayOp.DrawRRect ||
                    (operation is DisplayOp.DrawPath &&
                        operation.sourceOperation == DrawPathSourceOperation.DRAW_PATH.stableName) ||
                    operation is DisplayOp.DrawColor ||
                    operation is DisplayOp.SetTransform ||
                    operation is DisplayOp.SetClip ||
                    operation is DisplayOp.Annotation
            })

    private const val W6B_PICTURE_VISIT_LIMIT_I32: Int = 4_096
}
