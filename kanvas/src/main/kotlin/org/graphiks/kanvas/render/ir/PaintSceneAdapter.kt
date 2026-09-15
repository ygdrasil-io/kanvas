package org.graphiks.kanvas.render.ir

import java.util.ArrayDeque
import java.util.IdentityHashMap
import org.graphiks.kanvas.geometry.toCompatibilityPath
import org.graphiks.kanvas.geometry.toPathF32
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Blender
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.math.color.ColorMatrixF32
import org.graphiks.math.geometry.SizeF32
import org.graphiks.math.vector.Vector2F32

/** Captures complete public paint state without selecting a renderer implementation. */
public object PaintSceneAdapter {
    public fun capture(
        paint: Paint,
        limits: SceneCaptureLimits = SceneCaptureLimits.DEFAULT,
        captureImage: (Image) -> ImageResourceSnapshot = ResourceSceneAdapter::captureImage,
        capturePicture: (org.graphiks.kanvas.picture.Picture) -> SceneSnapshot = {
            throw CaptureFailure("picture-filter-requires-context", "Picture image filters require scene capture context")
        },
    ): PaintNode {
        ColorFilterCapturePreflight.validatePaint(paint, limits)?.let {
            throw CaptureFailure(it.code.value, it.message)
        }
        val gradientStops = GradientStopCaptureBudget(limits.maxGradientStopsI32)
        paint.shader?.let { preflightShader(it, limits, gradientStops) }
        (paint.maskFilter as? MaskFilter.Shader)?.shader?.let { preflightShader(it, limits, gradientStops) }
        return PaintNode(
            color = paint.color,
            shader = paint.shader?.toMaterial(captureImage),
            blendMode = BlendMode.valueOf(paint.blendMode.name),
            blender = paint.blender?.toNode(),
            colorFilter = paint.colorFilter?.toNode(captureImage),
            maskFilter = paint.maskFilter?.toNode(captureImage),
            pathEffect = paint.pathEffect?.toNode(),
            imageFilter = paint.imageFilter?.toNode(captureImage, capturePicture),
            style = PaintStyleNode.valueOf(paint.style.name),
            strokeWidth = paint.strokeWidth.checked("paint.strokeWidth"),
            strokeCap = StrokeCapNode.valueOf(paint.strokeCap.name),
            strokeJoin = StrokeJoinNode.valueOf(paint.strokeJoin.name),
            strokeMiter = paint.strokeMiter.checked("paint.strokeMiter"),
            antiAlias = paint.antiAlias,
        ).also { captured ->
            captured.shader?.let { validateMaterial(it, limits) }
            captured.colorFilter?.let { validateEffect(it, limits) }
            captured.maskFilter?.let { validateEffect(it, limits) }
            captured.pathEffect?.let { validateEffect(it, limits) }
            captured.imageFilter?.let { validateEffect(it, limits) }
        }
    }

    /**
     * Bounds public shader graphs before [Shader.toMaterial] recursively copies
     * them.  Direct [capture] callers do not have DisplayOp capture's preflight.
     */
    private fun preflightShader(root: Shader, limits: SceneCaptureLimits, gradientStops: GradientStopCaptureBudget) {
        data class Visit(val shader: Shader, val depth: Int, val leaving: Boolean)

        val active = IdentityHashMap<Shader, Unit>()
        val pending = ArrayDeque<Visit>()
        var nodes = 0
        pending.addLast(Visit(root, depth = 1, leaving = false))
        while (pending.isNotEmpty()) {
            val visit = pending.removeLast()
            if (visit.leaving) {
                active.remove(visit.shader)
                continue
            }
            if (active.put(visit.shader, Unit) != null) {
                throw CaptureFailure("cyclic-effect-graph", "Paint, effect, or material graph contains an identity cycle")
            }
            if (visit.depth > minOf(limits.maxDepth, limits.graphLimits.maxDepth)) {
                throw CaptureFailure("graph-depth-limit", "Paint, effect, or material graph exceeds configured depth")
            }
            nodes += 1
            if (nodes > limits.graphLimits.maxNodes) {
                throw CaptureFailure("graph-node-limit", "Paint, effect, or material graph exceeds configured nodes")
            }
            gradientStops.reserve(visit.shader)
            pending.addLast(Visit(visit.shader, visit.depth, leaving = true))
            shaderChildren(visit.shader).asReversed().forEach { child ->
                pending.addLast(Visit(child, visit.depth + 1, leaving = false))
            }
        }
    }

    private fun shaderChildren(shader: Shader): List<Shader> = when (shader) {
        is Shader.Blend -> listOf(shader.dst, shader.src)
        is Shader.RuntimeEffect -> shader.children.values.toList()
        is Shader.WithLocalMatrix -> listOf(shader.shader)
        is Shader.WithColorFilter -> listOf(shader.shader)
        is Shader.WithWorkingColorSpace -> listOf(shader.shader)
        is Shader.CoordClamp -> listOf(shader.shader)
        is Shader.Opacity -> listOf(shader.shader)
        else -> emptyList()
    }

    /** Reconstructs every public paint component retained in [PaintNode]. */
    public fun restore(node: PaintNode): Paint = Paint(
        color = node.color,
        shader = node.shader?.toShader(),
        blendMode = org.graphiks.kanvas.paint.BlendMode.valueOf(node.blendMode.name),
        colorFilter = node.colorFilter?.toColorFilter(),
        maskFilter = node.maskFilter?.toMaskFilter(),
        pathEffect = node.pathEffect?.toPathEffect(),
        imageFilter = node.imageFilter?.toImageFilter(),
        blender = node.blender?.toBlender(),
        style = PaintStyle.valueOf(node.style.name),
        strokeWidth = node.strokeWidth,
        strokeCap = StrokeCap.valueOf(node.strokeCap.name),
        strokeJoin = StrokeJoin.valueOf(node.strokeJoin.name),
        strokeMiter = node.strokeMiter,
        antiAlias = node.antiAlias,
    )

    public fun restoreImageFilter(node: ImageFilterNode): ImageFilter = node.toImageFilter()

