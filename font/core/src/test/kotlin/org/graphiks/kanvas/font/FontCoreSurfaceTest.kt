package org.graphiks.kanvas.font

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FontCoreSurfaceTest {
    @Test
    fun exposesCoreFontValueObjectsAndContracts() {
        val sourceId = FontSourceID("system:inter-regular")
        val typefaceId = TypefaceID("inter-regular")
        val source = FontSource(
            id = sourceId,
            kind = FontSourceKind.SYSTEM,
            displayName = "Inter Regular",
            bytes = ByteArray(0),
        )
        val diagnostic = FontSourceDiagnostic(
            sourceId = sourceId,
            message = "not parsed yet",
        )
        val data = TypefaceData(
            id = typefaceId,
            source = source,
            familyName = "Inter",
            styleName = "Regular",
            diagnostics = listOf(diagnostic),
        )
        val face = FontFace(typeface = data)
        val collection = FontCollection(faces = listOf(face))
        val request = FallbackRequest(
            text = "Hello",
            locale = "en-US",
            preferredFamilies = listOf("Inter"),
        )
        val run = ResolvedFontRun(
            start = 0,
            end = 5,
            face = face,
        )
        val catalog = FallbackCatalog(families = mapOf("Inter" to collection))

        assertEquals("system:inter-regular", sourceId.value)
        assertEquals("inter-regular", typefaceId.value)
        assertEquals(FontSourceKind.SYSTEM, source.kind)
        assertEquals(sourceId, diagnostic.sourceId)
        assertEquals(typefaceId, data.id)
        assertEquals(collection, catalog.families.getValue("Inter"))
        assertEquals(face, run.face)
        assertTrue(request.preferredFamilies.contains("Inter"))
    }
}
