package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Disabled test stub for [PdfTableBasedSubsetGM] (upstream
 * `gm/pdf_never_embed.cpp::pdf_table_based_subset`).
 *
 * The GM's `onDraw` contains a
 * `TODO("STUB.PDF_TABLE_SUBSET_FONTMGR: …")` that throws
 * [NotImplementedError] if called. Running the test against the
 * upstream reference PNG is meaningless until the stub is resolved.
 *
 * Unblock by:
 *  (a) exposing `ToolUtils.TestFontMgr()` backed by a FreeType/JNI
 *      font manager that supports woff/woff2 streams, and
 *  (b) adding `SkCanvas.drawGlyphs(glyphs, positions, clusters,
 *      utf8Text, origin, font, paint)` to the Kotlin API surface.
 */
@Disabled("STUB.PDF_TABLE_SUBSET_FONTMGR: TestFontMgr/woff streams and cluster drawGlyphs overload not in :kanvas-skia")
class PdfTableBasedSubsetTest {

    @Test
    fun `pdf_table_based_subset GM stub`() {
        PdfTableBasedSubsetGM()
    }
}
