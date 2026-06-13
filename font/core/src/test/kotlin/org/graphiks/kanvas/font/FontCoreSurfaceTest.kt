package org.graphiks.kanvas.font

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class FontCoreSurfaceTest {
    @Test
    fun exposesCoreFontValueObjectsAndContracts() {
        val sourceUuid = Uuid.parse("550e8400-e29b-41d4-a716-446655440000")
        val typefaceUuid = Uuid.parse("550e8400-e29b-41d4-a716-446655440001")
        val sourceId = FontSourceID(sourceUuid)
        val typefaceId = TypefaceID(typefaceUuid)
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

        assertEquals(sourceUuid, sourceId.value)
        assertEquals(typefaceUuid, typefaceId.value)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", sourceId.value.toHexDashString())
        assertEquals("550e8400-e29b-41d4-a716-446655440001", typefaceId.value.toHexDashString())
        assertEquals(FontSourceKind.SYSTEM, source.kind)
        assertEquals(sourceId, diagnostic.sourceId)
        assertEquals(typefaceId, data.id)
        assertEquals(collection, catalog.families.getValue("Inter"))
        assertEquals(face, run.face)
        assertTrue(request.preferredFamilies.contains("Inter"))
    }
}
