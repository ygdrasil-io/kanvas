package org.graphiks.kanvas.gpu.renderer.clips

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class BoundedClipPreparedRouteTest {
    @Test
    fun `bounded rrect path clip builds CPU prepared mask evidence`() {
        val plan = GPUBoundedClipPreparedPlanner().plan(
            stack = clipStack,
            elements = listOf(rrectClip, pathClip),
        )

        val mask = assertIs<GPUClipElementPlan.Mask>(plan.elements.single()).plan

        assertEquals("CPUPreparedGPU", plan.routeKind)
        assertEquals(expectedMaskKey(rrectClip, pathClip), mask.maskArtifactKey)
        assertEquals("CoverageMaskArtifact", mask.artifactType)
        assertEquals("recording-local", mask.lifetimeClass)
        assertEquals("clip-bounded", mask.budgetClass)
        assertEquals("coverage-mask.standalone", mask.strategyLabel)
        assertEquals("clip-mask.sample", mask.consumerKind)
        assertEquals("nearest", mask.samplingPolicy)
        assertEquals(GPUClipOrderingToken("clip-order.stack_m3_clip.gen7.elements2"), plan.orderingToken)
        assertEquals(listOf(rrectClip, pathClip), plan.elementDescriptors)
        assertFalse(mask.maskArtifactKey.contains("handle"))
        assertFalse(mask.maskArtifactKey.contains("0x"))
        assertEquals(
            listOf(
                "clip:prepared routeKind=CPUPreparedGPU strategy=coverage-mask.standalone consumer=clip-mask.sample ordering=clip-order.stack_m3_clip.gen7.elements2",
                "clip:stack id=stack:m3-clip state=Complex bounds=local[0,0,32,24] generation=7 elements=2 provenance=unit-test",
                "clip:element order=0 id=clip-rrect shape=rrect operation=Intersect key=rrect:32x24:r6 transform=identity aa=coverage-aa bounds=local[0,0,32,24] inverse=false fillRule=NonZero",
                "clip:element order=1 id=clip-path shape=path operation=Intersect key=path:clip:v1 transform=identity aa=coverage-aa bounds=local[4,4,28,20] inverse=false fillRule=NonZero",
                "clip:mask artifact=${expectedMaskKey(rrectClip, pathClip)} type=CoverageMaskArtifact lifetime=recording-local budget=clip-bounded bounds=local[0,0,32,24] sampling=nearest atlasPolicy=NoAtlas",
                "nonclaim:no-product-activation no-adapter-backed-execution no-arbitrary-clip-stack no-stencil-coverage no-atlas-generation no-clip-shader no-cpu-rendered-clipped-layer",
            ),
            plan.dumpLines(),
        )
    }

    @Test
    fun `accepted bounded clip contents derive distinct mask artifact keys`() {
        val basePlan = GPUBoundedClipPreparedPlanner().plan(
            stack = clipStack,
            elements = listOf(rrectClip, pathClip),
        )
        val changedPathPlan = GPUBoundedClipPreparedPlanner().plan(
            stack = clipStack,
            elements = listOf(
                rrectClip,
                pathClip.copy(
                    shapeKey = "path:clip:v2",
                    boundsLabel = "local[5,4,28,20]",
                ),
            ),
        )
        val changedFillRulePlan = GPUBoundedClipPreparedPlanner().plan(
            stack = clipStack,
            elements = listOf(rrectClip, pathClip.copy(fillRule = "EvenOdd")),
        )
        val underscoreKeyPlan = GPUBoundedClipPreparedPlanner().plan(
            stack = clipStack,
            elements = listOf(rrectClip, pathClip.copy(shapeKey = "path:a_b")),
        )
        val colonKeyPlan = GPUBoundedClipPreparedPlanner().plan(
            stack = clipStack,
            elements = listOf(rrectClip, pathClip.copy(shapeKey = "path:a:b")),
        )
        val changedStackPlan = GPUBoundedClipPreparedPlanner().plan(
            stack = clipStack.copy(boundsLabel = "local[1,0,32,24]"),
            elements = listOf(rrectClip, pathClip),
        )

        val baseMask = assertIs<GPUClipElementPlan.Mask>(basePlan.elements.single()).plan
        val changedPathMask = assertIs<GPUClipElementPlan.Mask>(changedPathPlan.elements.single()).plan
        val changedFillRuleMask = assertIs<GPUClipElementPlan.Mask>(changedFillRulePlan.elements.single()).plan
        val underscoreKeyMask = assertIs<GPUClipElementPlan.Mask>(underscoreKeyPlan.elements.single()).plan
        val colonKeyMask = assertIs<GPUClipElementPlan.Mask>(colonKeyPlan.elements.single()).plan
        val changedStackMask = assertIs<GPUClipElementPlan.Mask>(changedStackPlan.elements.single()).plan

        assertNotEquals(baseMask.maskArtifactKey, changedPathMask.maskArtifactKey)
        assertNotEquals(baseMask.maskArtifactKey, changedFillRuleMask.maskArtifactKey)
        assertNotEquals(underscoreKeyMask.maskArtifactKey, colonKeyMask.maskArtifactKey)
        assertNotEquals(baseMask.maskArtifactKey, changedStackMask.maskArtifactKey)
        assertFalse(changedPathMask.maskArtifactKey.contains("handle"))
        assertFalse(changedPathMask.maskArtifactKey.contains("0x"))
        assertFalse(colonKeyMask.maskArtifactKey.contains("handle"))
        assertFalse(colonKeyMask.maskArtifactKey.contains("0x"))
    }

    @Test
    fun `unsupported bounded clip variants refuse with stable diagnostics`() {
        val cases = listOf(
            ClipRefusalCase("unsupported.clip.operation", elements = listOf(rrectClip.copy(operation = "Difference"), pathClip)),
            ClipRefusalCase("unsupported.clip.inverse_unaccepted", elements = listOf(rrectClip, pathClip.copy(inverseFill = true))),
            ClipRefusalCase("unsupported.clip.shader_unregistered", elements = listOf(rrectClip, pathClip.copy(shapeKind = "shader"))),
            ClipRefusalCase("unsupported.clip.mask_budget_exceeded", elements = listOf(rrectClip, pathClip.copy(coveragePixelEstimate = 4097))),
            ClipRefusalCase(
                "unsupported.clip.element_key_nondeterministic",
                elements = listOf(rrectClip, pathClip.copy(shapeKey = "path:handle:0xdeadbeef")),
            ),
            ClipRefusalCase(
                "unsupported.clip.element_key_nondeterministic",
                elements = listOf(rrectClip.copy(shapeKey = "rrect:handle:0xdeadbeef"), pathClip),
            ),
            ClipRefusalCase(
                "unsupported.clip.element_key_mismatch",
                elements = listOf(rrectClip.copy(shapeKey = "path:clip:v1"), pathClip),
            ),
            ClipRefusalCase("unsupported.clip.stack_unbounded", stack = clipStack.copy(boundsLabel = "unbounded")),
        )

        for (case in cases) {
            val plan = GPUBoundedClipPreparedPlanner().plan(
                stack = case.stack,
                elements = case.elements,
            )
            val refused = assertIs<GPUClipElementPlan.Refused>(plan.elements.single())

            assertEquals("RefuseDiagnostic", plan.routeKind)
            assertEquals(case.expectedCode, refused.diagnostic.code)
            assertContains(plan.diagnostics.map { it.code }, case.expectedCode)
            assertEquals(
                listOf(
                    "clip:refused reason=${case.expectedCode} stack=stack:m3-clip routeKind=RefuseDiagnostic",
                    "nonclaim:no-product-activation no-adapter-backed-execution no-arbitrary-clip-stack no-stencil-coverage no-atlas-generation no-clip-shader no-cpu-rendered-clipped-layer",
                ),
                plan.dumpLines(),
            )
        }
    }
}

