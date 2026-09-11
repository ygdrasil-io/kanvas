@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.plan.MaterialPlanTable
import org.graphiks.kanvas.gpu.plan.MaterialPlanEntry
import org.graphiks.kanvas.gpu.plan.MaterialPlanRef
import org.graphiks.kanvas.gpu.plan.MaterialProgramPlan
import org.graphiks.kanvas.gpu.plan.MaterialBindingPlan
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.CornerRadiiF32
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class W5bBlendSurfacePixelTest {
    @AfterEach fun disposeGpuRuntime() = GPUBackendRuntimeFactory.dispose()


    @Test fun `geometry Rect retains fixed DST and destination blends`() = geometryBlends(GeometryFamily.Rect)
    @Test fun `geometry fractional Rect retains fixed DST and destination blends`() = geometryBlends(GeometryFamily.FractionalRect)
    @Test fun `geometry RRect retains fixed DST and destination blends`() = geometryBlends(GeometryFamily.RRect)
    @Test fun `geometry direct Path retains fixed DST and destination blends`() = geometryBlends(GeometryFamily.DirectPath)
    @Test fun `geometry stencil Path retains fixed DST and destination blends`() = geometryBlends(GeometryFamily.StencilPath)
    @Test fun `geometry stroke retains fixed DST and destination blends`() = geometryBlends(GeometryFamily.Stroke)
    @Test fun `geometry hairline retains fixed DST and destination blends`() = geometryBlends(GeometryFamily.Hairline)

    @Test fun `geometry NoOp-only frames retain transparent target without a source write`() {
        assertAll(GeometryFamily.entries.map { family -> { geometryBlends(family, noOpOnly = true) } })
    }

    @Test fun `geometry mixed NoOp-only Picture has no source or geometry write`() {
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 4f, 4f))
        val paint = Paint(shader = Shader.Opacity(Shader.SolidColor(ColorARGB.White), .5f),
            blendMode = BlendMode.DST, antiAlias = true)
        canvas.drawRect(RectF32.ofLTRB(.25f, 0f, 4f, 4f), paint)
        canvas.drawRRect(RRectF32.of(RectF32.ofLTRB(.25f, .25f, 3.75f, 3.75f), CornerRadiiF32.of(.5f)), paint)
        canvas.drawPath(Path().apply { addRect(RectF32.ofLTRB(0f, 0f, 4f, 4f)) }, paint.copy(antiAlias = false))
        val picture = requireNotNull(Picture.fromByteArray(recorder.finishRecordingAsPicture().toByteArray()))
        val surface = Surface(4, 4)
        surface.canvas { picture.playback(this) }
        val clear = W5aSolidOpacityCpuOracle.draw(ColorARGB.Transparent, 0f)
        assertDisjoint(clear, WgslFloatEnvelopeV1Oracle.sourceOverExclusion(halfWhiteSource(), MaterialPlanRef(1),
            WgslFloatEnvelopeV1Oracle.clearAttachment(), .75f))
        WgslFloatEnvelopeV1Oracle.assertAdmits(clear, surface.render().pixels.copyOfRange(4, 8))
    }

    @Test fun `geometry mixed RRect Path fractional Rect preserves copies order and capture`() {
        val white = solidSource(ColorF32.of(1f, 1f, 1f, 1f), .45f)
        val green = W5aSolidOpacityCpuOracle.draw(ColorARGB.Green, 1f)
        val blue = W5aSolidOpacityCpuOracle.draw(ColorARGB.Blue, 1f)
        fun expected(background: WgslFloatEnvelopeV1Oracle.DrawResult, coverage: Float) =
            WgslFloatEnvelopeV1Oracle.drawDestination(white, MaterialPlanRef(1),
                requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(background)), BlendMode.DIFFERENCE, coverage)
        val top = expected(blue, .75f)
        val bottom = expected(green, 1f)
        listOf(top to blue, bottom to green).forEach { (actual, background) ->
            assertDisjoint(actual, WgslFloatEnvelopeV1Oracle.ConservativeExclusion(
                (background as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded).channels))
            assertDisjoint(actual, WgslFloatEnvelopeV1Oracle.sourceOverExclusion(white, MaterialPlanRef(1),
                requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(background)), if (background === blue) .75f else 1f))
        }
        assertDisjoint(top, WgslFloatEnvelopeV1Oracle.destinationExclusion(white, MaterialPlanRef(1),
            requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(green)), BlendMode.DIFFERENCE, .75f))
        fun picture(reverse: Boolean): Picture {
            val rect = RectF32.ofLTRB(.25f, .25f, 3.75f, 1f)
            val rounded = RRectF32.of(RectF32.ofLTRB(.25f, .25f, 3.75f, 3.75f), CornerRadiiF32.of(.5f))
            val path = Path().apply { addRect(RectF32.ofLTRB(0f, 0f, 4f, 1f)) }
            val recorder = PictureRecorder()
            val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 4f, 4f))
            val blend = Paint(shader = Shader.Opacity(Shader.SolidColor(ColorARGB.White), .45f),
                antiAlias = true, blendMode = BlendMode.DIFFERENCE)
            val draws = listOf<() -> Unit>(
                { canvas.drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), Paint(shader = Shader.SolidColor(ColorARGB.Green), antiAlias = true)) },
                { canvas.drawRRect(rounded, blend) },
                { canvas.drawPath(path, Paint(shader = Shader.SolidColor(ColorARGB.Blue), antiAlias = false, blendMode = BlendMode.SRC)) },
                { canvas.drawRRect(rounded, blend.copy(blendMode = BlendMode.DST)) },
                { canvas.drawRect(rect, blend) },
            )
            (if (reverse) draws.reversed() else draws).forEach { it() }
            rect.offset(8f, 8f)
            rounded.rect.offset(8f, 8f)
            path.addRect(RectF32.ofLTRB(0f, 2f, 4f, 3f))
            return requireNotNull(Picture.fromByteArray(recorder.finishRecordingAsPicture().toByteArray()))
        }
        fun pixels(reverse: Boolean) = Surface(4, 4).also { surface ->
            surface.canvas { picture(reverse).playback(this) }
        }.render().pixels
        val forward = pixels(false)
        WgslFloatEnvelopeV1Oracle.assertAdmits(top, forward.copyOfRange(4, 8))
        WgslFloatEnvelopeV1Oracle.assertAdmits(bottom, forward.copyOfRange(36, 40))
        val reverse = pixels(true)
        WgslFloatEnvelopeV1Oracle.assertAdmits(green, reverse.copyOfRange(4, 8))
        WgslFloatEnvelopeV1Oracle.assertAdmits(green, reverse.copyOfRange(36, 40))
    }

    @Test fun `geometry W5a fractional SrcOver Rect composes with W5b destination geometry`() {
        val background = W5aSolidOpacityCpuOracle.draw(ColorARGB.Green, 1f)
        val fringe = W5aSolidOpacityCpuOracle.draw(ColorARGB.Green, .75f)
        val source = halfWhiteSource()
        val destination = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(background))
        val expected = WgslFloatEnvelopeV1Oracle.drawDestination(source, MaterialPlanRef(1), destination, BlendMode.DIFFERENCE)
        val sourceOnly = W5aSolidOpacityCpuOracle.draw(ColorARGB.White, .5f)
        assertDisjoint(expected, WgslFloatEnvelopeV1Oracle.ConservativeExclusion(
            (background as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded).channels))
        assertDisjoint(expected, WgslFloatEnvelopeV1Oracle.sourceOverExclusion(source, MaterialPlanRef(1), destination))
        assertDisjoint(expected, WgslFloatEnvelopeV1Oracle.ConservativeExclusion(
            (sourceOnly as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded).channels))
        assertAll(listOf(false, true).map { rounded -> {
            fun pixels(reverse: Boolean = false, mutateRectBefore: Boolean = false): UByteArray {
                val rect = RectF32.ofLTRB(.25f, 0f, 4f, 4f)
                val rrect = RRectF32.of(RectF32.ofLTRB(0f, 0f, 4f, 2f), CornerRadiiF32.of(.5f))
                val path = Path().apply { moveTo(-1f, -1f); lineTo(5f, -1f); lineTo(-1f, 3f); close() }
                if (mutateRectBefore) rect.offset(2f, 0f)
                val recorder = PictureRecorder()
                val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 4f, 4f))
                fun background() = canvas.drawRect(rect, Paint(shader = Shader.SolidColor(ColorARGB.Green),
                    blendMode = BlendMode.SRC_OVER, antiAlias = true))
                fun foreground() {
                    val paint = Paint(shader = Shader.Opacity(Shader.SolidColor(ColorARGB.White), .5f),
                        blendMode = BlendMode.DIFFERENCE, antiAlias = rounded)
                    if (rounded) canvas.drawRRect(rrect, paint) else canvas.drawPath(path, paint)
                }
                if (reverse) { foreground(); background() } else { background(); foreground() }
                if (!mutateRectBefore) rect.offset(8f, 8f)
                rrect.rect.offset(8f, 8f)
                path.addRect(RectF32.ofLTRB(0f, 3f, 4f, 4f))
                val picture = requireNotNull(Picture.fromByteArray(recorder.finishRecordingAsPicture().toByteArray()))
                return Surface(4, 4).also { surface -> surface.canvas { picture.playback(this) } }.render().pixels
            }
            val forward = pixels()
            // (1.5,0.5) is fully covered by each foreground and the fractional Rect.
            WgslFloatEnvelopeV1Oracle.assertAdmits(expected, forward.copyOfRange(4, 8))
            // The foreground is absent at (0.5,3.5); only the Rect's three-quarter fringe remains.
            WgslFloatEnvelopeV1Oracle.assertAdmits(fringe, forward.copyOfRange(48, 52))
            WgslFloatEnvelopeV1Oracle.assertAdmits(background, pixels(reverse = true).copyOfRange(4, 8))
            WgslFloatEnvelopeV1Oracle.assertAdmits(sourceOnly, pixels(mutateRectBefore = true).copyOfRange(4, 8))
        } })
    }

    @Test fun `geometry transformed hard Paths retain final blends and captured math geometry`() = transformedGeometryBlends(false)

    @Test fun `geometry mixed transformed Paths retain distinct native lanes and final blends`() = transformedGeometryBlends(true)

    private fun transformedGeometryBlends(mixed: Boolean) {
        assertAll(listOf(GeometryFamily.DirectPath, GeometryFamily.StencilPath, GeometryFamily.Stroke, GeometryFamily.Hairline).flatMap { family ->
            listOf(BlendMode.DST_OUT, BlendMode.DST, BlendMode.DIFFERENCE).map { mode -> {
                val opacity = if (mode == BlendMode.DIFFERENCE) .45f else .5f
                val background = W5aSolidOpacityCpuOracle.draw(ColorARGB.Green, 1f)
                val destination = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(background))
                val white = solidSource(ColorF32.of(1f, 1f, 1f, 1f), opacity)
                val expected = when (mode) {
                    BlendMode.DST -> background
                    BlendMode.DST_OUT -> W5aSolidOpacityCpuOracle.draw(ColorARGB.Green, .5f)
                    else -> WgslFloatEnvelopeV1Oracle.drawDestination(white, MaterialPlanRef(1), destination, mode)
                }
                assertDisjoint(expected, WgslFloatEnvelopeV1Oracle.sourceOverExclusion(white, MaterialPlanRef(1), destination))
                if (mode != BlendMode.DST) assertDisjoint(expected, WgslFloatEnvelopeV1Oracle.ConservativeExclusion(
                    (background as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded).channels))
                fun record(reverse: Boolean, mutateBefore: Boolean): Picture {
                    val source = Path().apply { when (family) {
                        GeometryFamily.DirectPath -> { moveTo(-1f, -1f); lineTo(5f, -1f); lineTo(-1f, 5f); close() }
                        GeometryFamily.StencilPath -> { moveTo(-1f, -1f); lineTo(5f, -1f); lineTo(5f, 5f); lineTo(2f, 2f); lineTo(-1f, 5f); close() }
                        else -> { moveTo(-1f, .5f); lineTo(5f, .5f) }
                    } }
                    fun mutate() {
                        if (family in setOf(GeometryFamily.Stroke, GeometryFamily.Hairline)) {
                            source.moveTo(-1f, 3.5f); source.lineTo(5f, 3.5f)
                        } else source.addRect(RectF32.ofLTRB(0f, 3f, 4f, 4f))
                    }
                    if (mutateBefore) mutate()
                    val recorder = PictureRecorder()
                    val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 4f, 4f))
                    fun foreground() {
                        canvas.save()
                        canvas.concat(Matrix3x3F32.skewing(.25f, 0f))
                        canvas.drawPath(source, Paint(
                        shader = Shader.Opacity(Shader.SolidColor(ColorARGB.White), opacity), antiAlias = false,
                        blendMode = mode, style = if (family in setOf(GeometryFamily.Stroke, GeometryFamily.Hairline)) PaintStyle.STROKE else PaintStyle.FILL,
                        strokeWidth = if (family == GeometryFamily.Hairline) 0f else 1f))
                        canvas.restore()
                        canvas.resetMatrix()
                    }
                    fun background() {
                        val paint = Paint(shader = Shader.SolidColor(ColorARGB.Green), antiAlias = false)
                        if (mixed) canvas.drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), paint)
                        else canvas.drawPath(Path().apply { addRect(RectF32.ofLTRB(-10f, -10f, 10f, 10f)) }, paint)
                    }
                    if (reverse) { foreground(); background() } else { background(); foreground() }
                    if (!mutateBefore) mutate()
                    return requireNotNull(Picture.fromByteArray(recorder.finishRecordingAsPicture().toByteArray()))
                }
                fun pixels(reverse: Boolean = false, mutateBefore: Boolean = false) = Surface(4, 4).also { surface ->
                    surface.canvas { record(reverse, mutateBefore).playback(this) }
                }.render().pixels
                WgslFloatEnvelopeV1Oracle.assertAdmits(expected, pixels().copyOfRange(0, 4))
                if (mode != BlendMode.DST) {
                    WgslFloatEnvelopeV1Oracle.assertAdmits(background, pixels(reverse = true).copyOfRange(0, 4))
                    // At (2.5,3.5), inverse-skew x=1.625: original paths are outside,
                    // while each appended rectangle/line covers the sample.
                    WgslFloatEnvelopeV1Oracle.assertAdmits(background, pixels().copyOfRange(56, 60))
                    WgslFloatEnvelopeV1Oracle.assertAdmits(expected, pixels(mutateBefore = true).copyOfRange(56, 60))
                }
            } }
        })
    }

    @Test fun `geometry transformed NoOp preserves the authentic AA4 refusal and recovery`() {
        val path = Path().apply { moveTo(-1f, -1f); lineTo(5f, -1f); lineTo(-1f, 5f); close() }
        val white = solidSource(ColorF32.of(1f, 1f, 1f, 1f), .5f)
        val clear = W5aSolidOpacityCpuOracle.draw(ColorARGB.Transparent, 0f)
        assertDisjoint(clear, WgslFloatEnvelopeV1Oracle.sourceOverExclusion(white, MaterialPlanRef(1),
            WgslFloatEnvelopeV1Oracle.clearAttachment()))
        fun surface(mode: BlendMode, aa: Boolean) = Surface(4, 4).also {
            it.canvas {
                concat(Matrix3x3F32.skewing(.25f, 0f))
                drawPath(path, Paint(shader = Shader.Opacity(Shader.SolidColor(ColorARGB.White), .5f),
                    blendMode = mode, antiAlias = aa))
            }
        }
        WgslFloatEnvelopeV1Oracle.assertAdmits(clear, surface(BlendMode.DST, false).render().pixels.copyOfRange(0, 4))
        for (mode in listOf(BlendMode.DST, BlendMode.DIFFERENCE)) {
            val failure = assertFailsWith<org.graphiks.kanvas.surface.gpu.GPUPlanSurfaceTerminalException> {
                surface(mode, true).render()
            }
            kotlin.test.assertEquals("w4d.general.texture-sample-support-unavailable", failure.code)
        }
        val expected = W5aSolidOpacityCpuOracle.draw(ColorARGB.White, .5f)
        assertDisjoint(expected, WgslFloatEnvelopeV1Oracle.ConservativeExclusion(
            (clear as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded).channels))
        WgslFloatEnvelopeV1Oracle.assertAdmits(expected,
            surface(BlendMode.SRC, false).render().pixels.copyOfRange(0, 4))
    }

    @Test fun `geometry W4e clipped and inverse Paths retain final blends and captured coverage`() = w4eGeometryBlends(1f)

    @Test fun `geometry W4e scalar mask retains three quarter final blend interpolation`() = w4eGeometryBlends(.75f)

    private fun w4eGeometryBlends(coverage: Float) {
        // The original 2x2 AA producer leaves three samples, stored as R8 code191.
        val storedCoverage = if (coverage == 1f) 1f else 191f / 255f
        assertAll((if (coverage == 1f) listOf(false, true) else listOf(false)).flatMap { inverse ->
            listOf(BlendMode.DST_OUT, BlendMode.DST, BlendMode.DIFFERENCE).map { mode -> {
                val opacity = if (mode == BlendMode.DIFFERENCE) .45f else if (coverage == 1f) .5f else .75f
                val background = W5aSolidOpacityCpuOracle.draw(ColorARGB.Green, 1f)
                val destination = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(background))
                val white = solidSource(ColorF32.of(1f, 1f, 1f, 1f), opacity)
                val expected = when (mode) {
                    BlendMode.DST -> background
                    BlendMode.DST_OUT -> W5aSolidOpacityCpuOracle.draw(ColorARGB.Green, 1f - opacity * storedCoverage)
                    else -> WgslFloatEnvelopeV1Oracle.drawDestination(white, MaterialPlanRef(1), destination, mode, storedCoverage)
                }
                assertDisjoint(expected, WgslFloatEnvelopeV1Oracle.sourceOverExclusion(white, MaterialPlanRef(1), destination, storedCoverage))
                if (mode != BlendMode.DST) assertDisjoint(expected, WgslFloatEnvelopeV1Oracle.ConservativeExclusion(
                    (background as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded).channels))
                if (coverage != 1f && mode != BlendMode.DST) assertDisjoint(expected,
                    if (mode == BlendMode.DST_OUT) WgslFloatEnvelopeV1Oracle.sourceOverExclusion(
                        solidSource(ColorF32.of(0f, 1f, 0f, 1f), 1f - opacity), MaterialPlanRef(1),
                        WgslFloatEnvelopeV1Oracle.clearAttachment())
                    else WgslFloatEnvelopeV1Oracle.destinationExclusion(white, MaterialPlanRef(1), destination, mode))
                fun pixels(reverse: Boolean = false, mutateBefore: Boolean = false): UByteArray {
                    val path = Path().apply {
                        addRect(if (inverse) RectF32.ofLTRB(1f, 1f, 3f, 3f) else RectF32.ofLTRB(-1f, -1f, 5f, 5f))
                        if (inverse) fillType = org.graphiks.kanvas.geometry.FillType.INVERSE_WINDING
                    }
                    val clip = Path().apply { moveTo(0f, 0f); lineTo(4f, 0f); lineTo(0f, 4f); close() }
                    fun mutate() {
                        if (inverse) path.addRect(RectF32.ofLTRB(0f, 0f, 1f, 1f))
                        else path.fillType = org.graphiks.kanvas.geometry.FillType.INVERSE_WINDING
                    }
                    if (mutateBefore) mutate()
                    val recorder = PictureRecorder()
                    recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 4f, 4f)).drawPath(path,
                        Paint(shader = Shader.Opacity(Shader.SolidColor(ColorARGB.White), opacity),
                            blendMode = mode, antiAlias = false))
                    if (!mutateBefore) mutate()
                    val picture = requireNotNull(Picture.fromByteArray(recorder.finishRecordingAsPicture().toByteArray()))
                    val surface = Surface(4, 4)
                    surface.canvas {
                        fun foreground() {
                            save()
                            if (!inverse) clipPath(clip, antiAlias = false)
                            if (coverage != 1f) clipRect(RectF32.ofLTRB(.5f, .5f, 4f, 4f), org.graphiks.kanvas.pipeline.ClipOp.DIFFERENCE, antiAlias = true)
                            picture.playback(this)
                            restore()
                        }
                        fun background() = drawPath(Path().apply { addRect(RectF32.ofLTRB(-1f, -1f, 5f, 5f)) },
                            Paint(shader = Shader.SolidColor(ColorARGB.Green), antiAlias = false))
                        if (reverse) { foreground(); background() } else { background(); foreground() }
                    }
                    clip.addRect(RectF32.ofLTRB(3f, 3f, 4f, 4f))
                    return surface.render().pixels
                }
                val actual = pixels()
                try { WgslFloatEnvelopeV1Oracle.assertAdmits(expected, actual.copyOfRange(0, 4)) }
                catch (failure: IllegalArgumentException) {
                    throw AssertionError("inverse=$inverse mode=$mode", failure)
                }
                if (mode != BlendMode.DST) {
                    WgslFloatEnvelopeV1Oracle.assertAdmits(background, pixels(reverse = true).copyOfRange(0, 4))
                    val holeI32 = if (inverse) 40 else 60
                    WgslFloatEnvelopeV1Oracle.assertAdmits(background, pixels().copyOfRange(holeI32, holeI32 + 4))
                    WgslFloatEnvelopeV1Oracle.assertAdmits(background, pixels(mutateBefore = true).copyOfRange(0, 4))
                }
            } }
        })
    }

    private enum class GeometryFamily { Rect, FractionalRect, RRect, DirectPath, StencilPath, Stroke, Hairline }

    /** Catches lost blend/coverage, stale destination, reordered draws and mutable geometry reuse. */
    private fun geometryBlends(family: GeometryFamily, noOpOnly: Boolean = false) {
        assertAll((if (noOpOnly) listOf(BlendMode.DST) else listOf(BlendMode.DST_OUT, BlendMode.DST, BlendMode.DIFFERENCE)).map { mode -> {
            val coverage = if (family in setOf(GeometryFamily.FractionalRect, GeometryFamily.RRect)) .75f else 1f
            val opacity = if (mode == BlendMode.DIFFERENCE) .45f else if (coverage != 1f) .75f else .5f
            val x = if (family == GeometryFamily.RRect) 1 else 0
            val background = if (noOpOnly) W5aSolidOpacityCpuOracle.draw(ColorARGB.Transparent, 0f)
                else W5aSolidOpacityCpuOracle.draw(ColorARGB.Green, 1f)
            val state = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(background))
            val material = solidSource(ColorF32.of(1f, 1f, 1f, 1f), opacity)
            val expected = when (mode) {
                BlendMode.DST -> background
                // Green is an exact stored endpoint; all products here are binary exact.
                BlendMode.DST_OUT -> W5aSolidOpacityCpuOracle.draw(ColorARGB.Green, 1f - opacity * coverage)
                else -> WgslFloatEnvelopeV1Oracle.drawDestination(material, MaterialPlanRef(1), state, mode, coverage)
            }
            assertDisjoint(expected, WgslFloatEnvelopeV1Oracle.sourceOverExclusion(material, MaterialPlanRef(1), state, coverage))
            if (mode != BlendMode.DST) assertDisjoint(expected,
                WgslFloatEnvelopeV1Oracle.ConservativeExclusion((background as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded).channels))
            if (coverage != 1f && mode != BlendMode.DST) {
                val full = if (mode == BlendMode.DST_OUT) WgslFloatEnvelopeV1Oracle.sourceOverExclusion(
                    solidSource(ColorF32.of(0f, 1f, 0f, 1f), 1f - opacity), MaterialPlanRef(1),
                    WgslFloatEnvelopeV1Oracle.clearAttachment())
                    else WgslFloatEnvelopeV1Oracle.destinationExclusion(material, MaterialPlanRef(1), state, mode)
                assertDisjoint(expected, full)
            }
            fun recordGeometry(reverse: Boolean, mutateBefore: Boolean = false): Picture {
                val rect = RectF32.ofLTRB(if (coverage == 1f) 0f else .25f, 0f, 2f, 2f)
                val rounded = RRectF32.of(RectF32.ofLTRB(.25f, .25f, 3.75f, 3.75f), CornerRadiiF32.of(.5f))
                val path = Path().apply {
                    when (family) {
                        GeometryFamily.DirectPath -> { moveTo(-1f, -1f); lineTo(5f, -1f); lineTo(-1f, 5f); close() }
                        GeometryFamily.StencilPath -> { moveTo(-1f, -1f); lineTo(5f, -1f); lineTo(5f, 5f); lineTo(2f, 2f); lineTo(-1f, 5f); close() }
                        else -> { moveTo(-1f, .5f); lineTo(5f, .5f) }
                    }
                }
                fun mutate() {
                    rect.offset(8f, 8f)
                    rounded.rect.offset(8f, 8f)
                    if (family in setOf(GeometryFamily.Stroke, GeometryFamily.Hairline)) {
                        path.moveTo(-1f, 3.5f); path.lineTo(5f, 3.5f)
                    } else path.addRect(RectF32.ofLTRB(0f, 3f, 4f, 4f))
                }
                if (mutateBefore) mutate()
                val recorder = PictureRecorder()
                val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 4f, 4f))
                val sourcePaint = Paint(shader = Shader.Opacity(Shader.SolidColor(ColorARGB.White), opacity),
                    blendMode = mode, antiAlias = family in setOf(GeometryFamily.FractionalRect, GeometryFamily.RRect),
                    style = if (family in setOf(GeometryFamily.Stroke, GeometryFamily.Hairline)) PaintStyle.STROKE else PaintStyle.FILL,
                    strokeWidth = if (family == GeometryFamily.Hairline) 0f else 1f)
                fun source() = when (family) {
                    GeometryFamily.Rect, GeometryFamily.FractionalRect -> canvas.drawRect(rect, sourcePaint)
                    GeometryFamily.RRect -> canvas.drawRRect(rounded, sourcePaint)
                    else -> canvas.drawPath(path, sourcePaint)
                }
                fun destination() = canvas.drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f),
                    Paint(shader = Shader.SolidColor(ColorARGB.Green), antiAlias = true))
                if (noOpOnly) source() else if (reverse) { source(); destination() } else { destination(); source() }
                if (!mutateBefore) mutate()
                return requireNotNull(Picture.fromByteArray(recorder.finishRecordingAsPicture().toByteArray()))
            }
            fun pixel(picture: Picture, offset: Int = x * 4): UByteArray = Surface(4, 4).also { surface ->
                surface.canvas { picture.playback(this) }
            }.render().pixels.copyOfRange(offset, offset + 4)
            WgslFloatEnvelopeV1Oracle.assertAdmits(expected, pixel(recordGeometry(false)))
            if (mode != BlendMode.DST) {
                WgslFloatEnvelopeV1Oracle.assertAdmits(background, pixel(recordGeometry(true)))
                if (family in setOf(GeometryFamily.Rect, GeometryFamily.FractionalRect, GeometryFamily.RRect))
                    WgslFloatEnvelopeV1Oracle.assertAdmits(background, pixel(recordGeometry(false, mutateBefore = true)))
                if (family in setOf(GeometryFamily.DirectPath, GeometryFamily.StencilPath, GeometryFamily.Stroke, GeometryFamily.Hairline)) {
                    WgslFloatEnvelopeV1Oracle.assertAdmits(background, pixel(recordGeometry(false), 52))
                    WgslFloatEnvelopeV1Oracle.assertAdmits(expected, pixel(recordGeometry(false, mutateBefore = true), 52))
                }
            }
        } })
    }

    @Test
    fun `Surface W5b point does not hide a later round point refusal`() {
        val surface = Surface(4, 4)
        surface.canvas {
            drawPoint(1f, 1f, Paint(shader = Shader.Opacity(Shader.SolidColor(ColorARGB.White), .5f),
                antiAlias = false, blendMode = BlendMode.PLUS))
            drawPoint(2f, 2f, Paint(shader = Shader.Opacity(Shader.SolidColor(ColorARGB.Red), .5f),
                strokeCap = StrokeCap.ROUND, strokeWidth = 2f, antiAlias = false))
        }
        val failure = assertFailsWith<IllegalStateException> { surface.render() }
        assertTrue(failure.message.orEmpty().contains("unsupported.core_primitive.point.round_cap_exact_lowering"), failure.message)
    }

    @Test
    fun `Surface HUE points retain bounded zero and near black source pixels`() {
        val destination = W5bBlendCpuOracle.Draw(ColorARGB.Red, 1f, BlendMode.SRC_OVER)
        for (source in listOf(W5bBlendCpuOracle.Draw(ColorARGB.Black, .000001f, BlendMode.HUE),
            W5bBlendCpuOracle.Draw(ColorARGB.of(255, 1, 0, 0), .000001f, BlendMode.HUE))) {
            val surface = Surface(4, 4)
            surface.canvas {
                drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), Paint(shader = Shader.SolidColor(destination.color), antiAlias = false))
                drawPoint(2f, 2f, Paint(shader = Shader.Opacity(Shader.SolidColor(source.color), source.opacityF32),
                    antiAlias = false, blendMode = source.mode))
            }
            W5bBlendCpuOracle.assertPoint(source, destination, 1f, surface.render().pixels.copyOfRange(40, 44))
        }
    }

    @Test
    fun `Picture replays fixed function SRC IN Solid Opacity in recorded order`() {
        val draws = listOf(
            W5bBlendCpuOracle.Draw(ColorARGB.Red, 1f, BlendMode.SRC_OVER),
            W5bBlendCpuOracle.Draw(ColorARGB.Black, .5f, BlendMode.SRC_IN),
        )
        // Forward: half-alpha black SRC_IN opaque red. Reverse: SRC_IN clears,
        // then the same red SRC_OVER draw covers it. Both use the public replay path.
        val actual = render(record(draws))
        val reversedActual = render(record(draws.reversed()))
        W5bBlendCpuOracle.assertOrder(draws, actual, reversedActual)
    }

    @Test
    fun `Picture replays DST NoOp Solid Opacity`() {
        val background = ColorARGB.White
        val picture = record(listOf(
            W5bBlendCpuOracle.Draw(background, .5f, BlendMode.SRC_OVER),
            W5bBlendCpuOracle.Draw(ColorARGB.Blue, 1f, BlendMode.DST),
        ))
        W5bBlendCpuOracle.assertDst(background, .5f, render(picture))
    }

    @Test
    fun `Surface DST only returns clear without admitting its unused source`() {
        val source = Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(4f, 0f),
            listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)))
        val surface = Surface(4, 4, config = RenderConfig(frameLocalBudgetBytes = 1088L))
        surface.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f),
            Paint(shader = source, antiAlias = false, blendMode = BlendMode.DST)) }
        val pixels = surface.render().pixels
        require(pixels.contentEquals(UByteArray(4 * 4 * 4)))
    }

    @Test
    fun `Picture multiply is disjoint from SRC OVER for the same captured source`() {
        val source = ColorARGB.of(255, 120, 0, 0)
        val destination = ColorARGB.Green
        val picture = record(listOf(
            W5bBlendCpuOracle.Draw(destination, 1f, BlendMode.SRC_OVER),
            W5bBlendCpuOracle.Draw(source, .75f, BlendMode.MULTIPLY),
        ))
        val background = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(W5aSolidOpacityCpuOracle.draw(destination, 1f)))
        val material = solidSource(ColorF32.of(120f / 255f, 0f, 0f, 1f), .75f)
        val expected = WgslFloatEnvelopeV1Oracle.drawDestination(material, MaterialPlanRef(1), background, BlendMode.MULTIPLY)
        assertDisjoint(expected, WgslFloatEnvelopeV1Oracle.sourceOverExclusion(material, MaterialPlanRef(1), background))
        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, render(picture))
    }

    @Test
    fun `Picture difference is disjoint from SRC OVER for the same captured source`() {
        val source = ColorARGB.White
        val destination = ColorARGB.Green
        val picture = record(listOf(
            W5bBlendCpuOracle.Draw(destination, 1f, BlendMode.SRC_OVER),
            W5bBlendCpuOracle.Draw(source, .45f, BlendMode.DIFFERENCE),
        ))
        val background = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(W5aSolidOpacityCpuOracle.draw(destination, 1f)))
        val material = solidSource(ColorF32.of(1f, 1f, 1f, 1f), .45f)
        val expected = WgslFloatEnvelopeV1Oracle.drawDestination(material, MaterialPlanRef(1), background, BlendMode.DIFFERENCE)
        assertDisjoint(expected, WgslFloatEnvelopeV1Oracle.sourceOverExclusion(material, MaterialPlanRef(1), background))
        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, render(picture))
    }

    @Test
    fun `Picture destination read first draw observes transparent initial target`() {
        val expected = WgslFloatEnvelopeV1Oracle.drawDestination(halfWhiteSource(), MaterialPlanRef(1),
            WgslFloatEnvelopeV1Oracle.clearAttachment(), BlendMode.MULTIPLY)
        val picture = record(listOf(W5bBlendCpuOracle.Draw(ColorARGB.White, .5f, BlendMode.MULTIPLY)))
        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, render(picture))
    }

    @Test
    fun `Picture destination read observes write between two snapshots`() {
        val picture = record(listOf(
            W5bBlendCpuOracle.Draw(ColorARGB.Blue, 1f, BlendMode.SRC_OVER),
            W5bBlendCpuOracle.Draw(ColorARGB.of(255, 120, 0, 0), .5f, BlendMode.MULTIPLY),
            W5bBlendCpuOracle.Draw(ColorARGB.Green, 1f, BlendMode.SRC),
            W5bBlendCpuOracle.Draw(ColorARGB.Red, 1f, BlendMode.DST),
            W5bBlendCpuOracle.Draw(ColorARGB.White, .45f, BlendMode.DIFFERENCE),
        ))
        val material = solidSource(ColorF32.of(1f, 1f, 1f, 1f), .45f)
        val green = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(W5aSolidOpacityCpuOracle.draw(ColorARGB.Green, 1f)))
        val blue = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(W5aSolidOpacityCpuOracle.draw(ColorARGB.Blue, 1f)))
        val expected = WgslFloatEnvelopeV1Oracle.drawDestination(material, MaterialPlanRef(1), green, BlendMode.DIFFERENCE)
        val stale = WgslFloatEnvelopeV1Oracle.drawDestination(material, MaterialPlanRef(1), blue, BlendMode.DIFFERENCE)
        check(stale is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded)
        assertDisjoint(expected, WgslFloatEnvelopeV1Oracle.ConservativeExclusion(stale.channels))
        assertDisjoint(expected, WgslFloatEnvelopeV1Oracle.sourceOverExclusion(material, MaterialPlanRef(1), green))
        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, render(picture))
    }

    @Test
    fun `Surface destination read preserves integral scissor and outside destination`() {
        val surface = Surface(4, 4)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), Paint(shader = Shader.SolidColor(ColorARGB.Green), antiAlias = false))
            clipRect(RectF32.ofLTRB(1f, 1f, 3f, 3f), antiAlias = false)
            drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), Paint(shader = Shader.Opacity(Shader.SolidColor(ColorARGB.White), .45f),
                antiAlias = false, blendMode = BlendMode.DIFFERENCE))
        }
        val pixels = surface.render().pixels
        val green = W5aSolidOpacityCpuOracle.draw(ColorARGB.Green, 1f)
        val background = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(green))
        val material = solidSource(ColorF32.of(1f, 1f, 1f, 1f), .45f)
        val inside = WgslFloatEnvelopeV1Oracle.drawDestination(material, MaterialPlanRef(1), background, BlendMode.DIFFERENCE)
        assertDisjoint(inside, WgslFloatEnvelopeV1Oracle.sourceOverExclusion(material, MaterialPlanRef(1), background))
        for (yI32 in 0 until 4) for (xI32 in 0 until 4) {
            val offsetI32 = (yI32 * 4 + xI32) * 4
            try {
                WgslFloatEnvelopeV1Oracle.assertAdmits(if (xI32 in 1..2 && yI32 in 1..2) inside else green,
                    pixels.copyOfRange(offsetI32, offsetI32 + 4))
            } catch (failure: IllegalArgumentException) {
                throw AssertionError("Pixel ($xI32,$yI32): ${failure.message}", failure)
            }
        }
    }

    private fun halfWhiteSource() = halfSolidSource(ColorF32.of(1f, 1f, 1f, 1f))

    private fun halfSolidSource(color: ColorF32) = solidSource(color, .5f)

    private fun assertDisjoint(expected: WgslFloatEnvelopeV1Oracle.DrawResult, counterfactual: WgslFloatEnvelopeV1Oracle.ConservativeExclusion) {
        check(expected is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { "Expected: $expected" }
        check(expected.channels.zip(counterfactual.channels).any { (a, b) -> a.intersect(b).isEmpty() }) {
            "Counterfactual overlaps: ${expected.channels} versus ${counterfactual.channels}"
        }
    }

    private fun solidSource(color: ColorF32, opacityF32: Float) = MaterialPlanTable.of(listOf(
        MaterialPlanEntry(MaterialProgramPlan.SolidLinearPremulV1,
            MaterialBindingPlan.SolidRgbaF32V1.of(color)),
        MaterialPlanEntry(MaterialProgramPlan.OpacityV1(MaterialProgramPlan.SolidLinearPremulV1),
            MaterialBindingPlan.OpacityF32V1.of(opacityF32)),
    ))

    private fun render(picture: Picture): UByteArray = Surface(4, 4).also { surface ->
        surface.canvas { requireNotNull(Picture.fromByteArray(picture.toByteArray())).playback(this) }
    }.render().pixels.copyOfRange(0, 4)

    private fun record(draws: List<W5bBlendCpuOracle.Draw>) = PictureRecorder().also { recorder ->
        recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 4f, 4f)).apply {
            for (draw in draws) {
                drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), Paint(shader = Shader.Opacity(Shader.SolidColor(draw.color), draw.opacityF32), antiAlias = false, blendMode = draw.mode))
            }
        }
    }.finishRecordingAsPicture()
}
