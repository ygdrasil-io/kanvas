package org.graphiks.kanvas.gpu.renderer.images

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CodecProvenanceDependencyGateTest {
    @Test
    fun `codec registry dump exposes dependency gated provenance without decode support`() {
        val snapshot = GPUImageCodecRegistrySnapshot(
            registryId = "codec-registry:m4",
            generation = 1,
            codecs = listOf(
                GPUImageCodecDescriptor(
                    codecName = "kanvas-png-planned",
                    codecVersion = "descriptor:v1",
                    supportedFormats = setOf("png"),
                    colorManagementPolicy = "descriptor-only",
                    implementationKind = "planned-pure-kotlin",
                    deterministic = true,
                    dependencyGate = "dependency.image.codec.png.delivery",
                ),
            ),
        )

        val plan = snapshot.planDecodeProvenance(
            request = GPUImageCodecProvenanceRequest(
                source = pngSource,
                requestedFormat = "png",
                conformanceTier = "contract-only",
            ),
        )

        assertEquals("DependencyGated", plan.classification)
        assertEquals("dependency.image.codec.png.delivery", plan.diagnostic.code)
        assertFalse(plan.dumpLines().joinToString("\n").contains("decoded-pixels"))
        assertEquals(
            listOf(
                "codec:registry id=codec-registry:m4 generation=1 descriptors=1",
                "codec:descriptor id=kanvas-png-planned version=descriptor:v1 formats=png kind=planned-pure-kotlin deterministic=true color=descriptor-only gate=dependency.image.codec.png.delivery",
                "codec:provenance source=encoded:png:checker format=png tier=contract-only classification=DependencyGated reason=dependency.image.codec.png.delivery",
                "nonclaim:no-codec-implementation no-decode-output no-uploaded-texture-route-from-provenance no-platform-decoder-substitute no-product-activation",
            ),
            plan.dumpLines(),
        )
    }

    @Test
    fun `unsupported codec provenance cases refuse with dependency diagnostics`() {
        val snapshot = GPUImageCodecRegistrySnapshot(
            registryId = "codec-registry:m4",
            generation = 1,
            codecs = listOf(
                GPUImageCodecDescriptor(
                    codecName = "external-webp-observer",
                    codecVersion = "descriptor:v1",
                    supportedFormats = setOf("webp"),
                    colorManagementPolicy = "observational-only",
                    implementationKind = "external-platform",
                    deterministic = true,
                ),
                GPUImageCodecDescriptor(
                    codecName = "gif-nondeterministic",
                    codecVersion = "",
                    supportedFormats = setOf("gif"),
                    colorManagementPolicy = "descriptor-only",
                    implementationKind = "planned-pure-kotlin",
                    deterministic = false,
                ),
            ),
        )
        val cases = listOf(
            CodecRefusalCase(
                expectedCode = "dependency.image.codec.unregistered",
                request = GPUImageCodecProvenanceRequest(
                    source = pngSource.copy(containerFormat = "avif"),
                    requestedFormat = "avif",
                    conformanceTier = "contract-only",
                ),
            ),
            CodecRefusalCase(
                expectedCode = "dependency.image.codec.external_not_allowed",
                request = GPUImageCodecProvenanceRequest(
                    source = pngSource.copy(containerFormat = "webp"),
                    requestedFormat = "webp",
                    conformanceTier = "contract-only",
                ),
            ),
            CodecRefusalCase(
                expectedCode = "dependency.image.codec.version_nondeterministic",
                request = GPUImageCodecProvenanceRequest(
                    source = pngSource.copy(containerFormat = "gif"),
                    requestedFormat = "gif",
                    conformanceTier = "contract-only",
                ),
            ),
        )

        for (case in cases) {
            val plan = snapshot.planDecodeProvenance(case.request)

            assertEquals("DependencyGated", plan.classification)
            assertEquals(case.expectedCode, plan.diagnostic.code)
            assertContains(plan.dumpLines().single { it.startsWith("codec:provenance") }, "reason=${case.expectedCode}")
        }
    }

    @Test
    fun `decode output without codec provenance is refused`() {
        val diagnostic = GPUImageCodecRegistrySnapshot.refuseDecodeOutputWithoutProvenance(
            sourceId = "encoded:png:checker",
            outputLabel = "decoded-pixels:rgba8",
        )

        assertEquals("dependency.image.decode.provenance_missing", diagnostic.code)
        assertEquals(true, diagnostic.terminal)
        assertFalse(diagnostic.message.contains("support"))
    }
}

private data class CodecRefusalCase(
    val expectedCode: String,
    val request: GPUImageCodecProvenanceRequest,
)

private val pngSource = GPUEncodedImageSource(
    sourceId = "encoded:png:checker",
    byteHash = "sha256:png-checker-v1",
    containerFormat = "png",
    frameCount = 1,
)
