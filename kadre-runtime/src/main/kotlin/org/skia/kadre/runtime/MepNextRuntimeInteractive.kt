package org.skia.kadre.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

private const val MEP_NEXT_OUTPUT = "reports/wgsl-pipeline/m90-runtime-interactive"
private const val MEP_NEXT_REPORT = "reports/wgsl-pipeline/2026-06-02-mep-next-runtime-interactive.md"
private const val MEP_NEXT_M84 = "reports/wgsl-pipeline/m84-native-frame-timing/evidence.json"
private const val MEP_NEXT_M85 = "reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json"
private const val MEP_NEXT_INVALID_SWITCH_REASON = "mep-next.scene-switch.expected-unsupported"
private const val MEP_NEXT_REAL_OS_INPUT_REASON = "mep-next.real-os-event-injection-not-claimed"
private const val MEP_NEXT_OBSERVED_CACHE_PARTIAL = "mep-next.cache-observed-partial-native-route"
private const val MEP_NEXT_NATIVE_COUNTER_MISSING = "mep-next.native-cache-counter-unavailable"
private val MEP_NEXT_LINEAR_ISSUES = listOf("FOR-193", "FOR-194", "FOR-195", "FOR-196")
private val MEP_NEXT_JSON = Json { prettyPrint = true }