    public fun captureMeshProgram(
        program: org.graphiks.kanvas.paint.MeshProgram,
        captureImage: (Image) -> ImageResourceSnapshot = ResourceSceneAdapter::captureImage,
    ): MeshProgramNode {
        val children = program.children.entries.map { entry -> when (val child = entry.child) {
            is org.graphiks.kanvas.paint.ShaderChild -> MeshProgramChild.Shader(entry.name, child.shader.toMaterial(captureImage))
            is org.graphiks.kanvas.paint.ColorFilterChild -> MeshProgramChild.ColorFilter(entry.name, child.filter.toNode(captureImage))
            is org.graphiks.kanvas.paint.BlenderChild -> MeshProgramChild.Blender(entry.name, child.blender.toNode())
        } }
        return MeshProgramNode.of(
            descriptor = program.effect.toDescriptor(
                abi = RuntimeEffectAbi.SHADER,
                extraChildren = children.map { child ->
                    RuntimeChildSlot(child.binding.name, child.binding.type)
                },
            ),
            uniforms = program.uniforms.toRuntimeUniforms(),
            children = children,
        )
    }

    public fun restoreMeshProgram(node: MeshProgramNode): org.graphiks.kanvas.paint.MeshProgram =
        org.graphiks.kanvas.paint.MeshProgram(
            effect = node.descriptor.registeredEffect(),
            uniforms = node.uniforms().toUniformBlock(),
            children = org.graphiks.kanvas.paint.MeshChildren(node.map { child -> when (child) {
                is MeshProgramChild.Shader -> org.graphiks.kanvas.paint.MeshChildren.Entry(child.name, org.graphiks.kanvas.paint.ShaderChild(child.material.toShader()))
                is MeshProgramChild.ColorFilter -> org.graphiks.kanvas.paint.MeshChildren.Entry(child.name, org.graphiks.kanvas.paint.ColorFilterChild(child.filter.toColorFilter()))
                is MeshProgramChild.Blender -> org.graphiks.kanvas.paint.MeshChildren.Entry(child.name, org.graphiks.kanvas.paint.BlenderChild(child.blender.toBlender()))
            } }),
        )

    /** Preserve coordinate values for typed validation of admitted gradient and image leaves. */
    private fun Shader.preservesW5dMatrices(): Boolean {
        var source = this
        while (true) when (val node = source) {
            is Shader.Opacity -> source = node.shader
            is Shader.WithLocalMatrix -> source = node.shader
            is Shader.CoordClamp -> source = node.shader
            is Shader.WithColorFilter -> source = node.shader
            is Shader.WithWorkingColorSpace -> source = node.shader
            is Shader.Blend -> return node.dst.preservesW5dMatrices() || node.src.preservesW5dMatrices()
            is Shader.LinearGradient, is Shader.RadialGradient, is Shader.SweepGradient, is Shader.ConicalGradient -> return true
            is Shader.Image -> return node.sampling == SamplingOptions.NEAREST &&
                node.tileModeX == org.graphiks.kanvas.paint.TileMode.CLAMP &&
                node.tileModeY == org.graphiks.kanvas.paint.TileMode.CLAMP
            else -> return false
        }
    }

    private fun Shader.toMaterial(captureImage: (Image) -> ImageResourceSnapshot,
        preserveW5dMatrices: Boolean = preservesW5dMatrices(),
        completed: IdentityHashMap<Shader,MutableMap<Boolean,MaterialNode>> = IdentityHashMap(),
        active: IdentityHashMap<Shader,Unit> = IdentityHashMap()): MaterialNode {
        // Occurrence/depth budgets have already been charged by preflightShader.
        // A completed payload is reusable only after the current active-path check.
        if (active.put(this,Unit) != null) throw CaptureFailure("cyclic-effect-graph", "Paint, effect, or material graph contains an identity cycle")
        completed[this]?.get(preserveW5dMatrices)?.let { active.remove(this); return it }
        fun child(shader: Shader, preserve: Boolean = preserveW5dMatrices): MaterialNode =
            shader.toMaterial(captureImage,preserve,completed,active)
        val captured = when (this) {
        is Shader.SolidColor -> MaterialNode.Solid(color)
        is Shader.Opacity -> MaterialNode.Opacity(child(shader), alphaF32)
        is Shader.LinearGradient -> MaterialNode.LinearGradient.of(start.checked("shader.start"), end.checked("shader.end"), stops.map { GradientStop(it.position.checked("shader.stop"), it.color) }, TileMode.valueOf(tileMode.name), ColorInterpolation.valueOf(interpolation.name))
        is Shader.RadialGradient -> MaterialNode.RadialGradient.of(center.checked("shader.center"), radius.checked("shader.radius"), stops.map { GradientStop(it.position.checked("shader.stop"), it.color) }, TileMode.valueOf(tileMode.name), ColorInterpolation.valueOf(interpolation.name))
        is Shader.SweepGradient -> MaterialNode.SweepGradient.of(center.checked("shader.center"), startAngle.checked("shader.start-angle"), endAngle.checked("shader.end-angle"), stops.map { GradientStop(it.position.checked("shader.stop"), it.color) }, TileMode.valueOf(tileMode.name), ColorInterpolation.valueOf(interpolation.name))
        is Shader.ConicalGradient -> MaterialNode.ConicalGradient.of(start.checked("shader.start"), startRadius.checked("shader.start-radius"), end.checked("shader.end"), endRadius.checked("shader.end-radius"), stops.map { GradientStop(it.position.checked("shader.stop"), it.color) }, TileMode.valueOf(tileMode.name), ColorInterpolation.valueOf(interpolation.name))
        is Shader.Image -> MaterialNode.ImageSample(captureImage(image), TileMode.valueOf(tileModeX.name), TileMode.valueOf(tileModeY.name), sampling.toImageSampling())
        is Shader.Blend -> MaterialNode.Blend(BlendMode.valueOf(mode.name), child(dst,preserveW5dMatrices), child(src,preserveW5dMatrices))
        is Shader.WithLocalMatrix -> MaterialNode.WithLocalMatrix(child(shader),
            if (preserveW5dMatrices) matrix.copy() else matrix.checked("shader.local-matrix"))
        is Shader.WithColorFilter -> MaterialNode.WithColorFilter(child(shader), filter.toNode(captureImage))
        is Shader.PerlinNoise -> MaterialNode.PerlinNoise(baseX, baseY, numOctaves, seed, tileSize)
        is Shader.FractalNoise -> MaterialNode.FractalNoise(baseX, baseY, numOctaves, seed, tileSize)
        is Shader.WithWorkingColorSpace -> MaterialNode.WithWorkingColorSpace(child(shader), ColorInterpolation.valueOf(interpolation.name))
        is Shader.CoordClamp -> MaterialNode.CoordClamp(child(shader),
            if (preserveW5dMatrices) subset.copy() else subset.checked("shader.subset"))
        is Shader.RuntimeEffect -> {
            val descriptor=effect.toDescriptor(RuntimeEffectAbi.SHADER)
            val capturedUniforms=uniforms.toRuntimeUniforms()
            if(descriptor.semanticVersionI32 > 0) org.graphiks.kanvas.gpu.plan.W5hPlanDiagnostics.bindingCode(
                RuntimeBindingValidator.validate(descriptor,capturedUniforms,children.keys.map { RuntimeChildBinding(it,RuntimeChildType.SHADER) }))
                ?.let { throw CaptureFailure(it,"Runtime effect bindings do not match the registered descriptor") }
            MaterialNode.RuntimeEffect.of(descriptor,capturedUniforms,
                children.map { (name, shader) -> RuntimeMaterialChild(name, child(shader,false)) },
                resources.toRuntimeResources(descriptor,captureImage))
        }
        }
        active.remove(this)
        completed.getOrPut(this) { mutableMapOf() }[preserveW5dMatrices] = captured
        return captured
    }

