package org.graphiks.kanvas.font

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TypefaceIdentityTest {
    @Test
    fun `same face selected twice derives identical typeface identity and sorted preimage`() {
        val first = fixtureTypefacePreimage(
            tableTags = listOf("name", "cmap", "glyf", "head", "fvar", "cmap"),
            variationCoordinates = listOf(
                TypefaceVariationCoordinate(axisTag = "wght", value = 400.0),
                TypefaceVariationCoordinate(axisTag = "wdth", value = 100.0),
            ),
        )
        val repeated = fixtureTypefacePreimage(
            tableTags = listOf("glyf", "head", "cmap", "name", "fvar"),
            variationCoordinates = listOf(
                TypefaceVariationCoordinate(axisTag = "wdth", value = 100.0),
                TypefaceVariationCoordinate(axisTag = "wght", value = 400.0),
            ),
        )

        assertEquals(first, repeated)
        assertEquals(first.hashCode(), repeated.hashCode())
        assertEquals(first.toCanonicalJson(), repeated.toCanonicalJson())
        assertEquals(first.typefaceId(), repeated.typefaceId())
        assertEquals(listOf("cmap", "fvar", "glyf", "head", "name"), first.tableTags)
        assertEquals(listOf("wdth", "wght"), first.variationCoordinates.map { it.axisTag })
        assertContains(first.toCanonicalJson(), """"schema": "org.graphiks.kanvas.font.TypefaceIdentityPreimage.v1"""")
        assertContains(first.toCanonicalJson(), """"tableTags": ["cmap", "fvar", "glyf", "head", "name"]""")
    }

    @Test
    fun `collection index variation coordinates and palette affect typeface id`() {
        val base = fixtureTypefacePreimage()
        val collectionVariant = fixtureTypefacePreimage(collectionIndex = 1)
        val variationVariant = fixtureTypefacePreimage(
            variationCoordinates = listOf(TypefaceVariationCoordinate(axisTag = "wght", value = 700.0)),
        )
        val paletteVariant = fixtureTypefacePreimage(
            palette = TypefacePaletteSelection(index = 1, overrides = listOf("gid=42:#ff0000ff", "gid=7:#000000ff")),
        )

        assertNotEquals(base.typefaceId(), collectionVariant.typefaceId())
        assertNotEquals(base.typefaceId(), variationVariant.typefaceId())
        assertNotEquals(base.typefaceId(), paletteVariant.typefaceId())
        assertNotEquals(collectionVariant.typefaceId(), variationVariant.typefaceId())
        assertNotEquals(variationVariant.typefaceId(), paletteVariant.typefaceId())
    }

    @Test
    fun `variation zero normalizes equality and typeface id consistently`() {
        val positiveZero = fixtureTypefacePreimage(
            variationCoordinates = listOf(TypefaceVariationCoordinate(axisTag = "wght", value = 0.0)),
        )
        val negativeZero = fixtureTypefacePreimage(
            variationCoordinates = listOf(TypefaceVariationCoordinate(axisTag = "wght", value = -0.0)),
        )

        assertEquals(positiveZero, negativeZero)
        assertEquals(positiveZero.hashCode(), negativeZero.hashCode())
        assertEquals(positiveZero.toCanonicalJson(), negativeZero.toCanonicalJson())
        assertEquals(positiveZero.typefaceId(), negativeZero.typefaceId())
    }

    private fun fixtureTypefacePreimage(
        sourceId: FontSourceID = fixtureSourceId(),
        collectionIndex: Int = 0,
        tableTags: List<String> = listOf("cmap", "glyf", "head", "name"),
        variationCoordinates: List<TypefaceVariationCoordinate> = emptyList(),
        palette: TypefacePaletteSelection? = null,
    ): TypefaceIdentityPreimage =
        typefaceIdentityPreimage(
            sourceId = sourceId,
            collectionIndex = collectionIndex,
            postScriptName = "FixtureSans-Regular",
            familyName = "Fixture Sans",
            styleName = "Regular",
            style = FontStyle(weight = 400, width = 5, slant = FontSlant.UPRIGHT),
            outlineFormat = TypefaceOutlineFormat.TRUE_TYPE_GLYF,
            selectedCMap = TypefaceCMapSelection(
                platformId = 3,
                encodingId = 10,
                format = 12,
                language = 0,
                unicode = true,
            ),
            scalerMode = TypefaceScalerMode.OUTLINE,
            variationCoordinates = variationCoordinates,
            palette = palette,
            fallbackCatalogGeneration = null,
            tableTags = tableTags,
        )

    private fun fixtureSourceId(): FontSourceID =
        fontSourceIdentityPreimage(
            kind = FontSourceKind.BUNDLED_FIXTURE,
            declaredName = "Fixture Sans",
            licenseId = "OFL-1.1",
            contentBytes = byteArrayOf(1, 2, 3),
            faceCount = 1,
            tableTags = listOf("cmap", "head", "name"),
            parserGeneration = 1,
        ).sourceId()

}
