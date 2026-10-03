package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.BlendMode
import org.graphiks.kanvas.render.ir.BlendNode
import org.graphiks.kanvas.render.ir.ClipStackNode
import org.graphiks.kanvas.render.ir.ColorInterpolation
import org.graphiks.kanvas.render.ir.CompositionDomain
import org.graphiks.kanvas.render.ir.CoverageRequest
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.kanvas.render.ir.ImagePremultiplicationV1
import org.graphiks.kanvas.render.ir.ImagePixelFormat
import org.graphiks.kanvas.render.ir.ImageResourceSnapshot
import org.graphiks.kanvas.render.ir.MaterialNode
import org.graphiks.kanvas.render.ir.PaintStyleNode
import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticCode
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.PathSegmentF32
import org.graphiks.math.geometry.coordinateF32ToExactI32OrNull
import org.graphiks.math.geometry.toExactRectI32OrNull
import org.graphiks.math.matrix.PathTransformClass
import org.graphiks.math.matrix.classifyPathTransform
import org.graphiks.math.matrix.invertFiniteOrNull
import org.graphiks.math.matrix.toMatrix3x3F64

/**
 * Whole-scene policy for the opt-in encoded composition contract.
 *
 * This runs before capability selection: an excluded command therefore cannot
 * acquire a device and cannot fall through to a legacy renderer.
 */
public object CompositionAdmissionV1 {
    public fun validate(scene: SceneSnapshot, target: RenderTargetDescriptor): List<RenderDiagnostic> {
        if (target.compositionDomain != CompositionDomain.SRGB_ENCODED) return emptyList()
        topologyRefusal(scene)?.let { return listOf(it) }
        if (scene.any { command -> command is SceneCommand.Draw &&
                command.node.origin == DrawOrigin.PATH && command.node.geometry is GeometryNode.Path &&
                command.node.coverage == CoverageRequest.ANTIALIASED
            }) {
            scene.forEachIndexed { index, command ->
                rootAaPathFrameRefusal(command, index)?.let { return listOf(it) }
            }
            return emptyList()
        }
        scene.forEachIndexed { index, command ->
            when (command) {
                is SceneCommand.BeginLayer -> plainLayerRefusal(command, index)?.let { return listOf(it) }
                SceneCommand.EndLayer -> Unit
                else -> refusal(command, index)?.let { return listOf(it) }
            }
        }
        return emptyList()
    }

    /** Shared contextual admission for the public encoded root AA Path family. */
    internal fun isAdmittedEncodedRootAaPathFrame(
        scene: SceneSnapshot,
        target: RenderTargetDescriptor,
    ): Boolean {
        if (target.compositionDomain != CompositionDomain.SRGB_ENCODED || topologyRefusal(scene) != null) return false
        if (scene.none { command -> command is SceneCommand.Draw && command.node.origin == DrawOrigin.PATH &&
                command.node.geometry is GeometryNode.Path && command.node.coverage == CoverageRequest.ANTIALIASED
            }) return false
        return scene.withIndex().all { (index, command) -> rootAaPathFrameRefusal(command, index) == null }
    }

    private fun rootAaPathFrameRefusal(command: SceneCommand, index: Int): RenderDiagnostic? = when (command) {
        is SceneCommand.Draw -> if (command.node.origin == DrawOrigin.RECT && command.node.geometry is GeometryNode.Rect)
            rootAaRectDrawRefusal(command, index)
        else rootAaPathDrawRefusal(command, index)
        is SceneCommand.SetTransform -> if (command.matrix.isRootAaPathTransform()) null
            else diagnostic("geometry", index, "Encoded root AA Path requires finite invertible identity or axis-aligned affine transforms.")
        is SceneCommand.SetClip -> if (command.clip.isHardIntegerRectOrEmpty()) null
            else diagnostic("geometry", index, "Encoded root AA Path requires an empty or hard integer rectangle clip.")
        is SceneCommand.Annotation -> null
        is SceneCommand.BeginLayer, SceneCommand.EndLayer ->
            diagnostic("layer", index, "Encoded root AA Path frames do not admit layers.")
        is SceneCommand.DrawColor, is SceneCommand.Clear, is SceneCommand.Readback, is SceneCommand.State ->
            diagnostic("geometry", index, "Encoded root AA Path frames admit only Path draws and state transforms/clips.")
    }

