package org.graphiks.kanvas.skia

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.test.GpuAvailability
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import kotlin.math.abs

class SrgbColorFilterGmSurfaceRefusalEvidenceTest {
    @AfterEach
    fun disposeSharedBackend() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `linear color filter image filter GM renders with independent interior controls`() {
        GpuAvailability.requireWebGpu()
        val controls = listOf(
            Triple(15, 15, intArrayOf(0, 0, 0, 255)),
            Triple(215, 15, intArrayOf(255, 0, 0, 255)),
            Triple(415, 15, intArrayOf(255, 255, 255, 255)),
        )
        val gm = requireNotNull(SkiaGmRegistry.all().singleOrNull { it.name == "colorfilterimagefilter" })
        val result = SkiaGmRenderer.render(gm)
        val evidenceRoot = System.getProperty("w7.ordinaryAaEvidenceDir")
        if (evidenceRoot != null) {
            val evidenceDirectory = Path.of(evidenceRoot)
            Files.createDirectories(evidenceDirectory)
            val rawPath = evidenceDirectory.resolve("w7-task9-colorfilterimagefilter-linear-rgba.raw")
            Files.write(rawPath, result.rgba, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
            val sha256 = MessageDigest.getInstance("SHA-256")
                .digest(result.rgba)
                .joinToString("") { byte -> "%02x".format(byte) }
            println("task9.gm-raw-evidence path=${rawPath.toAbsolutePath().normalize()} bytes=${result.rgba.size} sha256=$sha256")
        }
        println(
            "task9.gm-positive name=colorfilterimagefilter width=${result.width} height=${result.height} " +
                "rgbaBytes=${result.rgba.size} ops=${result.ops.size} dispatched=${result.dispatchedCount} " +
                "refused=${result.refusedCount}"
        )
        controls.forEach { (x, y, _) ->
            val offset = (y * result.width + x) * 4
            val actual = (0..3).map { channel -> result.rgba[offset + channel].toInt() and 0xFF }
            println("task9.gm-actual-pixel x=$x y=$y rgba=${actual.joinToString(",")}")
        }

        assertEquals(435, result.width)
        assertEquals(120, result.height)
        assertEquals(435 * 120 * 4, result.rgba.size)
        assertTrue(result.dispatchedCount > 0, "the LINEAR GM must dispatch native work")
        assertEquals(84, result.ops.size)
        assertEquals(0, result.refusedCount)
        assertEquals(emptyList<String>(), result.diagnostics)
        controls.forEach { (x, y, expected) ->
            val offset = (y * result.width + x) * 4
            for (channel in 0..2) {
                val actual = result.rgba[offset + channel].toInt() and 0xFF
                assertTrue(abs(actual - expected[channel]) <= 2,
                    "pixel ($x,$y) channel $channel: expected ${expected[channel]} ±2, got $actual")
            }
            assertEquals(expected[3], result.rgba[offset + 3].toInt() and 0xFF, "pixel ($x,$y) alpha")
        }
    }

    @Test
    fun `sRGB color filter GM retains its independent composed numeric domain refusal`() {
        GpuAvailability.requireWebGpu()
        val gm = requireNotNull(SkiaGmRegistry.all().singleOrNull { it.name == "srgb_colorfilter" })
        val attempt = requireNotNull(SkiaGmRenderer.renderTerminalAttempt(gm)) {
            "srgb_colorfilter unexpectedly rendered through an unsupported route"
        }
        println("task9.gm-refusal name=srgb_colorfilter operations=${attempt.operationCount} diagnostic=${attempt.diagnostic}")
        assertEquals("7:unsupported.material.composed.numeric-domain-unbounded",
            "${attempt.operationCount}:${attempt.diagnostic.substringBefore(":")}")
    }
}
