@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.render.ir

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.SizeF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.vector.Vector2F32

class SceneArchiveCodecTest {
    @Test
    fun `picture archive round trips an ordered scene and cull bounds`() {
        val scene = SceneSnapshot.of(
            extent = SceneExtent(32, 16),
            colorSpace = ColorSpace.SRGB,
            commands = listOf(
                SceneCommand.Draw(
                    DrawNode(
                        geometry = GeometryNode.Rect.of(RectF32(1f, 2f, 10f, 12f)),
                        material = MaterialNode.Solid(ColorARGB.Red),
                        coverage = CoverageRequest.ANTIALIASED,
                        clip = ClipStackNode.Empty,
                        blend = BlendNode.SrcOver,
                        effects = EffectStack.Empty,
                        transform = org.graphiks.math.matrix.Matrix3x3F32.Identity,
                        paint = solidPaint(),
                    ),
                ),
                SceneCommand.DrawColor(ColorARGB.Blue, BlendMode.SRC_OVER),
            ),
        )

        val bytes = SceneArchiveCodec.encodePicture(scene, RectF32(1f, 2f, 33f, 18f))
        val decoded = assertIs<SceneArchiveDecodeResult.Decoded>(SceneArchiveCodec.decodePicture(bytes))

        assertEquals("KPIC", bytes.copyOfRange(0, 4).decodeToString())
        assertEquals(8, java.nio.ByteBuffer.wrap(bytes, 4, 4).int)
        assertEquals(scene.canonicalId, decoded.scene.canonicalId)
        assertEquals(RectF32(1f, 2f, 33f, 18f), decoded.copyCullRect())
    }

    @Test
    fun `picture archive rejects an unknown version without allocating a scene`() {
        val bytes = byteArrayOf(0x4b, 0x50, 0x49, 0x43, 0, 0, 0, 9)

        val result = SceneArchiveCodec.decodePicture(bytes)

        val invalid = assertIs<SceneArchiveDecodeResult.Invalid>(result)
        assertEquals("unknown-version", invalid.code)
        assertTrue(invalid.message.isNotBlank())
    }

    @Test
    fun `picture archive round trips every command node graph resource and runtime contract`() {
        val scene = exhaustiveScene()
        val encoded = SceneArchiveCodec.encodePicture(scene, RectF32(0f, 0f, 64f, 64f))

        val decoded = assertIs<SceneArchiveDecodeResult.Decoded>(
            SceneArchiveCodec.decodePicture(encoded),
        )

        assertEquals(scene.canonicalId, decoded.scene.canonicalId)
        assertEquals(scene.commandCount, decoded.scene.commandCount)
        assertTrue(encoded.contentEquals(SceneArchiveCodec.encodePicture(scene, RectF32(0f, 0f, 64f, 64f))))
    }

    @Test
    fun `picture archive rejects corrupted marker tag and collection length`() {
        val bytes = SceneArchiveCodec.encodePicture(exhaustiveScene(), RectF32(0f, 0f, 64f, 64f))
        val truncated = bytes.copyOf(bytes.size - 1)
        val invalidMarker = bytes.copyOf().also { encoded ->
            ByteBuffer.wrap(encoded).putInt(24, -2)
        }
        val invalidTag = bytes.copyOf().also { encoded ->
            ByteBuffer.wrap(encoded).putInt(firstCommandTagOffset(encoded), 999)
        }
        val invalidLength = bytes.copyOf().also { encoded ->
            ByteBuffer.wrap(encoded).putInt(40, Int.MAX_VALUE)
        }

        assertIs<SceneArchiveDecodeResult.Invalid>(SceneArchiveCodec.decodePicture(truncated))
        val marker = assertIs<SceneArchiveDecodeResult.Invalid>(SceneArchiveCodec.decodePicture(invalidMarker))
        assertEquals("invalid-marker", marker.code)
        val invalid = assertIs<SceneArchiveDecodeResult.Invalid>(SceneArchiveCodec.decodePicture(invalidTag))
        assertEquals("unknown-command", invalid.code)
        val invalidLengthResult = assertIs<SceneArchiveDecodeResult.Invalid>(SceneArchiveCodec.decodePicture(invalidLength))
        assertEquals("invalid-length", invalidLengthResult.code)
    }

    @Test
    fun `historical v8 payload is explicitly distinguished from the IR payload`() {
        val historical = ByteBuffer.allocate(28)
            .put("KPIC".encodeToByteArray())
            .putInt(8)
            .putFloat(0f).putFloat(0f).putFloat(1f).putFloat(1f)
            .putInt(0)
            .array()

        assertEquals(SceneArchiveDecodeResult.LegacyV8, SceneArchiveCodec.decodePicture(historical))
    }

    @Test
    fun `picture archive rejects a mutated draw origin before returning a decoded scene`() {
        val wire = SceneArchiveCodec.encodePicture(validArchiveScene(), RectF32(0f, 0f, 8f, 8f))
        val mutated = replaceFirstUtf8(wire, "RECT", "TEXT")

        val invalid = assertIs<SceneArchiveDecodeResult.Invalid>(SceneArchiveCodec.decodePicture(mutated))

        assertEquals("invalid-draw-origin", invalid.code)
    }

