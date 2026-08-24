package org.graphiks.kanvas.gpu.renderer.payloads

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import java.lang.reflect.Modifier
import org.graphiks.kanvas.gpu.renderer.analysis.corePrimitiveRectGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.analysis.corePrimitiveRRectGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.analysis.GPUCorePrimitiveRRectGeometryAuthorityIssue
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageElement
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageElementKind
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipFillRule
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPURRect
import org.graphiks.kanvas.gpu.renderer.commands.GPURRectCornerRadii
import org.graphiks.kanvas.gpu.renderer.commands.GPURRectNormalizationResult
import org.graphiks.kanvas.gpu.renderer.commands.GPURRectNormalizer
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformType
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.recording.stableCoreDump
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.materials.preparedMaterialSrgbToLinear

class GPUCorePrimitivePayloadContractsTest {
    @Test
    fun `linear descriptor graph hot paths use its owned value operations`() {
        val source = File("src/main/kotlin/org/graphiks/kanvas/gpu/renderer/commands/NormalizedDrawCommand.kt").readText()
        assertTrue("is GPUMaterialDescriptor.LinearGradient -> descriptor.copy()" in source)
        assertTrue("right is GPUMaterialDescriptor.LinearGradient ->\n                left == right" in source)
        assertTrue("is GPUMaterialDescriptor.LinearGradient -> descriptor.hashCode()" in source)
    }

    @Test
    fun `core canonical hash stays outside gather builder and preflight hot paths`() {
        val payloadSource = File(
            "src/main/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/PayloadContracts.kt",
        ).readText()
        val gatherStart = payloadSource.indexOf("class GPUCorePrimitivePayloadGatherer")
        val gatherEnd = payloadSource.indexOf(
            "private fun GPUCorePrimitiveRectGeometryAuthority?.canonicalPreimage",
            gatherStart,
        )
        val withClipStart = payloadSource.indexOf("internal fun withClipExecutionPlanIdentity")
        val withClipEnd = payloadSource.indexOf("/** Exact immutable uniform bytes", withClipStart)
        val builderSource = File(
            "src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/" +
                "GPUCorePrimitivePreparedFrameTaskListBuilder.kt",
        ).readText()
        val preflightSource = File(
            "src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt",
        ).readText()
        val corePreflightStart = preflightSource.indexOf("private fun validateCorePrimitiveSemanticPayload")
        val corePreflightEnd = preflightSource.indexOf(
            "private fun validateSeparableBlurRectSemanticPayload",
            corePreflightStart,
        )

        assertTrue(gatherStart >= 0 && gatherEnd > gatherStart)
        assertTrue(withClipStart >= 0 && withClipEnd > withClipStart)
        assertTrue(corePreflightStart >= 0 && corePreflightEnd > corePreflightStart)
        assertTrue(payloadSource.contains("val canonicalHash: String by lazy"))
        val gatherSource = payloadSource.substring(gatherStart, gatherEnd)
        assertFalse(gatherSource.contains("corePrimitiveCanonicalHash("))
        assertFalse(gatherSource.contains("sha256Hex("))
        assertFalse(gatherSource.contains("MessageDigest"))
        assertFalse(
            Regex("listOf\\([\\s\\S]*?\\.joinToString\\(").containsMatchIn(gatherSource),
        )
        assertFalse(payloadSource.substring(withClipStart, withClipEnd).contains("corePrimitiveCanonicalHash("))
        assertFalse(builderSource.contains("hasCanonicalHashIntegrity()"))
        assertFalse(builderSource.contains("semantic.canonicalHash"))
        assertFalse(
            preflightSource.substring(corePreflightStart, corePreflightEnd)
                .contains("hasCanonicalHashIntegrity()"),
        )
        assertFalse(preflightSource.contains("semantic.canonicalHash"))
    }

    @Test
    fun `rect geometry authority has no public value forging surface`() {
        val authority = rectGeometryAuthorityFixture()
        val publicMethodNames = authority.javaClass.methods.map { it.name }.toSet()
        val authorityDeclaredPublicMethodNames = authority.javaClass.methods
            .filter { it.declaringClass == authority.javaClass && !it.isSynthetic }
            .map { it.name }
            .toSet()

        assertTrue(
            authority.javaClass.constructors.all { constructor ->
                constructor.parameterTypes.any { type -> !Modifier.isPublic(type.modifiers) }
            },
        )
        assertEquals(setOf("equals", "hashCode", "toString"), authorityDeclaredPublicMethodNames)
        assertTrue(publicMethodNames.none { it == "copy" || it.startsWith("component") })
        assertTrue(
            listOf(
                "getVersion",
                "getRectLeftBits",
                "getRectTopBits",
                "getRectRightBits",
                "getRectBottomBits",
                "getTransformType",
                "getTransformTranslateXBits",
                "getTransformTranslateYBits",
                "getTransformScaleXBits",
                "getTransformScaleYBits",
                "getTransformSkewXBits",
                "getTransformSkewYBits",
            ).none(publicMethodNames::contains),
        )
        assertEquals(authority, rectGeometryAuthorityFixture())
        assertEquals(authority.hashCode(), rectGeometryAuthorityFixture().hashCode())
        assertEquals(authority.toString(), rectGeometryAuthorityFixture().toString())
        assertEquals("GPUCorePrimitiveRectGeometryAuthority(opaque)", authority.toString())
    }

    @Test
    fun `rrect geometry authority has no public value forging surface`() {
        val authority = rrectGeometryAuthorityFixture()
        val publicMethodNames = authority.javaClass.methods.map { it.name }.toSet()
        val declaredPublicMethodNames = authority.javaClass.methods
            .filter { it.declaringClass == authority.javaClass && !it.isSynthetic }
            .map { it.name }
            .toSet()

        assertTrue(GPUCorePrimitiveRRectGeometryAuthority::class.java.isInterface)
        assertTrue(GPUCorePrimitiveRRectGeometryAuthority::class.java.declaredConstructors.isEmpty())
        assertTrue(GPUCorePrimitiveRRectGeometryAuthority::class.java.declaredMethods.isEmpty())
        assertFalse(Modifier.isPublic(authority.javaClass.modifiers))
        assertTrue(declaredPublicMethodNames.containsAll(setOf("equals", "hashCode", "toString")))
        assertTrue(publicMethodNames.none { it == "copy" || it.startsWith("component") })
        assertEquals(authority, rrectGeometryAuthorityFixture())
        assertEquals("GPUCorePrimitiveRRectGeometryAuthority(opaque)", authority.toString())
    }

    @Test
    fun `rrect authority maps translation scale and every axis reflection with exact raw bits`() {
        val source = GPURRect(
            rect = GPURect(2f, 3f, 12f, 13f),
            topLeft = GPURRectCornerRadii(1f, 2f),
            topRight = GPURRectCornerRadii(3f, 1f),
            bottomRight = GPURRectCornerRadii(2f, 4f),
            bottomLeft = GPURRectCornerRadii(4f, 3f),
        )
        val cases = listOf(
            Triple(
                "translation",
                GPUTransformFacts.translation(5f, -1f),
                GPUCorePrimitiveGeometryInput.RRect(
                    7f, 2f, 17f, 12f,
                    listOf(1f, 2f, 3f, 1f, 2f, 4f, 4f, 3f),
                ),
            ),
            Triple(
                "scale",
                GPUTransformFacts.scale(2f, 3f),
                GPUCorePrimitiveGeometryInput.RRect(
                    4f, 9f, 24f, 39f,
                    listOf(2f, 6f, 6f, 3f, 4f, 12f, 8f, 9f),
                ),
            ),
            Triple(
                "reflection-x",
                GPUTransformFacts(
                    type = GPUTransformType.Affine,
                    translateX = 30f,
                    scaleX = -2f,
                    scaleY = 3f,
                ),
                GPUCorePrimitiveGeometryInput.RRect(
                    6f, 9f, 26f, 39f,
                    listOf(6f, 3f, 2f, 6f, 8f, 9f, 4f, 12f),
                ),
            ),
            Triple(
                "reflection-y",
                GPUTransformFacts(
                    type = GPUTransformType.Affine,
                    translateY = 50f,
                    scaleX = 2f,
                    scaleY = -3f,
                ),
                GPUCorePrimitiveGeometryInput.RRect(
                    4f, 11f, 24f, 41f,
                    listOf(8f, 9f, 4f, 12f, 6f, 3f, 2f, 6f),
                ),
            ),
            Triple(
                "reflection-xy",
                GPUTransformFacts(
                    type = GPUTransformType.Affine,
                    translateX = 30f,
                    translateY = 50f,
                    scaleX = -2f,
                    scaleY = -3f,
                ),
                GPUCorePrimitiveGeometryInput.RRect(
                    6f, 11f, 26f, 41f,
                    listOf(4f, 12f, 8f, 9f, 2f, 6f, 6f, 3f),
                ),
            ),
        )

        cases.forEach { (label, transform, expected) ->
            val actual = rrectGeometryAuthorityFixture(source, transform).sealedDeviceGeometryInput()
            assertRRectRawBits(expected, actual, label)
        }
    }

