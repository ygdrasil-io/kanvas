@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.picture

import java.nio.ByteBuffer
import java.util.Base64
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.DropShadowMode
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.surface.W6bImageBlurCpuOracle
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class W6bFilterPictureTest {
    /**
     * Catches a writer which continues to emit the recursive v13 payload, or
     * a reader which aliases equal filters by value instead of captured identity.
     */
    @Test
    fun picture15PreservesSharedFilterIdentityWithoutValueAliasing() {
        val shared = ImageFilter.Blur(1f, 2f, TileMode.MIRROR)
        val equalButDistinct = ImageFilter.Blur(1f, 2f, TileMode.MIRROR)
        val picture = pictureWithThreeFilteredDraws(shared, shared, equalButDistinct)

        val bytes = picture.toByteArray()
        val memoryFilters = filtersFromPublicTraversal(picture)
        assertSame(memoryFilters[0], memoryFilters[1])
        assertNotSame(memoryFilters[0], memoryFilters[2])
        assertEquals(15, ByteBuffer.wrap(bytes).getInt(4))
        assertEquals(9, ByteBuffer.wrap(bytes).getInt(28))

        val decoded = assertNotNull(Picture.fromByteArray(bytes))
        val wireFilters = filtersFromPublicTraversal(decoded)
        assertSame(wireFilters[0], wireFilters[1])
        assertNotSame(wireFilters[0], wireFilters[2])
        assertContentEquals(bytes, decoded.toByteArray())
    }

    @Test
    fun dropShadowDefaultsToCompositeThroughPictureMemoryAndWireReplay() {
        val shadow = ImageFilter.DropShadow(1f, 2f, 3f, 4f, ColorARGB.Red)
        val picture = pictureWithThreeFilteredDraws(shadow)

        assertEquals(DropShadowMode.COMPOSITE, (filtersFromPublicTraversal(picture).single() as ImageFilter.DropShadow).mode)

        val decoded = assertNotNull(Picture.fromByteArray(picture.toByteArray()))
        assertEquals(DropShadowMode.COMPOSITE, (filtersFromPublicTraversal(decoded).single() as ImageFilter.DropShadow).mode)
    }

    /**
     * Catches a schema-8 payload which aliases roots but recursively rebuilds
     * their common child during replay.
     */
    @Test
    fun picture14PreservesSharedInternalFilterIdentityAcrossDistinctRoots() {
        val child = ImageFilter.Blur(1f, 2f, TileMode.MIRROR)
        val firstRoot = ImageFilter.Offset(3f, 4f, child)
        val secondRoot = ImageFilter.DropShadow(5f, 6f, 7f, 8f, ColorARGB.Blue, child)
        val picture = pictureWithThreeFilteredDraws(firstRoot, secondRoot)

        assertSharedInternalChild(filtersFromPublicTraversal(picture))
        val bytes = picture.toByteArray()
        val decoded = assertNotNull(Picture.fromByteArray(bytes))
        assertSharedInternalChild(filtersFromPublicTraversal(decoded))
        assertContentEquals(bytes, decoded.toByteArray())
    }

    /** Catches a capture or schema-8 reader which silently coerces every shadow to COMPOSITE. */
    @Test
    fun shadowOnlySurvivesPictureMemoryAndWireReplay() {
        val shadow = ImageFilter.DropShadow(
            1f, 2f, 3f, 4f, ColorARGB.Red,
            mode = DropShadowMode.SHADOW_ONLY,
        )
        val picture = pictureWithThreeFilteredDraws(shadow)

        assertEquals(DropShadowMode.SHADOW_ONLY, (filtersFromPublicTraversal(picture).single() as ImageFilter.DropShadow).mode)
        val bytes = picture.toByteArray()
        val decoded = assertNotNull(Picture.fromByteArray(bytes))
        assertEquals(DropShadowMode.SHADOW_ONLY, (filtersFromPublicTraversal(decoded).single() as ImageFilter.DropShadow).mode)
        assertContentEquals(bytes, decoded.toByteArray())
    }

    @Test
    fun schema7DropShadowFixtureDefaultsAndMigratesToSchema8() {
        val decoded = assertNotNull(Picture.fromByteArray(fixture("format-13-drop-shadow-composite.base64")))

        assertEquals(DropShadowMode.COMPOSITE, (filtersFromPublicTraversal(decoded).single() as ImageFilter.DropShadow).mode)
        assertEquals(14, ByteBuffer.wrap(decoded.toByteArray()).getInt(4))
        assertEquals(8, ByteBuffer.wrap(decoded.toByteArray()).getInt(28))
        assertPlaybackRenders(decoded)
    }

    @Test
    fun schema7RecursiveEqualOccurrencesStayDistinctWhenMigratedToSchema8() {
        val decoded = assertNotNull(Picture.fromByteArray(fixture("format-13-recursive-equal-filter-occurrences.base64")))

        val filters = filtersFromPublicTraversal(decoded)
        assertEquals(2, filters.size)
        assertNotSame(filters[0], filters[1])
        assertEquals(14, ByteBuffer.wrap(decoded.toByteArray()).getInt(4))
        assertNotNull(Picture.fromByteArray(decoded.toByteArray()))
        assertPlaybackRenders(decoded)
    }

    @Test
    fun malformedSchema8FilterReferencesAndCyclesAreRejectedByPublicPictureDecode() {
        assertNull(Picture.fromByteArray(corruptedNestedBlurReference(replacement = 2)))
        assertNull(Picture.fromByteArray(corruptedNestedBlurReference(replacement = 0)))
    }

    @Test
    fun pictureMemoryAndWireReplayKeepDistinctBlurredOccurrencesPixelEquivalent() {
        val source = PictureRecorder().also { recorder ->
            recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 7f, 7f)).drawRect(
                RectF32.ofLTRB(3f, 3f, 4f, 4f), Paint(ColorARGB.White, antiAlias = false),
            )
        }.finishRecordingAsPicture()
        val decoded = assertNotNull(Picture.fromByteArray(source.toByteArray()))
        // This independent oracle has two source domains before either Picture is replayed.
        // The same captured Picture occurs twice under different transform and paint state; the
        // second run repeats that exact pair after public wire decode.
        val expected = replayedOccurrencesExpected()

        listOf(source, decoded).forEach { replayedPicture ->
            val pixels = Surface(20, 7).also { surface ->
                surface.canvas {
                    drawPicture(replayedPicture, Paint(imageFilter = ImageFilter.Blur(1f, 1f, TileMode.DECAL)))
                    save()
                    translate(10f, 0f)
                    drawPicture(replayedPicture, Paint(
                        color = ColorARGB.of(128, 255, 255, 255),
                        imageFilter = ImageFilter.Blur(1f, 1f, TileMode.DECAL),
                    ))
                    restore()
                }
            }.render().pixels
            W6bImageBlurCpuOracle.assertNear(expected, pixels)
        }
    }

    @Test
    fun maskShaderAndTableSurvivePictureMemoryAndWireReplay() {
        val table = UByteArray(256)
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 2f, 1f)).apply {
                drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(
                    ColorARGB.Red,
                    maskFilter = MaskFilter.Shader(Shader.SolidColor(ColorARGB.White)),
                    antiAlias = false,
                ))
                drawRect(RectF32.ofLTRB(1f, 0f, 2f, 1f), Paint(
                    ColorARGB.Red,
                    maskFilter = MaskFilter.Table(table),
                    antiAlias = false,
                ))
            }
        }.finishRecordingAsPicture()
        table.fill(255u)

        listOf(picture, assertNotNull(Picture.fromByteArray(picture.toByteArray()))).forEach { replay ->
            val filters = replayMaskFilters(replay)
            assertTrue(filters[0] is MaskFilter.Shader)
            assertContentEquals(UByteArray(256), (filters[1] as MaskFilter.Table).table)
            val pixels = Surface(2, 1).also { surface -> surface.canvas { replay.playback(this) } }.render().pixels
            assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u, 0u, 0u, 0u, 0u), pixels)
        }
    }

    @Test
    fun `Picture capture snapshots mutable crop before bytes replay`() {
        val supplied = RectF32.ofLTRB(1f, 2f, 3f, 4f)
        val picture = pictureWithThreeFilteredDraws(ImageFilter.Crop(supplied, TileMode.CLAMP))
        val wire = picture.toByteArray()

        supplied.left = -100f
        assertContentEquals(wire, picture.toByteArray())

        val replay = assertNotNull(Picture.fromByteArray(wire))
        assertContentEquals(wire, replay.toByteArray())
        assertPlaybackRenders(replay)
    }

    @Test
    fun `oversized schema 8 filter table is rejected before Picture publication`() {
        val archive = oversizedFilterTableArchive()

        assertNull(Picture.fromByteArray(archive))
        assertPlaybackRenders(assertNotNull(Picture.fromByteArray(
            pictureWithThreeFilteredDraws(ImageFilter.Blur(1f, 1f)).toByteArray(),
        )))
    }

    @Test
    fun `oversized schema 8 Merge fanout is rejected before Picture publication`() {
        val archive = oversizedMergeFanoutArchive()

        assertNull(Picture.fromByteArray(archive))
        assertPlaybackRenders(assertNotNull(Picture.fromByteArray(
            pictureWithThreeFilteredDraws(ImageFilter.Blur(1f, 1f)).toByteArray(),
        )))
    }

    private fun replayedOccurrencesExpected(): UByteArray {
        val first = W6bImageBlurCpuOracle.blurredAlpha(20, 7,
            UByteArray(20 * 7).also { it[3 + 3 * 20] = 255u }, 1f, 1f, TileMode.DECAL,
            knownRight = 7,
        )
        val second = W6bImageBlurCpuOracle.blurredAlpha(20, 7,
            UByteArray(20 * 7).also { it[13 + 3 * 20] = 128u }, 1f, 1f, TileMode.DECAL,
            knownLeft = 10, knownRight = 17,
        )
        return W6bImageBlurCpuOracle.toOpaqueWhiteRgba(UByteArray(first.size) { pixel ->
            (first[pixel].toInt() + second[pixel].toInt()).coerceAtMost(255).toUByte()
        })
    }

    private fun pictureWithThreeFilteredDraws(vararg filters: ImageFilter): Picture {
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 3f, 1f))
        filters.forEachIndexed { index, filter ->
            canvas.drawRect(
                RectF32.ofLTRB(index.toFloat(), 0f, index + 1f, 1f),
                Paint(color = ColorARGB.Red, imageFilter = filter, antiAlias = false),
            )
        }
        return recorder.finishRecordingAsPicture()
    }

    private fun filtersFromPublicTraversal(picture: Picture): List<ImageFilter> = buildList {
        picture.forEachOp { operation ->
            val draw = operation as? DisplayOp.DrawRect ?: return@forEachOp
            draw.paint.imageFilter?.let(::add)
        }
    }

    private fun replayMaskFilters(picture: Picture): List<MaskFilter> = buildList {
        picture.forEachOp { operation ->
            val draw = operation as? DisplayOp.DrawRect ?: return@forEachOp
            draw.paint.maskFilter?.let(::add)
        }
    }

    private fun assertSharedInternalChild(filters: List<ImageFilter>) {
        val offset = filters[0] as ImageFilter.Offset
        val shadow = filters[1] as ImageFilter.DropShadow
        assertNotNull(offset.input)
        assertSame(offset.input, shadow.input)
    }

    private fun fixture(name: String): ByteArray = Base64.getDecoder().decode(
        requireNotNull(javaClass.getResource("/picture/$name")).readText().trim(),
    )

    private fun assertPlaybackRenders(picture: Picture) {
        val recorder = PictureRecorder()
        picture.playback(recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 8f, 8f)))
        assertNotNull(Picture.fromByteArray(recorder.finishRecordingAsPicture().toByteArray()))
    }

    private fun oversizedFilterTableArchive(): ByteArray {
        val bytes = pictureWithThreeFilteredDraws(ImageFilter.Blur(1f, 1f)).toByteArray()
        val tableOffset = schema8FilterTableOffset(bytes)
        val buffer = ByteBuffer.wrap(bytes)
        require(buffer.getInt(tableOffset) == 1) { "expected one schema-8 table entry" }
        val nodeStart = tableOffset + 4
        require(buffer.getInt(nodeStart) == 2) { "expected a blur filter table entry" }
        val tileLengthOffset = nodeStart + 12
        val nodeEnd = tileLengthOffset + 4 + buffer.getInt(tileLengthOffset) + 4
        val node = bytes.copyOfRange(nodeStart, nodeEnd)
        val count = 4_097
        val expanded = ByteArray(bytes.size + (count - 1) * node.size)
        bytes.copyInto(expanded, endIndex = nodeStart)
        repeat(count) { indexI32 -> node.copyInto(expanded, nodeStart + indexI32 * node.size) }
        bytes.copyInto(expanded, nodeStart + count * node.size, nodeEnd)
        ByteBuffer.wrap(expanded).putInt(tableOffset, count)
        return expanded
    }

    private fun oversizedMergeFanoutArchive(): ByteArray {
        val bytes = pictureWithThreeFilteredDraws(ImageFilter.Merge(listOf(ImageFilter.Blur(1f, 1f)))).toByteArray()
        val tableOffset = schema8FilterTableOffset(bytes)
        val buffer = ByteBuffer.wrap(bytes)
        require(buffer.getInt(tableOffset) == 2) { "expected Merge and blur schema-8 table entries" }
        val mergeStart = tableOffset + 4
        require(buffer.getInt(mergeStart) == 17) { "expected Merge as schema-8 table root" }
        val fanoutOffset = mergeStart + 4
        require(buffer.getInt(fanoutOffset) == 1) { "expected one Merge input" }
        val inputStart = fanoutOffset + 4
        val inputEnd = inputStart + 8
        val input = bytes.copyOfRange(inputStart, inputEnd)
        val count = 4_097
        val expanded = ByteArray(bytes.size + (count - 1) * input.size)
        bytes.copyInto(expanded, endIndex = inputEnd)
        repeat(count - 1) { indexI32 -> input.copyInto(expanded, inputEnd + indexI32 * input.size) }
        bytes.copyInto(expanded, inputEnd + (count - 1) * input.size, inputEnd)
        ByteBuffer.wrap(expanded).putInt(fanoutOffset, count)
        return expanded
    }

    private fun schema8FilterTableOffset(bytes: ByteArray): Int {
        val buffer = ByteBuffer.wrap(bytes)
        var position = 40 // v14 header, extent width, and extent height.
        repeat(3) { position += 4 + buffer.getInt(position) }
        return position
    }

    private fun corruptedNestedBlurReference(replacement: Int): ByteArray {
        val outer = ImageFilter.Blur(1f, 2f, TileMode.MIRROR, ImageFilter.Blur(1f, 2f, TileMode.MIRROR))
        val bytes = pictureWithThreeFilteredDraws(outer).toByteArray()
        val buffer = ByteBuffer.wrap(bytes)
        var position = 40 // v14 header, extent width, and extent height.
        repeat(3) { position += 4 + buffer.getInt(position) }
        require(buffer.getInt(position) == 2) { "expected two schema-8 table entries" }
        position += 4 // table count
        require(buffer.getInt(position) == 2) { "expected the outer blur as table node zero" }
        position += 12 // blur tag and sigma values
        position += 4 + buffer.getInt(position) // tile-mode string
        require(buffer.getInt(position) == 3 && buffer.getInt(position + 4) == 1) {
            "schema-8 blur input reference was not found"
        }
        ByteBuffer.wrap(bytes).putInt(position + 4, replacement)
        return bytes
    }
}
