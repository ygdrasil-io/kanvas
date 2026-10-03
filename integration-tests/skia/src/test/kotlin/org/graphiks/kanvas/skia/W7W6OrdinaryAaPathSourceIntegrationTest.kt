package org.graphiks.kanvas.skia

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.kanvas.render.ir.SceneCaptureResult
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.surface.SceneRecordingScope
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.skia.gm.blur.BlurCircles2Gm
import org.graphiks.kanvas.skia.gm.blur.RRectBlurGm
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Real-GM native RED witnesses for the two first refused W6 ordinary PATH draws. */
class W7W6OrdinaryAaPathSourceIntegrationTest {
    companion object {
        @AfterAll
        @JvmStatic
        fun cleanupGpu() = GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun rrectBlursRendersCompleteOrdinarySeparators() {
        val gm = RRectBlurGm()
        val surface = recordGm(gm)
        printOriginalDraw(gm.name, surface, commandIndex = 10)

        // On the old product this call must fail at the actual white PATH/STROKE separator.
        // The Task 2 qualification pass extends this same test with full-scene pixels and replay.
        val result = surface.render()
        assertTrue(result.isClean, result.diagnostics.summary())
        assertTrue(result.stats.opsDispatched > 0, "expected native W6 render evidence")
        assertTrue(result.stats.opsRefused == 0, "${result.stats.opsRefused} native operations refused")
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(setOf("Render", "Readback")),
            result.nativeEvidenceScopeKinds.toString())
    }

    @Test
    fun blurCircles2RendersCompleteOrdinarySeparators() {
        val gm = BlurCircles2Gm()
        val surface = recordGm(gm)
        printOriginalDraw(gm.name, surface, commandIndex = 22)

        // On the old product this call must fail at the actual unfiltered PATH separator.
        // The Task 2 qualification pass extends this same test with full-scene pixels and replay.
        val result = surface.render()
        assertTrue(result.isClean, result.diagnostics.summary())
        assertTrue(result.stats.opsDispatched > 0, "expected native W6 render evidence")
        assertTrue(result.stats.opsRefused == 0, "${result.stats.opsRefused} native operations refused")
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(setOf("Render", "Readback")),
            result.nativeEvidenceScopeKinds.toString())
    }

    private fun recordGm(gm: SkiaGm): Surface {
        val surface = Surface(gm.width, gm.height, config = gm.compositionConfig())
        val canvas = GmCanvas(surface.canvas(), gm.width, gm.height)
        SceneRecordingScope.recordingOnly {
            canvas.drawRect(RectF32.ofLTRB(0f, 0f, gm.width.toFloat(), gm.height.toFloat()),
                Paint(color = ColorARGB.White, antiAlias = false))
            gm.onOnceBeforeDraw(canvas)
            gm.draw(canvas, gm.width, gm.height)
        }
        return surface
    }

    private fun printOriginalDraw(gmName: String, surface: Surface, commandIndex: Int) {
        val scene = when (val capture = surface.snapshotScene()) {
            is SceneCaptureResult.Captured -> capture.scene
            is SceneCaptureResult.Invalid -> error(capture.diagnostics.joinToString { "${it.code.value}: ${it.message}" })
        }
        val command = scene.commandAt(commandIndex) as SceneCommand.Draw
        val draw = command.node
        println(formatOriginalDraw(gmName, commandIndex, draw))
    }

    private fun formatOriginalDraw(gmName: String, commandIndex: Int, draw: DrawNode): String {
        val paint = draw.paint
        val pathFillRule = (draw.geometry as? GeometryNode.Path)?.path?.fillRule
        return buildString {
            append("W7_W6_ORDINARY_AA_ORIGINAL")
            append(" gm=").append(gmName)
            append(" commandIndexI32=").append(commandIndex)
            append(" origin=").append(draw.origin)
            append(" geometry=").append(draw.geometry)
            append(" pathFillRule=").append(pathFillRule)
            append(" coverage=").append(draw.coverage)
            append(" style=").append(paint?.style)
            append(" strokeWidth=").append(paint?.strokeWidth)
            append(" strokeCap=").append(paint?.strokeCap)
            append(" strokeJoin=").append(paint?.strokeJoin)
            append(" material=").append(draw.material)
            append(" blend=").append(draw.blend)
            append(" clip=").append(draw.clip)
            append(" ctm=").append(draw.transform)
            append(" imageFilter=").append(paint?.imageFilter)
            append(" maskFilter=").append(paint?.maskFilter)
            append(" colorFilter=").append(paint?.colorFilter)
            append(" pathEffect=").append(paint?.pathEffect)
            append(" effects=").append(draw.effects)
            append(" paint=").append(paint)
        }
    }
}