    @Test
    fun `rrect authority accepts only its sealed device geometry and analysis identity`() {
        val authority = rrectGeometryAuthorityFixture()
        val exactGeometry = authority.sealedDeviceGeometryInput()

        val semantic = gather(
            geometry = exactGeometry,
            sourceFamily = GPUCorePrimitiveSourceFamily.RRect,
            rrectGeometryAuthority = authority,
        )
        assertTrue(semantic.hasCanonicalHashIntegrity())
        assertEquals(authority, semantic.rrectGeometryAuthority)

        assertFailsWith<IllegalArgumentException> {
            gather(
                geometry = exactGeometry,
                sourceFamily = GPUCorePrimitiveSourceFamily.RRect,
                includeRRectAnalysisAuthority = false,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            gather(
                geometry = exactGeometry.copy(
                    radii = exactGeometry.radii.toMutableList().apply { this[0] += 1f },
                ),
                sourceFamily = GPUCorePrimitiveSourceFamily.RRect,
                rrectGeometryAuthority = authority,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            gather(
                geometry = exactGeometry,
                sourceFamily = GPUCorePrimitiveSourceFamily.RRect,
                analysisCommandFamily = "FillRect",
                rrectGeometryAuthority = authority,
            )
        }
    }

    @Test
    fun `different raw rrect sources with one normalized device geometry share structure but not canonical hash`() {
        val source = GPURRect(
            rect = GPURect(2f, 3f, 14f, 13f),
            topLeft = GPURRectCornerRadii(8f, 2f),
            topRight = GPURRectCornerRadii(8f, 6f),
            bottomRight = GPURRectCornerRadii(4f, 6f),
            bottomLeft = GPURRectCornerRadii(2f, 2f),
        )
        val doubled = source.withRadiiScale(2f)
        val firstAuthority = rrectGeometryAuthorityFixture(source)
        val secondAuthority = rrectGeometryAuthorityFixture(doubled)
        val firstGeometry = firstAuthority.sealedDeviceGeometryInput()
        val secondGeometry = secondAuthority.sealedDeviceGeometryInput()
        val first = gather(
            geometry = firstGeometry,
            sourceFamily = GPUCorePrimitiveSourceFamily.RRect,
            rrectGeometryAuthority = firstAuthority,
        )
        val second = gather(
            geometry = secondGeometry,
            sourceFamily = GPUCorePrimitiveSourceFamily.RRect,
            rrectGeometryAuthority = secondAuthority,
        )
        val blendPlan = blend(GPUBlendMode.SRC_OVER)
        val firstStructuralKey = corePrimitiveRenderPipelineStructuralKey(
            first,
            GPUClipExecutionPlan.NoClip,
            blendPlan,
        )
        val secondStructuralKey = corePrimitiveRenderPipelineStructuralKey(
            second,
            GPUClipExecutionPlan.NoClip,
            blendPlan,
        )
        val copied = first.withClipExecutionPlanIdentity("clip.execution.rrect")

        assertRRectRawBits(firstGeometry, secondGeometry, "normalized-device-geometry")
        assertTrue(first.hasStructuralIntegrity())
        assertTrue(second.hasStructuralIntegrity())
        assertTrue(first.hasCanonicalHashIntegrity())
        assertTrue(second.hasCanonicalHashIntegrity())
        assertEquals(firstStructuralKey, secondStructuralKey)
        assertNotEquals(firstAuthority, secondAuthority)
        assertNotEquals(first.canonicalHash, second.canonicalHash)
        assertEquals(first.rrectGeometryAuthority, copied.rrectGeometryAuthority)
        assertTrue(copied.hasCanonicalHashIntegrity())
    }

    @Test
    fun `rrect normalization provenance cannot be transplanted between raw sources`() {
        val sourceA = rrectFixture()
        val sourceB = sourceA.copy(topLeft = GPURRectCornerRadii(3f, 2f))
        val acceptedB = assertIs<GPURRectNormalizationResult.Accepted>(
            GPURRectNormalizer.normalize(sourceB),
        )

        assertEquals(
            "invalid.core_primitive.rrect.normalization_provenance",
            assertIs<GPUCorePrimitiveRRectGeometryAuthorityIssue.Refused>(
                corePrimitiveRRectGeometryAuthority(
                    sourceA,
                    acceptedB,
                    GPUTransformFacts.identity(),
                ),
            ).code,
        )
    }

    @Test
    fun `semantic retains exact blend and provenance authorities`() {
        val semantic = gather(
            blendPlan = blend(GPUBlendMode.SRC_OVER),
            provenance = GPUFrameProvenance.GmContent,
        )

        assertEquals(blend(GPUBlendMode.SRC_OVER).canonicalIdentity(), semantic.blendPlanIdentity)
        assertEquals(GPUFrameProvenance.GmContent, semantic.frameProvenance)
        assertTrue(semantic.hasCanonicalHashIntegrity())
    }

    @Test
    fun `uniform fingerprint includes target size`() {
        val small = gather(target = GPUPixelBounds(0, 0, 16, 16))
        val large = gather(target = GPUPixelBounds(0, 0, 32, 32))

        assertNotEquals(
            small.payloadRef.uniformBlock?.fingerprint,
            large.payloadRef.uniformBlock?.fingerprint,
        )
    }

    @Test
    fun `uniform fingerprint is versioned exact hex and distinguishes every byte mutation`() {
        val bytes = List(32) { index -> index * 7 and 0xff }
        val fingerprint = corePrimitiveUniformFingerprint(bytes)
        val prefix = "core-primitive.uniform32-v1:"
        val encodedBytes = fingerprint.value.removePrefix(prefix)

        assertTrue(fingerprint.value.startsWith(prefix))
        assertEquals(64, encodedBytes.length)
        assertTrue(encodedBytes.all { character -> character in '0'..'9' || character in 'a'..'f' })
        assertEquals(fingerprint, corePrimitiveUniformFingerprint(bytes.toList()))
        bytes.indices.forEach { byteIndex ->
            val mutated = bytes.toMutableList().apply {
                this[byteIndex] = (this[byteIndex] + 1) and 0xff
            }
            assertNotEquals(fingerprint, corePrimitiveUniformFingerprint(mutated))
        }
    }

    @Test
    fun `radial material snapshots facts and uses fixed stop packing`() {
        val positions = mutableListOf(0f, 0.5f, 1f)
        val colors = mutableListOf(
            1f, 0f, 0f, 0.5f,
            0f, 1f, 0f, 0.75f,
            0f, 0f, 1f, 1f,
        )
        val matrix = mutableListOf(
            1f, 0f, 2f,
            0f, 1f, 3f,
            0f, 0f, 1f,
        )
        val material = GPUCorePrimitiveMaterialPayload.RadialGradient(
            centerX = 4f,
            centerY = 5f,
            radius = 8f,
            localMatrix = matrix,
            interpolation = "srgb",
            tileMode = "clamp",
            positions = positions,
            colors = colors,
        )
        val semantic = gather(material = material)

        positions[0] = 0.25f
        colors[0] = 0.25f
        matrix[2] = 9f

        val snapshot = assertIs<GPUCorePrimitiveMaterialPayload.RadialGradient>(semantic.material)
        assertEquals(listOf(0f, 0.5f, 1f), snapshot.positions)
        assertEquals(
            listOf(
                1f, 0f, 0f, 0.5f,
                0f, 1f, 0f, 0.75f,
                0f, 0f, 1f, 1f,
            ),
            snapshot.colors,
        )
        assertEquals(listOf(1f, 0f, 2f, 0f, 1f, 3f, 0f, 0f, 1f), snapshot.localMatrix)
        assertEquals("clamp", snapshot.tileMode)
        assertEquals("srgb", snapshot.interpolation)
        assertEquals(material.materialHash, snapshot.materialHash)
        assertTrue(semantic.hasStructuralIntegrity())
        assertTrue(semantic.hasCanonicalHashIntegrity())

        val block = requireNotNull(semantic.payloadRef.uniformBlock)
        assertEquals(CORE_PRIMITIVE_GRADIENT_UNIFORM_BYTES.toLong(), block.byteSize)
        assertEquals(CORE_PRIMITIVE_GRADIENT_UNIFORM_BYTES, block.bytes.size)
        assertEquals(
            listOf(
                "target.size",
                "material.kind",
                "material.stop-count",
                "material.center",
                "material.radius",
                "material.geometry-padding",
                "material.local-matrix",
                "material.header-padding",
                "material.stops",
                "material.unused-stops",
            ),
            block.fields.map(GPUUniformPayloadField::fieldPath),
        )
        assertEquals(4f, block.bytes.readFloatAt(16))
        assertEquals(5f, block.bytes.readFloatAt(20))
        assertEquals(8f, block.bytes.readFloatAt(24))
        assertEquals(3f, block.bytes.readFloatAt(32 + 5 * 4))
        assertEquals(0f, block.bytes.readFloatAt(80))
        assertEquals(0f, block.bytes.readFloatAt(80 + 3 * 32))
        assertTrue(block.bytes.drop(80 + 3 * 32).all { byte -> byte == 0 })

        assertEquals(
            semantic.canonicalHash,
            gather(material = GPUCorePrimitiveMaterialPayload.RadialGradient(
                centerX = 4f,
                centerY = 5f,
                radius = 8f,
                localMatrix = listOf(1f, 0f, 2f, 0f, 1f, 3f, 0f, 0f, 1f),
                interpolation = "srgb",
                tileMode = "clamp",
                positions = listOf(0f, 0.5f, 1f),
                colors = listOf(
                    1f, 0f, 0f, 0.5f,
                    0f, 1f, 0f, 0.75f,
                    0f, 0f, 1f, 1f,
                ),
             )).canonicalHash,
        )
    }

    @Test
    fun `gradient packing uses premultiplied linear sRGB and clip copies retain material`() {
        val semantic = gather(
            material = radialMaterial(
                positions = listOf(0f, 1f),
                colors = listOf(0.5f, 0.25f, 0f, 0.5f, 1f, 1f, 1f, 1f),
            ),
        )
        val block = requireNotNull(semantic.payloadRef.uniformBlock)

        assertEquals(
            preparedMaterialSrgbToLinear(0.5f) * 0.5f,
            block.bytes.readFloatAt(80 + 16),
        )
        assertEquals(
            preparedMaterialSrgbToLinear(0.25f) * 0.5f,
            block.bytes.readFloatAt(80 + 20),
        )
        assertEquals(0f, block.bytes.readFloatAt(80 + 24))
        assertEquals(0.5f, block.bytes.readFloatAt(80 + 28))

        val copied = semantic.withClipExecutionPlanIdentity("clip.plan.v1")
        assertEquals(semantic.material, copied.material)
        assertEquals(semantic.payloadRef.corePrimitiveMaterial, copied.payloadRef.corePrimitiveMaterial)
        assertTrue(copied.hasStructuralIntegrity())
        assertTrue(copied.hasCanonicalHashIntegrity())
    }

    @Test
    fun `gradient material hash is stable when derived from immutable facts`() {
        val first = radialMaterial(materialHash = null)
        val second = radialMaterial(materialHash = null)

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals(first.materialHash, second.materialHash)
    }

    @Test
    fun `gradient material hash must match immutable material facts`() {
        assertFailsWith<IllegalArgumentException> {
            radialMaterial(materialHash = "sha256:wrong")
        }
    }

    @Test
    fun `linear material packs every shared gradient ABI field exactly`() {
        val material = linearMaterial(
            startX = 2f,
            startY = 3f,
            endX = 14f,
            endY = 5f,
            localMatrix = listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f),
            positions = listOf(0.25f, 0.75f),
            colors = listOf(0.5f, 0.25f, 0f, 0.5f, 0f, 0.25f, 1f, 0.75f),
        )

        val semantic = gather(material = material)
        val block = requireNotNull(semantic.payloadRef.uniformBlock)

        assertEquals(GPUCorePrimitiveMaterialKind.LinearGradient, material.kind)
        assertEquals(CORE_PRIMITIVE_GRADIENT_UNIFORM_BYTES, block.bytes.size)
        assertEquals(16f, block.bytes.readFloatAt(0))
        assertEquals(16f, block.bytes.readFloatAt(4))
        assertEquals(3, block.bytes.readIntAt(8))
        assertEquals(2, block.bytes.readIntAt(12))
        assertEquals(2f, block.bytes.readFloatAt(16))
        assertEquals(3f, block.bytes.readFloatAt(20))
        assertEquals(14f, block.bytes.readFloatAt(24))
        assertEquals(5f, block.bytes.readFloatAt(28))
        listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f).forEachIndexed { index, value ->
            assertEquals(value, block.bytes.readFloatAt(32 + index * Float.SIZE_BYTES))
        }
        assertTrue(block.bytes.subList(68, 80).all { it == 0 })

        assertEquals(0.25f, block.bytes.readFloatAt(80))
        assertTrue(block.bytes.subList(84, 96).all { it == 0 })
        assertEquals(preparedMaterialSrgbToLinear(0.5f) * 0.5f, block.bytes.readFloatAt(96))
        assertEquals(preparedMaterialSrgbToLinear(0.25f) * 0.5f, block.bytes.readFloatAt(100))
        assertEquals(0f, block.bytes.readFloatAt(104))
        assertEquals(0.5f, block.bytes.readFloatAt(108))

        assertEquals(0.75f, block.bytes.readFloatAt(112))
        assertTrue(block.bytes.subList(116, 128).all { it == 0 })
        assertEquals(0f, block.bytes.readFloatAt(128))
        assertEquals(preparedMaterialSrgbToLinear(0.25f) * 0.75f, block.bytes.readFloatAt(132))
        assertEquals(preparedMaterialSrgbToLinear(1f) * 0.75f, block.bytes.readFloatAt(136))
        assertEquals(0.75f, block.bytes.readFloatAt(140))
        assertTrue(block.bytes.drop(144).all { it == 0 })
    }

