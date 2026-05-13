package org.skia.docs

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkPaint
import org.skia.foundation.stream.SkDynamicMemoryWStream
import org.skia.math.SkRect

/**
 * Phase R-suivi.40 — verifies that [SkPDF] honours
 * [SkPDF.Metadata.encryptionPassword] by emitting a standard-security
 * `/Encrypt` dictionary in the trailer and encrypting content-stream
 * payloads with AES-128.
 */
class SkPDFEncryptionTest {

    private fun ByteArray.asLatin1(): String = String(this, Charsets.ISO_8859_1)

    @Test
    fun `encrypted PDF has Encrypt dict and ID array in trailer`() {
        val stream = SkDynamicMemoryWStream()
        val doc = SkPDF.MakeDocument(
            stream,
            SkPDF.Metadata(encryptionPassword = "swordfish", compress = false),
        )
        val c = doc.beginPage(100f, 100f)
        c.drawRect(SkRect.MakeWH(80f, 80f), SkPaint())
        doc.close()
        val text = stream.toByteArray().asLatin1()

        assertTrue(
            text.contains("/Encrypt "),
            "Encrypted PDF must declare /Encrypt in the trailer ; got:\n${text.takeLast(400)}",
        )
        assertTrue(
            text.contains("/Filter /Standard"),
            "Encrypt dict must use the Standard security handler",
        )
        assertTrue(
            text.contains("/V 4") && text.contains("/R 4"),
            "Encrypt dict must declare V=4, R=4 (AES-128) ; got:\n$text",
        )
        assertTrue(
            text.contains("/CFM /AESV2"),
            "Encrypt dict must declare /CFM /AESV2 (AES-128) inside /CF /StdCF",
        )
        assertTrue(
            text.contains("/ID [<"),
            "Encrypted PDF must carry a file /ID array in the trailer",
        )
    }

    @Test
    fun `encrypted content stream is opaque ciphertext, not the original operators`() {
        val stream = SkDynamicMemoryWStream()
        val pwd = "hunter2"
        val doc = SkPDF.MakeDocument(
            stream,
            SkPDF.Metadata(encryptionPassword = pwd, compress = false),
        )
        val c = doc.beginPage(100f, 100f)
        // Use a sentinel coordinate that would land in the plaintext
        // content stream as "12.345 ... re" — we'll grep for it.
        c.drawRect(SkRect.MakeXYWH(12.345f, 12.345f, 5f, 5f), SkPaint().apply { color = 0xFFAABBCC.toInt() })
        doc.close()
        val bytes = stream.toByteArray()
        val text = bytes.asLatin1()

        // The sentinel coordinate must NOT appear as plaintext when
        // encryption is on (it would land verbatim under uncompressed,
        // unencrypted output).
        assertFalse(
            text.contains("12.345"),
            "Encrypted content stream must not leak the original coordinate as plaintext",
        )
        // The PDF must still terminate cleanly.
        assertTrue(text.trimEnd().endsWith("%%EOF"))
        // And declare /Encrypt in the trailer.
        assertTrue(text.contains("/Encrypt "))
    }

    @Test
    fun `unencrypted PDF has no Encrypt dict`() {
        val stream = SkDynamicMemoryWStream()
        val doc = SkPDF.MakeDocument(stream, SkPDF.Metadata(compress = false))
        val c = doc.beginPage(50f, 50f)
        c.drawRect(SkRect.MakeWH(10f, 10f), SkPaint())
        doc.close()
        val text = stream.toByteArray().asLatin1()
        assertFalse(text.contains("/Encrypt "),
            "Unencrypted PDF trailer must not declare /Encrypt")
        assertFalse(text.contains("/CFM /AESV2"),
            "Unencrypted PDF must not declare an AES crypt filter")
    }
}