    private fun ColorFilter.toNode(captureImage: (Image) -> ImageResourceSnapshot): ColorFilterNode = when (this) {
        is ColorFilter.Matrix -> ColorFilterNode.Matrix(ImmutableFloats.copyOf(matrix.toFloatArray().checked("color-filter.matrix")))
        is ColorFilter.Blend -> ColorFilterNode.Blend(color, BlendMode.valueOf(mode.name))
        is ColorFilter.Compose -> ColorFilterNode.Compose(outer.toNode(captureImage), inner.toNode(captureImage))
        is ColorFilter.Table -> ColorFilterNode.Table(ImmutableUBytes.copyOf(table))
        is ColorFilter.Lighting -> ColorFilterNode.Lighting(mul, add)
        ColorFilter.SRGBToLinear -> ColorFilterNode.SRGBToLinear
        ColorFilter.LinearToSRGB -> ColorFilterNode.LinearToSRGB
        is ColorFilter.HSLAMatrix -> ColorFilterNode.HSLAMatrix(ImmutableFloats.copyOf(values.checked("color-filter.hsla")))
        is ColorFilter.Lerp -> ColorFilterNode.Lerp(t.checked("color-filter.lerp"), dst.toNode(captureImage), src.toNode(captureImage))
        ColorFilter.HighContrast -> ColorFilterNode.HighContrast
        ColorFilter.Luma -> ColorFilterNode.Luma
        ColorFilter.Overdraw -> ColorFilterNode.Overdraw
        is ColorFilter.RuntimeEffect -> ColorFilterNode.RuntimeEffect.of(
            effect.toDescriptor(RuntimeEffectAbi.COLOR_FILTER),
            uniforms.toRuntimeUniforms(),
            children.map { (name, child) -> RuntimeColorFilterChild(name, child.toNode(captureImage)) },
        )
    }

    private fun MaskFilter.toNode(captureImage: (Image) -> ImageResourceSnapshot): MaskFilterNode = when (this) {
        is MaskFilter.Blur -> MaskFilterNode.Blur(MaskBlurStyle.valueOf(style.name), sigma.checked("mask-filter.sigma"))
        is MaskFilter.Shader -> MaskFilterNode.Shader(shader.toMaterial(captureImage))
        is MaskFilter.Table -> MaskFilterNode.Table(ImmutableUBytes.copyOf(table))
    }

    private fun PathEffect.toNode(): PathEffectNode = when (this) {
        is PathEffect.Dash -> PathEffectNode.Dash(ImmutableFloats.copyOf(intervals.checked("path-effect.dash")), phase.checked("path-effect.phase"))
        is PathEffect.Corner -> PathEffectNode.Corner(radius.checked("path-effect.corner"))
        is PathEffect.Discrete -> PathEffectNode.Discrete(segmentLength.checked("path-effect.segment-length"), deviation.checked("path-effect.deviation"))
        is PathEffect.Path1D -> PathEffectNode.Path1D(path.toPathF32().checked("path-effect.path1d"), advance.checked("path-effect.advance"), phase.checked("path-effect.phase"), org.graphiks.kanvas.render.ir.Path1DStyle.valueOf(style.name))
        is PathEffect.Path2D -> PathEffectNode.Path2D(matrix.checked("path-effect.matrix"), path.toPathF32().checked("path-effect.path2d"))
        is PathEffect.Trim -> PathEffectNode.Trim(start.checked("path-effect.trim-start"), stop.checked("path-effect.trim-stop"))
    }