    @Test
    fun `linear material hash changes with each admitted content category`() {
        val baseline = linearMaterial()
        val variants = listOf(
            "start" to linearMaterial(startX = 1f),
            "end" to linearMaterial(endX = 9f),
            "matrix" to linearMaterial(localMatrix = listOf(1f, 0f, 2f, 0f, 1f, 0f, 0f, 0f, 1f)),
            "positions" to linearMaterial(positions = listOf(0.25f, 1f)),
            "colors" to linearMaterial(colors = listOf(0.5f, 0f, 0f, 1f, 0f, 0f, 1f, 1f)),
            "tileMode" to linearMaterial(tileMode = "repeat"),
        )

        variants.forEach { (category, variant) ->
            assertNotEquals(baseline.materialHash, variant.materialHash, category)
        }
        listOf(
            "interpolation" to { linearMaterial(interpolation = "linear") },
        ).forEach { (category, create) ->
            assertFailsWith<IllegalArgumentException>(category) { create() }
        }
    }

    @Test
    fun `linear payload selects exact direct and analytic structural programs rather than falling back to geometry shader`() {
        val direct = corePrimitiveRenderPipelineStructuralKey(
            gather(material = linearMaterial()),
            GPUClipExecutionPlan.NoClip,
            blend(GPUBlendMode.SRC_OVER),
        )
        val analytic = corePrimitiveRenderPipelineStructuralKey(
            gather(
                material = linearMaterial(),
                coverageMode = GPUCorePrimitiveCoverageMode.ScalarAA,
            ),
            GPUClipExecutionPlan.NoClip,
            blend(GPUBlendMode.SRC_OVER),
        )

        assertEquals(GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectLinearGradient, direct.shader)
        assertEquals(GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientUniform592V1, direct.uniformLayout)
        assertEquals(GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticLinearGradient, analytic.shader)
        assertEquals(GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientAnalyticShape656V1, analytic.uniformLayout)
    }