    private fun rootAaPathDrawRefusal(command: SceneCommand.Draw, index: Int): RenderDiagnostic? {
        val node = command.node
        val geometry = node.geometry as? GeometryNode.Path
        if (node.origin != DrawOrigin.PATH || geometry == null) {
            return diagnostic("geometry", index, "Encoded root AA Path frames admit only Path draws.")
        }
        val path = geometry.path
        if (path.fillRule !in setOf(org.graphiks.math.geometry.FillRule.WINDING, org.graphiks.math.geometry.FillRule.EVEN_ODD) ||
            path.any { it !is PathSegmentF32.MoveTo && it !is PathSegmentF32.LineTo && it != PathSegmentF32.Close }
        ) return diagnostic("geometry", index, "Encoded root AA Path admits only non-inverse linear Paths.")
        if (!node.transform.isRootAaPathTransform() || !node.clip.isHardIntegerRectOrEmpty()) {
            return diagnostic("geometry", index, "Encoded root AA Path requires a finite invertible supported transform and hard integer clip.")
        }
        val paint = node.paint ?: return diagnostic("source", index, "Encoded root AA Path requires direct solid paint.")
        if (paint.style !in setOf(PaintStyleNode.FILL, PaintStyleNode.STROKE) ||
            (paint.style == PaintStyleNode.STROKE && (!paint.strokeWidth.isFinite() || paint.strokeWidth <= 0f ||
                paint.strokeCap != org.graphiks.kanvas.render.ir.StrokeCapNode.BUTT ||
                paint.strokeJoin != org.graphiks.kanvas.render.ir.StrokeJoinNode.MITER ||
                !paint.strokeMiter.isFinite() || paint.strokeMiter < 2f)) ||
            (node.coverage == CoverageRequest.HARD_EDGE && (paint.style != PaintStyleNode.FILL || paint.antiAlias))
        ) return diagnostic("geometry", index, "Encoded root AA Path admits fills and positive BUTT/MITER strokes only.")
        if (!node.blend.isSrcOver() || paint.blendMode != BlendMode.SRC_OVER || node.operationBlendMode != null) {
            return diagnostic("blend", index, "Encoded root AA Path admits only SrcOver blending.")
        }
        if (node.material !is org.graphiks.kanvas.render.ir.MaterialNode.Solid || paint.shader != null ||
            paint.blender != null || paint.colorFilter != null || paint.maskFilter != null ||
            paint.pathEffect != null || paint.imageFilter != null || node.effects !=
            org.graphiks.kanvas.render.ir.EffectStack.Empty || node.resource != null
        ) return diagnostic("source", index, "Encoded root AA Path requires direct solid paint without effects.")
        return null
    }

    private fun rootAaRectDrawRefusal(command: SceneCommand.Draw, index: Int): RenderDiagnostic? {
        val node = command.node
        val paint = node.paint
        val bounds = (node.geometry as GeometryNode.Rect).copyBounds()
        if (paint?.style != PaintStyleNode.FILL || paint.antiAlias || node.coverage != CoverageRequest.HARD_EDGE ||
            bounds.toExactRectI32OrNull() == null || !node.transform.isIdentityOrIntegerI32Translation() ||
            !node.clip.isHardIntegerRectOrEmpty()
        ) return diagnostic("geometry", index,
            "Encoded root AA Path Rect siblings require finite non-empty I32 bounds, non-AA FILL, integer translation, and hard integer clip.")
        if (node.material !is MaterialNode.Solid || paint.shader != null || paint.blender != null ||
            paint.colorFilter != null || paint.maskFilter != null || paint.pathEffect != null ||
            paint.imageFilter != null || node.effects != org.graphiks.kanvas.render.ir.EffectStack.Empty ||
            node.resource != null
        ) return diagnostic("geometry", index, "Encoded root AA Path Rect siblings require direct solid paint without effects.")
        if (!node.blend.isSrcOver() || paint.blendMode != BlendMode.SRC_OVER || node.operationBlendMode != null) {
            return diagnostic("geometry", index, "Encoded root AA Path Rect siblings require direct solid SrcOver paint.")
        }
        return null
    }

    private fun Matrix3x3F32.isRootAaPathTransform(): Boolean {
        val matrix = toMatrix3x3F64()
        return matrix.classifyPathTransform() in setOf(PathTransformClass.Identity, PathTransformClass.AxisAlignedAffine) &&
            matrix.invertFiniteOrNull() != null
    }