    private fun ImageFilter.toNode(
        captureImage: (Image) -> ImageResourceSnapshot,
        capturePicture: (org.graphiks.kanvas.picture.Picture) -> SceneSnapshot,
    ): ImageFilterNode = when (this) {
        is ImageFilter.Crop -> ImageFilterNode.Crop.of(crop.checked("image-filter.crop"), TileMode.valueOf(tileMode.name), input?.toNode(captureImage, capturePicture))
        is ImageFilter.Blur -> ImageFilterNode.Blur(sigmaX.checked("image-filter.sigma-x"), sigmaY.checked("image-filter.sigma-y"), TileMode.valueOf(tileMode.name), input?.toNode(captureImage, capturePicture))
        is ImageFilter.DropShadow -> ImageFilterNode.DropShadow(dx.checked("image-filter.dx"), dy.checked("image-filter.dy"), sigmaX.checked("image-filter.sigma-x"), sigmaY.checked("image-filter.sigma-y"), color, input?.toNode(captureImage, capturePicture))
        is ImageFilter.ColorFilter -> ImageFilterNode.ColorFilter(filter.toNode(captureImage), input?.toNode(captureImage, capturePicture))
        is ImageFilter.Compose -> ImageFilterNode.Compose(outer.toNode(captureImage, capturePicture), inner.toNode(captureImage, capturePicture))
        is ImageFilter.Blend -> ImageFilterNode.Blend(BlendMode.valueOf(mode.name), background.toNode(captureImage, capturePicture), foreground.toNode(captureImage, capturePicture))
        is ImageFilter.Dilate -> ImageFilterNode.Dilate(radiusX.checked("image-filter.radius-x"), radiusY.checked("image-filter.radius-y"), input?.toNode(captureImage, capturePicture))
        is ImageFilter.Erode -> ImageFilterNode.Erode(radiusX.checked("image-filter.radius-x"), radiusY.checked("image-filter.radius-y"), input?.toNode(captureImage, capturePicture))
        is ImageFilter.DistantLitDiffuse -> ImageFilterNode.DistantLitDiffuse(direction.x.checked("image-filter.direction-x"), direction.y.checked("image-filter.direction-y"), lightColor, surfaceScale.checked("image-filter.surface"), kd.checked("image-filter.kd"), input?.toNode(captureImage, capturePicture))
        is ImageFilter.PointLitDiffuse -> ImageFilterNode.PointLitDiffuse(location.checked("image-filter.location"), lightColor, surfaceScale.checked("image-filter.surface"), kd.checked("image-filter.kd"), input?.toNode(captureImage, capturePicture))
        is ImageFilter.SpotLitDiffuse -> ImageFilterNode.SpotLitDiffuse(location.checked("image-filter.location"), target.checked("image-filter.target"), specularExponent.checked("image-filter.exponent"), cutoffAngle.checked("image-filter.cutoff"), lightColor, surfaceScale.checked("image-filter.surface"), kd.checked("image-filter.kd"), input?.toNode(captureImage, capturePicture))
        is ImageFilter.DistantLitSpecular -> ImageFilterNode.DistantLitSpecular(direction.x.checked("image-filter.direction-x"), direction.y.checked("image-filter.direction-y"), lightColor, surfaceScale.checked("image-filter.surface"), ks.checked("image-filter.ks"), shininess.checked("image-filter.shininess"), input?.toNode(captureImage, capturePicture))
        is ImageFilter.PointLitSpecular -> ImageFilterNode.PointLitSpecular(location.checked("image-filter.location"), lightColor, surfaceScale.checked("image-filter.surface"), ks.checked("image-filter.ks"), shininess.checked("image-filter.shininess"), input?.toNode(captureImage, capturePicture))
        is ImageFilter.SpotLitSpecular -> ImageFilterNode.SpotLitSpecular(location.checked("image-filter.location"), target.checked("image-filter.target"), specularExponent.checked("image-filter.exponent"), cutoffAngle.checked("image-filter.cutoff"), lightColor, surfaceScale.checked("image-filter.surface"), ks.checked("image-filter.ks"), shininess.checked("image-filter.shininess"), input?.toNode(captureImage, capturePicture))
        is ImageFilter.Offset -> ImageFilterNode.Offset(dx.checked("image-filter.dx"), dy.checked("image-filter.dy"), input?.toNode(captureImage, capturePicture))
        is ImageFilter.Tile -> ImageFilterNode.Tile.of(src.checked("image-filter.src"), dst.checked("image-filter.dst"), input?.toNode(captureImage, capturePicture))
        is ImageFilter.Merge -> ImageFilterNode.Merge.of(inputs.map { it.toNode(captureImage, capturePicture) })
        is ImageFilter.DisplacementMap -> ImageFilterNode.DisplacementMap(ColorChannel.valueOf(xChannelSelector.name.replace("R", "RED").replace("G", "GREEN").replace("B", "BLUE").replace("A", "ALPHA")), ColorChannel.valueOf(yChannelSelector.name.replace("R", "RED").replace("G", "GREEN").replace("B", "BLUE").replace("A", "ALPHA")), scale.checked("image-filter.scale"), displacement.toNode(captureImage, capturePicture), input?.toNode(captureImage, capturePicture))
        is ImageFilter.Picture -> ImageFilterNode.Picture.of(capturePicture(picture), picture.cullRect.checked("image-filter.picture-cull"), src?.checked("image-filter.picture-src"))
        is ImageFilter.Magnifier -> ImageFilterNode.Magnifier.of(src.checked("image-filter.src"), zoom.checked("image-filter.zoom"), inset.checked("image-filter.inset"), input?.toNode(captureImage, capturePicture))
        is ImageFilter.MatrixConvolution -> ImageFilterNode.MatrixConvolution.of(kernelSize.checked("image-filter.kernel-size"), ImmutableFloats.copyOf(kernel.checked("image-filter.kernel")), gain.checked("image-filter.gain"), bias.checked("image-filter.bias"), kernelOffset.checked("image-filter.kernel-offset"), TileMode.valueOf(tileMode.name), convolveAlpha, input?.toNode(captureImage, capturePicture))
        is ImageFilter.RuntimeEffect -> ImageFilterNode.RuntimeEffect.of(
            effect.toDescriptor(
                RuntimeEffectAbi.IMAGE_FILTER,
                childImageFilters.keys.map { RuntimeChildSlot(it, RuntimeChildType.IMAGE_FILTER) } +
                    listOfNotNull(childShaderName?.let { RuntimeChildSlot(it, RuntimeChildType.SHADER) }),
            ),
            uniforms.toRuntimeUniforms(),
            childShaderName,
            childImageFilters.map { (name, child) -> RuntimeImageFilterChild(name, child?.toNode(captureImage, capturePicture)) },
        )
    }

    private fun Blender.toNode(): BlenderNode = when (this) {
        is Blender.Mode -> BlenderNode.Mode(BlendMode.valueOf(mode.name))
        is Blender.Arithmetic -> BlenderNode.Arithmetic(k1.checked("blender.k1"), k2.checked("blender.k2"), k3.checked("blender.k3"), k4.checked("blender.k4"))
    }