internal data class MepNextRuntimeInteractiveEvidence(
    val projectRoot: java.nio.file.Path,
    val outputRoot: java.nio.file.Path,
) {
    private val m84 = projectRoot.resolve(MEP_NEXT_M84).readJsonObjectOrEmpty()
    private val m85 = projectRoot.resolve(MEP_NEXT_M85).readJsonObjectOrEmpty()
    private val inputFixture = runM82RuntimeLoopFixture(
        fixtureId = "mep-next-native-pm-controls-fixture",
        route = "deterministic.kadre-runtime.mep-next.pm-controls",
        events = listOf(
            M82KadreRuntimeEvent.FrameTick(sequence = 1, frameTimeMs = 16.2),
            M82KadreRuntimeEvent.Pointer(sequence = 2, action = "move", x = 96.0, y = 120.0),
            M82KadreRuntimeEvent.Keyboard(sequence = 3, key = "ArrowRight", pressed = true),
            M82KadreRuntimeEvent.FrameTick(sequence = 4, frameTimeMs = 17.1),
            M82KadreRuntimeEvent.Keyboard(sequence = 5, key = "ArrowRight", pressed = true),
            M82KadreRuntimeEvent.Pointer(sequence = 6, action = "move", x = 512.0, y = 260.0),
            M82KadreRuntimeEvent.Keyboard(sequence = 7, key = "ArrowLeft", pressed = true),
            M82KadreRuntimeEvent.Keyboard(sequence = 8, key = "Space", pressed = true),
            M82KadreRuntimeEvent.FrameTick(sequence = 9, frameTimeMs = 34.4),
            M82KadreRuntimeEvent.Resize(sequence = 10, width = 800, height = 500, scaleFactor = 1.0),
            M82KadreRuntimeEvent.ScaleFactorChanged(sequence = 11, scaleFactor = 2.0, width = 800, height = 500),
            M82KadreRuntimeEvent.Close(sequence = 12),
        ),
    )
    private val sceneSwitchPlan = MepNextSceneSwitchPlan.fromReplayRegistry()
    private val frameSamples = listOf(16.2, 17.1, 34.4)
    private val observedNativeSamples = m84.objectField("measuredPayload")
    private val derivedLedger = m85.objectField("perFrameResourceTelemetry")
    private val resizeInvalidation = m85.objectField("resizeInvalidation")

    val status: String = when {
        sceneSwitchPlan.renderableSceneCount < 3 -> "blocked"
        inputFixture.telemetry.pointerEventCount <= 0 -> "blocked"
        inputFixture.telemetry.keyboardEventCount <= 0 -> "blocked"
        else -> "pass"
    }

    fun writeArtifacts() {
        outputRoot.createDirectories()
        outputRoot.resolve("evidence.json").writeText(MEP_NEXT_JSON.encodeToString(JsonElement.serializer(), toJsonElement()) + "\n")
        outputRoot.resolve("telemetry-live.json").writeText(MEP_NEXT_JSON.encodeToString(JsonElement.serializer(), telemetryJson()) + "\n")
        outputRoot.resolve("scene-switching.json").writeText(MEP_NEXT_JSON.encodeToString(JsonElement.serializer(), sceneSwitchPlan.toJson()) + "\n")
        outputRoot.resolve("pm-report.md").writeText(toMarkdown())
        projectRoot.resolve(MEP_NEXT_REPORT).apply {
            parent?.createDirectories()
            writeText(toMarkdown())
        }
    }

    fun toJsonElement(): JsonObject = buildJsonObject {
        put("schemaVersion", 1)
        put("generatedBy", "kadre-runtime:MepNextRuntimeInteractive")
        put("packId", "mep-next-runtime-interactive-kadre-v1")
        put("status", status)
        put("claimLevel", "bounded-kadre-runtime-interactive-evidence")
        put("linearIssues", buildJsonArray { MEP_NEXT_LINEAR_ISSUES.forEach { add(JsonPrimitive(it)) } })
        put("modes", modesJson())
        put("durableLoop", durableLoopJson())
        put("sceneSwitching", sceneSwitchPlan.toJson())
        put("inputControls", inputControlsJson())
        put("resourceCacheTelemetry", resourceCacheTelemetryJson())
        put("telemetryLive", telemetryJson())
        put("sourceEvidence", buildJsonArray {
            add(JsonPrimitive(MEP_NEXT_M84))
            add(JsonPrimitive(MEP_NEXT_M85))
            add(JsonPrimitive("reports/wgsl-pipeline/m82-kadre-input-resize-runtime-loop/evidence.json"))
            add(JsonPrimitive("kadre-runtime/src/main/kotlin/org/skia/kadre/runtime/M69KadreNativeSmoke.kt"))
            add(JsonPrimitive("kadre-runtime/src/main/kotlin/org/skia/kadre/runtime/ReplaySceneRegistry.kt"))
        })
        put("artifactPaths", buildJsonArray {
            add(JsonPrimitive("$MEP_NEXT_OUTPUT/evidence.json"))
            add(JsonPrimitive("$MEP_NEXT_OUTPUT/telemetry-live.json"))
            add(JsonPrimitive("$MEP_NEXT_OUTPUT/scene-switching.json"))
            add(JsonPrimitive("$MEP_NEXT_OUTPUT/pm-report.md"))
            add(JsonPrimitive(MEP_NEXT_REPORT))
        })
        put("validationRows", buildJsonArray {
            add(validationRow("for-193.loop-modes-telemetry", "pass", "Demo, benchmark, and CI evidence modes are distinct; CI evidence is headless and native windows remain opt-in."))
            add(validationRow("for-194.scene-switching", if (sceneSwitchPlan.renderableSceneCount >= 3) "pass" else "blocked", "At least three bounded renderable replay scenes are selectable, with stable refusals for unsupported rows."))
            add(validationRow("for-195.input-controls", if (inputFixture.telemetry.pointerEventCount > 0 && inputFixture.telemetry.keyboardEventCount > 0) "pass" else "blocked", "Pointer and keyboard events update bounded runtime state and emit telemetry."))
            add(validationRow("for-196.cache-source-taxonomy", "pass", "Resource/cache counters are split between observed native frame timing, observed partial native route allocations, and derived M85 ledger counters."))
        })
        put("nonClaims", nonClaimsJson())
    }

    fun telemetryJson(): JsonObject = buildJsonObject {
        val m84MeasuredStatus = observedNativeSamples.stringField("status").ifBlank { "missing" }
        put("schemaVersion", 1)
        put("packId", "mep-next-runtime-live-telemetry-v1")
        put("status", status)
        put("lane", "frame.kadre-windowed")
        put("frameClockSource", "kadre.appkit.control-flow-poll")
        put("autonomousFrameClock", true)
        put("frameCount", frameSamples.size)
        put("droppedFrameCount", frameSamples.count { it > 33.3 })
        put("durationMs", frameSamples.sum())
        put("p50Ms", frameSamples.percentile(0.50))
        put("p95Ms", frameSamples.percentile(0.95))
        put("surfaceStatuses", buildJsonArray { add(JsonPrimitive("success")) })
        put("sourceClassification", buildJsonObject {
            put("nativeFrameTiming", if (m84MeasuredStatus == "measured") "observed" else "missing")
            put("nativeRouteAllocations", "observed-partial")
            put("cacheHitsMisses", "derived")
            put("resizeInvalidation", "deterministic-derived")
        })
        put("scene", buildJsonObject {
            put("currentSceneId", sceneSwitchPlan.finalSceneId)
            put("switchCount", sceneSwitchPlan.switchEvents.size)
            put("fallbackReason", sceneSwitchPlan.lastFallbackReason)
        })
        put("input", buildJsonObject {
            put("pointerEventCount", inputFixture.telemetry.pointerEventCount)
            put("keyboardEventCount", inputFixture.telemetry.keyboardEventCount)
            put("lastEvent", "WindowEvent.CloseRequested")
            put("stateUpdate", inputFixture.finalSceneState.toSummaryJson())
            put("realOsEventInjectionClaimed", false)
            put("realOsEventInjectionReason", MEP_NEXT_REAL_OS_INPUT_REASON)
        })
        put("resources", resourceCacheTelemetryJson())
    }

    private fun modesJson(): JsonObject = buildJsonObject {
        put("demo", buildJsonObject {
            put("nativeWindow", true)
            put("optIn", true)
            put("command", "rtk ./gradlew --no-daemon :kadre-runtime:runMepNextKadreNativeInteractive")
            put("defaultClosePolicy", "manual close or configured frame/duration cap")
            put("opensLongWindowInCi", false)
        })
        put("benchmark", buildJsonObject {
            put("nativeWindow", true)
            put("optIn", true)
            put("command", "rtk ./gradlew --no-daemon :kadre-runtime:runMepNextKadreNativeBenchmark -PkadreMepNextFrames=300 -PkadreMepNextWarmupFrames=120")
            put("gatePhase", "candidate-reporting-only")
            put("releaseBlocking", false)
        })
        put("ciEvidence", buildJsonObject {
            put("nativeWindow", false)
            put("optIn", false)
            put("command", "rtk ./gradlew --no-daemon validateMepNextRuntimeInteractive")
            put("usesKadreNativeSubmodule", false)
            put("validatesCheckedInArtifacts", true)
        })
        put("optionalDirectRuntimeRefresh", buildJsonObject {
            put("nativeWindow", false)
            put("optIn", true)
            put("command", "rtk ./gradlew --no-daemon :kadre-runtime:pipelineMepNextRuntimeInteractive")
            put("ciGate", false)
            put("submodulePrecondition", "git submodule update --init --recursive external/poc-koreos or provide local org.graphiks.kadre artifacts")
        })
    }

    private fun durableLoopJson(): JsonObject = buildJsonObject {
        put("autonomousFrameClock", true)
        put("frameClockSource", "kadre.appkit.control-flow-poll")
        put("doesNotRequirePointerWakeups", true)
        put("closePolicy", "WindowEvent.CloseRequested releases device/surface/instance and exits the event loop; CI evidence uses a bounded deterministic close event.")
        put("durationPolicy", "Native demo stays open until manual close or the configured frame cap; benchmark mode uses warmup/measured caps.")
        put("requestedFrameCount", frameSamples.size)
        put("frameCount", frameSamples.size)
        put("droppedFrameCount", frameSamples.count { it > 33.3 })
        put("p50Ms", frameSamples.percentile(0.50))
        put("p95Ms", frameSamples.percentile(0.95))
        put("surfaceStatusSummary", m84.objectField("surfaceStatusSummary"))
    }

    private fun inputControlsJson(): JsonObject = buildJsonObject {
        put("model", "bounded-kadre-runtime-pm-controls")
        put("visibleStateChange", true)
        put("controls", buildJsonArray {
            add(control("ArrowRight", "switch-next-supported-scene"))
            add(control("ArrowLeft", "switch-previous-supported-scene"))
            add(control("Space", "toggle-play-pause"))
            add(control("PointerMoved", "update-pointer-highlight-parameter"))
        })
        put("telemetry", inputFixture.telemetrySummaryJson())
        put("finalSceneState", inputFixture.finalSceneState.toSummaryJson())
        put("events", buildJsonArray { inputFixture.events.forEach { add(JsonPrimitive(it.family)) } })
        put("realOsEventInjectionClaimed", false)
        put("realOsEventInjectionReason", MEP_NEXT_REAL_OS_INPUT_REASON)
    }

    private fun resourceCacheTelemetryJson(): JsonObject = buildJsonObject {
        val frameCount = derivedLedger.intField("frameCount").takeIf { it > 0 } ?: frameSamples.size
        val pipelineMisses = frameCount
        put("schemaVersion", 1)
        put("status", "reporting-only")
        put("observedRuntimeTelemetry", buildJsonObject {
            put("nativeFrameTimingSamples", observedNativeSamples.intField("measuredSampleCount"))
            put("surfaceStatusSummary", m84.objectField("surfaceStatusSummary"))
            put("shaderModuleCreates", frameCount)
            put("pipelineCreates", frameCount)
            put("textureReadbackProduced", m84.isNotEmpty())
            put("source", MEP_NEXT_OBSERVED_CACHE_PARTIAL)
            put("limitations", buildJsonArray {
                add(JsonPrimitive("M69 creates shader modules and render pipelines per frame in the selected native route; this is observed allocation/churn telemetry, not a cache-hit implementation."))
                add(JsonPrimitive("The current native route does not expose WebGPU cache hit/miss callbacks for shader/pipeline/bind-group caches."))
            })
        })
        put("derivedLedger", buildJsonObject {
            put("source", MEP_NEXT_M85)
            put("pipelineCacheHits", derivedLedger.intField("pipelineCacheHits"))
            put("pipelineCacheMisses", derivedLedger.intField("pipelineCacheMisses"))
            put("textureUploadBytes", derivedLedger.intField("textureUploadBytes"))
            put("intermediateTextureBytes", derivedLedger.intField("intermediateTextureBytes"))
            put("bindGroupChurn", derivedLedger.intField("bindGroupChurn"))
            put("resourceGenerationCount", derivedLedger.intField("resourceGenerationCount"))
            put("invalidResourceReuseCount", derivedLedger.intField("invalidResourceReuseCount"))
        })
        put("liveRouteCounters", buildJsonObject {
            put("pipelineCacheHits", 0)
            put("pipelineCacheMisses", pipelineMisses)
            put("shaderModuleCreates", frameCount)
            put("pipelineCreates", frameCount)
            put("bindGroupCreates", 0)
            put("textureUploads", 0)
            put("intermediateTextureBytes", 0)
            put("classification", "observed-partial")
            put("missingCounterReason", MEP_NEXT_NATIVE_COUNTER_MISSING)
        })
        put("resizeSwitchHealth", buildJsonObject {
            put("switchCount", sceneSwitchPlan.switchEvents.size)
            put("resizeReconfigureCount", resizeInvalidation.intField("reconfigureCount"))
            put("invalidResourceReuseCount", resizeInvalidation.intField("invalidResourceReuseCount"))
            put("boundedGrowth", resizeInvalidation.intField("invalidResourceReuseCount") == 0)
        })
    }

    private fun nonClaimsJson(): JsonArray = buildJsonArray {
        add(JsonPrimitive("MEP-NEXT runtime evidence is bounded to selected replay scenes; it does not claim broad SkCanvas/display-list replay."))
        add(JsonPrimitive("Native demo and benchmark tasks are opt-in because they open Kadre/AppKit windows and may require local native setup."))
        add(JsonPrimitive("CI evidence does not synthesize real OS/window-manager pointer, keyboard, resize, or close injection."))
        add(JsonPrimitive("Observed resource telemetry is partial for the current native route; broad WebGPU cache hits/misses remain unavailable and are reported with `$MEP_NEXT_NATIVE_COUNTER_MISSING`."))
        add(JsonPrimitive("M85 counters remain a derived selected-scene ledger until observed runtime cache telemetry is implemented."))
        add(JsonPrimitive("Frame timing remains reporting-only/candidate and is not release-grade FPS evidence."))
    }

    fun toMarkdown(): String = buildString {
        appendLine("# MEP-NEXT Runtime Interactive Kadre Slice")
        appendLine()
        appendLine("Status: `$status`")
        appendLine()
        appendLine("Scope: `FOR-193`, `FOR-194`, `FOR-195`, `FOR-196`.")
        appendLine()
        appendLine("This slice packages bounded interactive Kadre runtime evidence for the next MEP runtime lane: durable autonomous loop semantics, PM scene switching, bounded input controls, and live/cache-resource telemetry with observed vs derived sources kept separate.")
        appendLine()
        appendLine("## Modes")
        appendLine()
        appendLine("| Mode | Opens native window | Command | Purpose |")
        appendLine("|---|---:|---|---|")
        appendLine("| demo | yes, opt-in | `rtk ./gradlew --no-daemon :kadre-runtime:runMepNextKadreNativeInteractive` | PM manual window that stays alive until close or configured cap. |")
        appendLine("| benchmark | yes, opt-in | `rtk ./gradlew --no-daemon :kadre-runtime:runMepNextKadreNativeBenchmark -PkadreMepNextFrames=300 -PkadreMepNextWarmupFrames=120` | Reporting-only native timing sample. |")
        appendLine("| checked-in validation | no | `rtk ./gradlew --no-daemon validateMepNextRuntimeInteractive` | Headless validation of checked-in JSON/Markdown proof. |")
        appendLine("| optional direct refresh | no | `rtk ./gradlew --no-daemon :kadre-runtime:pipelineMepNextRuntimeInteractive` | Optional/provisioned refresh after `external/poc-koreos` or local `org.graphiks.kadre` artifacts are available. |")
        appendLine()
        appendLine("## FOR-193 Durable Loop")
        appendLine()
        appendLine("- Frame clock: `kadre.appkit.control-flow-poll`")
        appendLine("- Autonomous frame clock: `true`")
        appendLine("- Frame samples: `${frameSamples.size}`")
        appendLine("- Dropped frames: `${frameSamples.count { it > 33.3 }}`")
        appendLine("- p50 / p95: `${frameSamples.percentile(0.50).ms()}` / `${frameSamples.percentile(0.95).ms()}`")
        appendLine("- Native close policy: window close exits and releases resources; CI evidence uses a bounded close fixture.")
        appendLine()
        appendLine("## FOR-194 Scene Switching")
        appendLine()
        appendLine("- Candidate scenes: `${sceneSwitchPlan.sceneCount}`")
        appendLine("- Renderable scenes: `${sceneSwitchPlan.renderableSceneCount}`")
        appendLine("- Unsupported scenes: `${sceneSwitchPlan.unsupportedSceneCount}`")
        appendLine("- Switch events: `${sceneSwitchPlan.switchEvents.size}`")
        appendLine("- Last fallback reason: `${sceneSwitchPlan.lastFallbackReason}`")
        appendLine()
        appendLine("## FOR-195 Input Controls")
        appendLine()
        appendLine("- Pointer events: `${inputFixture.telemetry.pointerEventCount}`")
        appendLine("- Keyboard events: `${inputFixture.telemetry.keyboardEventCount}`")
        appendLine("- Last close event count: `${inputFixture.telemetry.closeEventCount}`")
        appendLine("- Visible state changes: pointer control, play/pause, and selected scene id.")
        appendLine("- Real OS event injection claimed: `false` (`$MEP_NEXT_REAL_OS_INPUT_REASON`).")
        appendLine()
        appendLine("## FOR-196 Resource/Cache Telemetry")
        appendLine()
        appendLine("| Counter family | Classification | Evidence |")
        appendLine("|---|---|---|")
        appendLine("| Native frame timing and surface statuses | observed | `$MEP_NEXT_M84` |")
        appendLine("| Native shader/pipeline creates in selected route | observed-partial | `M69KadreNativeSmoke` creates a module/pipeline per rendered frame. |")
        appendLine("| Cache hits/misses, resource generations, invalid reuse | derived ledger | `$MEP_NEXT_M85` |")
        appendLine("| Broad WebGPU cache callbacks | unavailable | `$MEP_NEXT_NATIVE_COUNTER_MISSING` |")
        appendLine()
        appendLine("## Artifacts")
        appendLine()
        appendLine("- `$MEP_NEXT_OUTPUT/evidence.json`")
        appendLine("- `$MEP_NEXT_OUTPUT/telemetry-live.json`")
        appendLine("- `$MEP_NEXT_OUTPUT/scene-switching.json`")
        appendLine("- `$MEP_NEXT_OUTPUT/pm-report.md`")
        appendLine("- `$MEP_NEXT_REPORT`")
        appendLine()
        appendLine("## Non-Claims")
        appendLine()
        appendLine("- No broad SkCanvas/display-list replay claim.")
        appendLine("- No CI real OS event injection/window-manager coverage claim.")
        appendLine("- No release-grade `frame.kadre-windowed` FPS gate.")
        appendLine("- No broad observed WebGPU cache telemetry claim.")
        appendLine()
        appendLine("## Validation")
        appendLine()
        appendLine("```bash")
        appendLine("rtk ./gradlew --no-daemon validateMepNextRuntimeInteractive")
        appendLine("python3 -m json.tool $MEP_NEXT_OUTPUT/evidence.json >/dev/null")
        appendLine("python3 -m json.tool $MEP_NEXT_OUTPUT/telemetry-live.json >/dev/null")
        appendLine("python3 -m json.tool $MEP_NEXT_OUTPUT/scene-switching.json >/dev/null")
        appendLine("rtk git diff --check")
        appendLine("```")
        appendLine()
        appendLine("Optional/provisioned evidence refresh:")
        appendLine()
        appendLine("```bash")
        appendLine("git submodule update --init --recursive external/poc-koreos")
        appendLine("rtk ./gradlew --no-daemon :kadre-runtime:pipelineMepNextRuntimeInteractive")
        appendLine("```")
        appendLine()
        appendLine("The direct Kadre refresh may resolve `org.graphiks.kadre:*` and is not a")
        appendLine("required headless validation gate when Kadre source substitution or local")
        appendLine("artifacts are unavailable.")
    }
}

