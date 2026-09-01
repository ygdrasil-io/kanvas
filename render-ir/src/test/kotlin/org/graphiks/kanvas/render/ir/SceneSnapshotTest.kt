package org.graphiks.kanvas.render.ir

import org.graphiks.math.color.ColorF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class SceneSnapshotTest {
    @Test
    fun `equivalent scene values have a stable canonical identity`() {
        val first = SceneSnapshot.of(
            extent = SceneExtent(64, 32),
            colorSpace = org.graphiks.kanvas.color.ColorSpace.SRGB,
            commands = listOf(SceneCommand.Annotation.of(RectF32(0f, 0f, 1f, 1f), "phase", "recorded")),
        )
        val second = SceneSnapshot.of(
            extent = SceneExtent(64, 32),
            colorSpace = org.graphiks.kanvas.color.ColorSpace.SRGB,
            commands = listOf(SceneCommand.Annotation.of(RectF32(0f, 0f, 1f, 1f), "phase", "recorded")),
        )

        assertEquals(first.canonicalId, second.canonicalId)
    }

    @Test
    fun `snapshot keeps commands recorded before the caller mutates its collection`() {
        val commands = mutableListOf<SceneCommand>(
            SceneCommand.Annotation.of(RectF32(0f, 0f, 1f, 1f), "phase", "recorded"),
        )
        val snapshot = SceneSnapshot.of(
            extent = SceneExtent(64, 32),
            colorSpace = org.graphiks.kanvas.color.ColorSpace.SRGB,
            commands = commands,
        )

        commands.clear()

        assertEquals(1, snapshot.commandCount)
        assertEquals("phase", (snapshot.commandAt(0) as SceneCommand.Annotation).key)
        assertEquals(1, snapshot.toList().size)
    }

    @Test
    fun `snapshot iterator cannot mutate its retained commands`() {
        val snapshot = SceneSnapshot.of(
            extent = SceneExtent(64, 32),
            colorSpace = org.graphiks.kanvas.color.ColorSpace.SRGB,
            commands = listOf(SceneCommand.Annotation.of(RectF32(1f, 2f, 3f, 4f), "phase", "recorded")),
        )
        val identity = snapshot.canonicalId

        assertFailsWith<UnsupportedOperationException> {
            (snapshot.iterator() as MutableIterator<SceneCommand>).remove()
        }

        assertEquals(1, snapshot.commandCount)
        assertEquals(identity, snapshot.canonicalId)
    }

    @Test
    fun `state snapshots source entries and returns a hostile-mutation-safe output`() {
        val source = linkedMapOf("phase" to "recorded")
        val state = SceneCommand.State.of("paint", source)
        val identity = state.canonicalId

        source["phase"] = "mutated"
        val returned = state.entries()
        assertFailsWith<UnsupportedOperationException> {
            (returned as MutableMap<String, String>).clear()
        }

        assertEquals(mapOf("phase" to "recorded"), state.entries())
        assertEquals(identity, state.canonicalId)
    }

    @Test
    fun `command families retain every public semantic field in canonical identity`() {
        val draw = DrawNode(
            geometry = GeometryNode.Points.of(PointMode.POINTS, listOf(Point2F32(1f, 2f))),
            material = MaterialNode.Transparent,
            coverage = CoverageRequest.DEFAULT,
            clip = ClipStackNode.Empty,
            blend = BlendNode.SrcOver,
            effects = EffectStack.Empty,
            transform = Matrix3x3F32.Identity,
        )
        val layer = LayerDescriptor.of(
            label = "group",
            bounds = RectF32(1f, 2f, 3f, 4f),
            material = MaterialNode.Transparent,
            backdrop = EffectStack.Empty,
            effects = EffectStack.Empty,
            transform = Matrix3x3F32(tx = 2f, ty = 3f),
        )

        val commands = listOf(
            SceneCommand.Draw(draw),
            SceneCommand.Clear(ColorF32.Red),
            SceneCommand.BeginLayer(layer),
            SceneCommand.EndLayer,
            SceneCommand.State.of("state", mapOf("mode" to "recorded")),
            SceneCommand.Annotation.of(RectF32(1f, 2f, 3f, 4f), "name", "value"),
            SceneCommand.Readback(ReadbackRequest.of("read", RectF32(4f, 5f, 6f, 7f))),
        )

        assertEquals(commands.size, commands.map(SceneCommand::canonicalId).distinct().size)
        assertNotEquals(
            layer.canonicalId,
            LayerDescriptor.of(
                label = "group",
                bounds = RectF32(1f, 2f, 3f, 4f),
                material = MaterialNode.Transparent,
                backdrop = EffectStack.Empty,
                effects = EffectStack.Empty,
                transform = Matrix3x3F32(tx = 3f, ty = 2f),
            ).canonicalId,
        )
        assertNotEquals(
            layer.canonicalId,
            LayerDescriptor.of(
                label = "other", bounds = RectF32(1f, 2f, 3f, 4f), material = MaterialNode.Transparent,
                backdrop = EffectStack.Empty, effects = EffectStack.Empty, transform = Matrix3x3F32(tx = 2f, ty = 3f),
            ).canonicalId,
        )
        assertNotEquals(
            layer.canonicalId,
            LayerDescriptor.of(
                label = "group", bounds = RectF32(1f, 2f, 3f, 5f), material = MaterialNode.Transparent,
                backdrop = EffectStack.Empty, effects = EffectStack.Empty, transform = Matrix3x3F32(tx = 2f, ty = 3f),
            ).canonicalId,
        )
        assertNotEquals(
            layer.canonicalId,
            LayerDescriptor.of(
                label = "group", bounds = RectF32(1f, 2f, 3f, 4f), material = null,
                backdrop = EffectStack.Empty, effects = EffectStack.Empty, transform = Matrix3x3F32(tx = 2f, ty = 3f),
            ).canonicalId,
        )
        assertNotEquals(
            SceneCommand.Clear(ColorF32.Red).canonicalId,
            SceneCommand.Clear(ColorF32.Blue).canonicalId,
        )
        assertNotEquals(
            SceneCommand.State.of("state", mapOf("mode" to "recorded")).canonicalId,
            SceneCommand.State.of("state", mapOf("mode" to "changed")).canonicalId,
        )
        assertNotEquals(
            SceneCommand.Annotation.of(RectF32(1f, 2f, 3f, 4f), "name", "value").canonicalId,
            SceneCommand.Annotation.of(RectF32(1f, 2f, 3f, 5f), "name", "value").canonicalId,
        )
        assertNotEquals(
            ReadbackRequest.of("read", RectF32(4f, 5f, 6f, 7f)).canonicalId,
            ReadbackRequest.of("read", RectF32(4f, 5f, 6f, 8f)).canonicalId,
        )
    }

    @Test
    fun `layer annotation and readback copy their mutable bounds`() {
        val layerBounds = RectF32(1f, 2f, 3f, 4f)
        val annotationBounds = RectF32(5f, 6f, 7f, 8f)
        val readbackBounds = RectF32(9f, 10f, 11f, 12f)
        val layer = LayerDescriptor.of(bounds = layerBounds)
        val annotation = SceneCommand.Annotation.of(annotationBounds, "region", "marked")
        val readback = ReadbackRequest.of("capture", readbackBounds)

        layerBounds.setEmpty()
        annotationBounds.setEmpty()
        readbackBounds.setEmpty()
        layer.copyBounds()!!.setEmpty()
        annotation.copyBounds().setEmpty()
        readback.copyBounds().setEmpty()

        assertEquals(RectF32(1f, 2f, 3f, 4f), layer.copyBounds())
        assertEquals(RectF32(5f, 6f, 7f, 8f), annotation.copyBounds())
        assertEquals(RectF32(9f, 10f, 11f, 12f), readback.copyBounds())
    }
}
