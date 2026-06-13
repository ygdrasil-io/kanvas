package org.graphiks.kanvas.font.sfnt

import org.graphiks.kanvas.font.FontSourceID
import org.graphiks.kanvas.font.FontSourceKind
import org.graphiks.kanvas.font.FontSource
import org.graphiks.kanvas.font.TypefaceID
import kotlin.test.Test
import kotlin.test.assertEquals

class SFNTSurfaceTest {
    @Test
    fun exposesSfntDirectoryAndParsedTableContainers() {
        val tag = SFNTTableTag("name")
        val record = SFNTTableRecord(
            tag = tag,
            checksum = 1u,
            offset = 12u,
            length = 24u,
        )
        val directory = SFNTTableDirectory(
            scalerType = 0x00010000u,
            tables = listOf(record),
        )
        val diagnostic = OpenTypeParseDiagnostic(
            sourceId = FontSourceID("memory:font"),
            message = "table deferred",
        )
        val faceData = OpenTypeFaceData(
            id = TypefaceID("memory-face"),
            source = FontSource(
                id = FontSourceID("memory:font"),
                kind = FontSourceKind.MEMORY,
                displayName = "Memory Font",
                bytes = ByteArray(0),
            ),
            directory = directory,
            cmap = CMapTable(),
            names = NameTable(),
            metrics = MetricsTables(),
            variations = VariationTables(),
            layout = OpenTypeLayoutTables(),
            color = ColorFontTables(),
            diagnostics = listOf(diagnostic),
        )

        assertEquals("name", tag.value)
        assertEquals(record, directory.tables.single())
        assertEquals(directory, faceData.directory)
        assertEquals(diagnostic, faceData.diagnostics.single())
    }
}
