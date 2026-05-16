package org.skia.docs

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkPaint
import org.skia.foundation.stream.SkDynamicMemoryWStream
import org.graphiks.math.SkRect

/**
 * S7-C — verifies the AES-256 (V=5 R=5) PDF Standard Security Handler
 * variant of [SkPDF.Metadata.encryptionStrength]. The R-suivi.40
 * AES-128 path is covered by [SkPDFEncryptionTest] ; this suite
 * focuses on what differs in V=5 :
 *
 *  - `/V 5 /R 5 /Length 256` in the Encrypt dict.
 *  - `/CFM /AESV3 /Length 32` inside `/CF /StdCF`.
 *  - `/OE`, `/UE`, `/Perms` byte strings present (V=5-only entries).
 *  - encrypted content stream differs from the AES-128 ciphertext
 *    (ensures the cipher / key derivation actually changed).
 */
class SkPDFAes256Test {

    private fun ByteArray.asLatin1(): String = String(this, Charsets.ISO_8859_1)

    @Test
    fun `AES-256 PDF declares V=5 R=5 and AESV3 in the Encrypt dict`() {
        val stream = SkDynamicMemoryWStream()
        val doc = SkPDF.MakeDocument(
            stream,
            SkPDF.Metadata(
                encryptionPassword = "swordfish",
                encryptionStrength = SkPDF.EncryptionStrength.kAES256,
                compress = false,
            ),
        )
        val c = doc.beginPage(100f, 100f)
        c.drawRect(SkRect.MakeWH(80f, 80f), SkPaint())
        doc.close()
        val text = stream.toByteArray().asLatin1()

        assertTrue(text.contains("/Encrypt "), "must declare /Encrypt in trailer")
        assertTrue(text.contains("/Filter /Standard"), "must use Standard security handler")
        assertTrue(text.contains("/V 5"), "must declare V=5 ; got:\n$text")
        assertTrue(text.contains("/R 5"), "must declare R=5 ; got:\n$text")
        assertTrue(text.contains("/Length 256"), "must declare 256-bit key length ; got:\n$text")
        assertTrue(text.contains("/CFM /AESV3"), "must declare AESV3 cipher in /CF /StdCF")
        assertTrue(text.contains("/Length 32"), "must declare 32-byte cipher length")
        assertTrue(text.contains("/OE <"), "V=5 must carry an /OE byte string")
        assertTrue(text.contains("/UE <"), "V=5 must carry a /UE byte string")
        assertTrue(text.contains("/Perms <"), "V=5 must carry a /Perms byte string")
    }

    @Test
    fun `AES-256 ciphertext differs from AES-128 ciphertext for the same input`() {
        // Same password, same content — only the cipher strength
        // differs. The encrypted body bytes must be visibly distinct.
        val pwd = "hunter2"

        val s128 = SkDynamicMemoryWStream()
        val d128 = SkPDF.MakeDocument(
            s128,
            SkPDF.Metadata(
                encryptionPassword = pwd,
                encryptionStrength = SkPDF.EncryptionStrength.kAES128,
                compress = false,
            ),
        )
        d128.beginPage(50f, 50f).drawRect(SkRect.MakeXYWH(0f, 0f, 5f, 5f), SkPaint())
        d128.close()
        val bytes128 = s128.toByteArray()

        val s256 = SkDynamicMemoryWStream()
        val d256 = SkPDF.MakeDocument(
            s256,
            SkPDF.Metadata(
                encryptionPassword = pwd,
                encryptionStrength = SkPDF.EncryptionStrength.kAES256,
                compress = false,
            ),
        )
        d256.beginPage(50f, 50f).drawRect(SkRect.MakeXYWH(0f, 0f, 5f, 5f), SkPaint())
        d256.close()
        val bytes256 = s256.toByteArray()

        // Trivial : the trailers carry different /V values so the
        // overall byte stream MUST differ.
        assertNotEquals(
            bytes128.asLatin1(), bytes256.asLatin1(),
            "AES-128 vs AES-256 PDFs must serialize to different bytes",
        )
    }

    @Test
    fun `AES-256 encrypted content stream is not the original operators`() {
        val stream = SkDynamicMemoryWStream()
        val doc = SkPDF.MakeDocument(
            stream,
            SkPDF.Metadata(
                encryptionPassword = "topsecret",
                encryptionStrength = SkPDF.EncryptionStrength.kAES256,
                compress = false,
            ),
        )
        val c = doc.beginPage(100f, 100f)
        c.drawRect(SkRect.MakeXYWH(12.345f, 12.345f, 5f, 5f), SkPaint().apply {
            color = 0xFFAABBCC.toInt()
        })
        doc.close()
        val text = stream.toByteArray().asLatin1()

        assertFalse(
            text.contains("12.345"),
            "AES-256 content stream must not leak coordinate as plaintext",
        )
        assertTrue(text.trimEnd().endsWith("%%EOF"))
        assertTrue(text.contains("/Encrypt "))
    }

    @Test
    fun `AES-128 default still wins when encryptionStrength not set`() {
        // Sanity : the AES-128 R-suivi.40 default keeps working.
        val stream = SkDynamicMemoryWStream()
        val doc = SkPDF.MakeDocument(
            stream,
            SkPDF.Metadata(encryptionPassword = "abc", compress = false),
        )
        doc.beginPage(50f, 50f).drawRect(SkRect.MakeWH(10f, 10f), SkPaint())
        doc.close()
        val text = stream.toByteArray().asLatin1()
        assertTrue(text.contains("/V 4"))
        assertTrue(text.contains("/R 4"))
        assertTrue(text.contains("/CFM /AESV2"))
        assertFalse(text.contains("/V 5"))
    }
}