    @Test
    fun `linear repeat selects cache-distinct direct and analytic structural programs with unchanged ABIs`() {
        val direct = corePrimitiveRenderPipelineStructuralKey(
            gather(material = linearMaterial(tileMode = "repeat")),
            GPUClipExecutionPlan.NoClip,
            blend(GPUBlendMode.SRC_OVER),
        )
        val analytic = corePrimitiveRenderPipelineStructuralKey(
            gather(
                material = linearMaterial(tileMode = "repeat"),
                coverageMode = GPUCorePrimitiveCoverageMode.ScalarAA,
            ),
            GPUClipExecutionPlan.NoClip,
            blend(GPUBlendMode.SRC_OVER),
        )

        assertEquals(GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectLinearGradientRepeat, direct.shader)
        assertEquals(GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientUniform592V1, direct.uniformLayout)
        assertEquals(GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticLinearGradientRepeat, analytic.shader)
        assertEquals(GPUCorePrimitiveRenderPipelineStructuralKey.UniformLayout.GradientAnalyticShape656V1, analytic.uniformLayout)
        assertNotEquals(
            direct.stableRenderPipelineKey("core-primitive"),
            corePrimitiveRenderPipelineStructuralKey(
                gather(material = linearMaterial()),
                GPUClipExecutionPlan.NoClip,
                blend(GPUBlendMode.SRC_OVER),
            ).stableRenderPipelineKey("core-primitive"),
        )
    }

    @Test
    fun `gradient material wire ids preserve radial and sweep while linear occupies three`() {
        assertEquals(
            listOf(
                GPUCorePrimitiveMaterialKind.SolidColor,
                GPUCorePrimitiveMaterialKind.RadialGradient,
                GPUCorePrimitiveMaterialKind.SweepGradient,
                GPUCorePrimitiveMaterialKind.LinearGradient,
            ),
            GPUCorePrimitiveMaterialKind.entries,
        )
        assertEquals(0, GPUCorePrimitiveMaterialKind.SolidColor.ordinal)
        assertEquals(1, GPUCorePrimitiveMaterialKind.RadialGradient.ordinal)
        assertEquals(2, GPUCorePrimitiveMaterialKind.SweepGradient.ordinal)
        assertEquals(3, GPUCorePrimitiveMaterialKind.LinearGradient.ordinal)
        assertEquals(1, corePrimitiveUniformBytes(GPUPixelBounds(0, 0, 2, 2), radialMaterial()).readIntAt(8))
        assertEquals(2, corePrimitiveUniformBytes(GPUPixelBounds(0, 0, 2, 2), sweepMaterial()).readIntAt(8))
        assertEquals(3, corePrimitiveUniformBytes(GPUPixelBounds(0, 0, 2, 2), linearMaterial()).readIntAt(8))
    }

    @Test
    fun `linear material refuses forged and invalid facts`() {
        val invalidCases = listOf(
            "forged-hash" to { linearMaterial(materialHash = "sha256:wrong") },
            "mismatched-cardinality" to { linearMaterial(colors = listOf(1f, 0f, 0f, 1f)) },
            "unordered-stops" to { linearMaterial(positions = listOf(0.75f, 0.25f)) },
            "nonfinite-position" to { linearMaterial(positions = listOf(Float.NaN, 1f)) },
            "nonfinite-color" to { linearMaterial(colors = listOf(Float.NaN, 0f, 0f, 1f, 0f, 0f, 1f, 1f)) },
            "nonfinite-axis" to { linearMaterial(startX = Float.NaN) },
            "zero-axis" to { linearMaterial(endX = 0f, endY = 0f) },
            "overflow-axis" to { linearMaterial(startX = -Float.MAX_VALUE, endX = Float.MAX_VALUE) },
            "one-sided-squared-overflow-axis" to {
                linearMaterial(startX = -Float.MAX_VALUE, endX = 0f, startY = 0f, endY = 0f)
            },
            "squared-underflow-axis" to {
                linearMaterial(startX = 0f, endX = Float.MIN_VALUE, startY = 0f, endY = 0f)
            },
        )

        invalidCases.forEach { (name, create) ->
            assertFailsWith<IllegalArgumentException> {
                create()
            }
        }
    }

    @Test
    fun `legacy payload constructor descriptors remain available`() {
        assertTrue(
            GPUDrawPayloadRef::class.java.declaredConstructors.any { constructor ->
                constructor.parameterTypes.contentEquals(
                    arrayOf(
                        Int::class.javaPrimitiveType,
                        String::class.java,
                        GPUUniformPayloadSlot::class.java,
                        GPUResourceBindingSlot::class.java,
                        GPUGradientPayloadStore::class.java,
                        GPUUniformPayloadBlock::class.java,
                        GPUResourceBindingBlock::class.java,
                    ),
                )
            },
        )
        assertTrue(
            GPUCorePrimitivePayloadInput::class.java.declaredConstructors.any { constructor ->
                constructor.parameterTypes.contentEquals(
                    arrayOf(
                        Int::class.javaPrimitiveType,
                        GPUCorePrimitiveSourceFamily::class.java,
                        GPUCorePrimitiveGeometryInput::class.java,
                        List::class.java,
                        GPUPixelBounds::class.java,
                        GPUPixelBounds::class.java,
                        GPUClipCoveragePlan::class.java,
                        String::class.java,
                        String::class.java,
                        GPUFrameProvenance::class.java,
                        GPUCorePrimitiveCoverageMode::class.java,
                        String::class.java,
                        String::class.java,
                        GPUCorePrimitiveRectRouteAuthority::class.java,
                        GPUCorePrimitiveRectGeometryAuthority::class.java,
                        GPUCorePrimitiveRRectGeometryAuthority::class.java,
                    ),
                )
            },
        )
    }

    @Test
    fun `sweep material snapshots facts and changes canonical identity`() {
        val first = GPUCorePrimitiveMaterialPayload.SweepGradient(
            centerX = 6f,
            centerY = 7f,
            startAngle = -45f,
            endAngle = 315f,
            localMatrix = listOf(1f, 0f, 2f, 0f, 1f, 3f, 0f, 0f, 1f),
            interpolation = "srgb",
            tileMode = "clamp",
            positions = listOf(0f, 1f),
            colors = listOf(1f, 1f, 0f, 1f, 0f, 0f, 1f, 1f),
        )
        val second = GPUCorePrimitiveMaterialPayload.SweepGradient(
            centerX = 6f,
            centerY = 7f,
            startAngle = -45f,
            endAngle = 300f,
            localMatrix = listOf(1f, 0f, 2f, 0f, 1f, 3f, 0f, 0f, 1f),
            interpolation = "srgb",
            tileMode = "clamp",
            positions = listOf(0f, 1f),
            colors = listOf(1f, 1f, 0f, 1f, 0f, 0f, 1f, 1f),
        )
        val semantic = gather(material = first)
        val snapshot = assertIs<GPUCorePrimitiveMaterialPayload.SweepGradient>(semantic.material)

        assertEquals(-45f, snapshot.startAngle)
        assertEquals(315f, snapshot.endAngle)
        assertEquals(listOf(1f, 0f, 2f, 0f, 1f, 3f, 0f, 0f, 1f), snapshot.localMatrix)
        assertEquals(first.materialHash, snapshot.materialHash)
        assertTrue(semantic.hasStructuralIntegrity())
        assertTrue(semantic.hasCanonicalHashIntegrity())
        assertNotEquals(semantic.canonicalHash, gather(material = second).canonicalHash)
    }

    @Test
    fun `solid material keeps the existing 32 byte ABI`() {
        val semantic = gather()
        val solid = assertIs<GPUCorePrimitiveMaterialPayload.SolidColor>(semantic.material)
        val block = requireNotNull(semantic.payloadRef.uniformBlock)

        assertEquals(32L, block.byteSize)
        assertEquals(corePrimitiveUniformBytes(semantic.targetBounds, solid.premultipliedRgba), block.bytes)
        assertTrue(semantic.hasStructuralIntegrity())
        assertTrue(semantic.hasCanonicalHashIntegrity())
    }