    /** Layer topology precedes command-order refusal in the encoded contract. */
    private fun topologyRefusal(scene: SceneSnapshot): RenderDiagnostic? {
        var layerDepthI32 = 0
        var layerCountI32 = 0
        scene.forEachIndexed { index, command ->
            when (command) {
                is SceneCommand.BeginLayer -> {
                    if (layerDepthI32 != 0 || layerCountI32 != 0) {
                        return diagnostic("layer", index, "Encoded composition admits exactly one non-nested plain layer.")
                    }
                    layerDepthI32 = 1
                    layerCountI32 = 1
                }
                SceneCommand.EndLayer -> {
                    if (layerDepthI32 != 1) {
                        return diagnostic("layer", index, "Encoded composition layer boundaries are unbalanced.")
                    }
                    layerDepthI32 = 0
                }
                else -> Unit
            }
        }
        return if (layerDepthI32 != 0) diagnostic("layer", -1, "Encoded composition layer is not restored.") else null
    }

    private fun refusal(command: SceneCommand, index: Int): RenderDiagnostic? = when (command) {
        is SceneCommand.Draw -> drawRefusal(command, index)
        is SceneCommand.DrawColor -> when {
            !command.transform.isIdentity() -> diagnostic("geometry", index, "drawColor requires an identity transform.")
            !command.clip.isHardIntegerRectOrEmpty() -> diagnostic("geometry", index, "drawColor requires an empty or hard integer rectangle clip.")
            command.mode != BlendMode.SRC_OVER -> diagnostic("blend", index, "drawColor requires SrcOver.")
            else -> null
        }
        is SceneCommand.BeginLayer, is SceneCommand.EndLayer ->
            diagnostic("layer", index, "Layer boundaries must be validated as a complete plain layer occurrence.")
        is SceneCommand.Clear, is SceneCommand.Readback ->
            diagnostic("geometry", index, "This encoded composition command is not admitted.")
        is SceneCommand.SetTransform -> if (command.matrix.isIdentityOrIntegerTranslation()) null
            else diagnostic("geometry", index, "Encoded composition requires identity or integer translation transforms.")
        is SceneCommand.SetClip -> if (command.clip.isHardIntegerRectOrEmpty()) null
            else diagnostic("geometry", index, "Encoded composition requires an empty or hard integer rectangle clip.")
        is SceneCommand.Annotation -> null
        is SceneCommand.State -> diagnostic("geometry", index, "Opaque state commands are not admitted by encoded composition.")
    }

    private fun drawRefusal(command: SceneCommand.Draw, index: Int): RenderDiagnostic? {
        val node = command.node
        if (node.origin == DrawOrigin.IMAGE) return imagePatchRefusal(node, index)
        if (node.origin in setOf(DrawOrigin.IMAGE_NINE, DrawOrigin.IMAGE_LATTICE, DrawOrigin.ATLAS)) {
            return diagnostic("image", index, "Encoded composition admits only direct ImagePatch image draws.")
        }
        val paint = node.paint
        if (node.origin == DrawOrigin.RECT && node.geometry is GeometryNode.Rect &&
            paint?.style == PaintStyleNode.STROKE
        ) return rectHairlineRefusal(node, index)
        if (node.origin != DrawOrigin.RECT || node.geometry !is GeometryNode.Rect ||
            paint?.style != PaintStyleNode.FILL || paint.antiAlias ||
            node.coverage != CoverageRequest.HARD_EDGE || !node.transform.isIdentityOrIntegerTranslation() ||
            !node.clip.isHardIntegerRectOrEmpty() || !(node.geometry as GeometryNode.Rect).copyBounds().isIntegerRect()
        ) return diagnostic("geometry", index, "Encoded composition admits only non-AA integer Rect FILL draws.")
        if (!node.blend.isSrcOver() || paint.blendMode != BlendMode.SRC_OVER || node.operationBlendMode != null) {
            return diagnostic("blend", index, "Encoded composition admits only SrcOver blending.")
        }
        if (paint.blender != null || paint.colorFilter != null ||
            paint.maskFilter != null || paint.pathEffect != null || paint.imageFilter != null ||
            node.effects != org.graphiks.kanvas.render.ir.EffectStack.Empty || node.resource != null
        ) return diagnostic("source", index, "Encoded composition admits only direct solid or LinearGradient paint sources at this stage.")
        val shader = paint.shader ?: return null
        val gradient = shader as? MaterialNode.LinearGradient
            ?: return diagnostic("source", index, "Encoded composition admits only solid or LinearGradient paint sources.")
        if (gradient.interpolation != ColorInterpolation.SRGB ||
            gradient.tileMode != org.graphiks.kanvas.render.ir.TileMode.CLAMP
        ) return diagnostic("source", index, "Encoded composition LinearGradient requires sRGB interpolation and CLAMP tile mode.")
        return null
    }

