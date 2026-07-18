package org.graphiks.kanvas.gpu.renderer.payloads

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import java.lang.reflect.Modifier
import org.graphiks.kanvas.gpu.renderer.analysis.corePrimitiveRectGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageElement
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageElementKind
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipFillRule
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformType
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.recording.stableCoreDump
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance

class GPUCorePrimitivePayloadContractsTest {
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
    ): GPUDrawSemanticPayload.CorePrimitive {
        val resolvedSourceFamily = sourceFamily ?: when (geometry) {
            is GPUCorePrimitiveGeometryInput.Rect -> GPUCorePrimitiveSourceFamily.Rect
            is GPUCorePrimitiveGeometryInput.RRect -> GPUCorePrimitiveSourceFamily.RRect
            is GPUCorePrimitiveGeometryInput.TriangulatedPath -> GPUCorePrimitiveSourceFamily.Path
        }
        val isAuthorizedRect =
            includeRectAnalysisAuthority && resolvedSourceFamily == GPUCorePrimitiveSourceFamily.Rect
        return GPUCorePrimitivePayloadGatherer().gatherSemantic(
            GPUCorePrimitivePayloadInput(
            commandIdValue = 7,
            sourceFamily = resolvedSourceFamily,
            geometry = geometry,
            premultipliedRgba = listOf(0.25f, 0.5f, 0.75f, 1f),
            targetBounds = target,
            scissorBounds = scissor,
            clipCoveragePlan = clip,
            blendPlanIdentity = blendPlan.canonicalIdentity(),
            frameProvenance = provenance,
            coverageMode = coverageMode,
                analysisRecordId = analysisRecordId ?: if (isAuthorizedRect) {
                    "analysis.fill_rect.7"
                } else {
                    null
                },
                analysisCommandFamily = analysisCommandFamily ?: if (isAuthorizedRect) {
                    "FillRect"
                } else {
                    null
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
            ),
        )
    }

    private fun rectGeometryAuthorityFixture(
        rect: GPURect = GPURect(1f, 1f, 8f, 8f),
        transform: GPUTransformFacts = GPUTransformFacts.identity(),
    ) = corePrimitiveRectGeometryAuthority(rect, transform)

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