    @Test
    fun `picture archive refuses semantically mismatched draw origins before encoding`() {
        val invalidScene = validArchiveScene(
            DrawNode(
                geometry = GeometryNode.Rect.of(RectF32(0f, 0f, 8f, 8f)),
                material = MaterialNode.Solid(ColorARGB.Red),
                coverage = CoverageRequest.DEFAULT,
                clip = ClipStackNode.Empty,
                blend = BlendNode.SrcOver,
                effects = EffectStack.Empty,
                transform = Matrix3x3F32.Identity,
                origin = DrawOrigin.TEXT,
                paint = solidPaint(),
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            SceneArchiveCodec.encodePicture(invalidScene, RectF32(0f, 0f, 8f, 8f))
        }
    }

    @Test
    fun `picture archive enforces public draw field requirements before encoding`() {
        val image = ImageResourceSnapshot.fromPixels(
            sourceId = "other",
            width = 1,
            height = 1,
            pixelFormat = ImagePixelFormat.RGBA_8888,
            alphaType = ImageAlphaType.PREMUL,
            colorSpace = ColorSpace.SRGB,
            rowBytes = 4,
            pixels = ByteArray(4),
        )
        val matchingImage = ImageResourceSnapshot.fromPixels(
            sourceId = "pixels",
            width = 1,
            height = 1,
            pixelFormat = ImagePixelFormat.RGBA_8888,
            alphaType = ImageAlphaType.PREMUL,
            colorSpace = ColorSpace.SRGB,
            rowBytes = 4,
            pixels = ByteArray(4),
        )
        val pixelReference = ResourceReference(ResourceId("pixels"))
        val imagePatch = GeometryNode.ImagePatch.of(pixelReference, RectF32(0f, 0f, 1f, 1f), RectF32(0f, 0f, 8f, 8f))
        val atlas = GeometryNode.Atlas.of(
            pixelReference,
            listOf(GeometryNode.AtlasEntry.of(Matrix3x3F32.Identity, RectF32(0f, 0f, 1f, 1f))),
        )
        val verticesWithBounds = GeometryNode.IndexedMesh.of(
            MeshPrimitiveMode.TRIANGLES,
            listOf(Point2F32(0f, 0f)),
            bounds = RectF32(0f, 0f, 1f, 1f),
        )
        val meshWithoutBounds = GeometryNode.IndexedMesh.of(MeshPrimitiveMode.TRIANGLES, listOf(Point2F32(0f, 0f)))
        val text = GeometryNode.TextBlob.of(emptyList(), 0f, 0f)
        val picture = GeometryNode.Picture.of(SceneSnapshot.of(SceneExtent(1, 1), ColorSpace.SRGB, emptyList()), RectF32(0f, 0f, 1f, 1f))

        listOf(
            "invalid-draw-paint" to drawNode(text, DrawOrigin.TEXT, paint = null),
            "invalid-draw-resource" to drawNode(imagePatch, DrawOrigin.IMAGE, resource = image, paint = null),
            "invalid-draw-operation-blend" to drawNode(atlas, DrawOrigin.ATLAS, resource = matchingImage, paint = null),
            "invalid-draw-origin" to drawNode(verticesWithBounds, DrawOrigin.VERTICES),
            "invalid-draw-origin" to drawNode(meshWithoutBounds, DrawOrigin.MESH),
            "invalid-draw-resource" to drawNode(picture, DrawOrigin.PICTURE, resource = image, paint = null),
        ).forEach { (expectedCode, node) ->
            val failure = assertFailsWith<IllegalArgumentException> {
                SceneArchiveCodec.encodePicture(validArchiveScene(node), RectF32(0f, 0f, 8f, 8f))
            }

            assertEquals("Scene archive semantic validation failed: $expectedCode", failure.message)
        }
    }

    @Test
    fun `picture archive enforces balanced layers before encoding`() {
        val scene = SceneSnapshot.of(SceneExtent(8, 8), ColorSpace.SRGB, listOf(SceneCommand.EndLayer))

        val failure = assertFailsWith<IllegalArgumentException> {
            SceneArchiveCodec.encodePicture(scene, RectF32(0f, 0f, 8f, 8f))
        }

        assertEquals("Scene archive semantic validation failed: invalid-layer-balance", failure.message)
    }

    @Test
    fun `picture archive rejects malformed UTF 8 text`() {
        val wire = SceneArchiveCodec.encodePicture(validArchiveScene(), RectF32(0f, 0f, 8f, 8f))
        val mutated = wire.copyOf().also { bytes ->
            val offset = indexOf(bytes, ColorSpace.SRGB.name.encodeToByteArray())
            bytes[offset] = 0xC3.toByte()
            bytes[offset + 1] = 0x28
        }

        val invalid = assertIs<SceneArchiveDecodeResult.Invalid>(SceneArchiveCodec.decodePicture(mutated))

        assertEquals("invalid-utf8", invalid.code)
    }

    @Test
    fun `picture archive rejects an unpaired UTF 16 surrogate before encoding`() {
        val scene = SceneSnapshot.of(
            SceneExtent(1, 1),
            ColorSpace.SRGB,
            listOf(SceneCommand.Annotation.of(RectF32(0f, 0f, 1f, 1f), "key", "\uD800")),
        )

        val failure = assertFailsWith<IllegalArgumentException> {
            SceneArchiveCodec.encodePicture(scene, RectF32(0f, 0f, 1f, 1f))
        }

        assertEquals("Scene archive text is not valid UTF-8", failure.message)
    }

    @Test
    fun `picture archive preserves valid non ASCII UTF 8 text`() {
        val scene = SceneSnapshot.of(
            SceneExtent(1, 1),
            ColorSpace.SRGB,
            listOf(SceneCommand.Annotation.of(RectF32(0f, 0f, 1f, 1f), "café", "東京")),
        )

        val decoded = assertIs<SceneArchiveDecodeResult.Decoded>(
            SceneArchiveCodec.decodePicture(SceneArchiveCodec.encodePicture(scene, RectF32(0f, 0f, 1f, 1f))),
        )

        assertEquals(scene.canonicalId, decoded.scene.canonicalId)
    }

    @Test
    fun `picture archive refuses an effect stack above the default graph limit on encode`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            SceneArchiveCodec.encodePicture(
                validArchiveScene(effects = EffectStack.of(List(4_097) { ColorFilterNode.Luma })),
                RectF32(0f, 0f, 8f, 8f),
            )
        }