    private fun MaterialNode.toShader(): Shader = when (this) {
        MaterialNode.Transparent -> Shader.SolidColor(org.graphiks.math.color.ColorARGB.Transparent)
        is MaterialNode.Solid -> Shader.SolidColor(color)
        is MaterialNode.LinearGradient -> Shader.LinearGradient(start, end, stops().map { org.graphiks.kanvas.paint.GradientStop(it.position, it.color) }, org.graphiks.kanvas.paint.TileMode.valueOf(tileMode.name), org.graphiks.kanvas.paint.ColorSpaceInterpolation.valueOf(interpolation.name))
        is MaterialNode.RadialGradient -> Shader.RadialGradient(center, radius, stops().map { org.graphiks.kanvas.paint.GradientStop(it.position, it.color) }, org.graphiks.kanvas.paint.TileMode.valueOf(tileMode.name), org.graphiks.kanvas.paint.ColorSpaceInterpolation.valueOf(interpolation.name))
        is MaterialNode.SweepGradient -> Shader.SweepGradient(center, startAngle, endAngle, stops().map { org.graphiks.kanvas.paint.GradientStop(it.position, it.color) }, org.graphiks.kanvas.paint.TileMode.valueOf(tileMode.name), org.graphiks.kanvas.paint.ColorSpaceInterpolation.valueOf(interpolation.name))
        is MaterialNode.ConicalGradient -> Shader.ConicalGradient(start, startRadius, end, endRadius, stops().map { org.graphiks.kanvas.paint.GradientStop(it.position, it.color) }, org.graphiks.kanvas.paint.TileMode.valueOf(tileMode.name), org.graphiks.kanvas.paint.ColorSpaceInterpolation.valueOf(interpolation.name))
        is MaterialNode.ImageSample -> Shader.Image(ResourceSceneAdapter.toImage(image), org.graphiks.kanvas.paint.TileMode.valueOf(tileModeX.name), org.graphiks.kanvas.paint.TileMode.valueOf(tileModeY.name), sampling.toSampling())
        is MaterialNode.Blend -> Shader.Blend(org.graphiks.kanvas.paint.BlendMode.valueOf(mode.name), dst.toShader(), src.toShader())
        is MaterialNode.WithLocalMatrix -> Shader.WithLocalMatrix(material.toShader(), matrix)
        is MaterialNode.WithColorFilter -> Shader.WithColorFilter(material.toShader(), filter.toColorFilter())
        is MaterialNode.PerlinNoise -> Shader.PerlinNoise(baseX, baseY, numOctaves, seed, tileSize)
        is MaterialNode.FractalNoise -> Shader.FractalNoise(baseX, baseY, numOctaves, seed, tileSize)
        is MaterialNode.WithWorkingColorSpace -> Shader.WithWorkingColorSpace(material.toShader(), org.graphiks.kanvas.paint.ColorSpaceInterpolation.valueOf(interpolation.name))
        is MaterialNode.CoordClamp -> Shader.CoordClamp(material.toShader(), copySubset())
        is MaterialNode.Opacity -> Shader.Opacity(material.toShader(), alpha)
        is MaterialNode.RuntimeEffect -> Shader.RuntimeEffect(
            descriptor.registeredEffect(),
            uniforms().toUniformBlock(),
            associate { child -> child.name to child.material.toShader() },
            resources.toPublicResources(),
        )
    }

    private fun ColorFilterNode.toColorFilter(): ColorFilter = when (this) {
        is ColorFilterNode.Matrix -> ColorFilter.Matrix(ColorMatrixF32.of(values.copyToFloatArray()))
        is ColorFilterNode.Blend -> ColorFilter.Blend(color, org.graphiks.kanvas.paint.BlendMode.valueOf(mode.name))
        is ColorFilterNode.Compose -> ColorFilter.Compose(outer.toColorFilter(), inner.toColorFilter())
        is ColorFilterNode.Table -> ColorFilter.Table(table.copyToUByteArray())
        is ColorFilterNode.Lighting -> ColorFilter.Lighting(mul, add)
        ColorFilterNode.SRGBToLinear -> ColorFilter.SRGBToLinear
        ColorFilterNode.LinearToSRGB -> ColorFilter.LinearToSRGB
        is ColorFilterNode.HSLAMatrix -> ColorFilter.HSLAMatrix(values.copyToFloatArray())
        is ColorFilterNode.Lerp -> ColorFilter.Lerp(t, dst.toColorFilter(), src.toColorFilter())
        ColorFilterNode.HighContrast -> ColorFilter.HighContrast
        ColorFilterNode.Luma -> ColorFilter.Luma
        ColorFilterNode.Overdraw -> ColorFilter.Overdraw
        is ColorFilterNode.RuntimeEffect -> ColorFilter.RuntimeEffect(
            descriptor.registeredEffect(),
            uniforms().toUniformBlock(),
            associate { child -> child.name to child.filter.toColorFilter() },
        )
    }

    private fun MaskFilterNode.toMaskFilter(): MaskFilter = when (this) {
        is MaskFilterNode.Blur -> MaskFilter.Blur(org.graphiks.kanvas.pipeline.BlurStyle.valueOf(style.name), sigma)
        is MaskFilterNode.Shader -> MaskFilter.Shader(material.toShader())
        is MaskFilterNode.Table -> MaskFilter.Table(table.copyToUByteArray())
    }

    private fun PathEffectNode.toPathEffect(): PathEffect = when (this) {
        is PathEffectNode.Dash -> PathEffect.Dash(intervals.copyToFloatArray(), phase)
        is PathEffectNode.Corner -> PathEffect.Corner(radius)
        is PathEffectNode.Discrete -> PathEffect.Discrete(segmentLength, deviation)
        is PathEffectNode.Path1D -> PathEffect.Path1D(path.toCompatibilityPath(), advance, phase, org.graphiks.kanvas.paint.Path1DStyle.valueOf(style.name))
        is PathEffectNode.Path2D -> PathEffect.Path2D(matrix, path.toCompatibilityPath())
        is PathEffectNode.Trim -> PathEffect.Trim(start, stop)
    }