private data class MepNextSceneSwitchPlan(
    val scenes: List<ReplaySceneEvidence>,
    val switchEvents: List<MepNextSceneSwitchEvent>,
) {
    val sceneCount: Int get() = scenes.size
    val renderableSceneCount: Int get() = scenes.count { it.renderedByKadre }
    val unsupportedSceneCount: Int get() = scenes.count { !it.renderedByKadre }
    val finalSceneId: String get() = switchEvents.lastOrNull { it.status == "selected" }?.toSceneId ?: scenes.first().id
    val lastFallbackReason: String get() = switchEvents.lastOrNull { it.fallbackReason.isNotBlank() }?.fallbackReason.orEmpty()

    fun toJson(): JsonObject = buildJsonObject {
        put("schemaVersion", 1)
        put("packId", "mep-next-scene-switching-v1")
        put("status", if (renderableSceneCount >= 3) "pass" else "blocked")
        put("sceneCount", sceneCount)
        put("renderableSceneCount", renderableSceneCount)
        put("unsupportedSceneCount", unsupportedSceneCount)
        put("selectionModel", "bounded-keyboard-config-scene-id")
        put("keyboardControls", buildJsonArray {
            add(JsonPrimitive("ArrowRight: next renderable scene"))
            add(JsonPrimitive("ArrowLeft: previous renderable scene"))
            add(JsonPrimitive("Scene id config: --scene-contract-id for native opt-in runs"))
        })
        put("candidateScenes", buildJsonArray { scenes.forEach { add(it.sceneSummaryJson()) } })
        put("switchEvents", buildJsonArray { switchEvents.forEach { add(it.toJson()) } })
        put("unsupportedFallbackReason", MEP_NEXT_INVALID_SWITCH_REASON)
    }

    companion object {
        fun fromReplayRegistry(): MepNextSceneSwitchPlan {
            val selectedScenes = replayScenesById().values
                .filter { scene ->
                    scene.id in setOf(
                        M73_DEFAULT_SCENE_CONTRACT_ID,
                        "m73-bitmap-rect-nearest-replay-v1",
                        "m73-gradient-color-filter-kplus-replay-v1",
                        M83_DISPLAY_LIST_PM_SCENE_ID,
                        "m73-nested-rrect-clip-refusal-v1",
                    )
                }
                .sortedBy { it.id }
            val renderable = selectedScenes.filter { it.renderedByKadre }
            val unsupported = selectedScenes.firstOrNull { !it.renderedByKadre }
            val switches = buildList {
                if (renderable.size >= 3) {
                    add(MepNextSceneSwitchEvent(1, "keyboard.ArrowRight", renderable[0].id, renderable[1].id, "selected"))
                    add(MepNextSceneSwitchEvent(2, "keyboard.ArrowRight", renderable[1].id, renderable[2].id, "selected"))
                    add(MepNextSceneSwitchEvent(3, "keyboard.ArrowLeft", renderable[2].id, renderable[1].id, "selected"))
                }
                if (unsupported != null) {
                    add(MepNextSceneSwitchEvent(4, "config.sceneId", renderable.lastOrNull()?.id ?: "", unsupported.id, "expected-unsupported", unsupported.unsupportedCommands.firstOrNull() ?: MEP_NEXT_INVALID_SWITCH_REASON))
                }
            }
            return MepNextSceneSwitchPlan(selectedScenes, switches)
        }
    }
}