    @Test
    fun `gradient material validation fails closed for unsupported facts`() {
        val invalidMaterials = buildList<() -> Any> {
            add({ radialMaterial(tileMode = "repeat") })
            add({ radialMaterial(tileMode = "mirror") })
            add({ radialMaterial(tileMode = "decal") })
            add({ radialMaterial(radius = 0f) })
            add({ radialMaterial(radius = -1f) })
            add({ radialMaterial(centerX = Float.NaN) })
            add({ radialMaterial(radius = Float.POSITIVE_INFINITY) })
            add({ radialMaterial(positions = listOf(0f, Float.NaN)) })
            add({ radialMaterial(localMatrix = listOf(1f, 0f, 0f, 0f, Float.NaN, 0f, 0f, 0f, 1f)) })
            add({ radialMaterial(positions = List(17) { it / 16f }, colors = List(68) { 1f }) })
            listOf("linear", "oklab", "hsl", "oklch").forEach { interpolation ->
                add({ radialMaterial(interpolation = interpolation) })
            }
            add({ sweepMaterial(startAngle = Float.NaN) })
            add({ sweepMaterial(endAngle = Float.POSITIVE_INFINITY) })
            add({ sweepMaterial(startAngle = 10f, endAngle = 10f) })
            add({ sweepMaterial(startAngle = 20f, endAngle = 10f) })
            add({ sweepMaterial(colors = listOf(1f, 0f, 0f, 1f)) })
        }

        invalidMaterials.forEach { createMaterial ->
            assertFailsWith<IllegalArgumentException> {
                createMaterial()
            }
        }
    }

    @Test
    fun `canonical hash includes exact mask budgets samples and vertex count`() {
        val baseElement = GPUClipCoverageElement(
            operation = GPUClipCoverageOperation.Intersect,
            kind = GPUClipCoverageElementKind.Path,
            values = listOf(1f, 0f, 1f, 1f, 8f, 1f, 8f, 8f),
            vertexCount = 3,
            antiAlias = true,
            fillRule = GPUClipFillRule.Winding,
            inverseFill = false,
        )
        val base = gather(
            clip = GPUClipCoveragePlan.Mask("same-key", 16, 16, 1, 256, 256, listOf(baseElement)),
        )
        val changed = gather(
            clip = GPUClipCoveragePlan.Mask("same-key", 16, 16, 4, 256, 1024, listOf(baseElement)),
        )

        assertNotEquals(base.canonicalHash, changed.canonicalHash)
        val dump = changed.clipCoveragePlan.stableCoreDump()
        assertTrue("samples=4" in dump)
        assertTrue("resolvedBytes=256" in dump)
        assertTrue("requiredBytes=1024" in dump)
        assertTrue("vertices=3" in dump)
    }

    @Test
    fun `canonical integrity rejects substituted blend or provenance`() {
        val semantic = gather()
        val substituted = GPUDrawSemanticPayload.CorePrimitive(
            payloadRef = semantic.payloadRef,
            sourceFamily = semantic.sourceFamily,
            geometry = semantic.geometry,
            premultipliedRgba = semantic.premultipliedRgba,
            targetBounds = semantic.targetBounds,
            scissorBounds = semantic.scissorBounds,
            clipCoveragePlan = semantic.clipCoveragePlan,
            blendPlanIdentity = blend(GPUBlendMode.SRC).canonicalIdentity(),
            frameProvenance = GPUFrameProvenance.HarnessBackground,
            canonicalHash = semantic.canonicalHash,
        )

        assertFalse(substituted.hasCanonicalHashIntegrity())
    }

    @Test
    fun `structural integrity rejects corrupted uniform bytes without requiring a canonical hash`() {
        val semantic = gather()
        val uniformBlock = requireNotNull(semantic.payloadRef.uniformBlock)
        val corruptedBytes = uniformBlock.bytes.toMutableList().apply {
            this[lastIndex] = (this[lastIndex] + 1) and 0xff
        }
        val corrupted = GPUDrawSemanticPayload.CorePrimitive(
            payloadRef = semantic.payloadRef.copy(
                uniformBlock = uniformBlock.copy(bytes = corruptedBytes),
            ),
            sourceFamily = semantic.sourceFamily,
            geometry = semantic.geometry,
            premultipliedRgba = semantic.premultipliedRgba,
            targetBounds = semantic.targetBounds,
            scissorBounds = semantic.scissorBounds,
            clipCoveragePlan = semantic.clipCoveragePlan,
            clipExecutionPlanIdentity = semantic.clipExecutionPlanIdentity,
            blendPlanIdentity = semantic.blendPlanIdentity,
            frameProvenance = semantic.frameProvenance,
            coverageMode = semantic.coverageMode,
            analysisRecordId = semantic.analysisRecordId,
            analysisCommandFamily = semantic.analysisCommandFamily,
            rectRouteAuthority = semantic.rectRouteAuthority,
            rectGeometryAuthority = semantic.rectGeometryAuthority,
        )

        assertTrue(semantic.hasStructuralIntegrity())
        assertFalse(corrupted.hasStructuralIntegrity())
        assertFalse(corrupted.hasCanonicalHashIntegrity())
    }

    @Test
    fun `rect analysis authority is retained and sealed by the canonical hash`() {
        val semantic = gather(
            analysisRecordId = "analysis.fill_rect.7",
            analysisCommandFamily = "FillRect",
            rectRouteAuthority = GPUCorePrimitiveRectRouteAuthority.RectAxisAligned,
        )
        val substituted = GPUDrawSemanticPayload.CorePrimitive(
            payloadRef = semantic.payloadRef,
            sourceFamily = semantic.sourceFamily,
            geometry = semantic.geometry,
            premultipliedRgba = semantic.premultipliedRgba,
            targetBounds = semantic.targetBounds,
            scissorBounds = semantic.scissorBounds,
            clipCoveragePlan = semantic.clipCoveragePlan,
            blendPlanIdentity = semantic.blendPlanIdentity,
            frameProvenance = semantic.frameProvenance,
            canonicalHash = semantic.canonicalHash,
            analysisRecordId = semantic.analysisRecordId,
            analysisCommandFamily = semantic.analysisCommandFamily,
            rectRouteAuthority = GPUCorePrimitiveRectRouteAuthority.RectAffineDirectTrianglesV1,
            rectGeometryAuthority = semantic.rectGeometryAuthority,
        )
        val substitutedGeometryAuthority = GPUDrawSemanticPayload.CorePrimitive(
            payloadRef = semantic.payloadRef,
            sourceFamily = semantic.sourceFamily,
            geometry = semantic.geometry,
            premultipliedRgba = semantic.premultipliedRgba,
            targetBounds = semantic.targetBounds,
            scissorBounds = semantic.scissorBounds,
            clipCoveragePlan = semantic.clipCoveragePlan,
            blendPlanIdentity = semantic.blendPlanIdentity,
            frameProvenance = semantic.frameProvenance,
            canonicalHash = semantic.canonicalHash,
            analysisRecordId = semantic.analysisRecordId,
            analysisCommandFamily = semantic.analysisCommandFamily,
            rectRouteAuthority = semantic.rectRouteAuthority,
            rectGeometryAuthority = rectGeometryAuthorityFixture(
                rect = GPURect(1f, 1f, 9f, 8f),
            ),
        )

        assertEquals("analysis.fill_rect.7", semantic.analysisRecordId)
        assertEquals("FillRect", semantic.analysisCommandFamily)
        assertEquals(
            GPUCorePrimitiveRectRouteAuthority.RectAxisAligned,
            semantic.rectRouteAuthority,
        )
        assertEquals(rectGeometryAuthorityFixture(), semantic.rectGeometryAuthority)
        assertTrue(semantic.hasCanonicalHashIntegrity())
        assertFalse(substituted.hasCanonicalHashIntegrity())
        assertFalse(substitutedGeometryAuthority.hasCanonicalHashIntegrity())
    }

