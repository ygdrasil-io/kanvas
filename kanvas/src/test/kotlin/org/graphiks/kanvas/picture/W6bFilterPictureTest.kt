package org.graphiks.kanvas.picture

import java.nio.ByteBuffer
import java.util.Base64
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.DropShadowMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

class W6bFilterPictureTest {
    /**
     * Catches a writer which continues to emit the recursive v13 payload, or
     * a reader which aliases equal filters by value instead of captured identity.
     */
    @Test
    fun picture14PreservesSharedFilterIdentityWithoutValueAliasing() {
        val shared = ImageFilter.Blur(1f, 2f, TileMode.MIRROR)
        val equalButDistinct = ImageFilter.Blur(1f, 2f, TileMode.MIRROR)
        val picture = pictureWithThreeFilteredDraws(shared, shared, equalButDistinct)

        val bytes = picture.toByteArray()
        val memoryFilters = filtersFromPublicTraversal(picture)
        assertSame(memoryFilters[0], memoryFilters[1])
        assertNotSame(memoryFilters[0], memoryFilters[2])
        assertEquals(14, ByteBuffer.wrap(bytes).getInt(4))
        assertEquals(8, ByteBuffer.wrap(bytes).getInt(28))

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
        val decoded = assertNotNull(Picture.fromByteArray(picture.toByteArray()))
        assertEquals(DropShadowMode.SHADOW_ONLY, (filtersFromPublicTraversal(decoded).single() as ImageFilter.DropShadow).mode)
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