private data class ClipRefusalCase(
    val expectedCode: String,
    val stack: GPUClipStackDescriptor = clipStack,
    val elements: List<GPUClipElementDescriptor> = listOf(rrectClip, pathClip),
)

private val clipStack = GPUClipStackDescriptor(
    stackId = "stack:m3-clip",
    stateLabel = "Complex",
    boundsLabel = "local[0,0,32,24]",
    activeElementCount = 2,
    generation = 7,
    provenance = "unit-test",
)

private val rrectClip = GPUClipElementDescriptor(
    elementId = "clip-rrect",
    sourceOrder = 0,
    shapeKind = "rrect",
    operation = "Intersect",
    shapeKey = "rrect:32x24:r6",
    boundsLabel = "local[0,0,32,24]",
    transformClass = "identity",
    antiAliasMode = "coverage-aa",
    fillRule = "NonZero",
    inverseFill = false,
    coveragePixelEstimate = 512,
)

private val pathClip = GPUClipElementDescriptor(
    elementId = "clip-path",
    sourceOrder = 1,
    shapeKind = "path",
    operation = "Intersect",
    shapeKey = "path:clip:v1",
    boundsLabel = "local[4,4,28,20]",
    transformClass = "identity",
    antiAliasMode = "coverage-aa",
    fillRule = "NonZero",
    inverseFill = false,
    coveragePixelEstimate = 768,
)

private fun expectedMaskKey(
    vararg elements: GPUClipElementDescriptor,
    stack: GPUClipStackDescriptor = clipStack,
): String =
    "coverage.clip.${stack.stackId.sanitizeForExpectedKey()}.gen${stack.generation}." +
        "${stack.expectedSegment()}_${elements.sortedBy { it.sourceOrder }.joinToString("_") { it.expectedSegment() }}" +
        ".elements${elements.size}"

private fun GPUClipStackDescriptor.expectedSegment(): String =
    listOf(
        "stack",
        stackId,
        stateLabel,
        boundsLabel,
        activeElementCount.toString(),
        generation.toString(),
    ).joinToString(".") { value -> value.encodeForExpectedKey() }

private fun GPUClipElementDescriptor.expectedSegment(): String =
    listOf(
        "element",
        sourceOrder.toString(),
        shapeKind,
        shapeKey,
        operation,
        boundsLabel,
        transformClass,
        antiAliasMode,
        fillRule,
        inverseFill.toString(),
    ).joinToString(".") { value -> value.encodeForExpectedKey() }

private fun String.sanitizeForExpectedKey(): String =
    map { char ->
        when {
            char.isLetterOrDigit() -> char
            else -> '_'
        }
    }.joinToString("")
        .replace(Regex("_+"), "_")
        .trim('_')

private fun String.encodeForExpectedKey(): String =
    encodeToByteArray()
        .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