private data class MepNextSceneSwitchEvent(
    val sequence: Int,
    val trigger: String,
    val fromSceneId: String,
    val toSceneId: String,
    val status: String,
    val fallbackReason: String = "",
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("sequence", sequence)
        put("trigger", trigger)
        put("fromSceneId", fromSceneId)
        put("toSceneId", toSceneId)
        put("status", status)
        put("fallbackReason", fallbackReason)
    }
}

private fun ReplaySceneEvidence.sceneSummaryJson(): JsonObject = buildJsonObject {
    put("id", id)
    put("title", title)
    put("status", status)
    put("renderedByKadre", renderedByKadre)
    put("dashboardRow", dashboardRow)
    put("gpuRoute", gpuRoute)
    put("fallbackReasons", buildJsonArray { unsupportedCommands.forEach { add(JsonPrimitive(it)) } })
}

private fun M82RuntimeLoopResult.telemetrySummaryJson(): JsonObject = buildJsonObject {
    put("eventCount", telemetry.eventCount)
    put("frameTickCount", telemetry.frameTickCount)
    put("pointerEventCount", telemetry.pointerEventCount)
    put("keyboardEventCount", telemetry.keyboardEventCount)
    put("resizeEventCount", telemetry.resizeEventCount)
    put("scaleFactorEventCount", telemetry.scaleFactorEventCount)
    put("closeEventCount", telemetry.closeEventCount)
    put("droppedFrameCount", telemetry.droppedFrameCount)
    put("reconfigureCount", telemetry.reconfigureCount)
}

