package org.graphiks.kanvas.canvas

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Path1DStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.surface.Surface
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

class RecordedGeometrySnapshotTest {
    @Test
    fun `mutating picture cull bounds after recording begins cannot change the picture`() {
        val cullBounds = RectF32.ofLTRB(1f, 2f, 9f, 10f)
        val recorder = PictureRecorder()
        recorder.beginRecording(cullBounds)

        cullBounds.setLTRB(20f, 20f, 30f, 30f)

        assertEquals(RectF32.ofLTRB(1f, 2f, 9f, 10f), recorder.finishRecordingAsPicture().cullRect)
    }

    @Test
    fun `mutating a source path after draw cannot change recorded operations`() {
        val surface = Surface(32, 32)
        val source = Path().addRect(RectF32.ofLTRB(1f, 2f, 9f, 10f))
        surface.canvas().drawPath(source, Paint.fill(ColorARGB.Red))

        source.addRect(RectF32.ofLTRB(20f, 20f, 30f, 30f))

        val recorded = assertIs<DisplayOp.DrawPath>(surface.snapshotOps().single())
        assertEquals(RectF32.ofLTRB(1f, 2f, 9f, 10f), recorded.path.computeBounds())
    }

    @Test
    fun `mutating a clip path returned from an earlier snapshot cannot change later snapshots`() {
        val surface = Surface(32, 32)
        val source = Path().addRect(RectF32.ofLTRB(1f, 2f, 9f, 10f))
        surface.canvas().clipPath(source)

        val firstSnapshot = assertIs<DisplayOp.SetClip>(surface.snapshotOps().single())
        val firstClip = assertIs<ClipStack.Complex>(firstSnapshot.clip)
        val firstPath = assertIs<ClipStackOp.PathOp>(firstClip.ops.single()).path
        firstPath.addRect(RectF32.ofLTRB(20f, 20f, 30f, 30f))

        val recorded = assertIs<DisplayOp.SetClip>(surface.snapshotOps().single())
        val complexClip = assertIs<ClipStack.Complex>(recorded.clip)
        val pathClip = assertIs<ClipStackOp.PathOp>(complexClip.ops.single())
        assertEquals(RectF32.ofLTRB(1f, 2f, 9f, 10f), pathClip.path.computeBounds())
    }

    @Test
    fun `mutating a path effect path cannot change recorded operations`() {
        val surface = Surface(32, 32)
        val source = Path().addRect(RectF32.ofLTRB(1f, 2f, 9f, 10f))
        val paint = Paint(
            color = ColorARGB.Red,
            pathEffect = PathEffect.Path1D(source, advance = 4f, phase = 0f, style = Path1DStyle.ROTATE),
        )
        surface.canvas().drawRect(RectF32.ofLTRB(0f, 0f, 16f, 16f), paint)

        source.addRect(RectF32.ofLTRB(20f, 20f, 30f, 30f))

        val recorded = assertIs<DisplayOp.DrawRect>(surface.snapshotOps().single())
        val effect = assertIs<PathEffect.Path1D>(recorded.paint.pathEffect)
        assertEquals(RectF32.ofLTRB(1f, 2f, 9f, 10f), effect.path.computeBounds())
    }

    @Test
    fun `mutating a path returned from an earlier snapshot cannot change later snapshots`() {
        val surface = Surface(32, 32)
        surface.canvas().drawPath(
            Path().addRect(RectF32.ofLTRB(1f, 2f, 9f, 10f)),
            Paint.fill(ColorARGB.Red),
        )

        val firstSnapshot = assertIs<DisplayOp.DrawPath>(surface.snapshotOps().single())
        firstSnapshot.path.addRect(RectF32.ofLTRB(20f, 20f, 30f, 30f))

        val laterSnapshot = assertIs<DisplayOp.DrawPath>(surface.snapshotOps().single())
        assertEquals(RectF32.ofLTRB(1f, 2f, 9f, 10f), laterSnapshot.path.computeBounds())
    }
}