    /** The hard Rect hairline keeps its original STROKE provenance through admission. */
    internal fun isAdmittedEncodedRectHairline(command: SceneCommand.Draw): Boolean {
        val node = command.node
        return node.origin == DrawOrigin.RECT && node.geometry is GeometryNode.Rect &&
            node.paint?.style == PaintStyleNode.STROKE && rectHairlineRefusal(node, -1) == null
    }

    private fun rectHairlineRefusal(
        node: org.graphiks.kanvas.render.ir.DrawNode,
        index: Int,
    ): RenderDiagnostic? {
        val paint = requireNotNull(node.paint)
        val bounds = (node.geometry as GeometryNode.Rect).copyBounds()
        if (paint.antiAlias || node.coverage != CoverageRequest.HARD_EDGE || paint.strokeWidth != 0f ||
            paint.strokeCap != org.graphiks.kanvas.render.ir.StrokeCapNode.BUTT ||
            paint.strokeJoin != org.graphiks.kanvas.render.ir.StrokeJoinNode.MITER ||
            !paint.strokeMiter.isFinite() || paint.strokeMiter < 2f ||
            !node.transform.isIdentityOrIntegerTranslation() || !node.clip.isHardIntegerRectOrEmpty() ||
            bounds.isEmpty || !bounds.isIntegerRect()
        ) return diagnostic("geometry", index,
            "Encoded composition admits only non-AA integer Rect STROKE hairlines with BUTT/MITER.")
        if (!node.blend.isSrcOver() || paint.blendMode != BlendMode.SRC_OVER || node.operationBlendMode != null) {
            return diagnostic("blend", index, "Encoded composition admits only SrcOver blending.")
        }
        if (paint.shader != null || paint.blender != null || paint.colorFilter != null ||
            paint.maskFilter != null || paint.pathEffect != null || paint.imageFilter != null ||
            node.effects != org.graphiks.kanvas.render.ir.EffectStack.Empty || node.resource != null
        ) return diagnostic("source", index,
            "Encoded composition Rect hairline requires a direct solid paint source.")
        return null
    }

    /** The encoded image lane is intentionally narrower than W5e's historical family. */
    private fun imagePatchRefusal(node: org.graphiks.kanvas.render.ir.DrawNode, index: Int): RenderDiagnostic? {
        val patch = node.geometry as? GeometryNode.ImagePatch
            ?: return diagnostic("image", index, "Encoded composition admits only ImagePatch image geometry.")
        val paint = node.paint
        if (paint?.style != PaintStyleNode.FILL || paint?.antiAlias == true ||
            node.coverage != CoverageRequest.HARD_EDGE || !node.transform.isIdentityOrIntegerTranslation() ||
            !node.clip.isHardIntegerRectOrEmpty() || patch.sampling != org.graphiks.kanvas.render.ir.ImageSampling.Nearest
        ) return diagnostic("geometry", index, "Encoded composition image requires a non-AA integer nearest 1:1 patch.")
        val source = patch.copySource()
        val destination = patch.copyDestination()
        if (!source.isIntegerRect() || !destination.isIntegerRect() ||
            source.width() != destination.width() || source.height() != destination.height()
        ) return diagnostic("geometry", index, "Encoded composition image requires integer equal-extent source and destination.")
        if (!node.blend.isSrcOver() || paint?.blendMode?.let { it != BlendMode.SRC_OVER } == true ||
            node.operationBlendMode != null) {
            return diagnostic("blend", index, "Encoded composition image requires SrcOver blending.")
        }
        if (paint?.shader != null || paint?.blender != null || paint?.colorFilter != null ||
            paint?.maskFilter != null || paint?.pathEffect != null || paint?.imageFilter != null ||
            node.effects != org.graphiks.kanvas.render.ir.EffectStack.Empty
        ) return diagnostic("source", index, "Encoded composition image does not admit paint effects.")
        val pixels = node.resource as? ImageResourceSnapshot.Pixels
            ?: return diagnostic("image", index, "Encoded composition image requires owned pixels.")
        if (pixels.pixelFormat !in setOf(ImagePixelFormat.RGBA_8888, ImagePixelFormat.BGRA_8888) ||
            pixels.alphaType != org.graphiks.kanvas.render.ir.ImageAlphaType.PREMUL ||
            pixels.colorSpace != org.graphiks.kanvas.color.ColorSpace.SRGB ||
            pixels.premultiplication != ImagePremultiplicationV1.SOURCE_SPACE
        ) return diagnostic("image", index, "Encoded composition image requires SOURCE_SPACE PREMUL SRGB RGBA/BGRA pixels.")
        return null
    }

