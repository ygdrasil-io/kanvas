package org.graphiks.kanvas.surface

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.render.ir.SceneCaptureResult
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SceneRecordingScopeTest {
    @Test
    fun `recording-only snapshots retain the captured scene identity without pixels`() {
        val surface = Surface(3, 2, PixelFormat.BGRA8)
        surface.canvas { drawColor(ColorARGB.Red) }
        val scene = (surface.snapshotScene() as SceneCaptureResult.Captured).scene

        val image = SceneRecordingScope.recordingOnly { surface.makeImageSnapshot() }

        assertEquals(3, image.width)
        assertEquals(2, image.height)
        assertEquals(org.graphiks.kanvas.image.ColorType.BGRA_8888, image.colorType)
        assertNull(image.pixels)
        assertTrue(image.sourceId.contains(scene.canonicalId.value))
    }

    @Test
    fun `recording-only scope rejects renderer submission`() {
        val surface = Surface(1, 1)

        val failure = assertThrows(IllegalStateException::class.java) {
            SceneRecordingScope.recordingOnly { surface.render() }
        }

        assertTrue(failure.message.orEmpty().contains("recording-only"))
    }

    @Test
    fun `recording-only scopes nest and restore the prior scope after a failure`() {
        val surface = Surface(1, 1)

        assertThrows(IllegalArgumentException::class.java) {
            SceneRecordingScope.recordingOnly {
                SceneRecordingScope.recordingOnly {
                    throw IllegalArgumentException("expected")
                }
            }
        }

        val image = surface.makeImageSnapshot()

        assertTrue(image.pixels != null)
    }

    @Test
    fun `recording-only snapshots include capture diagnostics when scene conversion is invalid`() {
        val surface = Surface(1, 1)
        surface.canvas {
            drawImage(
                Image.fromPixels(1, 1, byteArrayOf(1), sourceId = "invalid-rgba"),
                RectF32.ofLTRB(0f, 0f, 1f, 1f),
            )
        }

        val failure = assertThrows(IllegalStateException::class.java) {
            SceneRecordingScope.recordingOnly { surface.makeImageSnapshot() }
        }

        assertTrue(failure.message.orEmpty().contains("scene-capture-invalid"))
    }

    @Test
    fun `recording-only scope initializes the handle-free runtime effect compiler`() {
        val wgsl = """
            @fragment
            fn main() -> @location(0) vec4f {
                return vec4f(1.0, 0.0, 0.0, 1.0);
            }
        """.trimIndent()

        val effect = SceneRecordingScope.recordingOnly {
            RuntimeEffect.compile(wgsl).getOrThrow()
        }

        assertEquals(wgsl, effect.module.source)
        assertEquals("main", effect.module.entryPoint)
    }
}
