@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.skia

import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.render.ir.CompositionDomain
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.test.ComparisonUtils
import org.graphiks.kanvas.test.ReferenceManager
import org.graphiks.kanvas.skia.gm.path.TeenyStrokesGm
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import org.graphiks.kanvas.test.ComparisonUtils.readPngAsSrgbBufferedImage

/** Actual unchanged TeenyStrokesGm scene captured by public Surface with the GM's domain. */
class TeenyStrokesCompositionSurfacePixelTest {
    @AfterEach fun disposeBackend() = GPUBackendRuntimeFactory.dispose()

    @Test fun linearActualSceneKeepsQualifiedControl() = actualScene(CompositionDomain.LINEAR)

    @Test fun encodedActualSceneSeparatesDomainFromCoverage() = actualScene(CompositionDomain.SRGB_ENCODED)

    private fun actualScene(domain: CompositionDomain) {
        // The full literal range contract is built before the first Surface.render call.
        val edgeCodes = if (domain == CompositionDomain.LINEAR) 187..188 else 127..128
        val anchorContract = listOf(
            Triple(67, 60, edgeCodes), Triple(72, 60, edgeCodes),
            Triple(70, 60, 0..0), Triple(66, 60, 255..255), Triple(73, 60, 255..255),
        )
        val gm = TeenyStrokesGm()
        val first = renderActual(gm, domain)
        assertNative(first, "actual Teeny $domain")
        anchorContract.forEach { (x, y, codes) ->
            for (colorChannel in 0..2) {
                val value = channel(first, x, y, colorChannel)
                assertTrue(value in codes, "${domain} anchor ($x,$y) channel=$colorChannel value=$value expected=$codes")
            }
            assertEquals(255, channel(first, x, y, 3), "${domain} anchor alpha ($x,$y)")
        }
        val repeated = renderActual(TeenyStrokesGm(), domain)
        assertNative(repeated, "actual Teeny $domain repeat")
        assertArrayEquals(first.pixels.toByteArray(), repeated.pixels.toByteArray(), "actual Teeny $domain repeat bytes")

        val referencePath = "/reference/${gm.referenceName}.png"
        val reference = ReferenceManager.loadReference(referencePath)
        val resourceBytes = requireNotNull(javaClass.getResourceAsStream(referencePath)) { "missing $referencePath" }.use { it.readBytes() }
        val referenceSha = sha256(resourceBytes)
        assertEquals("78cbf8bfe9b44e74f282311f517f86daa20ff0512f5770b6bc79819544a47597", referenceSha)
        val referenceImage = requireNotNull(javaClass.getResourceAsStream(referencePath)).use { readPngAsSrgbBufferedImage(it) }
        assertEquals(gm.width, referenceImage.width); assertEquals(gm.height, referenceImage.height)
        assertEquals(400, gm.width); assertEquals(800, gm.height)
        assertEquals(referenceImage.width * referenceImage.height * 4, reference.size)
        val actualRgba = first.pixels.map { it.toByte() }.toByteArray()
        val comparison0 = ComparisonUtils.compareRgba(actualRgba, reference, gm.width, gm.height, tolerance = 0, minSimilarity = 0.0)
        val comparison2 = ComparisonUtils.compareRgba(actualRgba, reference, gm.width, gm.height, tolerance = 2, minSimilarity = 0.0)
        val samples = (1..5).joinToString(",") { line ->
            val xVertical = 50 * line + 20
            val xDiagonal = 50 * line + 60
            "{\"line\":$line,\"verticalY60\":[${(-4..4).joinToString(",") { dx -> rampTuplePair(actualRgba, reference, xVertical + dx, 60) }}]," +
                "\"diagonalY60\":[${(-5..5).joinToString(",") { dx -> rampTuplePair(actualRgba, reference, xDiagonal + dx, 60) }}]}"
        }
        println("W7_TEENY_EVIDENCE {\"domain\":\"$domain\",\"width\":${first.width},\"height\":${first.height}," +
            "\"referencePngSha256\":\"$referenceSha\",\"actualRgbaSha256\":\"${sha256(actualRgba)}\"," +
            "\"tolerance0\":{\"similarity\":${comparison0.similarity},\"ssim\":${comparison0.ssim},\"meanChannelError\":${comparison0.meanChannelError}}," +
            "\"tolerance2\":{\"similarity\":${comparison2.similarity},\"ssim\":${comparison2.ssim},\"meanChannelError\":${comparison2.meanChannelError}}," +
            "\"opsDispatched\":${first.stats.opsDispatched},\"opsRefused\":${first.stats.opsRefused}," +
            "\"nativeScopes\":${first.nativeEvidenceScopeKinds.joinToString(prefix = "[\"", postfix = "\"]", separator = "\",\"")},\"ramps\":[$samples]}")
        val evidenceDir = System.getProperty("w7.teenyEvidenceDir")?.let(::File)
        if (evidenceDir != null) {
            evidenceDir.mkdirs()
            ComparisonUtils.saveRgbaAsPng(actualRgba, first.width, first.height, File(evidenceDir, "teeny-$domain.png"))
        }
    }

    private fun renderActual(gm: TeenyStrokesGm, domain: CompositionDomain): RenderResult {
        val surface = Surface(gm.width, gm.height, config = RenderConfig(compositionDomain = domain))
        val canvas = surface.canvas()
        canvas.drawRect(RectF32.ofLTRB(0f, 0f, gm.width.toFloat(), gm.height.toFloat()), Paint(ColorARGB.White, antiAlias = false))
        val gmCanvas = GmCanvas(canvas, gm.width, gm.height)
        gm.onOnceBeforeDraw(gmCanvas)
        gm.draw(gmCanvas, gm.width, gm.height)
        return surface.render()
    }

    private fun channel(result: RenderResult, x: Int, y: Int, channel: Int) = result.pixels[(y * result.width + x) * 4 + channel].toInt()

    private fun rampTuplePair(actual: ByteArray, reference: ByteArray, x: Int, y: Int): String =
        "{\"actual\":${rgbaTuple(actual, x, y)},\"reference\":${rgbaTuple(reference, x, y)}}"

    private fun rgbaTuple(bytes: ByteArray, x: Int, y: Int): String {
        val offset = (y * 400 + x) * 4
        return (0..3).joinToString(prefix = "[", postfix = "]") { channel ->
            (bytes[offset + channel].toInt() and 0xff).toString()
        }
    }

    private fun assertNative(result: RenderResult, label: String) {
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), "$label scopes=${result.nativeEvidenceScopeKinds}")
        assertTrue(result.diagnostics.isEmpty, "$label diagnostics=${result.diagnostics}")
        assertEquals(0, result.stats.opsRefused, "$label")
        assertTrue(result.stats.opsDispatched > 0, "$label no native dispatch")
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