    private fun plainLayerRefusal(command: SceneCommand.BeginLayer, index: Int): RenderDiagnostic? {
        val descriptor = command.descriptor
        val paint = descriptor.paint
        val material = descriptor.material
        if (descriptor.initWithPrevious || descriptor.backdrop != org.graphiks.kanvas.render.ir.EffectStack.Empty ||
            descriptor.effects != org.graphiks.kanvas.render.ir.EffectStack.Empty ||
            (material != null && (material !is org.graphiks.kanvas.render.ir.MaterialNode.Solid || material.color != paint?.color))
        ) return diagnostic("layer", index, "Encoded composition admits only a plain initialized layer restore.")
        if (paint?.shader != null || paint?.blender != null ||
            paint?.colorFilter != null || paint?.maskFilter != null || paint?.pathEffect != null ||
            paint?.imageFilter != null || !descriptor.blend.isSrcOver()
        ) return diagnostic("layer", index, "Encoded composition admits only a plain SrcOver layer restore.")
        val bounds = descriptor.copyBounds()
        if (bounds != null && !bounds.isIntegerRect()) {
            return diagnostic("geometry", index, "Encoded composition layer bounds must be integer aligned.")
        }
        if (!descriptor.transform.isIdentityOrIntegerTranslation()) {
            return diagnostic("geometry", index, "Encoded composition layer transform must be identity or an integer translation.")
        }
        if (!descriptor.clip.isHardIntegerRectOrEmpty()) {
            return diagnostic("geometry", index, "Encoded composition layer clip must be empty or a hard integer rectangle.")
        }
        val clip = descriptor.compositeClip
        if (clip != null && !clip.isHardIntegerRectOrEmpty()) {
            return diagnostic("geometry", index, "Encoded composition layer clip must be empty or a hard integer rectangle.")
        }
        return null
    }

    private fun BlendNode.isSrcOver(): Boolean = this === BlendNode.SrcOver ||
        this is BlendNode.Mode && mode == BlendMode.SRC_OVER ||
        this is BlendNode.Paint && mode == BlendMode.SRC_OVER && blender == null

    private fun Matrix3x3F32.isIdentity(): Boolean =
        sx == 1f && kx == 0f && tx == 0f && ky == 0f && sy == 1f && ty == 0f &&
            persp0 == 0f && persp1 == 0f && persp2 == 1f

    private fun Matrix3x3F32.isIdentityOrIntegerTranslation(): Boolean =
        sx == 1f && kx == 0f && ky == 0f && sy == 1f && persp0 == 0f && persp1 == 0f && persp2 == 1f &&
            tx.isIntegerFinite() && ty.isIntegerFinite()

    private fun ClipStackNode.isHardIntegerRectOrEmpty(): Boolean = when (this) {
        ClipStackNode.Empty -> true
        is ClipStackNode.DeviceRect -> !antiAlias && copyBounds().isIntegerRect()
        else -> false
    }

    private fun org.graphiks.math.geometry.RectF32.isIntegerRect(): Boolean =
        left.isIntegerFinite() && top.isIntegerFinite() && right.isIntegerFinite() && bottom.isIntegerFinite()

    private fun Matrix3x3F32.isIdentityOrIntegerI32Translation(): Boolean =
        sx == 1f && kx == 0f && ky == 0f && sy == 1f && persp0 == 0f && persp1 == 0f && persp2 == 1f &&
            coordinateF32ToExactI32OrNull(tx) != null && coordinateF32ToExactI32OrNull(ty) != null

    private fun Float.isIntegerFinite(): Boolean = isFinite() && toDouble() == kotlin.math.floor(toDouble())

    private fun diagnostic(suffix: String, index: Int, message: String): RenderDiagnostic = RenderDiagnostic(
        RenderDiagnosticCode("unsupported.surface.composition.$suffix"),
        RenderDiagnosticDomain.SCENE,
        RenderDiagnosticSeverity.ERROR,
        "Command $index: $message",
    )
}
