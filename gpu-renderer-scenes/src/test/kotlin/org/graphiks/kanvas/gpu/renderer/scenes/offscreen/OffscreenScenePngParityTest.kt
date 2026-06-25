package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * KGPU-M28 verification foundation: diffs the committed GPU offscreen
 * `render.png` for a scene against the independent [OffscreenSceneCpuReference].
 *
 * The GPU render itself runs in a separate JVM via the
 * `:gpu-renderer-scenes:renderGpuRendererSceneOffscreen` application task (the
 * WebGPU adapter is not available inside the test JVM), so this test consumes
 * the committed `render.png` evidence rather than rendering in-process.
 *
 * `solid-card-stack` is the positive anchor: an opaque Clear + SrcOver FillRect
 * scene the GPU renders through the verified solid-rect pass, so it must match
 * the CPU reference. This validates the harness itself (a failure means the GPU
 * diverged, not that the harness is wrong).
 */
class OffscreenScenePngParityTest {

    private data class ParityDiff(val total: Int, val mismatch: Int, val maxDelta: Int, val similarity: Double) {
        override fun toString(): String =
            "ParityDiff(similarity=${"%.4f".format(similarity)}, mismatch=$mismatch/$total, maxChannelDelta=$maxDelta)"
    }

    @Test
    fun solidCardStack_gpuMatchesCpuReference() {
        val cpu = OffscreenSceneCpuReference.renderSceneRgba("solid-card-stack")
        val gpu = loadCommittedPng("solid-card-stack")
        val d = diff(gpu, cpu, tolerance = 8)
        println("[parity] solid-card-stack -> $d")
        assertTrue(
            d.similarity >= 0.99,
            "solid-card-stack GPU↔CPU-reference similarity ${d.similarity} < 0.99 ($d)",
        )
    }

    @Test
    fun pathFillStencil_gpuMatchesCpuReference() {
        // KGPU-M28-002: the non-convex star is now filled via two-pass stencil-cover
        // (winding write + cover where stencil != 0), so it matches the clean CPU
        // star reference. Residual mismatches are single hard-edge pixels at the star
        // tips (GPU stencil coverage vs CPU even-odd point-in-polygon sampling).
        val cpu = OffscreenSceneCpuReference.renderSceneRgba("path-fill-stencil")
        val gpu = loadCommittedPng("path-fill-stencil")
        val d = diff(gpu, cpu, tolerance = 8)
        println("[parity] path-fill-stencil -> $d")
        assertTrue(
            d.similarity >= 0.99,
            "path-fill-stencil GPU↔CPU-reference similarity ${d.similarity} < 0.99 ($d)",
        )
    }

    @Test
    fun convexFanMesh_gpuMatchesCpuReference() {
        // KGPU-M28-002: the convex octagon mesh passes an identity-white uniform to the
        // vertex-color pass (`in.color * white`), so the fill color is no longer squared
        // and the octagon matches the CPU reference exactly.
        val cpu = OffscreenSceneCpuReference.renderSceneRgba("convex-fan-mesh")
        val gpu = loadCommittedPng("convex-fan-mesh")
        val d = diff(gpu, cpu, tolerance = 8)
        println("[parity] convex-fan-mesh -> $d")
        assertTrue(
            d.similarity >= 0.99,
            "convex-fan-mesh GPU↔CPU-reference similarity ${d.similarity} < 0.99 ($d)",
        )
    }

    @Test
    fun savelayerIsolated_gpuMatchesCpuReference() {
        // KGPU-M28-005/006: saveLayer children render into the offscreen secondary texture
        // and are composited via LayerCompositeWgsl srcOver onto the main target.
        val cpu = OffscreenSceneCpuReference.renderSceneRgba("savelayer-isolated")
        val gpu = loadCommittedPng("savelayer-isolated")
        val d = diff(gpu, cpu, tolerance = 8)
        println("[parity] savelayer-isolated -> $d")
        assertTrue(
            d.similarity >= 0.99,
            "savelayer-isolated GPU↔CPU-reference similarity ${d.similarity} < 0.99 ($d)",
        )
    }

    @Test
    fun recordCurrentShapeAndLayerSceneDivergence() {
        // Diagnostic only (no assert): documents the remaining layer-scene divergence
        // (blank save-layer composite on dst-read-strategy). Tasks M28-005/006 will add
        // passing parity assertions. The path/convex shape scenes are now asserted above.
        for (id in listOf("dst-read-strategy")) {
            val cpu = OffscreenSceneCpuReference.renderSceneRgba(id)
            val gpu = loadCommittedPng(id)
            println("[parity-current] $id -> ${diff(gpu, cpu, tolerance = 8)}")
        }
    }

    private fun diff(a: RefImage, b: RefImage, tolerance: Int): ParityDiff {
        require(a.width == b.width && a.height == b.height) {
            "dimension mismatch ${a.width}x${a.height} vs ${b.width}x${b.height}"
        }
        val total = a.width * a.height
        var mismatch = 0
        var maxDelta = 0
        var i = 0
        while (i < a.rgba.size) {
            val dr = kotlin.math.abs((a.rgba[i].toInt() and 0xFF) - (b.rgba[i].toInt() and 0xFF))
            val dg = kotlin.math.abs((a.rgba[i + 1].toInt() and 0xFF) - (b.rgba[i + 1].toInt() and 0xFF))
            val db = kotlin.math.abs((a.rgba[i + 2].toInt() and 0xFF) - (b.rgba[i + 2].toInt() and 0xFF))
            val m = maxOf(dr, dg, db)
            if (m > maxDelta) maxDelta = m
            if (m > tolerance) mismatch++
            i += 4
        }
        return ParityDiff(total, mismatch, maxDelta, (total - mismatch).toDouble() / total)
    }

    private fun loadCommittedPng(sceneId: String): RefImage {
        val file = File(repoRoot(), "reports/gpu-renderer-scenes/offscreen/$sceneId/render.png")
        check(file.isFile) { "Missing committed render.png for $sceneId at $file" }
        val img = ImageIO.read(file) ?: error("ImageIO failed to read $file")
        val w = img.width
        val h = img.height
        val rgba = ByteArray(w * h * 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val argb = img.getRGB(x, y)
                val base = (y * w + x) * 4
                rgba[base] = ((argb shr 16) and 0xFF).toByte()
                rgba[base + 1] = ((argb shr 8) and 0xFF).toByte()
                rgba[base + 2] = (argb and 0xFF).toByte()
                rgba[base + 3] = ((argb ushr 24) and 0xFF).toByte()
            }
        }
        return RefImage(w, h, rgba)
    }

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, "reports/gpu-renderer-scenes/offscreen").isDirectory) return dir
            dir = dir.parentFile
        }
        error("Could not locate repo root (reports/gpu-renderer-scenes/offscreen) from ${System.getProperty("user.dir")}")
    }
}