    private fun ImageFilterNode.toImageFilter(): ImageFilter = when (this) {
        is ImageFilterNode.Crop -> ImageFilter.Crop(copyCrop(), org.graphiks.kanvas.paint.TileMode.valueOf(tileMode.name), input?.toImageFilter())
        is ImageFilterNode.Blur -> ImageFilter.Blur(sigmaX, sigmaY, org.graphiks.kanvas.paint.TileMode.valueOf(tileMode.name), input?.toImageFilter())
        is ImageFilterNode.DropShadow -> ImageFilter.DropShadow(dx, dy, sigmaX, sigmaY, color, input?.toImageFilter())
        is ImageFilterNode.ColorFilter -> ImageFilter.ColorFilter(filter.toColorFilter(), input?.toImageFilter())
        is ImageFilterNode.Compose -> ImageFilter.Compose(outer.toImageFilter(), inner.toImageFilter())
        is ImageFilterNode.Blend -> ImageFilter.Blend(org.graphiks.kanvas.paint.BlendMode.valueOf(mode.name), background.toImageFilter(), foreground.toImageFilter())
        is ImageFilterNode.Dilate -> ImageFilter.Dilate(radiusX, radiusY, input?.toImageFilter())
        is ImageFilterNode.Erode -> ImageFilter.Erode(radiusX, radiusY, input?.toImageFilter())
        is ImageFilterNode.DistantLitDiffuse -> ImageFilter.DistantLitDiffuse(Vector2F32(directionX, directionY), lightColor, surfaceScale, kd, input?.toImageFilter())
        is ImageFilterNode.PointLitDiffuse -> ImageFilter.PointLitDiffuse(location, lightColor, surfaceScale, kd, input?.toImageFilter())
        is ImageFilterNode.SpotLitDiffuse -> ImageFilter.SpotLitDiffuse(location, target, specularExponent, cutoffAngle, lightColor, surfaceScale, kd, input?.toImageFilter())
        is ImageFilterNode.DistantLitSpecular -> ImageFilter.DistantLitSpecular(Vector2F32(directionX, directionY), lightColor, surfaceScale, ks, shininess, input?.toImageFilter())
        is ImageFilterNode.PointLitSpecular -> ImageFilter.PointLitSpecular(location, lightColor, surfaceScale, ks, shininess, input?.toImageFilter())
        is ImageFilterNode.SpotLitSpecular -> ImageFilter.SpotLitSpecular(location, target, specularExponent, cutoffAngle, lightColor, surfaceScale, ks, shininess, input?.toImageFilter())
        is ImageFilterNode.Offset -> ImageFilter.Offset(dx, dy, input?.toImageFilter())
        is ImageFilterNode.Tile -> ImageFilter.Tile(copySource(), copyDestination(), input?.toImageFilter())
        is ImageFilterNode.Merge -> ImageFilter.Merge(map { child -> child.toImageFilter() })
        is ImageFilterNode.DisplacementMap -> ImageFilter.DisplacementMap(org.graphiks.kanvas.paint.ColorChannel.valueOf(xChannelSelector.name.first().toString()), org.graphiks.kanvas.paint.ColorChannel.valueOf(yChannelSelector.name.first().toString()), scale, displacement.toImageFilter(), input?.toImageFilter())
        is ImageFilterNode.Picture -> ImageFilter.Picture(org.graphiks.kanvas.picture.Picture(copyCullRect(), SceneDisplayOpAdapter.toDisplayOps(scene)), copySource())
        is ImageFilterNode.Magnifier -> ImageFilter.Magnifier(copySource(), zoom, inset, input?.toImageFilter())
        is ImageFilterNode.MatrixConvolution -> ImageFilter.MatrixConvolution(kernelSize, kernel.copyToFloatArray(), gain, bias, kernelOffset, org.graphiks.kanvas.paint.TileMode.valueOf(tileMode.name), convolveAlpha, input?.toImageFilter())
        is ImageFilterNode.RuntimeEffect -> ImageFilter.RuntimeEffect(
            descriptor.registeredEffect(),
            uniforms().toUniformBlock(),
            childShaderName,
            associate { child -> child.name to child.filter?.toImageFilter() },
        )
    }

    private fun BlenderNode.toBlender(): Blender = when (this) {
        is BlenderNode.Mode -> Blender.Mode(org.graphiks.kanvas.paint.BlendMode.valueOf(mode.name))
        is BlenderNode.Arithmetic -> Blender.Arithmetic(k1, k2, k3, k4)
    }

    private fun SamplingOptions.toImageSampling(): ImageSampling = when (this) {
        SamplingOptions.NEAREST -> ImageSampling.Nearest
        SamplingOptions.LINEAR -> ImageSampling.Linear
        is SamplingOptions.Cubic -> ImageSampling.Cubic(B, C)
    }
    private fun ImageSampling.toSampling(): SamplingOptions = when (this) {
        ImageSampling.Nearest -> SamplingOptions.NEAREST
        ImageSampling.Linear -> SamplingOptions.LINEAR
        is ImageSampling.Cubic -> SamplingOptions.Cubic(b, c)
    }
    private fun validateMaterial(value: MaterialNode, limits: SceneCaptureLimits) {
        if (MaterialGraph.validate(value, limits.graphLimits) !is GraphValidationResult.Valid) throw CaptureFailure("material-graph-invalid", "Material graph exceeds configured bounds")
    }
    private fun validateEffect(value: EffectNode, limits: SceneCaptureLimits) {
        if (EffectGraph.validate(value, limits.graphLimits) !is GraphValidationResult.Valid) throw CaptureFailure("effect-graph-invalid", "Effect graph exceeds configured bounds")
    }

