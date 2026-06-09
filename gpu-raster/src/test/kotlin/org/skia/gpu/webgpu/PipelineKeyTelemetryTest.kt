package org.skia.gpu.webgpu

import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SkRect
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.gpu.webgpu.tools.GeneratedSolidRectWgsl

class PipelineKeyTelemetryTest {
    private var previousGeneratedSolidRectFlag: String? = null

    @BeforeEach
    fun disableGeneratedSolidRectForTelemetryFixtures() {
        previousGeneratedSolidRectFlag = System.getProperty(GeneratedSolidRectWgsl.FEATURE_FLAG)
        System.setProperty(GeneratedSolidRectWgsl.FEATURE_FLAG, "false")
    }

    @AfterEach
    fun restoreGeneratedSolidRectFlag() {
        val previous = previousGeneratedSolidRectFlag
        if (previous == null) {
            System.clearProperty(GeneratedSolidRectWgsl.FEATURE_FLAG)
        } else {
            System.setProperty(GeneratedSolidRectWgsl.FEATURE_FLAG, previous)
        }
    }

    @Test
    fun `pipeline key serialization is deterministic across map order`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            SkWebGpuDevice(ctx, 32, 32).use { device ->
                val a = device.buildPipelineKeyIdentityForDiagnostics(
                    linkedMapOf(
                        "blendMode" to "kSrcOver",
                        "generatedPath" to "false",
                    ),
                )
                val b = device.buildPipelineKeyIdentityForDiagnostics(
                    linkedMapOf(
                        "generatedPath" to "false",
                        "blendMode" to "kSrcOver",
                    ),
                )
                assertEquals(a, b)
            }
        }
    }

    @Test
    fun `pipeline key uses canonical grouped preimage and full sha256 hash`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            SkWebGpuDevice(ctx, 32, 32).use { device ->
                val key = device.buildPipelineKeyIdentityForDiagnostics(
                    linkedMapOf(
                        "blendMode" to "kSrcOver",
                        "coverageKind" to "analyticRect",
                        "generatedPath" to "true",
                        "pathFillRule" to "winding",
                        "tileModeY" to "clamp",
                        "tileModeX" to "repeat",
                    ),
                )

                assertEquals(
                    "pipeline.key v=1 layout=[tileModeX=repeat,tileModeY=clamp] " +
                        "code=[coverageKind=analyticRect,generatedPath=true] " +
                        "state=[blendMode=kSrcOver,pathFillRule=winding]",
                    key.preimage,
                )
                assertEquals(
                    "7ac2b29cceb2f587e6ffed54f86992a028e25e772533ff9d19a5e1d52e4fc199",
                    key.hash,
                )
                assertEquals("preimage=${key.preimage};hash=${key.hash};uniformFacts=[]", key.dump())
            }
        }
    }

    @Test
    fun `uniform only axes are excluded from canonical pipeline key and exposed as diagnostics`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            SkWebGpuDevice(ctx, 32, 32).use { device ->
                val base = device.buildPipelineKeyIdentityForDiagnostics(
                    linkedMapOf(
                        "generatedPath" to "true",
                    ),
                )
                val withUniformFacts = device.buildPipelineKeyIdentityForDiagnostics(
                    linkedMapOf(
                        "generatedPath" to "true",
                        "uniformSchemaVersion" to "3",
                    ),
                )

                assertEquals(
                    "pipeline.key v=1 layout=[] code=[generatedPath=true] state=[]",
                    withUniformFacts.preimage,
                )
                assertEquals(base.hash, withUniformFacts.hash)
                assertTrue(!withUniformFacts.preimage.contains("uniformSchemaVersion"))
                assertEquals("[uniformSchemaVersion=3]", withUniformFacts.uniformFacts)
                assertTrue(withUniformFacts.dump().contains("uniformFacts=[uniformSchemaVersion=3]"))
            }
        }
    }

    @Test
    fun `layout code and state changes produce distinct pipeline key hashes`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            SkWebGpuDevice(ctx, 32, 32).use { device ->
                val base = device.buildPipelineKeyIdentityForDiagnostics(
                    linkedMapOf(
                        "blendMode" to "kSrcOver",
                        "generatedPath" to "true",
                        "tileModeX" to "clamp",
                    ),
                )
                val layoutChanged = device.buildPipelineKeyIdentityForDiagnostics(
                    linkedMapOf(
                        "blendMode" to "kSrcOver",
                        "generatedPath" to "true",
                        "tileModeX" to "repeat",
                    ),
                )
                val codeChanged = device.buildPipelineKeyIdentityForDiagnostics(
                    linkedMapOf(
                        "blendMode" to "kSrcOver",
                        "generatedPath" to "false",
                        "tileModeX" to "clamp",
                    ),
                )
                val stateChanged = device.buildPipelineKeyIdentityForDiagnostics(
                    linkedMapOf(
                        "blendMode" to "kPlus",
                        "generatedPath" to "true",
                        "tileModeX" to "clamp",
                    ),
                )

                assertTrue(base.hash != layoutChanged.hash)
                assertTrue(base.hash != codeChanged.hash)
                assertTrue(base.hash != stateChanged.hash)
            }
        }
    }

    @Test
    fun `pipeline key cache fails fast on debug collision`() {
        val cache = PipelineKeyedCache<Int>("test", collisionFatal = true)
        val first = PipelineKey(preimage = "pipeline.key v=1 layout=[] code=[a=1] state=[]", hash = "same", uniformFacts = "[]")
        val second = PipelineKey(preimage = "pipeline.key v=1 layout=[] code=[a=2] state=[]", hash = "same", uniformFacts = "[]")

        assertEquals(1, cache.getOrPut(first) { 1 })
        val error = assertThrows(IllegalStateException::class.java) {
            cache.getOrPut(second) { 2 }
        }
        assertTrue(error.message?.contains("PipelineKey hash collision in test") == true)
        assertTrue(error.message?.contains("existingPreimage=${first.preimage}") == true)
        assertTrue(error.message?.contains("newPreimage=${second.preimage}") == true)
    }

    @Test
    fun `pipeline key cache defaults production collision to safe miss`() {
        val cache = PipelineKeyedCache<Int>("test")
        val first = PipelineKey(preimage = "pipeline.key v=1 layout=[] code=[a=1] state=[]", hash = "same", uniformFacts = "[]")
        val second = PipelineKey(preimage = "pipeline.key v=1 layout=[] code=[a=2] state=[]", hash = "same", uniformFacts = "[]")

        assertEquals(1, cache.getOrPut(first) { 1 })
        assertEquals(2, cache.getOrPut(second) { 2 })
        assertEquals(2, cache.size)
        assertTrue(cache.dump().contains(first.preimage))
        assertTrue(cache.dump().contains(second.preimage))
    }

    @Test
    fun `unknown pipeline axis throws stable diagnostic`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            SkWebGpuDevice(ctx, 32, 32).use { device ->
                val error = assertThrows(IllegalArgumentException::class.java) {
                    device.buildPipelineKeyForDiagnostics(mapOf("futureAxis" to "v"))
                }
                assertTrue(error.message?.contains("Unknown PipelineKey axis: futureAxis") == true)
            }
        }
    }

    @Test
    fun `warm frame reuses pipeline cache after first draw`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            SkWebGpuDevice(ctx, 64, 64).use { device ->
                val paint = SkPaint().apply { color = SK_ColorBLUE }
                val canvas = SkCanvas(device)
                canvas.drawRect(SkRect.MakeLTRB(4f, 4f, 40f, 40f), paint)
                device.flush()
                val cold = device.cacheTelemetrySnapshot()

                canvas.drawRect(SkRect.MakeLTRB(8f, 8f, 44f, 44f), paint)
                device.flush()
                val warm = device.cacheTelemetrySnapshot()

                assertTrue(cold.pipelineCacheMisses >= 1, "cold frame should create at least one pipeline")
                assertTrue(
                    warm.pipelineCacheHits > cold.pipelineCacheHits,
                    "warm frame should increase pipeline cache hits (cold=$cold warm=$warm)",
                )
            }
        }
    }
}