        assertEquals("Scene archive semantic validation failed: graph-node-limit", failure.message)
    }

    @Test
    fun `picture archive rejects an oversized effect stack before returning decoded data`() {
        val baseline = SceneArchiveCodec.encodePicture(
            validArchiveScene(effects = EffectStack.of(listOf(ColorFilterNode.Luma))),
            RectF32(0f, 0f, 8f, 8f),
        )
        val oversized = expandEffectStack(baseline, 4_097)

        val invalid = assertIs<SceneArchiveDecodeResult.Invalid>(SceneArchiveCodec.decodePicture(oversized))

        assertEquals("graph-node-limit", invalid.code)
        assertEquals("Scene graph exceeds 4096 nodes", invalid.message)
    }

    @Test
    fun `picture archive round trips the 64 scene depth boundary`() {
        val scene = nestedPictureScene(64)

        val decoded = assertIs<SceneArchiveDecodeResult.Decoded>(
            SceneArchiveCodec.decodePicture(SceneArchiveCodec.encodePicture(scene, RectF32(0f, 0f, 1f, 1f))),
        )

        assertEquals(scene.canonicalId, decoded.scene.canonicalId)
    }

    @Test
    fun `picture archive rejects the 65 scene depth boundary semantically`() {
        val scene = nestedPictureScene(65)

        val failure = assertFailsWith<IllegalArgumentException> {
            SceneArchiveCodec.encodePicture(scene, RectF32(0f, 0f, 1f, 1f))
        }

        assertEquals("Scene archive semantic validation failed: graph-depth-limit", failure.message)
    }

    @Test
    fun `picture archive bounds every mesh program child family before encoding`() {
        listOf(
            meshProgramWithBlenderChildren(4_097),
            meshProgramWithColorFilterChildren(4_097),
            meshProgramWithShaderChildren(4_097),
        ).forEach { program ->
            val failure = assertFailsWith<IllegalArgumentException> {
                SceneArchiveCodec.encodePicture(meshProgramScene(program), RectF32(0f, 0f, 1f, 1f))
            }

            assertEquals("Scene archive semantic validation failed: graph-node-limit", failure.message)
        }
    }

    @Test
    fun `picture archive bounds mesh program children before returning decoded data`() {
        val validWire = SceneArchiveCodec.encodePicture(
            meshProgramScene(meshProgramWithBlenderChildren(1)),
            RectF32(0f, 0f, 1f, 1f),
        )

        val invalid = assertIs<SceneArchiveDecodeResult.Invalid>(
            SceneArchiveCodec.decodePicture(expandMeshProgramBlenderChildren(validWire, 4_097)),
        )

        assertEquals("graph-node-limit", invalid.code)
    }

    @Test
    fun `picture archive rejects opacity only in public shader positions`() {
        val opacity = MaterialNode.Opacity(MaterialNode.Solid(ColorARGB.Red), 0.5f)
        val neutralDraw = validArchiveScene().let { scene ->
            SceneSnapshot.of(
                scene.extent,
                scene.colorSpace,
                listOf(SceneCommand.Draw((scene.first() as SceneCommand.Draw).node.copy(material = opacity))),
            )
        }
        val neutralLayer = SceneSnapshot.of(
            SceneExtent(1, 1),
            ColorSpace.SRGB,
            listOf(SceneCommand.BeginLayer(LayerDescriptor.of(material = opacity)), SceneCommand.EndLayer),
        )
        val paintShader = validArchiveScene(drawNode(GeometryNode.Rect.of(RectF32(0f, 0f, 1f, 1f)), DrawOrigin.RECT, solidPaint().copy(shader = opacity)))
        val layerPaintShader = SceneSnapshot.of(
            SceneExtent(1, 1),
            ColorSpace.SRGB,
            listOf(SceneCommand.BeginLayer(LayerDescriptor.of(paint = solidPaint().copy(shader = opacity))), SceneCommand.EndLayer),
        )
        val maskShader = validArchiveScene(drawNode(GeometryNode.Rect.of(RectF32(0f, 0f, 1f, 1f)), DrawOrigin.RECT, solidPaint().copy(maskFilter = MaskFilterNode.Shader(opacity))))
        val meshShader = meshProgramScene(meshProgramWithShaderChild(opacity))
        val blendShader = validArchiveScene(drawNode(GeometryNode.Rect.of(RectF32(0f, 0f, 1f, 1f)), DrawOrigin.RECT, solidPaint().copy(shader = MaterialNode.Blend(BlendMode.SRC_OVER, MaterialNode.Solid(ColorARGB.Blue), opacity))))
        val runtimeShader = validArchiveScene(
            drawNode(
                GeometryNode.Rect.of(RectF32(0f, 0f, 1f, 1f)),
                DrawOrigin.RECT,
                solidPaint().copy(
                    shader = MaterialNode.RuntimeEffect.of(
                        descriptor("opacity-child", RuntimeEffectAbi.SHADER, RuntimeChildSlot("child", RuntimeChildType.SHADER)),
                        mapOf("u" to RuntimeUniformValue.F1(1f)),
                        listOf(RuntimeMaterialChild("child", opacity)),
                    ),
                ),
            ),
        )

        listOf(neutralDraw, neutralLayer).forEach { scene ->
            assertIs<SceneArchiveDecodeResult.Decoded>(SceneArchiveCodec.decodePicture(SceneArchiveCodec.encodePicture(scene, RectF32(0f, 0f, 1f, 1f))))
        }
        listOf(paintShader, layerPaintShader, maskShader, meshShader, blendShader, runtimeShader).forEach { scene ->
            val failure = assertFailsWith<IllegalArgumentException> {
                SceneArchiveCodec.encodePicture(scene, RectF32(0f, 0f, 1f, 1f))
            }
            assertEquals("Scene archive semantic validation failed: invalid-shader-material", failure.message)
        }
    }

    @Test
    fun `picture archive rejects a mutated opacity material in a public shader`() {
        val marker = ColorARGB.fromPackedInt(0x13579BDF)
        val valid = validArchiveScene(
            drawNode(
                GeometryNode.Rect.of(RectF32(0f, 0f, 1f, 1f)),
                DrawOrigin.RECT,
                solidPaint().copy(shader = MaterialNode.Solid(marker)),
            ),
        )
        val wire = SceneArchiveCodec.encodePicture(valid, RectF32(0f, 0f, 1f, 1f))
        val invalidWire = replaceFirst(
            wire,
            ByteBuffer.allocate(8).putInt(2).putInt(marker.value.toInt()).array(),
            ByteBuffer.allocate(16).putInt(12).putInt(2).putInt(marker.value.toInt()).putFloat(0.5f).array(),
        )

        val invalid = assertIs<SceneArchiveDecodeResult.Invalid>(SceneArchiveCodec.decodePicture(invalidWire))

        assertEquals("invalid-shader-material", invalid.code)
    }

    @Test
    fun `picture archive restores recursive public shader materials`() {
        val bounds = RectF32(0f, 0f, 1f, 1f)
        val child = MaterialNode.Blend(
            BlendMode.SRC_OVER,
            MaterialNode.Solid(ColorARGB.Red),
            MaterialNode.WithLocalMatrix(MaterialNode.Solid(ColorARGB.Blue), Matrix3x3F32.Identity),
        )
        val descriptor = descriptor("recursive-shader", RuntimeEffectAbi.SHADER, RuntimeChildSlot("child", RuntimeChildType.SHADER))
        val shader = MaterialNode.RuntimeEffect.of(
            descriptor,
            mapOf("u" to RuntimeUniformValue.F1(1f)),
            listOf(RuntimeMaterialChild("child", child)),
        )
        val scene = validArchiveScene(drawNode(GeometryNode.Rect.of(bounds), DrawOrigin.RECT, solidPaint().copy(shader = shader)))

        val decoded = assertIs<SceneArchiveDecodeResult.Decoded>(
            SceneArchiveCodec.decodePicture(SceneArchiveCodec.encodePicture(scene, bounds)),
        )

        assertEquals(scene.canonicalId, decoded.scene.canonicalId)
    }

    private fun validArchiveScene(
        node: DrawNode = DrawNode(
            geometry = GeometryNode.Rect.of(RectF32(0f, 0f, 8f, 8f)),
            material = MaterialNode.Solid(ColorARGB.Red),
            coverage = CoverageRequest.DEFAULT,
            clip = ClipStackNode.Empty,
            blend = BlendNode.SrcOver,
            effects = EffectStack.Empty,
            transform = Matrix3x3F32.Identity,
            origin = DrawOrigin.RECT,
            paint = solidPaint(),
        ),
        effects: EffectStack = node.effects,
    ): SceneSnapshot = SceneSnapshot.of(
        SceneExtent(8, 8),
        ColorSpace.SRGB,
        listOf(SceneCommand.Draw(node.copy(effects = effects))),
    )

    private fun drawNode(
        geometry: GeometryNode,
        origin: DrawOrigin,
        paint: PaintNode? = solidPaint(),
        resource: ImageResourceSnapshot? = null,
        operationBlendMode: BlendMode? = null,
    ): DrawNode = DrawNode(
        geometry = geometry,
        material = MaterialNode.Solid(ColorARGB.Red),
        coverage = CoverageRequest.DEFAULT,
        clip = ClipStackNode.Empty,
        blend = BlendNode.SrcOver,
        effects = EffectStack.Empty,
        transform = Matrix3x3F32.Identity,
        origin = origin,
        paint = paint,
        resource = resource,
        operationBlendMode = operationBlendMode,
    )

    private fun solidPaint(): PaintNode = PaintNode(
        color = ColorARGB.Red,
        shader = null,
        blendMode = BlendMode.SRC_OVER,
        blender = null,
        colorFilter = null,
        maskFilter = null,
        pathEffect = null,
        imageFilter = null,
        style = PaintStyleNode.FILL,
        strokeWidth = 1f,
        strokeCap = StrokeCapNode.BUTT,
        strokeJoin = StrokeJoinNode.MITER,
        strokeMiter = 4f,
        antiAlias = true,
    )

    private fun replaceFirstUtf8(bytes: ByteArray, original: String, replacement: String): ByteArray {
        require(original.encodeToByteArray().size == replacement.encodeToByteArray().size)
        return bytes.copyOf().also { copy ->
            val offset = indexOf(copy, original.encodeToByteArray())
            replacement.encodeToByteArray().copyInto(copy, offset)
        }
    }

    private fun expandEffectStack(bytes: ByteArray, count: Int): ByteArray {
        val stack = ByteBuffer.allocate(16).putInt(2).putInt(1).putInt(1).putInt(11).array()
        val offset = indexOf(bytes, stack)
        val repeatedEffect = ByteBuffer.allocate(8).putInt(1).putInt(11).array()
        val expanded = ByteArray(bytes.size + (count - 1) * repeatedEffect.size)
        bytes.copyInto(expanded, endIndex = offset + stack.size)
        repeat(count - 1) { index ->
            repeatedEffect.copyInto(expanded, offset + stack.size + index * repeatedEffect.size)
        }
        bytes.copyInto(expanded, offset + stack.size + (count - 1) * repeatedEffect.size, offset + stack.size)
        ByteBuffer.wrap(expanded).putInt(offset + 4, count)
        return expanded
    }

    private fun expandMeshProgramBlenderChildren(bytes: ByteArray, count: Int): ByteArray {
        val slot = meshProgramSlot("b0000")
        val child = meshProgramBlenderChild("b0000")
        val slots = ByteBuffer.allocate(4 + slot.size).putInt(1).put(slot).array()
        val children = ByteBuffer.allocate(4 + child.size).putInt(1).put(child).array()
        val slotsOffset = indexOf(bytes, slots)
        val childrenOffset = indexOf(bytes, children, slotsOffset + slots.size)
        val allSlots = ByteArrayOutputStream().also { output ->
            repeat(count) { index -> output.write(meshProgramSlot(meshChildName(index))) }
        }.toByteArray()
        val allChildren = ByteArrayOutputStream().also { output ->
            repeat(count) { index -> output.write(meshProgramBlenderChild(meshChildName(index))) }
        }.toByteArray()
        return ByteArrayOutputStream().also { output ->
            output.write(bytes, 0, slotsOffset)
            output.write(ByteBuffer.allocate(4).putInt(count).array())
            output.write(allSlots)
            output.write(bytes, slotsOffset + slots.size, childrenOffset - (slotsOffset + slots.size))
            output.write(ByteBuffer.allocate(4).putInt(count).array())
            output.write(allChildren)
            output.write(bytes, childrenOffset + children.size, bytes.size - (childrenOffset + children.size))
        }.toByteArray()
    }

    private fun meshProgramSlot(name: String): ByteArray {
        val encodedName = name.encodeToByteArray()
        val encodedType = RuntimeChildType.BLENDER.name.encodeToByteArray()
        return ByteBuffer.allocate(4 + encodedName.size + 4 + encodedType.size)
            .putInt(encodedName.size).put(encodedName)
            .putInt(encodedType.size).put(encodedType)
            .array()
    }

    private fun meshProgramBlenderChild(name: String): ByteArray {
        val encodedName = name.encodeToByteArray()
        val encodedMode = BlendMode.SRC_OVER.name.encodeToByteArray()
        return ByteBuffer.allocate(4 + encodedName.size + 4 + 4 + 4 + encodedMode.size)
            .putInt(encodedName.size).put(encodedName)
            .putInt(3)
            .putInt(1)
            .putInt(encodedMode.size).put(encodedMode)
            .array()
    }

    private fun nestedPictureScene(sceneDepth: Int): SceneSnapshot {
        require(sceneDepth > 0)
        var scene = SceneSnapshot.of(SceneExtent(1, 1), ColorSpace.SRGB, emptyList())
        repeat(sceneDepth - 1) {
            scene = SceneSnapshot.of(
                SceneExtent(1, 1),
                ColorSpace.SRGB,
                listOf(SceneCommand.Draw(drawNode(GeometryNode.Picture.of(scene, RectF32(0f, 0f, 1f, 1f)), DrawOrigin.PICTURE, paint = null))),
            )
        }
        return scene
    }

    private fun meshProgramScene(program: MeshProgramNode): SceneSnapshot = validArchiveScene(
        drawNode(
            GeometryNode.IndexedMesh.of(
                primitiveMode = MeshPrimitiveMode.TRIANGLES,
                vertices = listOf(Point2F32(0f, 0f)),
                bounds = RectF32(0f, 0f, 1f, 1f),
                meshProgram = program,
            ),
            DrawOrigin.MESH,
        ),
    )

    private fun meshProgramWithBlenderChildren(count: Int): MeshProgramNode {
        val names = List(count, ::meshChildName)
        val descriptor = RuntimeEffectDescriptor.of(
            RuntimeEffectId("mesh-child-limit"),
            RuntimeEffectAbi.SHADER,
            RuntimeUniformLayout.of(emptyList()),
            names.map { RuntimeChildSlot(it, RuntimeChildType.BLENDER) },
        )
        return MeshProgramNode.of(
            descriptor,
            emptyMap(),
            names.map { MeshProgramChild.Blender(it, BlenderNode.Mode(BlendMode.SRC_OVER)) },
        )
    }

    private fun meshProgramWithColorFilterChildren(count: Int): MeshProgramNode {
        val names = List(count, ::meshChildName)
        val descriptor = RuntimeEffectDescriptor.of(
            RuntimeEffectId("mesh-color-filter-limit"),
            RuntimeEffectAbi.SHADER,
            RuntimeUniformLayout.of(emptyList()),
            names.map { RuntimeChildSlot(it, RuntimeChildType.COLOR_FILTER) },
        )
        return MeshProgramNode.of(
            descriptor,
            emptyMap(),
            names.map { MeshProgramChild.ColorFilter(it, ColorFilterNode.Luma) },
        )
    }

    private fun meshProgramWithShaderChildren(count: Int): MeshProgramNode {
        val names = List(count, ::meshChildName)
        val descriptor = RuntimeEffectDescriptor.of(
            RuntimeEffectId("mesh-shader-limit"),
            RuntimeEffectAbi.SHADER,
            RuntimeUniformLayout.of(emptyList()),
            names.map { RuntimeChildSlot(it, RuntimeChildType.SHADER) },
        )
        return MeshProgramNode.of(
            descriptor,
            emptyMap(),
            names.map { MeshProgramChild.Shader(it, MaterialNode.Solid(ColorARGB.Red)) },
        )
    }

    private fun meshProgramWithShaderChild(material: MaterialNode): MeshProgramNode {
        val descriptor = descriptor("mesh-shader", RuntimeEffectAbi.SHADER, RuntimeChildSlot("shader", RuntimeChildType.SHADER))
        return MeshProgramNode.of(
            descriptor,
            mapOf("u" to RuntimeUniformValue.F1(1f)),
            listOf(MeshProgramChild.Shader("shader", material)),
        )
    }

    private fun meshChildName(index: Int): String = "b${index.toString().padStart(4, '0')}"

    private fun replaceFirst(bytes: ByteArray, expected: ByteArray, replacement: ByteArray): ByteArray {
        val offset = indexOf(bytes, expected)
        return ByteArray(bytes.size - expected.size + replacement.size).also { rewritten ->
            bytes.copyInto(rewritten, endIndex = offset)
            replacement.copyInto(rewritten, offset)
            bytes.copyInto(rewritten, offset + replacement.size, offset + expected.size)
        }
    }

    private fun indexOf(bytes: ByteArray, expected: ByteArray): Int {
        return indexOf(bytes, expected, 0)
    }

    private fun indexOf(bytes: ByteArray, expected: ByteArray, startOffset: Int): Int {
        val offset = bytes.indices.firstOrNull { start ->
            start >= startOffset && start + expected.size <= bytes.size && expected.indices.all { index -> bytes[start + index] == expected[index] }
        }
        return requireNotNull(offset) { "Expected payload fragment is absent" }
    }

    private fun firstCommandTagOffset(bytes: ByteArray): Int {
        var offset = 4 + 4 + 16 + 4 + 4 + 4 + 4
        repeat(3) {
            val length = ByteBuffer.wrap(bytes, offset, 4).int
            offset += 4 + length
        }
        return offset + 4
    }

    private fun exhaustiveScene(): SceneSnapshot {
        val bounds = RectF32(1f, 2f, 30f, 40f)
        val image = ImageResourceSnapshot.fromPixels(
            sourceId = "pixels",
            width = 2,
            height = 2,
            pixelFormat = ImagePixelFormat.RGBA_8888,
            alphaType = ImageAlphaType.PREMUL,
            colorSpace = ColorSpace.SRGB,
            rowBytes = 8,
            pixels = ByteArray(16) { it.toByte() },
        )
        val external = ExternalImageReference.of("external", 0, 0, ImagePixelFormat.UNKNOWN, ImageAlphaType.UNKNOWN, ColorSpace.DISPLAY_P3)
        val path = PathBuilder().moveTo(1f, 2f).lineTo(3f, 4f).quadTo(5f, 6f, 7f, 8f)
            .cubicTo(9f, 10f, 11f, 12f, 13f, 14f).arcTo(2f, 3f, 45f, true, false, 15f, 16f).close().build()
        val nested = SceneSnapshot.of(SceneExtent(1, 1), ColorSpace.SRGB, listOf(SceneCommand.Clear(org.graphiks.math.color.ColorF32.Black)))
        val shaderDescriptor = descriptor("shader", RuntimeEffectAbi.SHADER, RuntimeChildSlot("child", RuntimeChildType.SHADER))
        val colorDescriptor = descriptor("color", RuntimeEffectAbi.COLOR_FILTER, RuntimeChildSlot("child", RuntimeChildType.COLOR_FILTER))
        val imageDescriptor = descriptor("image", RuntimeEffectAbi.IMAGE_FILTER, RuntimeChildSlot("child", RuntimeChildType.IMAGE_FILTER))
        val uniforms = linkedMapOf("u" to RuntimeUniformValue.F1(1f))
        val everyUniform = linkedMapOf(
            "f1" to RuntimeUniformValue.F1(1f),
            "f2" to RuntimeUniformValue.F2(1f, 2f),
            "f3" to RuntimeUniformValue.F3(1f, 2f, 3f),
            "f4" to RuntimeUniformValue.F4(1f, 2f, 3f, 4f),
            "i1" to RuntimeUniformValue.I1(5),
            "m3" to RuntimeUniformValue.M3(Matrix3x3F32.Identity),
            "m4" to RuntimeUniformValue.M4(FloatArray(16) { it.toFloat() }),
        )
        val materialChild = MaterialNode.Solid(ColorARGB.Green)
        val colorChild = ColorFilterNode.Blend(ColorARGB.Blue, BlendMode.SRC_OVER)
        val imageChild = ImageFilterNode.Blur(1f, 2f)
        val runtimeMaterial = MaterialNode.RuntimeEffect.of(shaderDescriptor, uniforms, listOf(RuntimeMaterialChild("child", materialChild)))
        val allUniformMaterial = MaterialNode.RuntimeEffect.of(
            descriptor("all-uniforms", RuntimeEffectAbi.SHADER, RuntimeChildSlot("child", RuntimeChildType.SHADER), everyUniform),
            everyUniform,
            listOf(RuntimeMaterialChild("child", materialChild)),
        )
        val runtimeColor = ColorFilterNode.RuntimeEffect.of(colorDescriptor, uniforms, listOf(RuntimeColorFilterChild("child", colorChild)))
        val runtimeImage = ImageFilterNode.RuntimeEffect.of(imageDescriptor, uniforms, null, listOf(RuntimeImageFilterChild("child", imageChild)))
        val meshProgram = MeshProgramNode.of(shaderDescriptor, uniforms, listOf(MeshProgramChild.Shader("child", materialChild)))
        val filters = listOf<ImageFilterNode>(
            ImageFilterNode.Crop.of(bounds), ImageFilterNode.Blur(1f, 2f), ImageFilterNode.DropShadow(1f, 2f, 3f, 4f, ColorARGB.Red),
            ImageFilterNode.ColorFilter(colorChild), ImageFilterNode.Compose(imageChild, ImageFilterNode.Offset(1f, 2f)),
            ImageFilterNode.Blend(BlendMode.SCREEN, imageChild, ImageFilterNode.Offset(3f, 4f)), ImageFilterNode.Dilate(1f, 2f), ImageFilterNode.Erode(1f, 2f),
            ImageFilterNode.DistantLitDiffuse(1f, 2f, ColorARGB.Red, 3f, 4f), ImageFilterNode.PointLitDiffuse(Point2F32(1f, 2f), ColorARGB.Red, 3f, 4f),
            ImageFilterNode.SpotLitDiffuse(Point2F32(1f, 2f), Point2F32(3f, 4f), 5f, 6f, ColorARGB.Red, 7f, 8f),
            ImageFilterNode.DistantLitSpecular(1f, 2f, ColorARGB.Red, 3f, 4f, 5f), ImageFilterNode.PointLitSpecular(Point2F32(1f, 2f), ColorARGB.Red, 3f, 4f, 5f),
            ImageFilterNode.SpotLitSpecular(Point2F32(1f, 2f), Point2F32(3f, 4f), 5f, 6f, ColorARGB.Red, 7f, 8f, 9f),
            ImageFilterNode.Offset(1f, 2f), ImageFilterNode.Tile.of(bounds, RectF32(2f, 3f, 20f, 30f)), ImageFilterNode.Merge.of(listOf(imageChild)),
            ImageFilterNode.DisplacementMap(ColorChannel.RED, ColorChannel.GREEN, 2f, imageChild), ImageFilterNode.Picture.of(nested, bounds, RectF32(2f, 3f, 4f, 5f)),
            ImageFilterNode.Magnifier.of(bounds, 2f, 1f), ImageFilterNode.MatrixConvolution.of(SizeF32(1f, 1f), ImmutableFloats.copyOf(floatArrayOf(1f)), 1f, 0f, Vector2F32(0f, 0f), TileMode.CLAMP, false),
            runtimeImage,
        )
        val allEffects = EffectStack.of(
            listOf(
                ColorFilterNode.Matrix(ImmutableFloats.copyOf(FloatArray(20) { it.toFloat() })),
                ColorFilterNode.Compose(colorChild, ColorFilterNode.Table(ImmutableUBytes.copyOf(ubyteArrayOf(1u)))),
                ColorFilterNode.Lighting(ColorARGB.Red, ColorARGB.Blue), ColorFilterNode.SRGBToLinear, ColorFilterNode.LinearToSRGB,
                ColorFilterNode.HSLAMatrix(ImmutableFloats.copyOf(floatArrayOf(1f))), ColorFilterNode.Lerp(0.5f, colorChild, ColorFilterNode.Luma),
                ColorFilterNode.HighContrast, ColorFilterNode.Overdraw, runtimeColor,
                MaskFilterNode.Blur(MaskBlurStyle.OUTER, 2f), MaskFilterNode.Shader(materialChild), MaskFilterNode.Table(ImmutableUBytes.copyOf(ubyteArrayOf(2u))),
                PathEffectNode.Dash(ImmutableFloats.copyOf(floatArrayOf(1f, 2f)), 0.5f), PathEffectNode.Corner(2f), PathEffectNode.Discrete(3f, 4f),
                PathEffectNode.Path1D(path, 5f, 6f, Path1DStyle.MORPH), PathEffectNode.Path2D(Matrix3x3F32.Identity, path), PathEffectNode.Trim(0.1f, 0.9f),
            ) + filters,
        )
        val paint = PaintNode(
            color = ColorARGB.Magenta,
            shader = runtimeMaterial,
            blendMode = BlendMode.OVERLAY,
            blender = BlenderNode.Arithmetic(1f, 2f, 3f, 4f),
            colorFilter = runtimeColor,
            maskFilter = MaskFilterNode.Blur(MaskBlurStyle.NORMAL, 1f),
            pathEffect = PathEffectNode.Path2D(Matrix3x3F32.Identity, path),
            imageFilter = runtimeImage,
            style = PaintStyleNode.STROKE_AND_FILL,
            strokeWidth = 2f,
            strokeCap = StrokeCapNode.ROUND,
            strokeJoin = StrokeJoinNode.BEVEL,
            strokeMiter = 3f,
            antiAlias = false,
        )
        val rect = GeometryNode.Rect.of(bounds)
        val rrect = GeometryNode.RRect.of(org.graphiks.math.geometry.RRectF32.of(bounds, 2f))
        val doubleRRect = GeometryNode.DoubleRRect.of(
            org.graphiks.math.geometry.RRectF32.of(bounds, 2f),
            org.graphiks.math.geometry.RRectF32.of(RectF32(3f, 4f, 20f, 25f), 1f),
        )
        val point = GeometryNode.Points.of(PointMode.POINTS, listOf(Point2F32(1f, 2f)))
        val points = GeometryNode.Points.of(PointMode.LINES, listOf(Point2F32(1f, 2f), Point2F32(3f, 4f)))
        val vertices = GeometryNode.IndexedMesh.of(
            MeshPrimitiveMode.TRIANGLES,
            listOf(Point2F32(0f, 0f)),
            listOf(Point2F32(1f, 1f)),
            listOf(ColorARGB.Red),
            intArrayOf(),
        )
        val mesh = GeometryNode.IndexedMesh.of(
            MeshPrimitiveMode.TRIANGLES,
            listOf(Point2F32(0f, 0f)),
            listOf(Point2F32(1f, 1f)),
            listOf(ColorARGB.Red),
            intArrayOf(),
            bounds,
            meshProgram = meshProgram,
        )
        val pixelPatch = GeometryNode.ImagePatch.of(ResourceReference(ResourceId("pixels")), bounds, RectF32(2f, 3f, 20f, 30f))
        val externalPatch = GeometryNode.ImagePatch.of(ResourceReference(ResourceId("external")), bounds, RectF32(2f, 3f, 20f, 30f))
        val lattice = GeometryNode.ImageLattice.of(
            ResourceReference(ResourceId("pixels")),
            intArrayOf(1),
            intArrayOf(2),
            listOf(bounds),
            listOf(ColorARGB.Red),
            listOf(LatticeCellFlag.FIXED_COLOR),
            bounds,
            ImageSampling.Cubic(0.2f, 0.3f),
        )
        val atlas = GeometryNode.Atlas.of(
            ResourceReference(ResourceId("pixels")),
            listOf(GeometryNode.AtlasEntry.of(Matrix3x3F32.Identity, bounds, ColorARGB.Blue)),
        )
        val text = GeometryNode.TextBlob.of(
            listOf(GeometryNode.GlyphRun.of(intArrayOf(2), listOf(Point2F32(3f, 4f)))),
            1f,
            2f,
            TypefaceReference(TypefaceId("font")),
            13f,
            mapOf("wdth" to 90f),
        )
        val picture = GeometryNode.Picture.of(nested, bounds)
        val materials = listOf<MaterialNode>(
            MaterialNode.Transparent, MaterialNode.Solid(ColorARGB.Red), MaterialNode.LinearGradient.of(Point2F32(0f, 0f), Point2F32(1f, 1f), listOf(GradientStop(0f, ColorARGB.Red))),
            MaterialNode.RadialGradient.of(Point2F32(0f, 0f), 1f, listOf(GradientStop(0f, ColorARGB.Red))), MaterialNode.SweepGradient.of(Point2F32(0f, 0f), stops = listOf(GradientStop(0f, ColorARGB.Red))),
            MaterialNode.ConicalGradient.of(Point2F32(0f, 0f), 1f, Point2F32(1f, 1f), 2f, listOf(GradientStop(0f, ColorARGB.Red))), MaterialNode.ImageSample(image),
            MaterialNode.Blend(BlendMode.SRC_OVER, materialChild, MaterialNode.Solid(ColorARGB.Blue)), runtimeMaterial, MaterialNode.WithLocalMatrix(materialChild, Matrix3x3F32.Identity),
            MaterialNode.WithColorFilter(materialChild, colorChild), MaterialNode.Opacity(materialChild, 0.5f), MaterialNode.PerlinNoise(1f, 2f, 3, 4, SizeF32(1f, 2f)),
            MaterialNode.FractalNoise(1f, 2f, 3, 4, null), MaterialNode.WithWorkingColorSpace(materialChild, ColorInterpolation.OKLAB), MaterialNode.CoordClamp(materialChild, bounds), allUniformMaterial,
        )
        return SceneSnapshot.of(
            SceneExtent(64, 64), ColorSpace.DISPLAY_P3,
            buildList {
                add(SceneCommand.Clear(org.graphiks.math.color.ColorF32.of(0.1f, 0.2f, 0.3f, 0.4f)))
                add(SceneCommand.DrawColor(ColorARGB.Cyan, BlendMode.MULTIPLY, Matrix3x3F32.Identity, ClipStackNode.DeviceRect.of(bounds, false)))
                add(SceneCommand.SetTransform(Matrix3x3F32.Identity))
                add(SceneCommand.SetClip(ClipStackNode.Operations.of(listOf(ClipEntry(GeometryNode.Path(path), ClipOperation.DIFFERENCE, false, true, "perspective")))))
                add(
                    SceneCommand.BeginLayer(
                        LayerDescriptor.of(
                            "layer",
                            bounds,
                            runtimeMaterial,
                            paint,
                            BlendNode.Paint(BlendMode.SCREEN, BlenderNode.Mode(BlendMode.XOR)),
                            ClipStackNode.Empty,
                            ClipStackNode.DeviceRect.of(bounds),
                            EffectStack.of(listOf(imageChild)),
                            allEffects,
                            Matrix3x3F32.Identity,
                        ),
                    ),
                )
                fun draw(
                    geometry: GeometryNode,
                    origin: DrawOrigin,
                    resource: ImageResourceSnapshot? = null,
                    operationBlendMode: BlendMode? = null,
                    drawPaint: PaintNode? = solidPaint(),
                ) {
                    add(
                        SceneCommand.Draw(
                            DrawNode(
                                geometry,
                                MaterialNode.Solid(ColorARGB.Black),
                                CoverageRequest.HARD_EDGE,
                                ClipStackNode.Empty,
                                BlendNode.Custom(BlenderNode.Mode(BlendMode.PLUS)),
                                EffectStack.Empty,
                                Matrix3x3F32.Identity,
                                origin,
                                drawPaint,
                                resource,
                                operationBlendMode,
                            ),
                        ),
                    )
                }
                draw(rect, DrawOrigin.RECT)
                draw(rrect, DrawOrigin.RRECT)
                draw(doubleRRect, DrawOrigin.DOUBLE_RRECT)
                draw(GeometryNode.Path(path), DrawOrigin.PATH)
                draw(GeometryNode.Path(path), DrawOrigin.TEXT_EXPANDED_PATH)
                draw(point, DrawOrigin.POINT)
                draw(points, DrawOrigin.POINTS)
                draw(pixelPatch, DrawOrigin.IMAGE, image, drawPaint = null)
                draw(externalPatch, DrawOrigin.IMAGE, external, drawPaint = null)
                draw(pixelPatch, DrawOrigin.IMAGE_NINE, image, drawPaint = null)
                draw(lattice, DrawOrigin.IMAGE_LATTICE, image, drawPaint = null)
                draw(atlas, DrawOrigin.ATLAS, image, BlendMode.DIFFERENCE, null)
                draw(vertices, DrawOrigin.VERTICES)
                draw(mesh, DrawOrigin.MESH)
                draw(text, DrawOrigin.TEXT)
                draw(picture, DrawOrigin.PICTURE, drawPaint = null)
                materials.forEach { material ->
                    add(
                        SceneCommand.Draw(
                            DrawNode(
                                rect,
                                material,
                                CoverageRequest.DEFAULT,
                                ClipStackNode.Empty,
                                BlendNode.Mode(BlendMode.DST_OVER),
                                EffectStack.Empty,
                                Matrix3x3F32.Identity,
                                DrawOrigin.RECT,
                                solidPaint(),
                            ),
                        ),
                    )
                }
                add(SceneCommand.EndLayer)
                add(SceneCommand.State.of("state", linkedMapOf("a" to "b")))
                add(SceneCommand.Annotation.of(bounds, "key", "value"))
                add(SceneCommand.Readback(ReadbackRequest.of("readback", bounds)))
            },
        )
    }

    private fun descriptor(
        id: String,
        abi: RuntimeEffectAbi,
        child: RuntimeChildSlot,
        values: Map<String, RuntimeUniformValue> = mapOf("u" to RuntimeUniformValue.F1(1f)),
    ): RuntimeEffectDescriptor =
        RuntimeEffectDescriptor.of(
            id = RuntimeEffectId(id),
            abi = abi,
            uniformLayout = RuntimeUniformLayout.of(values.entries.mapIndexed { index, (name, value) -> RuntimeUniformSlot(name, index, value.runtimeUniformType(), 0) }),
            childSlots = listOf(child),
            vertexLayout = RuntimeVertexLayout.of(4, listOf(RuntimeVertexAttribute(RuntimeVertexFormat.FLOAT32, 0, 0))),
            module = ShaderModuleDescriptor.of("void main() {}", "main", listOf(RuntimeUniformSlot("m", 1, RuntimeUniformType.FLOAT, 0))),
        )

    private fun RuntimeUniformValue.runtimeUniformType(): RuntimeUniformType = when (this) {
        is RuntimeUniformValue.F1 -> RuntimeUniformType.FLOAT
        is RuntimeUniformValue.F2 -> RuntimeUniformType.FLOAT2
        is RuntimeUniformValue.F3 -> RuntimeUniformType.FLOAT3
        is RuntimeUniformValue.F4 -> RuntimeUniformType.FLOAT4
        is RuntimeUniformValue.I1 -> RuntimeUniformType.INT1
        is RuntimeUniformValue.M3 -> RuntimeUniformType.MAT3X3
        is RuntimeUniformValue.M4 -> RuntimeUniformType.MAT4X4
    }
}