    private fun org.graphiks.kanvas.pipeline.RuntimeEffect.toDescriptor(
        abi: RuntimeEffectAbi,
        extraChildren: Collection<RuntimeChildSlot> = emptyList(),
    ): RuntimeEffectDescriptor {
        descriptor?.let { positive ->
            require(positive.abi == abi && extraChildren.isEmpty()) { "Runtime effect kind or children mismatch" }
            val builtin = requireNotNull(org.graphiks.kanvas.pipeline.RuntimeEffect.registered(id, semanticVersionI32))
            require(builtin.descriptor == positive && builtin.abiHash == abiHash)
            return positive
        }
        return RuntimeEffectDescriptor.of(
            id = RuntimeEffectId(id),
            abi = abi,
            uniformLayout = RuntimeUniformLayout.of(uniformLayout.slots.map { slot ->
                RuntimeUniformSlot(slot.name, slot.binding, RuntimeUniformType.valueOf(slot.type.name), slot.size)
            }),
            childSlots = children.map { slot ->
                extraChildren.firstOrNull { it.name == slot.name }
                    ?: RuntimeChildSlot(slot.name, abi.defaultChildType())
            } + extraChildren.filter { extra -> children.none { it.name == extra.name } },
            vertexLayout = RuntimeVertexLayout.of(
                stride = module.vertexLayout.stride,
                attributes = module.vertexLayout.attributes.map { attribute ->
                    RuntimeVertexAttribute(
                        format = RuntimeVertexFormat.valueOf(attribute.format.name.uppercase()),
                        offset = attribute.offset,
                        shaderLocation = attribute.shaderLocation,
                    )
                },
                stepMode = RuntimeVertexStepMode.valueOf(module.vertexLayout.stepMode.name),
            ),
            module = ShaderModuleDescriptor.of(
                source = module.source,
                entryPoint = module.entryPoint,
                uniforms = module.uniforms.map { slot ->
                    RuntimeUniformSlot(slot.name, slot.binding, RuntimeUniformType.valueOf(slot.type.name), slot.size)
                },
                textures = module.textures.map { slot -> RuntimeTextureSlot(slot.name, slot.binding) },
            ),
        )
    }

    private fun RuntimeEffectAbi.defaultChildType(): RuntimeChildType = when (this) {
        RuntimeEffectAbi.SHADER -> RuntimeChildType.SHADER
        RuntimeEffectAbi.COLOR_FILTER -> RuntimeChildType.COLOR_FILTER
        RuntimeEffectAbi.IMAGE_FILTER -> RuntimeChildType.IMAGE_FILTER
        RuntimeEffectAbi.BLENDER -> RuntimeChildType.BLENDER
    }

    private fun org.graphiks.kanvas.pipeline.UniformBlock.toRuntimeUniforms(): Map<String, RuntimeUniformValue> {
        var bytesI64 = 0L
        entries.values.forEach { value ->
            val sizeI64 = when (value) {
                is org.graphiks.kanvas.pipeline.UniformValue.F1, is org.graphiks.kanvas.pipeline.UniformValue.I1 -> 4L
                is org.graphiks.kanvas.pipeline.UniformValue.F2 -> 8L
                is org.graphiks.kanvas.pipeline.UniformValue.F3 -> 12L
                is org.graphiks.kanvas.pipeline.UniformValue.F4 -> 16L
                is org.graphiks.kanvas.pipeline.UniformValue.M3 -> 48L
                is org.graphiks.kanvas.pipeline.UniformValue.M4 -> 64L
            }
            bytesI64 = Math.addExact(bytesI64, sizeI64)
            require(bytesI64 <= 64L * 1024L * 1024L) { "Runtime uniform capture exceeds byte limit" }
        }
        return entries.mapValues { (_, value) ->
            when (value) {
                is org.graphiks.kanvas.pipeline.UniformValue.F1 -> RuntimeUniformValue.F1(value.v.checked("runtime.uniform"))
                is org.graphiks.kanvas.pipeline.UniformValue.F2 -> RuntimeUniformValue.F2(value.x.checked("runtime.uniform"), value.y.checked("runtime.uniform"))
                is org.graphiks.kanvas.pipeline.UniformValue.F3 -> RuntimeUniformValue.F3(value.x.checked("runtime.uniform"), value.y.checked("runtime.uniform"), value.z.checked("runtime.uniform"))
                is org.graphiks.kanvas.pipeline.UniformValue.F4 -> RuntimeUniformValue.F4(value.x.checked("runtime.uniform"), value.y.checked("runtime.uniform"), value.z.checked("runtime.uniform"), value.w.checked("runtime.uniform"))
                is org.graphiks.kanvas.pipeline.UniformValue.I1 -> RuntimeUniformValue.I1(value.v)
                is org.graphiks.kanvas.pipeline.UniformValue.M3 -> RuntimeUniformValue.M3(value.m.checked("runtime.uniform"))
                is org.graphiks.kanvas.pipeline.UniformValue.M4 -> RuntimeUniformValue.M4(value.values.checked("runtime.uniform"))
            }
        }
    }

    private fun Map<String, RuntimeUniformValue>.toUniformBlock(): org.graphiks.kanvas.pipeline.UniformBlock =
        org.graphiks.kanvas.pipeline.UniformBlock {
            this@toUniformBlock.forEach { (name, value) -> when (value) {
                is RuntimeUniformValue.F1 -> float1(name, value.value)
                is RuntimeUniformValue.F2 -> float2(name, value.x, value.y)
                is RuntimeUniformValue.F3 -> float3(name, value.x, value.y, value.z)
                is RuntimeUniformValue.F4 -> float4(name, value.x, value.y, value.z, value.w)
                is RuntimeUniformValue.I1 -> int1(name, value.value)
                is RuntimeUniformValue.M3 -> mat3x3(name, value.value)
                is RuntimeUniformValue.M4 -> mat4x4(name, value.copyValues())
            } }
        }