    @Test
    fun `rect authority rejects forged family analysis and geometry combinations`() {
        val failures = listOf(
            {
                gather(includeRectAnalysisAuthority = false)
            },
            {
                gather(
                    sourceFamily = GPUCorePrimitiveSourceFamily.Path,
                    rectGeometryAuthority = rectGeometryAuthorityFixture(),
                )
            },
            {
                gather(
                    sourceFamily = GPUCorePrimitiveSourceFamily.Path,
                    analysisRecordId = "analysis.fill_rect.7",
                    analysisCommandFamily = "FillRect",
                    rectRouteAuthority = GPUCorePrimitiveRectRouteAuthority.RectAxisAligned,
                )
            },
            {
                gather(
                    analysisRecordId = "analysis.fill_path.7",
                    analysisCommandFamily = "FillRect",
                    rectRouteAuthority = GPUCorePrimitiveRectRouteAuthority.RectAxisAligned,
                )
            },
            {
                gather(
                    analysisRecordId = "analysis.fill_rect.7",
                    analysisCommandFamily = "FillPath",
                    rectRouteAuthority = GPUCorePrimitiveRectRouteAuthority.RectAxisAligned,
                )
            },
            {
                gather(
                    analysisRecordId = "analysis.fill_rect.7",
                    analysisCommandFamily = "FillRect",
                    rectRouteAuthority = GPUCorePrimitiveRectRouteAuthority.RectAffineDirectTrianglesV1,
                )
            },
        )

        failures.forEach { gatherInvalid ->
            assertFailsWith<IllegalArgumentException> { gatherInvalid() }
        }
    }

    @Test
    fun `axis rect authority rejects local rect transform and type contradictions`() {
        val contradictions = listOf(
            rectGeometryAuthorityFixture(rect = GPURect(1f, 1f, 9f, 8f)),
            rectGeometryAuthorityFixture(
                transform = GPUTransformFacts(
                    type = GPUTransformType.Identity,
                    translateX = 1f,
                ),
            ),
            rectGeometryAuthorityFixture(
                transform = GPUTransformFacts.translation(1f, 0f),
            ),
            rectGeometryAuthorityFixture(
                transform = GPUTransformFacts.scale(2f, 1f),
            ),
            rectGeometryAuthorityFixture(
                transform = GPUTransformFacts.affine(1f, 0.25f, 0f, 1f),
            ),
            rectGeometryAuthorityFixture(transform = GPUTransformFacts.perspective()),
            rectGeometryAuthorityFixture(transform = GPUTransformFacts.singular()),
        )

        contradictions.forEach { authority ->
            assertFailsWith<IllegalArgumentException> {
                gather(rectGeometryAuthority = authority)
            }
        }
    }

    @Test
    fun `rect authority rejects transform classifications with lying coefficients`() {
        val rect = GPURect(1f, 1f, 8f, 8f)
        val cases = listOf(
            GPUTransformFacts(
                type = GPUTransformType.Identity,
                scaleX = 2f,
            ) to GPUCorePrimitiveGeometryInput.Rect(2f, 1f, 16f, 8f),
            GPUTransformFacts(
                type = GPUTransformType.Identity,
                translateX = 3f,
            ) to GPUCorePrimitiveGeometryInput.Rect(4f, 1f, 11f, 8f),
            GPUTransformFacts(
                type = GPUTransformType.Identity,
                translateX = -0.0f,
            ) to GPUCorePrimitiveGeometryInput.Rect(1f, 1f, 8f, 8f),
            GPUTransformFacts(
                type = GPUTransformType.Translate,
                translateX = 3f,
                scaleX = 2f,
            ) to GPUCorePrimitiveGeometryInput.Rect(5f, 1f, 19f, 8f),
            GPUTransformFacts(
                type = GPUTransformType.Scale,
                translateX = 3f,
                scaleX = 2f,
            ) to GPUCorePrimitiveGeometryInput.Rect(5f, 1f, 19f, 8f),
        )

        cases.forEach { (transform, adjustedGeometry) ->
            assertFailsWith<IllegalArgumentException> {
                gather(
                    target = GPUPixelBounds(0, 0, 32, 32),
                    geometry = adjustedGeometry,
                    rectGeometryAuthority = corePrimitiveRectGeometryAuthority(rect, transform),
                )
            }
        }
    }

