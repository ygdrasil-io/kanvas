package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUFirstSliceCapabilityName
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSourceKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Blender
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32

class GPUPreparedStrokeRectLowererTest {
    @Test
    fun `eligible public stroke rect expands to four device fill bands with contiguous identity`() {
        val clip = ClipStack.DeviceRect(RectF32.ofLTRB(12f, 14f, 54f, 54f), antiAlias = false)
        val lowered = assertIs<GPUPreparedStrokeRectLowering.Ready>(
            GPUPreparedStrokeRectLowerer.lower(
                operation = strokeRect(
                    paint = strokePaint.copy(
                        colorFilter = ColorFilter.Blend(ColorARGB.Blue, org.graphiks.kanvas.paint.BlendMode.SRC),
                        blendMode = org.graphiks.kanvas.paint.BlendMode.SRC,
                    ),
                    transform = Matrix3x3F32.translation(2f, 4f),
                    clip = clip,
                ),
                firstCommandId = GPUDrawCommandID(4),
                firstPaintOrder = 4,
                provenance = GPUFrameProvenance.GmContent,
                target = target(),
                config = RenderConfig.DEFAULT,
                capabilities = capabilities(),
            ),
        )

        val commands = lowered.commands
        assertEquals(4, commands.size)
        assertEquals(listOf(4, 5, 6, 7), commands.map { it.normalized.commandId.value })
        assertEquals(listOf(4, 5, 6, 7), commands.map { it.normalized.ordering.paintOrder })
        assertEquals(
            listOf(
                GPUPixelBounds(15, 17, 53, 23),
                GPUPixelBounds(15, 49, 53, 55),
                GPUPixelBounds(15, 23, 21, 49),
                GPUPixelBounds(47, 23, 53, 49),
            ),
            commands.map { command ->
                val fill = assertIs<NormalizedDrawCommand.FillRect>(command.normalized)
                assertEquals(false, fill.stroke)
                assertEquals("Identity", fill.transform.type.name)
                assertEquals("drawRect.stroke.analytic-four-band", fill.source.operation)
                assertEquals(GPUBlendMode.SRC, command.blendPlan.mode)
                assertEquals(clip.toGPUClipFacts(target()).kind, fill.clip.kind)
                assertEquals(clip.toGPUClipFacts(target()).bounds, fill.clip.bounds)
                GPUPixelBounds(
                    fill.rect.left.toInt(), fill.rect.top.toInt(),
                    fill.rect.right.toInt(), fill.rect.bottom.toInt(),
                )
            },
        )
        assertTrue(commands.all { it.provenance == GPUFrameProvenance.GmContent })
        assertTrue(commands.all { it.geometryRefusal == null })
        val materials = commands.map {
            assertIs<NormalizedDrawCommand.FillRect>(it.normalized).material
        }
        assertTrue(materials.drop(1).all { it === materials.first() })
        assertEquals(
            org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.SolidColor(0f, 0f, 1f, 1f),
            materials.first(),
        )
        assertFailsWith<UnsupportedOperationException> {
            (commands as MutableList<GPUFramePathVisualCommand>).clear()
        }
        assertEquals(4, lowered.commands.size)
    }

    @Test
    fun `clamp srgb linear gradient stroke preserves one device descriptor across all four bands`() {
        val gradient = Shader.LinearGradient(
            start = Point2F32(8.5f, 32.5f),
            end = Point2F32(55.5f, 32.5f),
            stops = listOf(
                org.graphiks.kanvas.paint.GradientStop(0f, ColorARGB.of(255, 255, 56, 56)),
                org.graphiks.kanvas.paint.GradientStop(1f, ColorARGB.of(255, 56, 112, 255)),
            ),
            tileMode = org.graphiks.kanvas.paint.TileMode.CLAMP,
        )
        val lowered = assertIs<GPUPreparedStrokeRectLowering.Ready>(
            GPUPreparedStrokeRectLowerer.lower(
                operation = strokeRect(
                    bounds = RectF32.ofLTRB(8f, 16f, 56f, 48f),
                    paint = Paint.stroke(ColorARGB.Transparent, 4f).copy(shader = gradient, antiAlias = false),
                ),
                firstCommandId = GPUDrawCommandID(0),
                firstPaintOrder = 0,
                provenance = GPUFrameProvenance.None,
                target = target(),
                config = RenderConfig.DEFAULT,
                capabilities = capabilities(),
            ),
        )

        val fills = lowered.commands.map { assertIs<NormalizedDrawCommand.FillRect>(it.normalized) }
        assertEquals(
            listOf(
                GPUPixelBounds(6, 14, 58, 18),
                GPUPixelBounds(6, 46, 58, 50),
                GPUPixelBounds(6, 18, 10, 46),
                GPUPixelBounds(54, 18, 58, 46),
            ),
            fills.map { fill -> GPUPixelBounds(fill.rect.left.toInt(), fill.rect.top.toInt(), fill.rect.right.toInt(), fill.rect.bottom.toInt()) },
        )
        val materials = fills.map { assertIs<org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.LinearGradient>(it.material) }
        assertTrue(materials.drop(1).all { it === materials.first() })
        assertEquals(8.5f, materials.first().startX)
        assertEquals(32.5f, materials.first().startY)
        assertEquals(55.5f, materials.first().endX)
        assertEquals(32.5f, materials.first().endY)
        assertEquals("clamp", materials.first().tileMode)
    }

    @Test
    fun `two stop clamp radial gradient stroke preserves one device descriptor across all four bands`() {
        val lowered = assertIs<GPUPreparedStrokeRectLowering.Ready>(
            GPUPreparedStrokeRectLowerer.lower(
                operation = strokeRect(
                    bounds = RectF32.ofLTRB(8f, 16f, 56f, 48f),
                    paint = Paint.stroke(ColorARGB.Transparent, 4f).copy(
                        shader = Shader.RadialGradient(
                            center = Point2F32(32.5f, 32.5f),
                            radius = 23.5f,
                            stops = listOf(
                                org.graphiks.kanvas.paint.GradientStop(0f, ColorARGB.Red),
                                org.graphiks.kanvas.paint.GradientStop(1f, ColorARGB.Blue),
                            ),
                            tileMode = org.graphiks.kanvas.paint.TileMode.CLAMP,
                        ),
                        antiAlias = false,
                    ),
                ),
                firstCommandId = GPUDrawCommandID(0),
                firstPaintOrder = 0,
                provenance = GPUFrameProvenance.None,
                target = target(),
                config = RenderConfig.DEFAULT,
                capabilities = capabilities(withTwoStopStrokeRadialGradient = true),
            ),
        )

        val materials = lowered.commands.map {
            assertIs<org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.RadialGradient>(
                assertIs<NormalizedDrawCommand.FillRect>(it.normalized).material,
            )
        }
        assertEquals(4, materials.size)
        assertTrue(materials.drop(1).all { it === materials.first() })
        assertEquals(32.5f, materials.first().centerX)
        assertEquals(32.5f, materials.first().centerY)
        assertEquals(23.5f, materials.first().radius)
        assertEquals("clamp", materials.first().tileMode)
    }

    @Test
    fun `three stop clamp radial gradient stroke requires its dedicated capability`() {
        val shader = Shader.RadialGradient(Point2F32(32.5f, 32.5f), 23.5f, listOf(
            org.graphiks.kanvas.paint.GradientStop(0f, ColorARGB.Red),
            org.graphiks.kanvas.paint.GradientStop(.5f, ColorARGB.Green),
            org.graphiks.kanvas.paint.GradientStop(1f, ColorARGB.Blue),
        ))
        fun lower(capability: Boolean) = GPUPreparedStrokeRectLowerer.lower(
            strokeRect(bounds = RectF32.ofLTRB(8f,16f,56f,48f), paint = Paint.stroke(ColorARGB.Transparent,4f).copy(shader=shader, antiAlias=false)),
            GPUDrawCommandID(0),0,GPUFrameProvenance.None,target(),RenderConfig.DEFAULT,
            capabilities(withThreeStopStrokeRadialGradient = capability),
        )
        assertEquals("unsupported.stroke.rect_radial_gradient_three_stop_capability", assertIs<GPUPreparedStrokeRectLowering.Refused>(lower(false)).code)
        assertEquals(4, assertIs<GPUPreparedStrokeRectLowering.Ready>(lower(true)).commands.size)
    }