    private fun RuntimeEffectDescriptor.registeredEffect(): org.graphiks.kanvas.pipeline.RuntimeEffect {
        if (semanticVersionI32 > 0) return requireNotNull(
            org.graphiks.kanvas.pipeline.RuntimeEffect.registered(id.value, semanticVersionI32),
        ) { "Unknown runtime effect triplet" }.also {
            require(it.abiHash == abiHash && it.descriptor == this) { "Runtime effect ABI mismatch" }
        }
        val legacy = requireNotNull(legacyV0)
        val module = requireNotNull(legacy.module) { "Legacy runtime effect module is absent" }
        val vertex = legacy.vertexLayout
        return org.graphiks.kanvas.pipeline.RuntimeEffect.detached(
            id.value,
            org.graphiks.kanvas.pipeline.ShaderModule.of(module.source, module.entryPoint,
                module.uniforms().map { org.graphiks.kanvas.pipeline.UniformSlot(it.name, it.binding, org.graphiks.kanvas.pipeline.UniformType.valueOf(it.type.name), it.size) },
                module.textures().map { org.graphiks.kanvas.pipeline.TextureSlot(it.name, it.binding) },
                org.graphiks.kanvas.pipeline.VertexLayout(
                    vertex?.map { org.graphiks.kanvas.pipeline.VertexAttribute(
                        org.graphiks.kanvas.pipeline.VertexFormat.entries.first { format -> format.name.uppercase() == it.format.name },
                        it.offset, it.shaderLocation) } ?: emptyList(),
                    vertex?.stride ?: 0,
                    vertex?.let { org.graphiks.kanvas.pipeline.VertexStepMode.valueOf(it.stepMode.name) }
                        ?: org.graphiks.kanvas.pipeline.VertexStepMode.VERTEX)),
            org.graphiks.kanvas.pipeline.UniformLayout(legacy.uniformLayout.map {
                org.graphiks.kanvas.pipeline.UniformSlot(it.name, it.binding, org.graphiks.kanvas.pipeline.UniformType.valueOf(it.type.name), it.size) }),
            legacy.childSlots.map { org.graphiks.kanvas.pipeline.ChildSlot(it.name, org.graphiks.kanvas.pipeline.ChildType.valueOf(it.type.name)) },
        )
    }

    private fun org.graphiks.kanvas.pipeline.RuntimeEffectResourceBindings.toRuntimeResources(
        descriptor: RuntimeEffectDescriptor,
        captureImage: (Image) -> ImageResourceSnapshot,
    ): RuntimeEffectResourceBindingSetV1 {
        require(sizeI32 == descriptor.logicalResources.size) { "Runtime resource count mismatch" }
        var bytesI64 = 0L
        entries().forEach { (_, binding) ->
            val countI64 = when (binding) {
                is org.graphiks.kanvas.pipeline.RuntimeEffectResourceBinding.StorageRead -> binding.sizeBytesI32.toLong()
                is org.graphiks.kanvas.pipeline.RuntimeEffectResourceBinding.SampledTexture -> {
                    val pixels = requireNotNull(binding.image.pixels) { "Runtime sampled textures must be pixel-backed" }
                    val extentI64 = Math.multiplyExact(binding.image.rowBytesI32.toLong(), binding.image.height.toLong())
                    require(extentI64 in 0L..pixels.size.toLong()) { "Runtime sampled texture extent is invalid" }
                    pixels.size.toLong()
                }
                is org.graphiks.kanvas.pipeline.RuntimeEffectResourceBinding.Sampler -> 0L
            }
            bytesI64 = Math.addExact(bytesI64, countI64)
            require(bytesI64 in 0L..(64L * 1024L * 1024L)) { "Runtime resource capture exceeds byte limit" }
        }
        return RuntimeEffectResourceBindingSetV1.of(descriptor.logicalResources.map { slot ->
            val binding = requireNotNull(this[slot.name]) { "Missing runtime resource" }
            RuntimeEffectResourceBindingEntryV1(slot.logicalSlotI32, slot.name, when (binding) {
                is org.graphiks.kanvas.pipeline.RuntimeEffectResourceBinding.StorageRead ->
                    RuntimeEffectResourceBindingV1.StorageRead(ImmutableBytes.copyOf(binding.copyBytes()))
                is org.graphiks.kanvas.pipeline.RuntimeEffectResourceBinding.SampledTexture ->
                    RuntimeEffectResourceBindingV1.SampledTexture(requireNotNull(captureImage(binding.image) as? ImageResourceSnapshot.Pixels))
                is org.graphiks.kanvas.pipeline.RuntimeEffectResourceBinding.Sampler -> RuntimeEffectResourceBindingV1.Sampler(binding.type)
            })
        }).also { it.requireMatches(descriptor) }
    }

    private fun RuntimeEffectResourceBindingSetV1.toPublicResources(): org.graphiks.kanvas.pipeline.RuntimeEffectResourceBindings =
        org.graphiks.kanvas.pipeline.RuntimeEffectResourceBindings.of(associate { entry -> entry.name to when (val binding = entry.binding) {
            is RuntimeEffectResourceBindingV1.StorageRead -> org.graphiks.kanvas.pipeline.RuntimeEffectResourceBinding.StorageRead.copyOf(binding.bytes.copyToByteArray())
            is RuntimeEffectResourceBindingV1.SampledTexture -> org.graphiks.kanvas.pipeline.RuntimeEffectResourceBinding.SampledTexture(ResourceSceneAdapter.toImage(binding.image))
            is RuntimeEffectResourceBindingV1.Sampler -> org.graphiks.kanvas.pipeline.RuntimeEffectResourceBinding.Sampler(binding.type)
        } })
}

private fun FloatArray.checked(field: String): FloatArray { forEach { it.checked(field) }; return copyOf() }

/** Counts input metadata before any stop sequence is mapped into Render IR. */
internal class GradientStopCaptureBudget(private val maxGradientStopsI32: Int) {
    private var stopsI64 = 0L

    fun reserve(shader: Shader) {
        val countI32 = when (shader) {
            is Shader.LinearGradient -> shader.stops.size
            is Shader.RadialGradient -> shader.stops.size
            is Shader.SweepGradient -> shader.stops.size
            is Shader.ConicalGradient -> shader.stops.size
            else -> return
        }
        val requestedI64 = Math.addExact(stopsI64, countI32.toLong())
        if (requestedI64 > maxGradientStopsI32.toLong()) {
            throw CaptureFailure(
                "scene-capture-gradient-stops-exceeded",
                "Capture requests $requestedI64 gradient stops, exceeding limit $maxGradientStopsI32",
            )
        }
        stopsI64 = requestedI64
    }
}
