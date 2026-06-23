package org.graphiks.kanvas.gpu.renderer.images

import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ImageAcceptanceTest {

    @Test
    fun `decode plan accepts valid PNG bytes`() {
        val pngBytes = redPixelPng()
        val planner = GPUImageDecodePlanner()

        val plan = planner.plan(pngBytes, "image/png")

        val accepted = assertIs<GPUImageDecodePlan.Accepted>(plan)
        assertEquals(1, accepted.width)
        assertEquals(1, accepted.height)
        assertEquals("rgba8unorm", accepted.colorType)
        assertEquals(4, accepted.pixelBytes.size)
        assertEquals(255.toByte(), accepted.pixelBytes[0])
        assertEquals(0.toByte(), accepted.pixelBytes[1])
        assertEquals(0.toByte(), accepted.pixelBytes[2])
        assertEquals(255.toByte(), accepted.pixelBytes[3])
    }

    @Test
    fun `decode plan refuses HEIF with dependency gated diagnostic`() {
        val planner = GPUImageDecodePlanner()

        val plan = planner.plan(byteArrayOf(0, 1, 2, 3), "image/heif")

        val refused = assertIs<GPUImageDecodePlan.Refused>(plan)
        assertEquals("dependency.image.codec.heif", refused.code)
        assertTrue(refused.reason.contains("heif", ignoreCase = true))
    }

    @Test
    fun `texture upload materialization accepts RGBA8 pixels within budget`() {
        val materializer = ValidatingTextureUploadMaterializer()
        val context = GPUTargetPreparationContext(
            targetId = "test-target",
            frameId = "frame-1",
            deviceGeneration = 1,
            budgetClass = "image-small",
        )
        val descriptor = GPUDecodedImagePixelsDescriptor(
            sourceId = "test:red-pixel",
            width = 1,
            height = 1,
            pixelFormat = "RGBA8Unorm",
            rowBytes = 4,
            alphaType = "Premul",
            colorProfileLabel = "srgb",
            orientationState = "Applied",
            generation = 1,
            contentHash = "sha256:red-pixel-v1",
            provenance = "unit-test",
        )

        val decision = materializer.materialize(descriptor, context)

        val materialized = assertIs<GPUResourceMaterializationDecision.Materialized>(decision)
        assertEquals("test-target", materialized.targetId)
        assertTrue(materialized.resources.isNotEmpty())
    }

    @Test
    fun `texture upload refuses pixels exceeding budget`() {
        val materializer = ValidatingTextureUploadMaterializer(maxUploadBytes = 8)
        val context = GPUTargetPreparationContext(
            targetId = "test-target",
            frameId = "frame-1",
            deviceGeneration = 1,
            budgetClass = "image-small",
        )
        val descriptor = GPUDecodedImagePixelsDescriptor(
            sourceId = "test:large",
            width = 1024,
            height = 1024,
            pixelFormat = "RGBA8Unorm",
            rowBytes = 4096,
            alphaType = "Premul",
            colorProfileLabel = "srgb",
            orientationState = "Applied",
            generation = 1,
            contentHash = "sha256:large-pixels-v1",
            provenance = "unit-test",
        )

        val decision = materializer.materialize(descriptor, context)

        val refused = assertIs<GPUResourceMaterializationDecision.Refused>(decision)
        assertTrue(refused.diagnostic.code.contains("budget"))
    }

    @Test
    fun `codec provenance accepts PNG and wires into pipeline plan`() {
        val snapshot = GPUImageCodecRegistrySnapshot(
            registryId = "codec-registry:m12",
            generation = 2,
            codecs = listOf(
                GPUImageCodecDescriptor(
                    codecName = "kanvas-png-kotlin",
                    codecVersion = "descriptor:v2",
                    supportedFormats = setOf("png"),
                    colorManagementPolicy = "descriptor-only",
                    implementationKind = "planned-pure-kotlin",
                    deterministic = true,
                    dependencyGate = null,
                ),
            ),
        )

        val plan = snapshot.planDecodeProvenance(
            request = GPUImageCodecProvenanceRequest(
                source = GPUEncodedImageSource(
                    sourceId = "encoded:png:test",
                    byteHash = "sha256:png-test-v1",
                    containerFormat = "png",
                    frameCount = 1,
                ),
                requestedFormat = "png",
                conformanceTier = "contract-only",
            ),
        )

        assertEquals("Accepted", plan.classification)
        assertEquals(false, plan.diagnostic.terminal)
        assertEquals("kanvas-png-kotlin", plan.codec?.codecName)
    }

    @Test
    fun `codec provenance refuses HEIF`() {
        val snapshot = GPUImageCodecRegistrySnapshot(
            registryId = "codec-registry:m12",
            generation = 2,
            codecs = listOf(
                GPUImageCodecDescriptor(
                    codecName = "kanvas-png-kotlin",
                    codecVersion = "descriptor:v2",
                    supportedFormats = setOf("png"),
                    colorManagementPolicy = "descriptor-only",
                    implementationKind = "planned-pure-kotlin",
                    deterministic = true,
                    dependencyGate = null,
                ),
            ),
        )

        val plan = snapshot.planDecodeProvenance(
            request = GPUImageCodecProvenanceRequest(
                source = GPUEncodedImageSource(
                    sourceId = "encoded:heif:test",
                    byteHash = "sha256:heif-test-v1",
                    containerFormat = "heif",
                    frameCount = 1,
                ),
                requestedFormat = "heif",
                conformanceTier = "contract-only",
            ),
        )

        assertEquals("DependencyGated", plan.classification)
        assertEquals(true, plan.diagnostic.terminal)
        assertTrue(plan.diagnostic.code.contains("unregistered"))
    }

    companion object {
        private fun redPixelPng(): ByteArray {
            val width = 1
            val height = 1
            val raw = byteArrayOf(0, 255.toByte(), 0, 0, 255.toByte())
            val deflated = deflate(raw)

            return ByteArrayOutputStream().apply {
                write(PNG_SIGNATURE)
                writeChunk("IHDR", ByteArrayOutputStream().apply {
                    writeI32BE(width)
                    writeI32BE(height)
                    write(8)
                    write(6)
                    write(0)
                    write(0)
                    write(0)
                }.toByteArray())
                writeChunk("IDAT", deflated)
                writeChunk("IEND", ByteArray(0))
            }.toByteArray()
        }

        private fun deflate(data: ByteArray): ByteArray {
            val deflater = Deflater()
            deflater.setInput(data)
            deflater.finish()
            val buffer = ByteArray(1024)
            val out = ByteArrayOutputStream()
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                out.write(buffer, 0, count)
            }
            deflater.end()
            return out.toByteArray()
        }

        private fun ByteArrayOutputStream.writeChunk(type: String, data: ByteArray) {
            writeI32BE(data.size)
            val typeBytes = type.toByteArray(Charsets.US_ASCII)
            write(typeBytes)
            write(data)
            val crc = java.util.zip.CRC32()
            crc.update(typeBytes)
            crc.update(data)
            writeI32BE(crc.value.toInt())
        }

        private fun ByteArrayOutputStream.writeI32BE(value: Int) {
            write((value ushr 24) and 0xFF)
            write((value ushr 16) and 0xFF)
            write((value ushr 8) and 0xFF)
            write(value and 0xFF)
        }

        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
    }
}