    @Test
    fun `three stop radial stroke rejects every material contract escape`() {
        val stops = listOf(org.graphiks.kanvas.paint.GradientStop(0f, ColorARGB.Red), org.graphiks.kanvas.paint.GradientStop(.5f, ColorARGB.Green), org.graphiks.kanvas.paint.GradientStop(1f, ColorARGB.Blue))
        fun op(shader: Shader, aa: Boolean = false, matrix: Matrix3x3F32 = Matrix3x3F32.Identity) = strokeRect(
            paint = Paint.stroke(ColorARGB.Transparent, 4f).copy(shader = shader, antiAlias = aa), transform = matrix,
        )
        val radial = Shader.RadialGradient(Point2F32(32f,32f), 16f, stops)
        val cases = listOf(
            Triple(op(Shader.RadialGradient(Point2F32(32f,32f),16f,stops.take(2))), target(), "unsupported.stroke.rect_radial_gradient_two_stop_capability"),
            Triple(op(Shader.RadialGradient(Point2F32(32f,32f),16f,stops + org.graphiks.kanvas.paint.GradientStop(1f,ColorARGB.White))), target(), "unsupported.stroke.rect_gradient_stop_count"),
            Triple(op(Shader.RadialGradient(Point2F32(32f,32f),16f,stops, org.graphiks.kanvas.paint.TileMode.REPEAT)), target(), "unsupported.stroke.rect_gradient_tile_mode"),
            Triple(op(radial, true), target(), "unsupported.stroke.rect_anti_alias"),
            Triple(op(radial, matrix=Matrix3x3F32.translation(1f,0f)), target(), "unsupported.stroke.rect_transform"),
            Triple(op(radial), target("rgba8unorm"), "unsupported.stroke.rect_gradient_target"),
            Triple(op(Shader.WithLocalMatrix(radial, Matrix3x3F32.translation(1f,0f))), target(), "unsupported.stroke.rect_material"),
            Triple(strokeRect(paint=Paint.stroke(ColorARGB.Transparent,4f).copy(shader=radial, colorFilter=ColorFilter.HighContrast, antiAlias=false)), target(), "unsupported.stroke.rect_material"),
        )
        cases.forEach { (operation, target, code) ->
            assertEquals(code, assertIs<GPUPreparedStrokeRectLowering.Refused>(GPUPreparedStrokeRectLowerer.lower(operation,GPUDrawCommandID(0),0,GPUFrameProvenance.None,target,RenderConfig.DEFAULT,capabilities(withThreeStopStrokeRadialGradient=true))).code)
        }
    }

    @Test
    fun `radial stroke rejects malformed stop positions before bands`() {
        val invalid = listOf(Float.NaN, -0.1f, 1.1f, .5f)
        invalid.forEach { middle ->
            val positions = if (middle == .5f) listOf(0f, 0f, 1f) else listOf(0f, middle, 1f)
            val shader = Shader.RadialGradient(Point2F32(32f,32f),16f,positions.mapIndexed { index, p -> org.graphiks.kanvas.paint.GradientStop(p, if(index == 0) ColorARGB.Red else ColorARGB.Blue) })
            val refused = assertIs<GPUPreparedStrokeRectLowering.Refused>(GPUPreparedStrokeRectLowerer.lower(
                strokeRect(paint=Paint.stroke(ColorARGB.Transparent,4f).copy(shader=shader,antiAlias=false)), GPUDrawCommandID(0),0,GPUFrameProvenance.None,target(),RenderConfig.DEFAULT,capabilities(withThreeStopStrokeRadialGradient=true),
            ))
            assertEquals("unsupported.stroke.rect_material", refused.code)
        }
    }

