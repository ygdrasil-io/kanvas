package org.graphiks.kanvas.gpu.renderer.planning

import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.render.ir.ClipStackNode

/** The prepared join's geometry/coverage domain, shared before capture and after authentication. */
object W5bPreparedPointDomainV3 {
    fun requiresMask(clip: GPUClipCoveragePlan): Boolean =
        clip != GPUClipCoveragePlan.NoClip && clip !is GPUClipCoveragePlan.Scissor

    fun acceptsCoverage(sourceFamily: GPUCorePrimitiveSourceFamily, coverage: GPUCorePrimitiveCoverageMode,
        clip: GPUClipCoveragePlan, hasPointClip: Boolean): Boolean =
        coverage == GPUCorePrimitiveCoverageMode.FullOrScissor && !requiresMask(clip) ||
            sourceFamily == GPUCorePrimitiveSourceFamily.PointLine && hasPointClip

    fun acceptsPath(sourceFamily: GPUCorePrimitiveSourceFamily, geometryMode: GPUCorePrimitiveGeometryMode): Boolean =
        sourceFamily == GPUCorePrimitiveSourceFamily.PointLine && geometryMode == GPUCorePrimitiveGeometryMode.DirectTriangles

    fun acceptsRect(left: Float, top: Float, right: Float, bottom: Float): Boolean =
        listOf(left, top, right, bottom).all { it.toInt().toFloat() == it }

    fun distinctClips(clips: Iterable<ClipStackNode>): List<ClipStackNode> = clips.distinctBy { it.canonicalId }

    fun acceptsClips(clips: Iterable<ClipStackNode>): Boolean = distinctClips(clips).size <= 1
}