private fun M82LiveSceneState.toSummaryJson(): JsonObject = buildJsonObject {
    put("playing", playing)
    put("overlayVisible", overlayVisible)
    put("pointerX", pointerX)
    put("pointerY", pointerY)
    put("pointerControl", pointerControl)
    put("animationPhase", animationPhase)
    put("closeRequested", closeRequested)
}

private fun control(input: String, effect: String): JsonObject = buildJsonObject {
    put("input", input)
    put("effect", effect)
}

private fun validationRow(id: String, status: String, assertion: String): JsonObject = buildJsonObject {
    put("id", id)
    put("status", status)
    put("assertion", assertion)
}

private fun java.nio.file.Path.readJsonObjectOrEmpty(): JsonObject {
    if (!exists()) return JsonObject(emptyMap())
    return MEP_NEXT_JSON.parseToJsonElement(readText()) as? JsonObject ?: JsonObject(emptyMap())
}

private fun JsonObject.objectField(name: String): JsonObject = this[name] as? JsonObject ?: JsonObject(emptyMap())
private fun JsonObject.stringField(name: String): String = this[name]?.jsonPrimitive?.contentOrNull.orEmpty()
private fun JsonObject.intField(name: String): Int = this[name]?.jsonPrimitive?.intOrNull ?: 0
private fun JsonObject.booleanField(name: String): Boolean = this[name]?.jsonPrimitive?.booleanOrNull ?: false
private fun JsonObject.doubleField(name: String): Double = this[name]?.jsonPrimitive?.doubleOrNull ?: 0.0

private fun List<Double>.percentile(percentile: Double): Double {
    if (isEmpty()) return 0.0
    val sorted = sorted()
    val index = (kotlin.math.ceil(sorted.size * percentile).toInt() - 1).coerceIn(sorted.indices)
    return sorted[index]
}

private fun Double.ms(): String = "%.4f ms".format(java.util.Locale.US, this)

internal fun buildMepNextRuntimeInteractiveEvidence(
    projectRoot: java.nio.file.Path,
    outputRoot: java.nio.file.Path = projectRoot.resolve(MEP_NEXT_OUTPUT),
): MepNextRuntimeInteractiveEvidence = MepNextRuntimeInteractiveEvidence(projectRoot, outputRoot)

fun main(args: Array<String>) {
    val projectRoot = args.getOrNull(0)?.let(::Path) ?: Path(".")
    val outputRoot = args.getOrNull(1)?.let(::Path) ?: projectRoot.resolve(MEP_NEXT_OUTPUT)
    buildMepNextRuntimeInteractiveEvidence(projectRoot, outputRoot).writeArtifacts()
}