    @Test
    fun `two stop full sweep gradient stroke lowers to four bands only with dedicated capability`() {
        val lowered = assertIs<GPUPreparedStrokeRectLowering.Ready>(GPUPreparedStrokeRectLowerer.lower(
            strokeRect(bounds = RectF32.ofLTRB(8f, 16f, 56f, 48f), paint = Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.SweepGradient(Point2F32(32.5f, 32.5f), 0f, 360f, listOf(
                    org.graphiks.kanvas.paint.GradientStop(0f, ColorARGB.Red),
                    org.graphiks.kanvas.paint.GradientStop(1f, ColorARGB.Blue),
                )), antiAlias = false,
            )), GPUDrawCommandID(0), 0, GPUFrameProvenance.None, target(), RenderConfig.DEFAULT,
            capabilities(withTwoStopStrokeSweepGradient = true),
        ))
        assertEquals(4, lowered.commands.size)
        assertTrue(lowered.commands.all { assertIs<NormalizedDrawCommand.FillRect>(it.normalized).material is org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.SweepGradient })
    }

    @Test
    fun `three stop full sweep gradient stroke requires its dedicated capability`() {
        val shader = Shader.SweepGradient(Point2F32(32f,32f),0f,360f,listOf(
            org.graphiks.kanvas.paint.GradientStop(0f,ColorARGB.Red), org.graphiks.kanvas.paint.GradientStop(.5f,ColorARGB.Green), org.graphiks.kanvas.paint.GradientStop(1f,ColorARGB.Blue),
        ))
        fun lower(cap: Boolean) = GPUPreparedStrokeRectLowerer.lower(strokeRect(paint=Paint.stroke(ColorARGB.Transparent,4f).copy(shader=shader,antiAlias=false)),GPUDrawCommandID(0),0,GPUFrameProvenance.None,target(),RenderConfig.DEFAULT,capabilities(withThreeStopStrokeSweepGradient=cap))
        assertEquals("unsupported.stroke.rect_sweep_gradient_three_stop_capability",assertIs<GPUPreparedStrokeRectLowering.Refused>(lower(false)).code)
        assertEquals(4,assertIs<GPUPreparedStrokeRectLowering.Ready>(lower(true)).commands.size)
    }

    @Test
    fun `three stop sweep gradient stroke refuses every bounded-contract escape before bands`() {
        fun sweep(
            stops: List<org.graphiks.kanvas.paint.GradientStop> = listOf(
                org.graphiks.kanvas.paint.GradientStop(0f, ColorARGB.Red),
                org.graphiks.kanvas.paint.GradientStop(.5f, ColorARGB.Green),
                org.graphiks.kanvas.paint.GradientStop(1f, ColorARGB.Blue),
            ),
            tileMode: org.graphiks.kanvas.paint.TileMode = org.graphiks.kanvas.paint.TileMode.CLAMP,
            startAngle: Float = 0f,
            endAngle: Float = 360f,
        ) = Shader.SweepGradient(Point2F32(32.5f, 32.5f), startAngle, endAngle, stops, tileMode)
        fun operation(shader: Shader, antiAlias: Boolean = false, transform: Matrix3x3F32 = Matrix3x3F32.Identity, colorFilter: ColorFilter? = null) =
            strokeRect(
                bounds = RectF32.ofLTRB(8f, 16f, 56f, 48f),
                paint = Paint.stroke(ColorARGB.Transparent, 4f).copy(shader = shader, antiAlias = antiAlias, colorFilter = colorFilter),
                transform = transform,
            )
        val cases = listOf(
            Triple(operation(sweep(listOf(org.graphiks.kanvas.paint.GradientStop(0f, ColorARGB.Red)))), target(), "unsupported.stroke.rect_gradient_stop_count"),
            Triple(operation(sweep(listOf(org.graphiks.kanvas.paint.GradientStop(0f, ColorARGB.Red), org.graphiks.kanvas.paint.GradientStop(.25f, ColorARGB.Green), org.graphiks.kanvas.paint.GradientStop(.5f, ColorARGB.Blue), org.graphiks.kanvas.paint.GradientStop(1f, ColorARGB.White)))), target(), "unsupported.stroke.rect_gradient_stop_count"),
            Triple(operation(sweep(listOf(org.graphiks.kanvas.paint.GradientStop(.1f, ColorARGB.Red), org.graphiks.kanvas.paint.GradientStop(.5f, ColorARGB.Green), org.graphiks.kanvas.paint.GradientStop(1f, ColorARGB.Blue)))), target(), "unsupported.stroke.rect_material"),
            Triple(operation(sweep(listOf(org.graphiks.kanvas.paint.GradientStop(0f, ColorARGB.Red), org.graphiks.kanvas.paint.GradientStop(.5f, ColorARGB.Green), org.graphiks.kanvas.paint.GradientStop(.5f, ColorARGB.Blue)))), target(), "unsupported.stroke.rect_material"),
            Triple(operation(sweep(tileMode = org.graphiks.kanvas.paint.TileMode.REPEAT)), target(), "unsupported.stroke.rect_gradient_tile_mode"),
            Triple(operation(sweep(tileMode = org.graphiks.kanvas.paint.TileMode.MIRROR)), target(), "unsupported.stroke.rect_gradient_tile_mode"),
            Triple(operation(sweep(tileMode = org.graphiks.kanvas.paint.TileMode.DECAL)), target(), "unsupported.stroke.rect_gradient_tile_mode"),
            Triple(operation(sweep(startAngle = 45f, endAngle = 315f)), target(), "unsupported.stroke.rect_gradient_angles"),
            Triple(operation(sweep()), target("rgba8unorm"), "unsupported.stroke.rect_gradient_target"),
            Triple(operation(sweep()), target("bgra8unorm"), "unsupported.stroke.rect_gradient_target"),
            Triple(operation(sweep(), antiAlias = true), target(), "unsupported.stroke.rect_anti_alias"),
            Triple(operation(sweep(), transform = Matrix3x3F32.translation(1f, 0f)), target(), "unsupported.stroke.rect_transform"),
            Triple(operation(Shader.WithLocalMatrix(sweep(), Matrix3x3F32.translation(1f, 0f))), target(), "unsupported.stroke.rect_material"),
            Triple(operation(sweep(), colorFilter = ColorFilter.HighContrast), target(), "unsupported.stroke.rect_material"),
        )
        cases.forEach { (operation, target, expectedCode) ->
            val refused = assertIs<GPUPreparedStrokeRectLowering.Refused>(
                GPUPreparedStrokeRectLowerer.lower(
                    operation, GPUDrawCommandID(0), 0, GPUFrameProvenance.None, target,
                    RenderConfig.DEFAULT, capabilities(withThreeStopStrokeSweepGradient = true),
                ),
            )
            assertEquals(expectedCode, refused.code)
        }
    }

    @Test
    fun `two stop sweep gradient stroke refuses every bounded-contract escape before bands`() {
        fun sweep(
            stops: List<org.graphiks.kanvas.paint.GradientStop> = listOf(
                org.graphiks.kanvas.paint.GradientStop(0f, ColorARGB.Red),
                org.graphiks.kanvas.paint.GradientStop(1f, ColorARGB.Blue),
            ),
            tileMode: org.graphiks.kanvas.paint.TileMode = org.graphiks.kanvas.paint.TileMode.CLAMP,
            startAngle: Float = 0f,
            endAngle: Float = 360f,
        ) = Shader.SweepGradient(Point2F32(32.5f, 32.5f), startAngle, endAngle, stops, tileMode)
        fun operation(shader: Shader, antiAlias: Boolean = false, transform: Matrix3x3F32 = Matrix3x3F32.Identity, colorFilter: ColorFilter? = null) =
            strokeRect(
                bounds = RectF32.ofLTRB(8f, 16f, 56f, 48f),
                paint = Paint.stroke(ColorARGB.Transparent, 4f).copy(shader = shader, antiAlias = antiAlias, colorFilter = colorFilter),
                transform = transform,
            )
        val cases = listOf(
            Triple(operation(sweep(listOf(org.graphiks.kanvas.paint.GradientStop(0f, ColorARGB.Red)))), target(), "unsupported.stroke.rect_gradient_stop_count"),
            Triple(operation(sweep(listOf(org.graphiks.kanvas.paint.GradientStop(0f, ColorARGB.Red), org.graphiks.kanvas.paint.GradientStop(.25f, ColorARGB.Green), org.graphiks.kanvas.paint.GradientStop(.5f, ColorARGB.Blue), org.graphiks.kanvas.paint.GradientStop(1f, ColorARGB.White)))), target(), "unsupported.stroke.rect_gradient_stop_count"),
            Triple(operation(sweep(tileMode = org.graphiks.kanvas.paint.TileMode.REPEAT)), target(), "unsupported.stroke.rect_gradient_tile_mode"),
            Triple(operation(sweep(tileMode = org.graphiks.kanvas.paint.TileMode.MIRROR)), target(), "unsupported.stroke.rect_gradient_tile_mode"),
            Triple(operation(sweep(tileMode = org.graphiks.kanvas.paint.TileMode.DECAL)), target(), "unsupported.stroke.rect_gradient_tile_mode"),
            Triple(operation(sweep(startAngle = 45f, endAngle = 315f)), target(), "unsupported.stroke.rect_gradient_angles"),
            Triple(operation(sweep()), target("rgba8unorm"), "unsupported.stroke.rect_gradient_target"),
            Triple(operation(sweep()), target("bgra8unorm"), "unsupported.stroke.rect_gradient_target"),
            Triple(operation(sweep(), antiAlias = true), target(), "unsupported.stroke.rect_anti_alias"),
            Triple(operation(sweep(), transform = Matrix3x3F32.translation(1f, 0f)), target(), "unsupported.stroke.rect_transform"),
            Triple(operation(Shader.WithLocalMatrix(sweep(), Matrix3x3F32.translation(1f, 0f))), target(), "unsupported.stroke.rect_material"),
            Triple(operation(sweep(), colorFilter = ColorFilter.HighContrast), target(), "unsupported.stroke.rect_material"),
        )
        cases.forEach { (operation, target, expectedCode) ->
            val refused = assertIs<GPUPreparedStrokeRectLowering.Refused>(
                GPUPreparedStrokeRectLowerer.lower(
                    operation, GPUDrawCommandID(0), 0, GPUFrameProvenance.None, target,
                    RenderConfig.DEFAULT, capabilities(withTwoStopStrokeSweepGradient = true),
                ),
            )
            assertEquals(expectedCode, refused.code)
        }
    }

    @Test
    fun `two stop radial gradient stroke refuses every bounded-contract escape before bands`() {
        fun radial(tileMode: org.graphiks.kanvas.paint.TileMode = org.graphiks.kanvas.paint.TileMode.CLAMP) =
            Shader.RadialGradient(
                Point2F32(32.5f, 32.5f), 23.5f,
                listOf(
                    org.graphiks.kanvas.paint.GradientStop(0f, ColorARGB.Red),
                    org.graphiks.kanvas.paint.GradientStop(1f, ColorARGB.Blue),
                ), tileMode,
            )
        fun operation(shader: Shader, antiAlias: Boolean = false, transform: Matrix3x3F32 = Matrix3x3F32.Identity) =
            strokeRect(
                bounds = RectF32.ofLTRB(8f, 16f, 56f, 48f),
                paint = Paint.stroke(ColorARGB.Transparent, 4f).copy(shader = shader, antiAlias = antiAlias),
                transform = transform,
            )
        val cases = listOf(
            Triple(operation(radial(), antiAlias = true), target(), "unsupported.stroke.rect_anti_alias"),
            Triple(operation(radial()), target("rgba8unorm"), "unsupported.stroke.rect_gradient_target"),
            Triple(operation(radial(org.graphiks.kanvas.paint.TileMode.REPEAT)), target(), "unsupported.stroke.rect_gradient_tile_mode"),
            Triple(operation(radial(org.graphiks.kanvas.paint.TileMode.MIRROR)), target(), "unsupported.stroke.rect_gradient_tile_mode"),
            Triple(operation(radial(org.graphiks.kanvas.paint.TileMode.DECAL)), target(), "unsupported.stroke.rect_gradient_tile_mode"),
            Triple(operation(radial(), transform = Matrix3x3F32.translation(1f, 0f)), target(), "unsupported.stroke.rect_transform"),
            Triple(
                operation(Shader.WithLocalMatrix(radial(), Matrix3x3F32.translation(1f, 0f))),
                target(), "unsupported.stroke.rect_material",
            ),
        )
        cases.forEach { (operation, target, expectedCode) ->
            val refused = assertIs<GPUPreparedStrokeRectLowering.Refused>(
                GPUPreparedStrokeRectLowerer.lower(
                    operation, GPUDrawCommandID(0), 0, GPUFrameProvenance.None, target,
                    RenderConfig.DEFAULT, capabilities(withTwoStopStrokeRadialGradient = true),
                ),
            )
            assertEquals(expectedCode, refused.code)
        }
    }

    @Test
    fun `three stop gradient stroke refuses before bands when its dedicated capability is absent`() {
        val lowered = assertIs<GPUPreparedStrokeRectLowering.Refused>(
            GPUPreparedStrokeRectLowerer.lower(
                operation = strokeRect(
                    bounds = RectF32.ofLTRB(8f, 16f, 56f, 48f),
                    paint = Paint.stroke(ColorARGB.Transparent, 4f).copy(
                        shader = Shader.LinearGradient(
                            Point2F32(8.5f, 32.5f), Point2F32(55.5f, 32.5f),
                            listOf(
                                org.graphiks.kanvas.paint.GradientStop(0f, ColorARGB.Red),
                                org.graphiks.kanvas.paint.GradientStop(.5f, ColorARGB.Green),
                                org.graphiks.kanvas.paint.GradientStop(1f, ColorARGB.Blue),
                            ),
                            org.graphiks.kanvas.paint.TileMode.CLAMP,
                        ),
                        antiAlias = false,
                    ),
                ),
                firstCommandId = GPUDrawCommandID(0),
                firstPaintOrder = 0,
                provenance = GPUFrameProvenance.None,
                target = target(),
                config = RenderConfig.DEFAULT,
                capabilities = capabilities(),
            ),
        )

        assertEquals("unsupported.stroke.rect_linear_gradient_three_stop_capability", lowered.code)
    }

    @Test
    fun `three stop gradient stroke refuses non srgb targets before bands`() {
        val operation = strokeRect(
            bounds = RectF32.ofLTRB(8f, 16f, 56f, 48f),
            paint = Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.LinearGradient(
                    Point2F32(8.5f, 32.5f), Point2F32(55.5f, 32.5f),
                    listOf(
                        org.graphiks.kanvas.paint.GradientStop(0f, ColorARGB.Red),
                        org.graphiks.kanvas.paint.GradientStop(.5f, ColorARGB.Green),
                        org.graphiks.kanvas.paint.GradientStop(1f, ColorARGB.Blue),
                    ),
                    org.graphiks.kanvas.paint.TileMode.CLAMP,
                ),
                antiAlias = false,
            ),
        )

        listOf("rgba8unorm", "bgra8unorm").forEach { format ->
            val lowered = assertIs<GPUPreparedStrokeRectLowering.Refused>(
                GPUPreparedStrokeRectLowerer.lower(
                    operation, GPUDrawCommandID(0), 0, GPUFrameProvenance.None,
                    target(format), RenderConfig.DEFAULT, capabilities(withThreeStopStrokeGradient = true),
                ),
            )
            assertEquals("unsupported.stroke.rect_gradient_target", lowered.code)
            assertEquals(format, lowered.facts["targetFormat"])
        }
    }

    @Test
    fun `three stop clamp gradient stroke lowers to four typed analytic bands only with its capability`() {
        val operation = strokeRect(
            bounds = RectF32.ofLTRB(8f, 16f, 56f, 48f),
            paint = Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.LinearGradient(
                    Point2F32(8.5f, 32.5f), Point2F32(55.5f, 32.5f),
                    listOf(
                        org.graphiks.kanvas.paint.GradientStop(0f, ColorARGB.Red),
                        org.graphiks.kanvas.paint.GradientStop(.5f, ColorARGB.Green),
                        org.graphiks.kanvas.paint.GradientStop(1f, ColorARGB.Blue),
                    ),
                    org.graphiks.kanvas.paint.TileMode.CLAMP,
                ),
                antiAlias = false,
            ),
        )

        val lowered = assertIs<GPUPreparedStrokeRectLowering.Ready>(
            GPUPreparedStrokeRectLowerer.lower(
                operation = operation,
                firstCommandId = GPUDrawCommandID(0),
                firstPaintOrder = 0,
                provenance = GPUFrameProvenance.GmContent,
                target = target(),
                config = RenderConfig.DEFAULT,
                capabilities = capabilities(withThreeStopStrokeGradient = true),
            ),
        )

        assertEquals(4, lowered.commands.size)
        lowered.commands.forEach { visual ->
            val fill = assertIs<NormalizedDrawCommand.FillRect>(visual.normalized)
            assertEquals(
                org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSourceKind.AnalyticStrokeRectBand,
                fill.source.kind,
            )
            val material = assertIs<org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.LinearGradient>(fill.material)
            assertTrue(material.allStopPositions.contentEquals(floatArrayOf(0f, .5f, 1f)))
            assertEquals("clamp", material.tileMode)
        }
    }

    @Test
    fun `three stop gradient stroke preserves bounded pre-band refusals`() {
        val threeStops = listOf(
            org.graphiks.kanvas.paint.GradientStop(0f, ColorARGB.Red),
            org.graphiks.kanvas.paint.GradientStop(.5f, ColorARGB.Green),
            org.graphiks.kanvas.paint.GradientStop(1f, ColorARGB.Blue),
        )
        val fourStops = threeStops + org.graphiks.kanvas.paint.GradientStop(1f, ColorARGB.White)
        val cases = listOf(
            strokeRect(paint = Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.LinearGradient(Point2F32(8f, 32f), Point2F32(56f, 32f), fourStops),
                antiAlias = false,
            )) to "unsupported.stroke.rect_gradient_stop_count",
            strokeRect(paint = Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.LinearGradient(
                    Point2F32(8f, 32f), Point2F32(56f, 32f), threeStops,
                    org.graphiks.kanvas.paint.TileMode.REPEAT,
                ),
                antiAlias = false,
            )) to "unsupported.stroke.rect_material",
            strokeRect(paint = Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.LinearGradient(Point2F32(8f, 32f), Point2F32(56f, 32f), threeStops),
                antiAlias = true,
            )) to "unsupported.stroke.rect_anti_alias",
            strokeRect(
                paint = Paint.stroke(ColorARGB.Transparent, 4f).copy(
                    shader = Shader.LinearGradient(Point2F32(8f, 32f), Point2F32(56f, 32f), threeStops),
                    antiAlias = false,
                ),
                transform = Matrix3x3F32.translation(2f, 0f),
            ) to "unsupported.stroke.rect_linear_gradient_three_stop_translate_capability",
            strokeRect(paint = Paint.stroke(ColorARGB.Transparent, 4f).copy(
                shader = Shader.WithLocalMatrix(
                    Shader.LinearGradient(Point2F32(8f, 32f), Point2F32(56f, 32f), threeStops),
                    Matrix3x3F32.translation(1f, 0f),
                ),
                antiAlias = false,
            )) to "unsupported.stroke.rect_material",
        )

        cases.forEach { (operation, expectedCode) ->
            val lowered = assertIs<GPUPreparedStrokeRectLowering.Refused>(
                GPUPreparedStrokeRectLowerer.lower(
                    operation, GPUDrawCommandID(0), 0, GPUFrameProvenance.None,
                    target(), RenderConfig.DEFAULT, capabilities(withThreeStopStrokeGradient = true),
                ),
            )
            assertEquals(expectedCode, lowered.code)
        }
    }

    @Test
    fun `integer translated two stop clamp linear gradient stroke rebases one device descriptor across all bands`() {
        val gradientOperation = strokeRect(
            bounds = RectF32.ofLTRB(8f, 16f, 56f, 48f),
            paint = Paint.stroke(ColorARGB.Transparent, 4f).copy(shader = linearGradient(), antiAlias = false),
            transform = Matrix3x3F32.translation(2f, 3f),
        )

        val lowered = assertIs<GPUPreparedStrokeRectLowering.Ready>(
            GPUPreparedStrokeRectLowerer.lower(
                gradientOperation,
                GPUDrawCommandID(0),
                0,
                GPUFrameProvenance.None,
                target(),
                RenderConfig.DEFAULT,
                capabilities(withTranslatedTwoStopStrokeGradient = true),
            ),
        )
        assertEquals(4, lowered.commands.size)
        val materials = lowered.commands.map {
            val fill = assertIs<NormalizedDrawCommand.FillRect>(it.normalized)
            assertEquals(GPUCommandSourceKind.AnalyticStrokeRectTranslatedBand, fill.source.kind)
            assertIs<org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.LinearGradient>(fill.material)
        }
        assertTrue(materials.drop(1).all { it === materials.first() })
        assertEquals(10f, materials.first().startX)
        assertEquals(35f, materials.first().startY)
        assertEquals(58f, materials.first().endX)
        assertEquals(35f, materials.first().endY)

        val missingCapability = assertIs<GPUPreparedStrokeRectLowering.Refused>(
            GPUPreparedStrokeRectLowerer.lower(
                gradientOperation, GPUDrawCommandID(0), 0, GPUFrameProvenance.None,
                target(), RenderConfig.DEFAULT, capabilities(),
            ),
        )
        assertEquals("unsupported.stroke.rect_linear_gradient_translate_capability", missingCapability.code)

        val unsupportedTarget = assertIs<GPUPreparedStrokeRectLowering.Refused>(
            GPUPreparedStrokeRectLowerer.lower(
                gradientOperation, GPUDrawCommandID(0), 0, GPUFrameProvenance.None,
                target("rgba8unorm"), RenderConfig.DEFAULT,
                capabilities(withTranslatedTwoStopStrokeGradient = true),
            ),
        )
        assertEquals("unsupported.stroke.rect_gradient_target", unsupportedTarget.code)
        assertEquals("rgba8unorm", unsupportedTarget.facts["targetFormat"])
        val unsupportedTargetMapping = GPUOpMapper.mapOperations(
            listOf(gradientOperation), target("rgba8unorm"), RenderConfig.DEFAULT,
            capabilities(withTranslatedTwoStopStrokeGradient = true),
        )
        assertEquals("unsupported.stroke.rect_gradient_target", unsupportedTargetMapping.preparedRefusal?.code)
        assertTrue(unsupportedTargetMapping.visualCommands.isEmpty())

        val fractionalTranslation = assertIs<GPUPreparedStrokeRectLowering.Refused>(
            GPUPreparedStrokeRectLowerer.lower(
                gradientOperation.copy(transform = Matrix3x3F32.translation(2.5f, 3f)),
                GPUDrawCommandID(0), 0, GPUFrameProvenance.None, target(), RenderConfig.DEFAULT,
                capabilities(withTranslatedTwoStopStrokeGradient = true),
            ),
        )
        assertEquals("unsupported.stroke.rect_transform", fractionalTranslation.code)

        val mapping = GPUOpMapper.mapOperations(
            listOf(gradientOperation), target(), RenderConfig.DEFAULT,
            capabilities(withTranslatedTwoStopStrokeGradient = true),
        )
        assertEquals(null, mapping.preparedRefusal)
        assertEquals(listOf(0, 1, 2, 3), mapping.visualCommands.map { it.normalized.commandId.value })

        val solidMapping = GPUOpMapper.mapOperations(
            listOf(strokeRect(transform = Matrix3x3F32.translation(2f, 0f))),
            target(), RenderConfig.DEFAULT, capabilities(),
        )
        assertEquals(null, solidMapping.preparedRefusal)
        assertEquals(listOf(0, 1, 2, 3), solidMapping.visualCommands.map { it.normalized.commandId.value })
    }

    @Test
    fun `integer translated three stop clamp linear gradient stroke needs its capability and preserves stops`() {
        val shader = Shader.LinearGradient(
            Point2F32(8f, 32f), Point2F32(56f, 32f),
            listOf(
                org.graphiks.kanvas.paint.GradientStop(0f, ColorARGB.Red),
                org.graphiks.kanvas.paint.GradientStop(.5f, ColorARGB.Green),
                org.graphiks.kanvas.paint.GradientStop(1f, ColorARGB.Blue),
            ),
            org.graphiks.kanvas.paint.TileMode.CLAMP,
        )
        val operation = strokeRect(
            bounds = RectF32.ofLTRB(8f, 16f, 56f, 48f),
            paint = Paint.stroke(ColorARGB.Transparent, 4f).copy(shader = shader, antiAlias = false),
            transform = Matrix3x3F32.translation(2f, 3f),
        )
        val absent = assertIs<GPUPreparedStrokeRectLowering.Refused>(GPUPreparedStrokeRectLowerer.lower(
            operation, GPUDrawCommandID(0), 0, GPUFrameProvenance.None, target(), RenderConfig.DEFAULT,
            capabilities(withThreeStopStrokeGradient = true),
        ))
        assertEquals("unsupported.stroke.rect_linear_gradient_three_stop_translate_capability", absent.code)
        val ready = assertIs<GPUPreparedStrokeRectLowering.Ready>(GPUPreparedStrokeRectLowerer.lower(
            operation, GPUDrawCommandID(0), 0, GPUFrameProvenance.None, target(), RenderConfig.DEFAULT,
            capabilities(withTranslatedThreeStopStrokeGradient = true),
        ))
        assertEquals(4, ready.commands.size)
        val material = assertIs<org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.LinearGradient>(
            assertIs<NormalizedDrawCommand.FillRect>(ready.commands.first().normalized).material,
        )
        assertEquals(GPUCommandSourceKind.AnalyticStrokeRectTranslatedThreeStopBand, assertIs<NormalizedDrawCommand.FillRect>(ready.commands.first().normalized).source.kind)
        assertEquals(10f, material.startX)
        assertEquals(35f, material.startY)
        assertEquals(58f, material.endX)
        assertEquals(35f, material.endY)
        assertEquals(listOf(0f, .5f, 1f), material.allStopPositions?.toList())
    }

    @Test
    fun `uniform integer scaled two stop clamp linear gradient stroke rebases device geometry and axis`() {
        val shader = Shader.LinearGradient(
            Point2F32(8f, 16f), Point2F32(28f, 16f), gradientStops(), org.graphiks.kanvas.paint.TileMode.CLAMP,
        )
        val operation = strokeRect(
            bounds = RectF32.ofLTRB(8f, 8f, 28f, 24f),
            paint = Paint.stroke(ColorARGB.Transparent, 2f).copy(shader = shader, antiAlias = false),
            transform = Matrix3x3F32(sx = 2f, sy = 2f, tx = 2f, ty = 4f),
        )
        val absent = assertIs<GPUPreparedStrokeRectLowering.Refused>(GPUPreparedStrokeRectLowerer.lower(
            operation, GPUDrawCommandID(0), 0, GPUFrameProvenance.None, target(), RenderConfig.DEFAULT, capabilities(),
        ))
        assertEquals("unsupported.stroke.rect_linear_gradient_uniform_scale_capability", absent.code)
        val scaledResult = GPUPreparedStrokeRectLowerer.lower(
            operation, GPUDrawCommandID(0), 0, GPUFrameProvenance.None, target(), RenderConfig.DEFAULT,
            capabilities(withUniformScaleTwoStopStrokeGradient = true),
        )
        val ready = assertIs<GPUPreparedStrokeRectLowering.Ready>(scaledResult)
        assertEquals(4, ready.commands.size)
        assertEquals("uniform-scale", ready.geometryPlan.path?.transformClass)
        assertEquals("uniform-scale", ready.geometryPlan.stroke?.transformClass)
        val first = assertIs<NormalizedDrawCommand.FillRect>(ready.commands.first().normalized)
        assertEquals(GPUCommandSourceKind.AnalyticStrokeRectUniformScaleBand, first.source.kind)
        val material = assertIs<org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.LinearGradient>(first.material)
        assertEquals(18f, material.startX); assertEquals(36f, material.startY)
        assertEquals(58f, material.endX); assertEquals(36f, material.endY)

        val nonEndpointStops = Shader.LinearGradient(
            Point2F32(8f, 16f), Point2F32(28f, 16f),
            listOf(
                org.graphiks.kanvas.paint.GradientStop(.1f, ColorARGB.Red),
                org.graphiks.kanvas.paint.GradientStop(.9f, ColorARGB.Blue),
            ),
            org.graphiks.kanvas.paint.TileMode.CLAMP,
        )
        val nonEndpointResult = GPUPreparedStrokeRectLowerer.lower(
            operation.copy(paint = operation.paint.copy(shader = nonEndpointStops)),
            GPUDrawCommandID(0), 0, GPUFrameProvenance.None, target(), RenderConfig.DEFAULT,
            capabilities(withUniformScaleTwoStopStrokeGradient = true),
        )
        assertEquals(
            "unsupported.stroke.rect_material",
            assertIs<GPUPreparedStrokeRectLowering.Refused>(nonEndpointResult).code,
        )
    }

    @Test
    fun `uniform integer scaled three stop clamp linear gradient stroke rebases device geometry and axis`() {
        val shader = Shader.LinearGradient(
            Point2F32(8f, 16f), Point2F32(28f, 16f),
            listOf(
                org.graphiks.kanvas.paint.GradientStop(0f, ColorARGB.Red),
                org.graphiks.kanvas.paint.GradientStop(.5f, ColorARGB.Green),
                org.graphiks.kanvas.paint.GradientStop(1f, ColorARGB.Blue),
            ),
            org.graphiks.kanvas.paint.TileMode.CLAMP,
        )
        val operation = strokeRect(
            bounds = RectF32.ofLTRB(8f, 8f, 28f, 24f),
            paint = Paint.stroke(ColorARGB.Transparent, 2f).copy(shader = shader, antiAlias = false),
            transform = Matrix3x3F32(sx = 2f, sy = 2f, tx = 2f, ty = 4f),
        )

        val ready = assertIs<GPUPreparedStrokeRectLowering.Ready>(GPUPreparedStrokeRectLowerer.lower(
            operation, GPUDrawCommandID(0), 0, GPUFrameProvenance.None, target(), RenderConfig.DEFAULT,
            capabilities(withUniformScaleThreeStopStrokeGradient = true),
        ))
        assertEquals(4, ready.commands.size)
        assertEquals("uniform-scale", ready.geometryPlan.path?.transformClass)
        assertEquals("uniform-scale", ready.geometryPlan.stroke?.transformClass)
        assertEquals(
            listOf(
                GPUPixelBounds(16, 18, 60, 22),
                GPUPixelBounds(16, 50, 60, 54),
                GPUPixelBounds(16, 22, 20, 50),
                GPUPixelBounds(56, 22, 60, 50),
            ),
            ready.commands.map { command ->
                val fill = assertIs<NormalizedDrawCommand.FillRect>(command.normalized)
                GPUPixelBounds(fill.rect.left.toInt(), fill.rect.top.toInt(), fill.rect.right.toInt(), fill.rect.bottom.toInt())
            },
        )
        val first = assertIs<NormalizedDrawCommand.FillRect>(ready.commands.first().normalized)
        assertEquals(GPUCommandSourceKind.AnalyticStrokeRectUniformScaleThreeStopBand, first.source.kind)
        val material = assertIs<org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.LinearGradient>(first.material)
        assertEquals(18f, material.startX); assertEquals(36f, material.startY)
        assertEquals(58f, material.endX); assertEquals(36f, material.endY)
        assertEquals(listOf(0f, .5f, 1f), material.allStopPositions?.toList())
    }

    @Test
    fun `uniform integer scaled two stop clamp sweep gradient stroke rebases center and device bands`() {
        val shader = Shader.SweepGradient(
            Point2F32(18f, 14f),
            0f,
            360f,
            listOf(
                org.graphiks.kanvas.paint.GradientStop(0f, ColorARGB.Red),
                org.graphiks.kanvas.paint.GradientStop(1f, ColorARGB.Blue),
            ),
            org.graphiks.kanvas.paint.TileMode.CLAMP,
        )
        val operation = strokeRect(
            bounds = RectF32.ofLTRB(8f, 8f, 28f, 24f),
            paint = Paint.stroke(ColorARGB.Transparent, 2f).copy(shader = shader, antiAlias = false),
            transform = Matrix3x3F32(sx = 2f, sy = 2f, tx = 2f, ty = 4f),
        )

        val ready = assertIs<GPUPreparedStrokeRectLowering.Ready>(GPUPreparedStrokeRectLowerer.lower(
            operation, GPUDrawCommandID(0), 0, GPUFrameProvenance.None, target(), RenderConfig.DEFAULT,
            capabilities(withUniformScaleTwoStopStrokeSweepGradient = true),
        ))
        assertEquals("uniform-scale", ready.geometryPlan.path?.transformClass)
        assertEquals("uniform-scale", ready.geometryPlan.stroke?.transformClass)
        assertEquals(
            listOf(
                GPUPixelBounds(16, 18, 60, 22),
                GPUPixelBounds(16, 50, 60, 54),
                GPUPixelBounds(16, 22, 20, 50),
                GPUPixelBounds(56, 22, 60, 50),
            ),
            ready.commands.map { command ->
                val fill = assertIs<NormalizedDrawCommand.FillRect>(command.normalized)
                GPUPixelBounds(fill.rect.left.toInt(), fill.rect.top.toInt(), fill.rect.right.toInt(), fill.rect.bottom.toInt())
            },
        )
        val first = assertIs<NormalizedDrawCommand.FillRect>(ready.commands.first().normalized)
        val material = assertIs<org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.SweepGradient>(first.material)
        assertEquals(38f, material.centerX)
        assertEquals(32f, material.centerY)
    }

    @Test
    fun `uniform integer scaled two stop clamp radial gradient stroke rebases center radius and device bands`() {
        val shader = Shader.RadialGradient(
            center = Point2F32(18f, 14f),
            radius = 8f,
            stops = gradientStops(),
            tileMode = org.graphiks.kanvas.paint.TileMode.CLAMP,
        )
        val operation = strokeRect(
            bounds = RectF32.ofLTRB(8f, 8f, 28f, 24f),
            paint = Paint.stroke(ColorARGB.Transparent, 2f).copy(shader = shader, antiAlias = false),
            transform = Matrix3x3F32(sx = 2f, sy = 2f, tx = 2f, ty = 4f),
        )

        val absent = assertIs<GPUPreparedStrokeRectLowering.Refused>(GPUPreparedStrokeRectLowerer.lower(
            operation, GPUDrawCommandID(0), 0, GPUFrameProvenance.None, target(), RenderConfig.DEFAULT, capabilities(),
        ))
        assertEquals("unsupported.stroke.rect_radial_gradient_two_stop_uniform_scale_capability", absent.code)

        val ready = assertIs<GPUPreparedStrokeRectLowering.Ready>(GPUPreparedStrokeRectLowerer.lower(
            operation, GPUDrawCommandID(0), 0, GPUFrameProvenance.None, target(), RenderConfig.DEFAULT,
            capabilities(withUniformScaleTwoStopStrokeRadialGradient = true),
        ))
        assertEquals(4, ready.commands.size)
        assertEquals("uniform-scale", ready.geometryPlan.path?.transformClass)
        assertEquals("uniform-scale", ready.geometryPlan.stroke?.transformClass)
        assertEquals(
            listOf(
                GPUPixelBounds(16, 18, 60, 22),
                GPUPixelBounds(16, 50, 60, 54),
                GPUPixelBounds(16, 22, 20, 50),
                GPUPixelBounds(56, 22, 60, 50),
            ),
            ready.commands.map { command ->
                val fill = assertIs<NormalizedDrawCommand.FillRect>(command.normalized)
                GPUPixelBounds(fill.rect.left.toInt(), fill.rect.top.toInt(), fill.rect.right.toInt(), fill.rect.bottom.toInt())
            },
        )
        val first = assertIs<NormalizedDrawCommand.FillRect>(ready.commands.first().normalized)
        assertEquals(GPUCommandSourceKind.AnalyticStrokeRectUniformScaleRadialTwoStopBand, first.source.kind)
        val material = assertIs<org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.RadialGradient>(first.material)
        assertEquals(38f, material.centerX)
        assertEquals(32f, material.centerY)
        assertEquals(16f, material.radius)
        assertEquals(listOf(0f, 1f), material.allStopPositions?.toList())
    }

    @Test
    fun `uniform integer scaled three stop clamp radial gradient stroke rebases center radius and device bands`() {
        val shader = Shader.RadialGradient(
            center = Point2F32(18f, 14f),
            radius = 8f,
            stops = listOf(
                org.graphiks.kanvas.paint.GradientStop(0f, ColorARGB.Red),
                org.graphiks.kanvas.paint.GradientStop(.5f, ColorARGB.Green),
                org.graphiks.kanvas.paint.GradientStop(1f, ColorARGB.Blue),
            ),
            tileMode = org.graphiks.kanvas.paint.TileMode.CLAMP,
        )
        val operation = strokeRect(
            bounds = RectF32.ofLTRB(8f, 8f, 28f, 24f),
            paint = Paint.stroke(ColorARGB.Transparent, 2f).copy(shader = shader, antiAlias = false),
            transform = Matrix3x3F32(sx = 2f, sy = 2f, tx = 2f, ty = 4f),
        )

        val absent = assertIs<GPUPreparedStrokeRectLowering.Refused>(GPUPreparedStrokeRectLowerer.lower(
            operation, GPUDrawCommandID(0), 0, GPUFrameProvenance.None, target(), RenderConfig.DEFAULT,
            capabilities(withThreeStopStrokeRadialGradient = true),
        ))
        assertEquals("unsupported.stroke.rect_radial_gradient_three_stop_uniform_scale_capability", absent.code)

        val ready = assertIs<GPUPreparedStrokeRectLowering.Ready>(GPUPreparedStrokeRectLowerer.lower(
            operation, GPUDrawCommandID(0), 0, GPUFrameProvenance.None, target(), RenderConfig.DEFAULT,
            capabilities(withUniformScaleThreeStopStrokeRadialGradient = true),
        ))
        assertEquals(4, ready.commands.size)
        assertEquals("uniform-scale", ready.geometryPlan.path?.transformClass)
        val first = assertIs<NormalizedDrawCommand.FillRect>(ready.commands.first().normalized)
        assertEquals(GPUCommandSourceKind.AnalyticStrokeRectUniformScaleRadialThreeStopBand, first.source.kind)
        val material = assertIs<org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.RadialGradient>(first.material)
        assertEquals(38f, material.centerX)
        assertEquals(32f, material.centerY)
        assertEquals(16f, material.radius)
        assertEquals(listOf(0f, .5f, 1f), material.allStopPositions?.toList())
    }

    @Test
    fun `translated three stop linear gradient stroke refuses positions outside the proven contract before bands`() {
        val cases = listOf(
            "arbitrary midpoint" to listOf(0f, .25f, 1f),
            "equal positions" to listOf(0f, .5f, .5f),
            "missing first endpoint" to listOf(.1f, .5f, 1f),
            "missing last endpoint" to listOf(0f, .5f, .9f),
        )
        cases.forEach { (name, positions) ->
            val shader = Shader.LinearGradient(
                Point2F32(8f, 32f), Point2F32(56f, 32f),
                positions.zip(listOf(ColorARGB.Red, ColorARGB.Green, ColorARGB.Blue)) { position, color ->
                    org.graphiks.kanvas.paint.GradientStop(position, color)
                },
                org.graphiks.kanvas.paint.TileMode.CLAMP,
            )
            val lowered = assertIs<GPUPreparedStrokeRectLowering.Refused>(GPUPreparedStrokeRectLowerer.lower(
                strokeRect(
                    bounds = RectF32.ofLTRB(8f, 16f, 56f, 48f),
                    paint = Paint.stroke(ColorARGB.Transparent, 4f).copy(shader = shader, antiAlias = false),
                    transform = Matrix3x3F32.translation(2f, 3f),
                ),
                GPUDrawCommandID(0), 0, GPUFrameProvenance.None, target(), RenderConfig.DEFAULT,
                capabilities(withTranslatedThreeStopStrokeGradient = true),
            ), name)
            assertEquals("unsupported.stroke.rect_material", lowered.code, name)
        }
    }

    @Test
    fun `malformed linear gradient stroke boundaries refuse as material without throwing`() {
        val validStops = gradientStops()
        val cases = listOf(
            "negative position" to Shader.LinearGradient(
                Point2F32(8f, 32f), Point2F32(56f, 32f),
                listOf(org.graphiks.kanvas.paint.GradientStop(-.1f, ColorARGB.Red), validStops.last()),
            ),
            "position above one" to Shader.LinearGradient(
                Point2F32(8f, 32f), Point2F32(56f, 32f),
                listOf(validStops.first(), org.graphiks.kanvas.paint.GradientStop(1.1f, ColorARGB.Blue)),
            ),
            "decreasing positions" to Shader.LinearGradient(
                Point2F32(8f, 32f), Point2F32(56f, 32f),
                listOf(org.graphiks.kanvas.paint.GradientStop(.75f, ColorARGB.Red), org.graphiks.kanvas.paint.GradientStop(.25f, ColorARGB.Blue)),
            ),
            "identical endpoints" to Shader.LinearGradient(
                Point2F32(8f, 32f), Point2F32(8f, 32f), validStops,
            ),
            "non finite endpoint" to Shader.LinearGradient(
                Point2F32(Float.NaN, 32f), Point2F32(56f, 32f), validStops,
            ),
            "overflowed axis" to Shader.LinearGradient(
                Point2F32(-Float.MAX_VALUE, 32f), Point2F32(Float.MAX_VALUE, 32f), validStops,
            ),
        )

        cases.forEach { (name, shader) ->
            val lowered = assertIs<GPUPreparedStrokeRectLowering.Refused>(
                GPUPreparedStrokeRectLowerer.lower(
                    strokeRect(paint = Paint.stroke(ColorARGB.Transparent, 4f).copy(shader = shader, antiAlias = false)),
                    GPUDrawCommandID(0), 0, GPUFrameProvenance.None, target(), RenderConfig.DEFAULT, capabilities(),
                ),
                name,
            )
            assertEquals("unsupported.stroke.rect_material", lowered.code, name)
        }
    }

    @Test
    fun `bounded stroke inputs refuse before generic path lowering with stable codes`() {
        val cases = listOf(
            "anti alias" to strokeRect(paint = strokePaint.copy(antiAlias = true)) to
                "unsupported.stroke.rect_anti_alias",
            "invalid width" to strokeRect(paint = strokePaint.copy(strokeWidth = 0f)) to
                "unsupported.stroke.width_invalid",
            "odd width" to strokeRect(paint = strokePaint.copy(strokeWidth = 5f)) to
                "unsupported.stroke.rect_subpixel_first_slice",
            "fractional translation" to strokeRect(transform = Matrix3x3F32.translation(0.5f, 0f)) to
                "unsupported.stroke.rect_transform",
            "scale" to strokeRect(transform = Matrix3x3F32.scaling(2f, 1f)) to
                "unsupported.stroke.rect_transform",
            "skew" to strokeRect(transform = Matrix3x3F32(kx = 0.5f)) to
                "unsupported.stroke.rect_transform",
            "perspective" to strokeRect(transform = Matrix3x3F32(persp0 = 0.25f)) to
                "unsupported.stroke.rect_transform",
            "singular" to strokeRect(transform = Matrix3x3F32.scaling(0f, 1f)) to
                "unsupported.stroke.rect_transform",
            "non finite transform" to strokeRect(transform = Matrix3x3F32(tx = Float.NaN)) to
                "unsupported.stroke.rect_transform",
            "square cap" to strokeRect(paint = strokePaint.copy(strokeCap = StrokeCap.SQUARE)) to
                "unsupported.stroke.cap",
            "round join" to strokeRect(paint = strokePaint.copy(strokeJoin = StrokeJoin.ROUND)) to
                "unsupported.stroke.join",
            "small miter" to strokeRect(paint = strokePaint.copy(strokeMiter = 1f)) to
                "unsupported.stroke.rect_miter_limit",
            "path effect" to strokeRect(
                paint = strokePaint.copy(pathEffect = PathEffect.Dash(floatArrayOf(2f, 2f))),
            ) to "unsupported.stroke.rect_path_effect",
            "shader material" to strokeRect(
                paint = strokePaint.copy(shader = Shader.SolidColor(ColorARGB.Blue)),
            ) to "unsupported.stroke.rect_material",
            "malformed shader material" to strokeRect(
                paint = strokePaint.copy(
                    shader = Shader.LinearGradient(
                        start = Point2F32(0f, 0f),
                        end = Point2F32(1f, 0f),
                        stops = emptyList(),
                    ),
                ),
            ) to "unsupported.stroke.rect_material",
            "three stop radial gradient stroke" to strokeRect(
                paint = strokePaint.copy(
                    shader = Shader.RadialGradient(
                        Point2F32(32f, 32f),
                        16f,
                        gradientStops() + org.graphiks.kanvas.paint.GradientStop(1f, ColorARGB.Blue),
                    ),
                ),
            ) to "unsupported.stroke.rect_radial_gradient_three_stop_capability",
            "sweep gradient stroke material" to strokeRect(
                paint = strokePaint.copy(
                    shader = Shader.SweepGradient(Point2F32(32f, 32f), stops = gradientStops()),
                ),
            ) to "unsupported.stroke.rect_sweep_gradient_two_stop_capability",
            "repeat gradient stroke material" to strokeRect(
                paint = strokePaint.copy(shader = linearGradient(org.graphiks.kanvas.paint.TileMode.REPEAT)),
            ) to "unsupported.stroke.rect_material",
            "mirror gradient stroke material" to strokeRect(
                paint = strokePaint.copy(shader = linearGradient(org.graphiks.kanvas.paint.TileMode.MIRROR)),
            ) to "unsupported.stroke.rect_material",
            "decal gradient stroke material" to strokeRect(
                paint = strokePaint.copy(shader = linearGradient(org.graphiks.kanvas.paint.TileMode.DECAL)),
            ) to "unsupported.stroke.rect_material",
            "local matrix gradient stroke material" to strokeRect(
                paint = strokePaint.copy(
                    shader = Shader.WithLocalMatrix(linearGradient(), Matrix3x3F32.translation(1f, 0f)),
                ),
            ) to "unsupported.stroke.rect_material",
            "unsupported color filter" to strokeRect(
                paint = strokePaint.copy(colorFilter = ColorFilter.HighContrast),
            ) to "unsupported.stroke.rect_material",
            "non foldable blend color filter" to strokeRect(
                paint = strokePaint.copy(colorFilter = ColorFilter.Blend(ColorARGB.Blue, org.graphiks.kanvas.paint.BlendMode.MULTIPLY)),
            ) to "unsupported.stroke.rect_material",
            "blender" to strokeRect(
                paint = strokePaint.copy(blender = Blender.Mode(org.graphiks.kanvas.paint.BlendMode.SRC)),
            ) to "unsupported.stroke.rect_material",
            "negative bounds" to strokeRect(bounds = RectF32.ofLTRB(-16f, 16f, 48f, 48f)) to
                "unsupported.stroke.rect_target_overflow",
            "outside target bounds" to strokeRect(bounds = RectF32.ofLTRB(16f, 16f, 80f, 48f)) to
                "unsupported.stroke.rect_target_overflow",
            "inverted bounds" to strokeRect(bounds = RectF32.ofLTRB(48f, 16f, 16f, 48f)) to
                "unsupported.stroke.rect_inner_degenerate",
            "target overflow" to strokeRect(bounds = RectF32.ofLTRB(2f, 16f, 48f, 48f)) to
                "unsupported.stroke.rect_target_overflow",
            "inner degenerate" to strokeRect(bounds = RectF32.ofLTRB(16f, 16f, 20f, 48f)) to
                "unsupported.stroke.rect_inner_degenerate",
        )

        cases.forEach { (namedOperation, expected) ->
            val (name, operation) = namedOperation
            val refused = assertIs<GPUPreparedStrokeRectLowering.Refused>(
                GPUPreparedStrokeRectLowerer.lower(
                    operation = operation,
                    firstCommandId = GPUDrawCommandID(12),
                    firstPaintOrder = 12,
                    provenance = GPUFrameProvenance.None,
                    target = target(),
                    config = RenderConfig.DEFAULT,
                    capabilities = capabilities(),
                    operationIndex = 9,
                ),
                name,
            )
            assertEquals(expected, refused.code, name)
            assertEquals(9, refused.operationIndex, name)
            assertEquals("drawRect.stroke", refused.facts["operation"], name)
            if (name == "shader material") assertEquals("SolidColor", refused.facts["shader"])
            if (name == "unsupported color filter") {
                assertEquals("HighContrast", refused.facts["colorFilter"])
            }
            if (name == "non foldable blend color filter") {
                assertEquals("Blend", refused.facts["colorFilter"])
            }
            if (name == "blender") assertEquals("Mode", refused.facts["blender"])
            if (name == "invalid width") assertEquals("0.0", refused.facts["strokeWidth"])
            if (name == "square cap") assertEquals("SQUARE", refused.facts["cap"])
            if (name == "round join") assertEquals("ROUND", refused.facts["join"])
            if (name == "small miter") assertEquals("1.0", refused.facts["miter"])
            if (name == "target overflow") {
                assertEquals("2,16,48,48", refused.facts["pathBounds"])
                assertEquals("64x64", refused.facts["target"])
            }
        }
    }

    @Test
    fun `foldable blend color filter is preserved as the lowered fill material`() {
        val lowered = assertIs<GPUPreparedStrokeRectLowering.Ready>(
            GPUPreparedStrokeRectLowerer.lower(
                operation = strokeRect(
                    paint = strokePaint.copy(
                        colorFilter = ColorFilter.Blend(ColorARGB.Blue, org.graphiks.kanvas.paint.BlendMode.SRC),
                    ),
                ),
                firstCommandId = GPUDrawCommandID(0),
                firstPaintOrder = 0,
                provenance = GPUFrameProvenance.None,
                target = target(),
                config = RenderConfig.DEFAULT,
                capabilities = capabilities(),
            ),
        )

        val material = assertIs<org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.SolidColor>(
            assertIs<NormalizedDrawCommand.FillRect>(lowered.commands.first().normalized).material,
        )
        assertEquals(0f, material.r)
        assertEquals(0f, material.g)
        assertEquals(1f, material.b)
        assertEquals(1f, material.a)
    }

    @Test
    fun `invalid command allocation and immutable refusal facts terminate before lowering`() {
        listOf(
            GPUDrawCommandID(Int.MAX_VALUE) to 0,
            GPUDrawCommandID(0) to -1,
            GPUDrawCommandID(0) to Int.MAX_VALUE,
        ).forEach { (commandId, paintOrder) ->
            val refused = assertIs<GPUPreparedStrokeRectLowering.Refused>(
                GPUPreparedStrokeRectLowerer.lower(
                    operation = strokeRect(),
                    firstCommandId = commandId,
                    firstPaintOrder = paintOrder,
                    provenance = GPUFrameProvenance.None,
                    target = target(),
                    config = RenderConfig.DEFAULT,
                    capabilities = capabilities(),
                ),
            )
            assertEquals("unsupported.stroke.rect_command_range", refused.code)
            assertEquals("GPUPreparedStrokeRectLowerer", refused.facts["authority"])
            assertEquals("drawRect.stroke", refused.facts["operation"])
            assertFailsWith<UnsupportedOperationException> {
                (refused.facts as MutableMap<String, String>).clear()
            }
        }
    }

    @Test
    fun `public mapper records all four analytic fill command ids for one stroke operation`() {
        val mapping = GPUOpMapper.mapOperations(
            operations = listOf(strokeRect()),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities(),
        )

        assertEquals(null, mapping.preparedRefusal)
        assertEquals(listOf(0, 1, 2, 3), mapping.visualCommands.map { it.normalized.commandId.value })
        assertEquals(setOf(0, 1, 2, 3), mapping.commandIdsByOperationIndex.getValue(0))
        assertTrue(mapping.visualCommands.all { it.normalized is NormalizedDrawCommand.FillRect })
        assertTrue(mapping.visualCommands.none { it.normalized is NormalizedDrawCommand.FillPath })
    }

    @Test
    fun `public frame preparation expands the admitted gradient stroke into four fill commands`() {
        val mapping = GPUOpMapper.mapOperations(
            operations = listOf(
                strokeRect(
                    bounds = RectF32.ofLTRB(8f, 16f, 56f, 48f),
                    paint = Paint.stroke(ColorARGB.Transparent, 4f).copy(
                        shader = Shader.LinearGradient(
                            Point2F32(8.5f, 32.5f), Point2F32(55.5f, 32.5f),
                            listOf(
                                org.graphiks.kanvas.paint.GradientStop(0f, ColorARGB.Red),
                                org.graphiks.kanvas.paint.GradientStop(1f, ColorARGB.Blue),
                            ),
                            org.graphiks.kanvas.paint.TileMode.CLAMP,
                        ),
                        antiAlias = false,
                    ),
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities(),
        )

        assertEquals(null, mapping.preparedRefusal)
        assertEquals(listOf(0, 1, 2, 3), mapping.visualCommands.map { it.normalized.commandId.value })
        val materials = mapping.visualCommands.map {
            assertIs<NormalizedDrawCommand.FillRect>(it.normalized).material
        }
        assertTrue(materials.all { it is org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.LinearGradient })
        assertTrue(materials.drop(1).all { it === materials.first() })
    }

    private fun strokeRect(
        bounds: RectF32 = RectF32.ofLTRB(16f, 16f, 48f, 48f),
        paint: Paint = strokePaint,
        transform: Matrix3x3F32 = Matrix3x3F32.Identity,
        clip: ClipStack = ClipStack.WideOpen,
    ) = DisplayOp.DrawRect(
        bounds,
        paint,
        transform,
        clip,
    )

    private fun target(format: String = "rgba8unorm-srgb") = GPUTargetFacts(64, 64, format)

    private fun capabilities(
        withThreeStopStrokeGradient: Boolean = false,
        withTranslatedThreeStopStrokeGradient: Boolean = false,
        withTranslatedTwoStopStrokeGradient: Boolean = false,
        withUniformScaleTwoStopStrokeGradient: Boolean = false,
        withUniformScaleThreeStopStrokeGradient: Boolean = false,
        withUniformScaleTwoStopStrokeSweepGradient: Boolean = false,
        withUniformScaleTwoStopStrokeRadialGradient: Boolean = false,
        withUniformScaleThreeStopStrokeRadialGradient: Boolean = false,
        withTwoStopStrokeRadialGradient: Boolean = false,
        withTwoStopStrokeSweepGradient: Boolean = false,
        withThreeStopStrokeRadialGradient: Boolean = false,
        withThreeStopStrokeSweepGradient: Boolean = false,
    ) = GPUCapabilities(
        implementation = GPUImplementationIdentity(
            facadeName = "test", implementationName = "fake", adapterName = "mock", deviceName = "mock",
        ),
        facts = buildList {
            add(
            GPUCapabilityFact(
                name = "first_slice.fill_rect.native", source = "test", value = "supported",
                affectsValidity = true, evidenceLabel = "test:fill-rect",
            ),
            )
            if (withThreeStopStrokeGradient) {
                add(
                    GPUCapabilityFact(
                        name = GPUFirstSliceCapabilityName.STROKE_RECT_LINEAR_GRADIENT_THREE_STOP_NATIVE,
                        source = "test",
                        value = "supported",
                        affectsValidity = true,
                        evidenceLabel = "test:stroke-rect-linear-gradient-three-stop",
                    ),
                )
            }
            if (withTranslatedThreeStopStrokeGradient) add(GPUCapabilityFact(
                GPUFirstSliceCapabilityName.STROKE_RECT_LINEAR_GRADIENT_THREE_STOP_TRANSLATE_NATIVE,
                "test", "supported", true, "test:stroke-rect-linear-gradient-three-stop-translate",
            ))
            if (withTranslatedTwoStopStrokeGradient) {
                add(
                    GPUCapabilityFact(
                        name = GPUFirstSliceCapabilityName.STROKE_RECT_LINEAR_GRADIENT_TRANSLATE_NATIVE,
                        source = "test",
                        value = "supported",
                        affectsValidity = true,
                        evidenceLabel = "test:stroke-rect-linear-gradient-translate",
                    ),
                )
            }
            if (withUniformScaleTwoStopStrokeGradient) add(GPUCapabilityFact(
                GPUFirstSliceCapabilityName.STROKE_RECT_LINEAR_GRADIENT_UNIFORM_SCALE_NATIVE,
                "test", "supported", true, "test:stroke-rect-linear-gradient-uniform-scale",
            ))
            if (withUniformScaleThreeStopStrokeGradient) add(GPUCapabilityFact(
                GPUFirstSliceCapabilityName.STROKE_RECT_LINEAR_GRADIENT_THREE_STOP_UNIFORM_SCALE_NATIVE,
                "test", "supported", true, "test:stroke-rect-linear-gradient-three-stop-uniform-scale",
            ))
            if (withUniformScaleTwoStopStrokeSweepGradient) add(GPUCapabilityFact(
                GPUFirstSliceCapabilityName.STROKE_RECT_SWEEP_GRADIENT_TWO_STOP_UNIFORM_SCALE_NATIVE,
                "test", "supported", true, "test:stroke-rect-sweep-gradient-two-stop-uniform-scale",
            ))
            if (withUniformScaleTwoStopStrokeRadialGradient) add(GPUCapabilityFact(
                GPUFirstSliceCapabilityName.STROKE_RECT_RADIAL_GRADIENT_TWO_STOP_UNIFORM_SCALE_NATIVE,
                "test", "supported", true, "test:stroke-rect-radial-gradient-two-stop-uniform-scale",
            ))
            if (withUniformScaleThreeStopStrokeRadialGradient) add(GPUCapabilityFact(
                GPUFirstSliceCapabilityName.STROKE_RECT_RADIAL_GRADIENT_THREE_STOP_UNIFORM_SCALE_NATIVE,
                "test", "supported", true, "test:stroke-rect-radial-gradient-three-stop-uniform-scale",
            ))
            if (withTwoStopStrokeRadialGradient) {
                add(
                    GPUCapabilityFact(
                        name = GPUFirstSliceCapabilityName.STROKE_RECT_RADIAL_GRADIENT_TWO_STOP_NATIVE,
                        source = "test",
                        value = "supported",
                        affectsValidity = true,
                        evidenceLabel = "test:stroke-rect-radial-gradient-two-stop",
                    ),
                )
            }
            if (withTwoStopStrokeSweepGradient) add(GPUCapabilityFact(
                GPUFirstSliceCapabilityName.STROKE_RECT_SWEEP_GRADIENT_TWO_STOP_NATIVE, "test", "supported", true,
                "test:stroke-rect-sweep-gradient-two-stop",
            ))
            if (withThreeStopStrokeRadialGradient) add(GPUCapabilityFact(
                GPUFirstSliceCapabilityName.STROKE_RECT_RADIAL_GRADIENT_THREE_STOP_NATIVE, "test", "supported", true,
                "test:stroke-rect-radial-gradient-three-stop",
            ))
            if (withThreeStopStrokeSweepGradient) add(GPUCapabilityFact(
                GPUFirstSliceCapabilityName.STROKE_RECT_SWEEP_GRADIENT_THREE_STOP_NATIVE, "test", "supported", true,
                "test:stroke-rect-sweep-gradient-three-stop",
            ))
        },
        knownUnsupportedFacts = emptyList(),
        snapshotId = "stroke-rect-lowerer-test",
        limits = GPULimits(
            maxTextureDimension2D = 8192,
            copyBytesPerRowAlignment = 256,
            minUniformBufferOffsetAlignment = 256,
            maxBufferSize = 1L shl 30,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        ),
        rendererFeatures = setOf(GPURendererFeature.RenderPass),
    )

    private fun gradientStops() = listOf(
        org.graphiks.kanvas.paint.GradientStop(0f, ColorARGB.Red),
        org.graphiks.kanvas.paint.GradientStop(1f, ColorARGB.Blue),
    )

    private fun linearGradient(tileMode: org.graphiks.kanvas.paint.TileMode = org.graphiks.kanvas.paint.TileMode.CLAMP) =
        Shader.LinearGradient(Point2F32(8f, 32f), Point2F32(56f, 32f), gradientStops(), tileMode)

    private companion object {
        val strokePaint: Paint = Paint.stroke(ColorARGB.Red, 6f).copy(antiAlias = false)
    }
}
