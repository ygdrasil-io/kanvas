package org.skia.gpu.webgpu

import java.io.File
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.gpu.webgpu.tools.GeneratedSolidRectWgsl

class GeneratedSolidRectMigrationTest {
    @Test
    fun `solid color rect uses generated WGSL path by default`() = withGeneratedSolidRectFlag(null) {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            SkWebGpuDevice(ctx, 64, 64).use { device ->
                drawMigratedRect(device)

                val diagnostics = device.solidRectMigrationDiagnosticsForTests()
                assertEquals("Rect + SolidColor + SrcOver", diagnostics.shaderFamily)
                assertEquals("generated", diagnostics.selectedPath)
                assertTrue(diagnostics.generatedDefaultAvailable)
                assertNull(diagnostics.retainedFallbackReason)
                assertTrue(diagnostics.handwrittenRetirementCriteria.contains("color filters"))
                val dump = device.generatedPipelineCacheDumpForTests()
                assertTrue(dump.contains("hash="))
                assertTrue(dump.contains("code=[generatedPath=true,shaderFamily=solidRect]"))
                assertTrue(dump.contains("code=[generatedPath=true] state=[blendMode=kSrcOver]"))
            }
        }
    }

    @Test
    fun `handwritten solid color fallback remains named when generated path is disabled`() =
        withGeneratedSolidRectFlag("false") {
            val context = WebGpuContext.createOrNull()
            Assumptions.assumeTrue(context != null, "No WebGPU adapter")
            context!!.use { ctx ->
                SkWebGpuDevice(ctx, 64, 64).use { device ->
                    drawMigratedRect(device)

                    val diagnostics = device.solidRectMigrationDiagnosticsForTests()
                    assertEquals("handwritten", diagnostics.selectedPath)
                    assertFalse(diagnostics.generatedDefaultAvailable)
                    assertEquals(
                        "generated solid rect disabled via -Dkanvas.gpu.generatedSolidRect.enabled=false",
                        diagnostics.retainedFallbackReason,
                    )
                }
            }
        }

    @Test
    fun `generated solid color rect reuses warm pipeline cache`() = withGeneratedSolidRectFlag(null) {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            SkWebGpuDevice(ctx, 64, 64).use { device ->
                drawMigratedRect(device)
                val cold = device.cacheTelemetrySnapshot()

                drawMigratedRect(device, left = 12f, top = 12f, right = 48f, bottom = 48f)
                val warm = device.cacheTelemetrySnapshot()

                assertTrue(cold.pipelineCacheMisses >= 1, "cold frame should create generated rect pipeline")
                assertTrue(
                    warm.pipelineCacheHits > cold.pipelineCacheHits,
                    "warm generated rect draw should reuse pipeline cache (cold=$cold warm=$warm)",
                )
                val diagnostics = device.solidRectMigrationDiagnosticsForTests()
                assertEquals("generated", diagnostics.selectedPath)

                if (System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true") {
                    val adapter = ctx.adapterInfo ?: "unknown-adapter"
                    writeCacheCounterEvidence(cold, warm, diagnostics, adapter)
                }
            }
        }
    }

    private fun drawMigratedRect(
        device: SkWebGpuDevice,
        left: Float = 8f,
        top: Float = 8f,
        right: Float = 40f,
        bottom: Float = 40f,
    ) {
        val paint = SkPaint().apply { color = SK_ColorBLUE }
        val canvas = SkCanvas(device)
        canvas.drawRect(SkRect.MakeLTRB(left, top, right, bottom), paint)
        device.flush()
    }

    private fun writeCacheCounterEvidence(
        cold: SkWebGpuDevice.GpuCacheTelemetrySnapshot,
        warm: SkWebGpuDevice.GpuCacheTelemetrySnapshot,
        diagnostics: SkWebGpuDevice.SolidRectMigrationDiagnostics,
        adapter: String,
    ) {
        val artifact = repoFile("reports/wgsl-pipeline/headless-webgpu-cache-counters-for315.json")
        val report = repoFile("reports/wgsl-pipeline/2026-06-04-for-315-headless-webgpu-cache-counters.md")
        artifact.parentFile.mkdirs()
        report.parentFile.mkdirs()
        artifact.writeText(cacheCounterEvidenceJson(cold, warm, diagnostics, adapter))
        report.writeText(cacheCounterEvidenceReport(cold, warm, diagnostics, adapter))
    }

    private fun cacheCounterEvidenceJson(
        cold: SkWebGpuDevice.GpuCacheTelemetrySnapshot,
        warm: SkWebGpuDevice.GpuCacheTelemetrySnapshot,
        diagnostics: SkWebGpuDevice.SolidRectMigrationDiagnostics,
        adapter: String,
    ): String = """
        {
          "schemaVersion": 1,
          "linearIssue": "FOR-315",
          "sourceMemory": "global/kanvas/ticket-drafts/draft-for-next-headless-web-gpu-cache-counter-evidence-ticket",
          "generatedBy": "org.skia.gpu.webgpu.GeneratedSolidRectMigrationTest#generated solid color rect reuses warm pipeline cache",
          "status": "pass",
          "claimLevel": "headless-webgpu-cache-counter-evidence",
          "sourceClass": "kanvas-headless-webgpu-observed",
          "sourceApi": "SkWebGpuDevice.cacheTelemetrySnapshot()",
          "sourceArtifact": "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/GeneratedSolidRectMigrationTest.kt",
          "backend": "WebGPU",
          "executionMode": "headless",
          "adapter": ${adapter.jsonString()},
          "notKadreNativeCallbacks": true,
          "broadKadreWgpu4kCallbacksClaimed": false,
          "releaseGate": false,
          "readinessGateChanged": false,
          "generatedPath": {
            "shaderFamily": ${diagnostics.shaderFamily.jsonString()},
            "selectedPath": ${diagnostics.selectedPath.orEmpty().jsonString()},
            "generatedDefaultAvailable": ${diagnostics.generatedDefaultAvailable},
            "retainedFallbackReason": ${diagnostics.retainedFallbackReason?.jsonString() ?: "null"}
          },
          "coldSnapshot": ${cold.toJsonObject()},
          "warmSnapshot": ${warm.toJsonObject()},
          "invariants": {
            "coldPipelineCacheMissesAtLeastOne": ${cold.pipelineCacheMisses >= 1},
            "warmPipelineCacheHitsIncreased": ${warm.pipelineCacheHits > cold.pipelineCacheHits},
            "generatedPathSelected": ${diagnostics.selectedPath == "generated"}
          },
          "nonChanges": {
            "rendererBehavior": "unchanged",
            "gradle": "unchanged",
            "shaders": "unchanged",
            "thresholds": "unchanged",
            "sceneStatus": "unchanged",
            "readiness": "unchanged",
            "releaseGateStatus": "unchanged",
            "fallbacks": "unchanged",
            "kadreNativeBehavior": "unchanged"
          }
        }
    """.trimIndent() + "\n"

    private fun cacheCounterEvidenceReport(
        cold: SkWebGpuDevice.GpuCacheTelemetrySnapshot,
        warm: SkWebGpuDevice.GpuCacheTelemetrySnapshot,
        diagnostics: SkWebGpuDevice.SolidRectMigrationDiagnostics,
        adapter: String,
    ): String = """
        # FOR-315 Headless WebGPU Cache Counter Evidence

        Linear issue: `FOR-315`.
        Source memory: `global/kanvas/ticket-drafts/draft-for-next-headless-web-gpu-cache-counter-evidence-ticket`.

        ## Summary

        `GeneratedSolidRectMigrationTest` captured cold and warm `SkWebGpuDevice.cacheTelemetrySnapshot()` values from an adapter-backed headless WebGPU run.
        The generated solid-rect path stayed selected, cold pipeline cache misses were present, and warm pipeline cache hits increased.
        This is Kanvas-owned headless WebGPU evidence, not a broad Kadre/wgpu4k native callback claim.

        ## Observed Counters

        | Counter | Cold | Warm |
        |---|---:|---:|
        | `shaderModuleCacheHits` | ${cold.shaderModuleCacheHits} | ${warm.shaderModuleCacheHits} |
        | `shaderModuleCacheMisses` | ${cold.shaderModuleCacheMisses} | ${warm.shaderModuleCacheMisses} |
        | `pipelineCacheHits` | ${cold.pipelineCacheHits} | ${warm.pipelineCacheHits} |
        | `pipelineCacheMisses` | ${cold.pipelineCacheMisses} | ${warm.pipelineCacheMisses} |
        | `resourceCacheHits` | ${cold.resourceCacheHits} | ${warm.resourceCacheHits} |
        | `resourceCacheMisses` | ${cold.resourceCacheMisses} | ${warm.resourceCacheMisses} |
        | `pipelineCreations` | ${cold.pipelineCreations} | ${warm.pipelineCreations} |
        | `shaderModuleCount` | ${cold.shaderModuleCount} | ${warm.shaderModuleCount} |
        | `pipelineCacheEntryCount` | ${cold.pipelineCacheEntryCount} | ${warm.pipelineCacheEntryCount} |
        | `resourceCacheEntryCount` | ${cold.resourceCacheEntryCount} | ${warm.resourceCacheEntryCount} |

        ## Scope

        - Source class: `kanvas-headless-webgpu-observed`.
        - Source API: `SkWebGpuDevice.cacheTelemetrySnapshot()`.
        - Adapter: `${adapter}`.
        - Generated path: `${diagnostics.selectedPath}` for `${diagnostics.shaderFamily}`.
        - Kadre/wgpu4k callback claim: none.
        - Readiness, release gates, renderer behavior, Gradle wiring, shaders, thresholds, scene status, fallback policy, and Kadre native behavior: unchanged.

        ## Validation

        - `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.GeneratedSolidRectMigrationTest`
        - `rtk python3 scripts/validate_for315_headless_webgpu_cache_counters.py`
        - `rtk python3 -m json.tool reports/wgsl-pipeline/headless-webgpu-cache-counters-for315.json >/dev/null`
        - `rtk ./gradlew --no-daemon validateMepNextRuntimeInteractive`
        - `rtk ./gradlew pipelineSceneDashboardGate`
        - `rtk git diff --check`
    """.trimIndent() + "\n"

    private fun SkWebGpuDevice.GpuCacheTelemetrySnapshot.toJsonObject(): String =
        "{" +
            "\"shaderModuleCacheHits\":$shaderModuleCacheHits," +
            "\"shaderModuleCacheMisses\":$shaderModuleCacheMisses," +
            "\"pipelineCacheHits\":$pipelineCacheHits," +
            "\"pipelineCacheMisses\":$pipelineCacheMisses," +
            "\"resourceCacheHits\":$resourceCacheHits," +
            "\"resourceCacheMisses\":$resourceCacheMisses," +
            "\"pipelineCreations\":$pipelineCreations," +
            "\"shaderModuleCount\":$shaderModuleCount," +
            "\"pipelineCacheEntryCount\":$pipelineCacheEntryCount," +
            "\"resourceCacheEntryCount\":$resourceCacheEntryCount" +
            "}"

    private fun repoFile(path: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        return File(dir, path)
    }

    private fun String.jsonString(): String = buildString {
        append('"')
        for (ch in this@jsonString) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }

    private fun <T> withGeneratedSolidRectFlag(value: String?, block: () -> T): T {
        val previous = System.getProperty(GeneratedSolidRectWgsl.FEATURE_FLAG)
        if (value == null) {
            System.clearProperty(GeneratedSolidRectWgsl.FEATURE_FLAG)
        } else {
            System.setProperty(GeneratedSolidRectWgsl.FEATURE_FLAG, value)
        }
        return try {
            block()
        } finally {
            if (previous == null) {
                System.clearProperty(GeneratedSolidRectWgsl.FEATURE_FLAG)
            } else {
                System.setProperty(GeneratedSolidRectWgsl.FEATURE_FLAG, previous)
            }
        }
    }

    private companion object {
        private const val WRITE_EVIDENCE_PROPERTY = "kanvas.sceneEvidence.write"
    }
}
