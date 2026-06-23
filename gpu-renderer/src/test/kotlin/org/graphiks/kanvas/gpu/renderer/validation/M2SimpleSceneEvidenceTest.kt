package org.graphiks.kanvas.gpu.renderer.validation

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class M2SimpleSceneEvidenceTest {
    @Test
    fun `simple scene records M2 closeout evidence without product activation`() {
        val scene = M2SimpleSceneEvidence.build()
        val lines = scene.dumpLines()

        assertEquals(
            listOf(
                "scene:m2.simple.rrect-gradient-scissor-batch mode=contract-fixture",
                "rrect:accepted routeCandidate=native.fill_rrect.solid geometry=rrect.corner_radii=tl(6.0,6.0);tr(10.0,10.0);br(14.0,14.0);bl(4.0,4.0)",
                "gradient:accepted materialKey=material:linear-gradient.clamp.inline2 payload=gradient.inline2 stops=2 tileMode=clamp",
                "wgsl:accepted module=m2-simple-rrect-linear-gradient parser=parser-backed:wgsl4k reflection=wgsl4k-parsed",
                "clip:accepted stack=m2-simple-device-scissor element=scissor rect=0.0,0.0,96.0,64.0 mode=intersect",
                "batch:accepted key=batch:rrect.linear-gradient.scissor draws=2 boundaries=materialKey,clipStack,layer,ordering",
                "batch:split reason=material-key-mismatch before=draw-2 after=draw-3",
                "batch:split reason=clip-stack-mismatch before=draw-3 after=draw-4",
                "batch:split reason=layer-or-ordering-boundary before=draw-4 after=draw-5",
                "gradient:refused reason=unsupported.gradient.tile_mode source=mirror requiredGate=tile-mode-wgsl-reference-evidence",
                "clip:refused reason=unsupported.clip.non_device_rect requiredGate=clip-stencil-mask-evidence",
                "nonclaim:paths-images-text-filters-saveLayer-complex-clips-not-routed",
                "gpu-lane:explicit-skipped reason=adapter-backed-webgpu-evidence-not-run productRouteActivated=false releaseBlocking=false readinessDelta=0.0",
            ),
            lines,
        )

        assertFalse(lines.any { it.contains("productRouteActivated=true") })
        assertFalse(lines.any { it.contains("gpuRasterRouteActivated=true") })
        assertFalse(lines.any { it.contains("readinessDelta=") && !it.endsWith("readinessDelta=0.0") })
    }

    @Test
    fun `simple scene records conservative M2 refusals`() {
        val lines = M2SimpleSceneEvidence.build().dumpLines()

        assertContains(lines, "gradient:refused reason=unsupported.gradient.tile_mode source=mirror requiredGate=tile-mode-wgsl-reference-evidence")
        assertContains(lines, "clip:refused reason=unsupported.clip.non_device_rect requiredGate=clip-stencil-mask-evidence")
        assertContains(lines, "batch:split reason=material-key-mismatch before=draw-2 after=draw-3")
        assertContains(lines, "batch:split reason=clip-stack-mismatch before=draw-3 after=draw-4")
        assertContains(lines, "batch:split reason=layer-or-ordering-boundary before=draw-4 after=draw-5")
        assertContains(lines, "nonclaim:paths-images-text-filters-saveLayer-complex-clips-not-routed")
    }
}