    @Test
    fun `affine rect authority accepts only its exact four transformed corners`() {
        val authority = affineRectGeometryAuthorityFixture()
        val exactGeometry = affineRectGeometryFixture()

        assertTrue(
            gather(
                geometry = exactGeometry,
                sourceFamily = GPUCorePrimitiveSourceFamily.Rect,
                rectRouteAuthority = GPUCorePrimitiveRectRouteAuthority.RectAffineDirectTrianglesV1,
                rectGeometryAuthority = authority,
            ).hasCanonicalHashIntegrity(),
        )

        val contradictoryAuthorities = listOf(
            affineRectGeometryAuthorityFixture(rect = GPURect(1f, 2f, 5f, 6f)),
            affineRectGeometryAuthorityFixture(
                transform = GPUTransformFacts.affine(
                    scaleX = 2f,
                    skewX = 0.75f,
                    skewY = -0.25f,
                    scaleY = 3f,
                    translateX = 7f,
                    translateY = -4f,
                ),
            ),
            affineRectGeometryAuthorityFixture(transform = GPUTransformFacts.perspective()),
            affineRectGeometryAuthorityFixture(transform = GPUTransformFacts.singular()),
        )
        contradictoryAuthorities.forEach { contradictoryAuthority ->
            assertFailsWith<IllegalArgumentException> {
                gather(
                    geometry = exactGeometry,
                    sourceFamily = GPUCorePrimitiveSourceFamily.Rect,
                    rectRouteAuthority = GPUCorePrimitiveRectRouteAuthority.RectAffineDirectTrianglesV1,
                    rectGeometryAuthority = contradictoryAuthority,
                )
            }
        }

        assertFailsWith<IllegalArgumentException> {
            gather(
                geometry = exactGeometry.copy(
                    vertices = exactGeometry.vertices.toMutableList().apply { this[4] = 18.5f },
                ),
                sourceFamily = GPUCorePrimitiveSourceFamily.Rect,
                rectRouteAuthority = GPUCorePrimitiveRectRouteAuthority.RectAffineDirectTrianglesV1,
                rectGeometryAuthority = authority,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            gather(
                geometry = exactGeometry.copy(indices = listOf(0, 2, 1, 0, 2, 3)),
                sourceFamily = GPUCorePrimitiveSourceFamily.Rect,
                rectRouteAuthority = GPUCorePrimitiveRectRouteAuthority.RectAffineDirectTrianglesV1,
                rectGeometryAuthority = authority,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            gather(
                geometry = exactGeometry.copy(
                    coverBounds = GPUPixelBounds(11, 1, 16, 14),
                ),
                sourceFamily = GPUCorePrimitiveSourceFamily.Rect,
                rectRouteAuthority = GPUCorePrimitiveRectRouteAuthority.RectAffineDirectTrianglesV1,
                rectGeometryAuthority = authority,
            )
        }
    }

    @Test
    fun `rect authority fails closed for non finite overflow and signed zero mismatches`() {
        val invalidAuthorities = listOf(
            rectGeometryAuthorityFixture(rect = GPURect(Float.NaN, 1f, 8f, 8f)),
            rectGeometryAuthorityFixture(
                transform = GPUTransformFacts(
                    type = GPUTransformType.Identity,
                    scaleX = Float.POSITIVE_INFINITY,
                ),
            ),
            rectGeometryAuthorityFixture(
                rect = GPURect(1f, 1f, Float.MAX_VALUE, 8f),
                transform = GPUTransformFacts.scale(Float.MAX_VALUE, 1f),
            ),
        )

        invalidAuthorities.forEach { authority ->
            assertFailsWith<IllegalArgumentException> {
                gather(rectGeometryAuthority = authority)
            }
        }

        assertFailsWith<IllegalArgumentException> {
            gather(
                geometry = GPUCorePrimitiveGeometryInput.Rect(-0.0f, 1f, 8f, 8f),
                rectGeometryAuthority = rectGeometryAuthorityFixture(
                    rect = GPURect(0.0f, 1f, 8f, 8f),
                ),
            )
        }
    }

    @Test
    fun `semantic retains exact path fill coverage and stroke facts`() {
        val stroke = GPUCorePrimitiveStrokeStyle(
            width = 4f,
            cap = "square",
            join = "bevel",
            miterLimit = 3f,
            dashIntervals = emptyList(),
            dashPhase = 0f,
            loweringProof = GPUCorePrimitiveStrokeLoweringProof.SingleSegmentSquareV1,
        )
        val semantic = gather(
            geometry = GPUCorePrimitiveGeometryInput.TriangulatedPath(
                vertices = listOf(0f, 0f, 8f, 0f, 8f, 8f),
                indices = listOf(0, 1, 2),
                sourceContourStarts = listOf(0),
                sourceVertexCount = 2,
                coverBounds = GPUPixelBounds(0, 0, 8, 8),
                geometryMode = GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan,
                fillRule = GPUCorePrimitiveFillRule.Winding,
                inverseFill = false,
                strokeStyle = stroke,
            ),
            coverageMode = GPUCorePrimitiveCoverageMode.StencilAA,
        )
        val geometry = semantic.geometry as GPUCorePrimitiveGeometry.TriangulatedPath

        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(GPUCorePrimitiveFillRule.Winding, geometry.fillRule)
        assertFalse(geometry.inverseFill)
        assertEquals(stroke, geometry.strokeStyle)
        assertEquals(GPUCorePrimitiveCoverageMode.StencilAA, semantic.coverageMode)
        assertTrue(semantic.hasCanonicalHashIntegrity())
    }

    @Test
    fun `stroke lowering proofs reject cap dash segment fill and inverse contradictions`() {
        val validSquare = GPUCorePrimitiveStrokeStyle(
            width = 4f,
            cap = "square",
            join = "bevel",
            miterLimit = 3f,
            dashIntervals = emptyList(),
            dashPhase = 0f,
            loweringProof = GPUCorePrimitiveStrokeLoweringProof.SingleSegmentSquareV1,
        )
        val contradictions = listOf(
            validSquare.copy(cap = "butt"),
            validSquare.copy(cap = "round"),
            validSquare.copy(dashIntervals = listOf(2f, 1f)),
            validSquare.copy(
                cap = "square",
                loweringProof = GPUCorePrimitiveStrokeLoweringProof.SingleSegmentButtV1,
            ),
        )
        contradictions.forEach { stroke ->
            assertFailsWith<IllegalArgumentException> {
                gather(geometry = strokeFan(strokeStyle = stroke))
            }
        }
        assertFailsWith<IllegalArgumentException> {
            gather(geometry = strokeFan(strokeStyle = validSquare, sourceVertexCount = 3))
        }
        assertFailsWith<IllegalArgumentException> {
            gather(
                geometry = strokeFan(
                    strokeStyle = validSquare,
                    fillRule = GPUCorePrimitiveFillRule.EvenOdd,
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            gather(geometry = strokeFan(strokeStyle = validSquare, inverseFill = true))
        }
    }

    @Test
    fun `rect and rrect may cross target bounds when their scissor remains bounded`() {
        val target = GPUPixelBounds(0, 0, 16, 16)

        val rect = gather(
            target = target,
            scissor = GPUPixelBounds(0, 0, 8, 8),
            geometry = GPUCorePrimitiveGeometryInput.Rect(-4f, -3f, 8f, 9f),
            rectGeometryAuthority = rectGeometryAuthorityFixture(
                rect = GPURect(-4f, -3f, 8f, 9f),
            ),
        )
        val rrect = gather(
            target = target,
            scissor = GPUPixelBounds(0, 0, 8, 8),
            geometry = GPUCorePrimitiveGeometryInput.RRect(
                -4f,
                -3f,
                8f,
                9f,
                List(8) { 2f },
            ),
        )

        assertTrue(rect.hasCanonicalHashIntegrity())
        assertTrue(rrect.hasCanonicalHashIntegrity())
    }

    @Test
    fun `stencil edge fan accepts only the canonical source topology`() {
        val anchor = listOf(-1f, -1f)
        val p0 = listOf(1f, 1f)
        val p1 = listOf(7f, 1f)
        val p2 = listOf(4f, 7f)
        val vertices = anchor + p0 + p1 + anchor + p1 + p2 + anchor + p2 + p0
        val valid = GPUCorePrimitiveGeometryInput.TriangulatedPath(
            vertices = vertices,
            indices = (0..8).toList(),
            sourceContourStarts = listOf(0),
            sourceVertexCount = 3,
            coverBounds = GPUPixelBounds(0, 0, 8, 8),
            geometryMode = GPUCorePrimitiveGeometryMode.StencilEdgeFan,
        )

        assertTrue(gather(geometry = valid).hasCanonicalHashIntegrity())
        assertFailsWith<IllegalArgumentException> {
            gather(geometry = valid.copy(indices = listOf(0, 1, 2, 3, 5, 4, 6, 7, 8)))
        }
        assertFailsWith<IllegalArgumentException> {
            gather(geometry = valid.copy(sourceContourStarts = listOf(0, 2)))
        }
        assertFailsWith<IllegalArgumentException> {
            gather(geometry = valid.copy(vertices = vertices.dropLast(6), indices = listOf(0, 1, 2, 3, 4, 5)))
        }
    }

    @Test
    fun `stencil edge fan rejects source metadata over its stable budget`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            gather(
                geometry = GPUCorePrimitiveGeometryInput.TriangulatedPath(
                    vertices = listOf(0f, 0f, 1f, 0f, 0f, 1f),
                    indices = listOf(0, 1, 2),
                    sourceContourStarts = listOf(0),
                    sourceVertexCount = 257,
                    coverBounds = GPUPixelBounds(0, 0, 8, 8),
                    geometryMode = GPUCorePrimitiveGeometryMode.StencilEdgeFan,
                ),
            )
        }

        assertEquals("unsupported.core_primitive.stencil_edge_fan_budget", failure.message)
    }

    private fun gather(
        target: GPUPixelBounds = GPUPixelBounds(0, 0, 16, 16),
        scissor: GPUPixelBounds = target,
        geometry: GPUCorePrimitiveGeometryInput = GPUCorePrimitiveGeometryInput.Rect(1f, 1f, 8f, 8f),
        clip: GPUClipCoveragePlan = GPUClipCoveragePlan.Scissor(GPUBounds(0f, 0f, 16f, 16f)),
        blendPlan: GPUBlendPlan = blend(GPUBlendMode.SRC_OVER),
        provenance: GPUFrameProvenance = GPUFrameProvenance.GmContent,
        coverageMode: GPUCorePrimitiveCoverageMode = GPUCorePrimitiveCoverageMode.FullOrScissor,
        sourceFamily: GPUCorePrimitiveSourceFamily? = null,
        analysisRecordId: String? = null,
        analysisCommandFamily: String? = null,
        rectRouteAuthority: GPUCorePrimitiveRectRouteAuthority? = null,
        rectGeometryAuthority: GPUCorePrimitiveRectGeometryAuthority? = null,
        includeRectAnalysisAuthority: Boolean = true,
        rrectGeometryAuthority: GPUCorePrimitiveRRectGeometryAuthority? = null,
        includeRRectAnalysisAuthority: Boolean = true,
        material: GPUCorePrimitiveMaterialPayload? = null,
    ): GPUDrawSemanticPayload.CorePrimitive {
        val resolvedSourceFamily = sourceFamily ?: when (geometry) {
            is GPUCorePrimitiveGeometryInput.Rect -> GPUCorePrimitiveSourceFamily.Rect
            is GPUCorePrimitiveGeometryInput.RRect -> GPUCorePrimitiveSourceFamily.RRect
            is GPUCorePrimitiveGeometryInput.TriangulatedPath -> GPUCorePrimitiveSourceFamily.Path
        }
        val isAuthorizedRect =
            includeRectAnalysisAuthority && resolvedSourceFamily == GPUCorePrimitiveSourceFamily.Rect
        val isAuthorizedRRect =
            includeRRectAnalysisAuthority && resolvedSourceFamily == GPUCorePrimitiveSourceFamily.RRect
        return GPUCorePrimitivePayloadGatherer().gatherSemantic(
            GPUCorePrimitivePayloadInput(
            commandIdValue = 7,
            sourceFamily = resolvedSourceFamily,
                geometry = geometry,
                premultipliedRgba = listOf(0.25f, 0.5f, 0.75f, 1f),
                material = material,
                targetBounds = target,
            scissorBounds = scissor,
            clipCoveragePlan = clip,
            blendPlanIdentity = blendPlan.canonicalIdentity(),
            frameProvenance = provenance,
            coverageMode = coverageMode,
                analysisRecordId = analysisRecordId ?: when {
                    isAuthorizedRect -> "analysis.fill_rect.7"
                    isAuthorizedRRect -> "analysis.fill_rrect.7"
                    else -> null
                },
                analysisCommandFamily = analysisCommandFamily ?: when {
                    isAuthorizedRect -> "FillRect"
                    isAuthorizedRRect -> "FillRRect"
                    else -> null
                },
                rectRouteAuthority = rectRouteAuthority ?: if (isAuthorizedRect) {
                    GPUCorePrimitiveRectRouteAuthority.RectAxisAligned
                } else {
                    null
                },
                rectGeometryAuthority = rectGeometryAuthority ?: if (isAuthorizedRect) {
                    rectGeometryAuthorityFixture()
                } else {
                    null
                },
                rrectGeometryAuthority = rrectGeometryAuthority ?: if (isAuthorizedRRect) {
                    val rrect = geometry as GPUCorePrimitiveGeometryInput.RRect
                    rrectGeometryAuthorityFixture(
                        source = GPURRect(
                            rect = GPURect(rrect.left, rrect.top, rrect.right, rrect.bottom),
                            topLeft = GPURRectCornerRadii(rrect.radii[0], rrect.radii[1]),
                            topRight = GPURRectCornerRadii(rrect.radii[2], rrect.radii[3]),
                            bottomRight = GPURRectCornerRadii(rrect.radii[4], rrect.radii[5]),
                            bottomLeft = GPURRectCornerRadii(rrect.radii[6], rrect.radii[7]),
                        ),
                    )
                } else {
                    null
                },
            ),
        )
    }

    private fun radialMaterial(
        centerX: Float = 1f,
        radius: Float = 4f,
        tileMode: String = "clamp",
        interpolation: String = "srgb",
        positions: List<Float> = listOf(0f, 1f),
        colors: List<Float> = listOf(1f, 0f, 0f, 1f, 0f, 0f, 1f, 1f),
        localMatrix: List<Float> = listOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
        materialHash: String? = null,
    ) = GPUCorePrimitiveMaterialPayload.RadialGradient(
        centerX = centerX,
        centerY = 2f,
        radius = radius,
        localMatrix = localMatrix,
        interpolation = interpolation,
        tileMode = tileMode,
        positions = positions,
        colors = colors,
        materialHash = materialHash,
    )

    private fun linearMaterial(
        startX: Float = 0f,
        startY: Float = 0f,
        endX: Float = 8f,
        endY: Float = 0f,
        localMatrix: List<Float> = listOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
        interpolation: String = "srgb",
        tileMode: String = "clamp",
        positions: List<Float> = listOf(0f, 1f),
        colors: List<Float> = listOf(1f, 0f, 0f, 1f, 0f, 0f, 1f, 1f),
        materialHash: String? = null,
    ) = GPUCorePrimitiveMaterialPayload.LinearGradient(
        startX = startX,
        startY = startY,
        endX = endX,
        endY = endY,
        localMatrix = localMatrix,
        interpolation = interpolation,
        tileMode = tileMode,
        positions = positions,
        colors = colors,
        materialHash = materialHash,
    )

    private fun sweepMaterial(
        startAngle: Float = 0f,
        endAngle: Float = 360f,
        colors: List<Float> = listOf(1f, 0f, 0f, 1f, 0f, 0f, 1f, 1f),
    ) = GPUCorePrimitiveMaterialPayload.SweepGradient(
        centerX = 1f,
        centerY = 2f,
        startAngle = startAngle,
        endAngle = endAngle,
        localMatrix = listOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
        interpolation = "srgb",
        tileMode = "clamp",
        positions = listOf(0f, 1f),
        colors = colors,
    )

    private fun List<Int>.readFloatAt(offset: Int): Float = ByteBuffer.wrap(
        ByteArray(size) { index -> this[index].toByte() },
    ).order(ByteOrder.LITTLE_ENDIAN).getFloat(offset)

    private fun List<Int>.readIntAt(offset: Int): Int = ByteBuffer.wrap(
        ByteArray(size) { index -> this[index].toByte() },
    ).order(ByteOrder.LITTLE_ENDIAN).getInt(offset)

    private fun rectGeometryAuthorityFixture(
        rect: GPURect = GPURect(1f, 1f, 8f, 8f),
        transform: GPUTransformFacts = GPUTransformFacts.identity(),
    ) = corePrimitiveRectGeometryAuthority(rect, transform)

    private fun rrectFixture() = GPURRect(
        rect = GPURect(1f, 1f, 9f, 9f),
        topLeft = GPURRectCornerRadii(2f, 2f),
        topRight = GPURRectCornerRadii(2f, 2f),
        bottomRight = GPURRectCornerRadii(2f, 2f),
        bottomLeft = GPURRectCornerRadii(2f, 2f),
    )

    private fun rrectGeometryAuthorityFixture(
        source: GPURRect = rrectFixture(),
        transform: GPUTransformFacts = GPUTransformFacts.identity(),
    ): GPUCorePrimitiveRRectGeometryAuthority {
        val accepted = GPURRectNormalizer.normalize(source) as GPURRectNormalizationResult.Accepted
        return assertIs<GPUCorePrimitiveRRectGeometryAuthorityIssue.Issued>(
            corePrimitiveRRectGeometryAuthority(source, accepted, transform),
        ).authority
    }

    private fun assertRRectRawBits(
        expected: GPUCorePrimitiveGeometryInput.RRect,
        actual: GPUCorePrimitiveGeometryInput.RRect,
        label: String,
    ) {
        assertEquals(expected.left.toRawBits(), actual.left.toRawBits(), "$label left")
        assertEquals(expected.top.toRawBits(), actual.top.toRawBits(), "$label top")
        assertEquals(expected.right.toRawBits(), actual.right.toRawBits(), "$label right")
        assertEquals(expected.bottom.toRawBits(), actual.bottom.toRawBits(), "$label bottom")
        assertEquals(
            expected.radii.map(Float::toRawBits),
            actual.radii.map(Float::toRawBits),
            "$label radii",
        )
    }

    private fun GPURRect.withRadiiScale(scale: Float): GPURRect = copy(
        topLeft = GPURRectCornerRadii(topLeft.x * scale, topLeft.y * scale),
        topRight = GPURRectCornerRadii(topRight.x * scale, topRight.y * scale),
        bottomRight = GPURRectCornerRadii(bottomRight.x * scale, bottomRight.y * scale),
        bottomLeft = GPURRectCornerRadii(bottomLeft.x * scale, bottomLeft.y * scale),
    )

    private fun affineRectGeometryAuthorityFixture(
        rect: GPURect = GPURect(1f, 2f, 4f, 6f),
        transform: GPUTransformFacts = GPUTransformFacts.affine(
            scaleX = 2f,
            skewX = 0.5f,
            skewY = -0.25f,
            scaleY = 3f,
            translateX = 7f,
            translateY = -4f,
        ),
    ) = corePrimitiveRectGeometryAuthority(rect, transform)

    private fun affineRectGeometryFixture() = GPUCorePrimitiveGeometryInput.TriangulatedPath(
        vertices = listOf(10f, 1.75f, 16f, 1f, 18f, 13f, 12f, 13.75f),
        indices = listOf(0, 1, 2, 0, 2, 3),
        sourceContourStarts = listOf(0),
        sourceVertexCount = 4,
        coverBounds = GPUPixelBounds(10, 1, 16, 14),
        geometryMode = GPUCorePrimitiveGeometryMode.DirectTriangles,
        fillRule = GPUCorePrimitiveFillRule.Winding,
        inverseFill = false,
    )

    private fun strokeFan(
        strokeStyle: GPUCorePrimitiveStrokeStyle,
        sourceVertexCount: Int = 2,
        fillRule: GPUCorePrimitiveFillRule = GPUCorePrimitiveFillRule.Winding,
        inverseFill: Boolean = false,
    ) = GPUCorePrimitiveGeometryInput.TriangulatedPath(
        vertices = listOf(0f, 0f, 8f, 0f, 8f, 8f),
        indices = listOf(0, 1, 2),
        sourceContourStarts = listOf(0),
        sourceVertexCount = sourceVertexCount,
        coverBounds = GPUPixelBounds(0, 0, 8, 8),
        geometryMode = GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan,
        fillRule = fillRule,
        inverseFill = inverseFill,
        strokeStyle = strokeStyle,
    )

    private fun blend(mode: GPUBlendMode): GPUBlendPlan = GPUBlendPlan.FixedFunctionBlend(
        mode = mode,
        state = GPUFixedFunctionBlendState(
            stateId = "state.${mode.name.lowercase()}",
            color = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
            alpha = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
            writeMask = "rgba",
        ),
        sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
    )
}
