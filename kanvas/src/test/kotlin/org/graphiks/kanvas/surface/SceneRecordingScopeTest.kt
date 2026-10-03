package org.graphiks.kanvas.surface

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.render.ir.CompositionDomain
import org.graphiks.kanvas.render.ir.ExternalImageReference
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.kanvas.render.ir.ImagePremultiplicationV1
import org.graphiks.kanvas.render.ir.MaterialNode
import org.graphiks.kanvas.render.ir.ResourceSceneAdapter
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.SceneCaptureResult
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SceneRecordingScopeTest {
    @Test
    fun `clean image snapshot keeps the recording only external reference`() {
        val surface = Surface(3, 2)
        surface.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 3f, 2f), Paint(ColorARGB.Red, antiAlias = false)) }

        val snapshots = SceneRecordingScope.recordingOnly {
            surface.makeImageSnapshot() to surface.makeCleanImageSnapshot()
        }

        assertEquals(snapshots.first.sourceId, snapshots.second.sourceId)
        assertEquals(3, snapshots.second.width)
        assertEquals(2, snapshots.second.height)
        assertNull(snapshots.second.pixels)
        assertEquals(snapshots.first.colorType, snapshots.second.colorType)
        assertEquals(snapshots.first.colorSpace, snapshots.second.colorSpace)
        assertEquals(snapshots.first.alphaType, snapshots.second.alphaType)
        assertEquals(ImagePremultiplicationV1.SOURCE_SPACE, snapshots.second.premultiplication)
    }

    @Test
    fun `recording snapshots preserve domain selection and detachment`() {
        val observations = mutableMapOf<CompositionDomain, RecordingSnapshotObservation>()
        for (format in PixelFormat.entries) {
            for (domain in CompositionDomain.entries) {
                val surface = Surface(3, 2, format, RenderConfig(compositionDomain = domain))
                val bounds = RectF32.ofLTRB(0f, 0f, 3f, 2f)
                surface.canvas { drawRect(bounds, Paint(ColorARGB.Red, antiAlias = false)) }
                val capturedScene = (surface.snapshotScene() as SceneCaptureResult.Captured).scene

                val observation = SceneRecordingScope.recordingOnly {
                    val full = surface.makeImageSnapshot()
                    val repeatedFull = surface.makeImageSnapshot()
                    val subset = requireNotNull(surface.makeImageSnapshot(RectF32.ofLTRB(1f, 0f, 3f, 2f)))
                    val repeatedSubset = requireNotNull(surface.makeImageSnapshot(RectF32.ofLTRB(1f, 0f, 3f, 2f)))
                    val normalizedSubset = requireNotNull(surface.makeImageSnapshot(RectF32.ofLTRB(1.25f, 0f, 3.25f, 2f)))
                    val otherSameSizeSubset = requireNotNull(surface.makeImageSnapshot(RectF32.ofLTRB(0f, 0f, 2f, 2f)))
                    RecordingSnapshotObservation(
                        capturedScene.canonicalId.value,
                        full,
                        repeatedFull,
                        subset,
                        repeatedSubset,
                        normalizedSubset,
                        otherSameSizeSubset,
                    )
                }

                val expectedColorType = if (format == PixelFormat.RGBA8) ColorType.RGBA_8888 else ColorType.BGRA_8888
                val expectedDimensions = listOf(
                    observation.full to (3 to 2),
                    observation.repeatedFull to (3 to 2),
                    observation.subset to (2 to 2),
                    observation.repeatedSubset to (2 to 2),
                    observation.normalizedSubset to (2 to 2),
                    observation.otherSameSizeSubset to (2 to 2),
                )
                for ((image, dimensions) in expectedDimensions) {
                    assertEquals(dimensions.first, image.width)
                    assertEquals(dimensions.second, image.height)
                    assertEquals(expectedColorType, image.colorType)
                    assertEquals(ColorSpace.SRGB, image.colorSpace)
                    assertEquals(AlphaType.PREMUL, image.alphaType)
                    assertEquals(ImagePremultiplicationV1.SOURCE_SPACE, image.premultiplication)
                    assertNull(image.pixels)
                }
                assertEquals(observation.full.sourceId, observation.repeatedFull.sourceId)
                assertEquals(observation.subset.sourceId, observation.repeatedSubset.sourceId)
                assertEquals(observation.subset.sourceId, observation.normalizedSubset.sourceId)
                assertNotEquals(observation.subset.sourceId, observation.otherSameSizeSubset.sourceId)
                assertEquals(2, observation.subset.width)
                assertEquals(2, observation.subset.height)

                surface.canvas { drawRect(bounds, Paint(ColorARGB.Blue, antiAlias = false)) }
                val afterMutation = SceneRecordingScope.recordingOnly {
                    val scene = (surface.snapshotScene() as SceneCaptureResult.Captured).scene
                    scene.canonicalId.value to surface.makeImageSnapshot()
                }
                assertNotEquals(observation.sceneCanonicalId, afterMutation.first)
                assertNotEquals(observation.full.sourceId, afterMutation.second.sourceId)
                assertEquals(3, observation.full.width)
                assertNull(observation.full.pixels)
                observations[domain] = observation
            }
            val linear = observations.getValue(CompositionDomain.LINEAR)
            val encoded = observations.getValue(CompositionDomain.SRGB_ENCODED)
            assertEquals(linear.sceneCanonicalId, encoded.sceneCanonicalId)
            assertNotEquals(linear.full.sourceId, encoded.full.sourceId)
        }
    }

    @Test
    fun `recording runtime children keep domain identity after picture round trip`() {
        val sourceImages = SceneRecordingScope.recordingOnly {
            CompositionDomain.entries.map { domain ->
                val source = Surface(3, 2, config = RenderConfig(compositionDomain = domain))
                source.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 3f, 2f), Paint(ColorARGB.Red, antiAlias = false)) }
                val image = source.makeImageSnapshot()
                assertNull(image.pixels)
                domain to image
            }
        }
        assertNotEquals(sourceImages[0].second.sourceId, sourceImages[1].second.sourceId)

        val effect = requireNotNull(RuntimeEffect.registered("kanvas.runtime.child-opacity", 1))
        fun childShader(image: Image): Shader = effect.makeShader(
            UniformBlock { float1("alpha", 1f) },
            mapOf("child" to Shader.Image(image, TileMode.CLAMP, TileMode.CLAMP, SamplingOptions.LINEAR)),
        )
        val pictureRecorder = PictureRecorder()
        val pictureCanvas = pictureRecorder.beginRecording(RectF32.ofLTRB(0f, 0f, 6f, 2f))
        pictureCanvas.drawRect(RectF32.ofLTRB(0f, 0f, 3f, 2f), Paint(shader = childShader(sourceImages[0].second), antiAlias = false))
        pictureCanvas.drawRect(RectF32.ofLTRB(3f, 0f, 6f, 2f), Paint(shader = childShader(sourceImages[1].second), antiAlias = false))
        val memoryPicture = pictureRecorder.finishRecordingAsPicture()
        val wirePicture = requireNotNull(Picture.fromByteArray(memoryPicture.toByteArray()))

        for (picture in listOf(memoryPicture, wirePicture)) {
            val outer = Surface(6, 2)
            outer.canvas { drawPicture(picture) }
            val scene = (SceneRecordingScope.recordingOnly {
                outer.snapshotScene()
            } as SceneCaptureResult.Captured).scene
            val pictureScene = capturedPictureScene(scene)
            val references = runtimeChildReferences(pictureScene)
            assertEquals(sourceImages.map { it.second.sourceId }, references.map { it.sourceId })
            assertNotEquals(references[0].canonicalId, references[1].canonicalId)
            assertTrue(references.all { ResourceSceneAdapter.toImage(it).pixels == null })
        }
    }

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

        val invalidCapture = surface.snapshotScene() as? SceneCaptureResult.Invalid
            ?: throw AssertionError("expected invalid image payload capture")
        println("SCENE_RECORDING_INVALID_IMAGE diagnostics=${invalidCapture.diagnostics}")
        assertEquals(
            listOf("unsupported.material.image.payload"),
            invalidCapture.diagnostics.map { it.code.value },
        )

        val failure = assertThrows(IllegalStateException::class.java) {
            SceneRecordingScope.recordingOnly { surface.makeImageSnapshot() }
        }

        assertTrue(failure.message.orEmpty().contains("unsupported.material.image.payload"), failure.message)
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

private data class RecordingSnapshotObservation(
    val sceneCanonicalId: String,
    val full: Image,
    val repeatedFull: Image,
    val subset: Image,
    val repeatedSubset: Image,
    val normalizedSubset: Image,
    val otherSameSizeSubset: Image,
)

private fun capturedPictureScene(scene: SceneSnapshot): SceneSnapshot {
    val pictureDraw = scene.filterIsInstance<SceneCommand.Draw>().single()
    return (pictureDraw.node.geometry as GeometryNode.Picture).scene
}

private fun runtimeChildReferences(scene: SceneSnapshot): List<ExternalImageReference> =
    scene.filterIsInstance<SceneCommand.Draw>().map { draw ->
        val runtime = draw.node.paint?.shader as MaterialNode.RuntimeEffect
        val child = runtime.childAt(0)
        assertEquals("child", child.name)
        val sample = child.material as MaterialNode.ImageSample
        sample.image as ExternalImageReference
    }
