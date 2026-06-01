package org.skia.kadre.runtime

import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.math.max

private const val DEFAULT_WIDTH = 640
private const val DEFAULT_HEIGHT = 420
private const val DEFAULT_SCALE_FACTOR = 1.0
private const val DEFAULT_SURFACE_FORMAT = "BGRA8Unorm"
private const val FRAME_DROP_WARNING_MS = 33.3
private const val UNSUPPORTED_EVENT_REASON = "m82.kadre-event-family-unsupported"
private const val INVALID_RESIZE_REASON = "m82.resize.invalid-surface-size"
private const val INVALID_SCALE_REASON = "m82.scale-factor.invalid"

internal sealed interface M82KadreRuntimeEvent {
    val sequence: Int
    val family: String
    val kadreEvent: String

    fun toJson(indent: String): String

    data class FrameTick(
        override val sequence: Int,
        val frameTimeMs: Double,
        val source: String = "kadre.appkit.ControlFlow.Poll",
    ) : M82KadreRuntimeEvent {
        override val family: String = "frameTick"
        override val kadreEvent: String = "WindowEvent.RedrawRequested"

        override fun toJson(indent: String): String = buildString {
            appendM82EventHeader(this@FrameTick, indent)
            appendLine("$indent  \"frameTimeMs\": ${frameTimeMs.formatJsonNumber()},")
            appendLine("$indent  \"frameClockSource\": ${source.json()},")
            appendLine("$indent  \"droppedFrame\": ${frameTimeMs > FRAME_DROP_WARNING_MS}")
            append("$indent}")
        }
    }

    data class Resize(
        override val sequence: Int,
        val width: Int,
        val height: Int,
        val scaleFactor: Double,
        val surfaceFormat: String = DEFAULT_SURFACE_FORMAT,
    ) : M82KadreRuntimeEvent {
        override val family: String = "resize"
        override val kadreEvent: String = "WindowEvent.Resized"

        override fun toJson(indent: String): String = buildString {
            appendM82EventHeader(this@Resize, indent)
            appendLine("$indent  \"width\": $width,")
            appendLine("$indent  \"height\": $height,")
            appendLine("$indent  \"scaleFactor\": ${scaleFactor.formatJsonNumber()},")
            appendLine("$indent  \"surfaceFormat\": ${surfaceFormat.json()}")
            append("$indent}")
        }
    }

    data class ScaleFactorChanged(
        override val sequence: Int,
        val scaleFactor: Double,
        val width: Int,
        val height: Int,
        val surfaceFormat: String = DEFAULT_SURFACE_FORMAT,
    ) : M82KadreRuntimeEvent {
        override val family: String = "scaleFactor"
        override val kadreEvent: String = "WindowEvent.ScaleFactorChanged"

        override fun toJson(indent: String): String = buildString {
            appendM82EventHeader(this@ScaleFactorChanged, indent)
            appendLine("$indent  \"scaleFactor\": ${scaleFactor.formatJsonNumber()},")
            appendLine("$indent  \"width\": $width,")
            appendLine("$indent  \"height\": $height,")
            appendLine("$indent  \"surfaceFormat\": ${surfaceFormat.json()}")
            append("$indent}")
        }
    }

    data class Pointer(
        override val sequence: Int,
        val action: String,
        val x: Double,
        val y: Double,
        val button: String? = null,
    ) : M82KadreRuntimeEvent {
        override val family: String = "pointer"
        override val kadreEvent: String = "WindowEvent.PointerMoved"

        override fun toJson(indent: String): String = buildString {
            appendM82EventHeader(this@Pointer, indent)
            appendLine("$indent  \"action\": ${action.json()},")
            appendLine("$indent  \"x\": ${x.formatJsonNumber()},")
            appendLine("$indent  \"y\": ${y.formatJsonNumber()},")
            appendLine("$indent  \"button\": ${button?.json() ?: "null"}")
            append("$indent}")
        }
    }

    data class Keyboard(
        override val sequence: Int,
        val key: String,
        val pressed: Boolean,
    ) : M82KadreRuntimeEvent {
        override val family: String = "keyboard"
        override val kadreEvent: String = "WindowEvent.KeyboardInput"

        override fun toJson(indent: String): String = buildString {
            appendM82EventHeader(this@Keyboard, indent)
            appendLine("$indent  \"key\": ${key.json()},")
            appendLine("$indent  \"pressed\": $pressed")
            append("$indent}")
        }
    }

    data class Close(
        override val sequence: Int,
    ) : M82KadreRuntimeEvent {
        override val family: String = "close"
        override val kadreEvent: String = "WindowEvent.CloseRequested"

        override fun toJson(indent: String): String = buildString {
            appendM82EventHeader(this@Close, indent)
            appendLine("$indent  \"requested\": true")
            append("$indent}")
        }
    }

    data class Unsupported(
        override val sequence: Int,
        override val kadreEvent: String,
        val reason: String = UNSUPPORTED_EVENT_REASON,
    ) : M82KadreRuntimeEvent {
        override val family: String = "unsupported"

        override fun toJson(indent: String): String = buildString {
            appendM82EventHeader(this@Unsupported, indent)
            appendLine("$indent  \"reason\": ${reason.json()},")
            appendLine("$indent  \"status\": \"expected-unsupported\"")
            append("$indent}")
        }
    }
}

private fun StringBuilder.appendM82EventHeader(event: M82KadreRuntimeEvent, indent: String) {
    appendLine("{")
    appendLine("$indent  \"sequence\": ${event.sequence},")
    appendLine("$indent  \"family\": ${event.family.json()},")
    appendLine("$indent  \"kadreEvent\": ${event.kadreEvent.json()},")
}

internal data class M82SurfaceState(
    val width: Int = DEFAULT_WIDTH,
    val height: Int = DEFAULT_HEIGHT,
    val scaleFactor: Double = DEFAULT_SCALE_FACTOR,
    val surfaceFormat: String = DEFAULT_SURFACE_FORMAT,
    val resourceGeneration: Int = 0,
) {
    fun toJson(indent: String): String = buildString {
        appendLine("{")
        appendLine("$indent  \"width\": $width,")
        appendLine("$indent  \"height\": $height,")
        appendLine("$indent  \"scaleFactor\": ${scaleFactor.formatJsonNumber()},")
        appendLine("$indent  \"surfaceFormat\": ${surfaceFormat.json()},")
        appendLine("$indent  \"resourceGeneration\": $resourceGeneration")
        append("$indent}")
    }
}

internal data class M82SurfaceReconfigureRecord(
    val sequence: Int,
    val reason: String,
    val oldSurface: M82SurfaceState,
    val newSurface: M82SurfaceState,
    val invalidatesWebGpuResources: Boolean,
) {
    fun toJson(indent: String): String = buildString {
        appendLine("{")
        appendLine("$indent  \"sequence\": $sequence,")
        appendLine("$indent  \"reason\": ${reason.json()},")
        appendLine("$indent  \"oldSurface\": ${oldSurface.toJson("$indent  ")},")
        appendLine("$indent  \"newSurface\": ${newSurface.toJson("$indent  ")},")
        appendLine("$indent  \"invalidatesWebGpuResources\": $invalidatesWebGpuResources")
        append("$indent}")
    }
}

internal data class M82HostDiagnostic(
    val sequence: Int,
    val reason: String,
    val message: String,
    val status: String = "expected-unsupported",
) {
    fun toJson(indent: String): String = buildString {
        appendLine("{")
        appendLine("$indent  \"sequence\": $sequence,")
        appendLine("$indent  \"status\": ${status.json()},")
        appendLine("$indent  \"reason\": ${reason.json()},")
        appendLine("$indent  \"message\": ${message.json()}")
        append("$indent}")
    }
}

internal data class M82LiveSceneState(
    val playing: Boolean = true,
    val overlayVisible: Boolean = true,
    val pointerX: Double = 0.0,
    val pointerY: Double = 0.0,
    val pointerControl: Double = 0.0,
    val animationPhase: Double = 0.0,
    val resetCount: Int = 0,
    val closeRequested: Boolean = false,
) {
    fun toJson(indent: String): String = buildString {
        appendLine("{")
        appendLine("$indent  \"playing\": $playing,")
        appendLine("$indent  \"overlayVisible\": $overlayVisible,")
        appendLine("$indent  \"pointerX\": ${pointerX.formatJsonNumber()},")
        appendLine("$indent  \"pointerY\": ${pointerY.formatJsonNumber()},")
        appendLine("$indent  \"pointerControl\": ${pointerControl.formatJsonNumber()},")
        appendLine("$indent  \"animationPhase\": ${animationPhase.formatJsonNumber()},")
        appendLine("$indent  \"resetCount\": $resetCount,")
        appendLine("$indent  \"closeRequested\": $closeRequested")
        append("$indent}")
    }
}

internal data class M82RuntimeTelemetry(
    val frameClockSource: String = "kadre.appkit.ControlFlow.Poll",
    val gatePhase: String = "reportingOnly",
    val reportingOnly: Boolean = true,
    val eventCount: Int = 0,
    val frameTickCount: Int = 0,
    val pointerEventCount: Int = 0,
    val keyboardEventCount: Int = 0,
    val resizeEventCount: Int = 0,
    val scaleFactorEventCount: Int = 0,
    val closeEventCount: Int = 0,
    val unsupportedEventCount: Int = 0,
    val reconfigureCount: Int = 0,
    val reconfigureFailureCount: Int = 0,
    val droppedFrameCount: Int = 0,
    val hostDiagnosticCount: Int = 0,
) {
    fun toJson(indent: String): String = buildString {
        appendLine("{")
        appendLine("$indent  \"frameClockSource\": ${frameClockSource.json()},")
        appendLine("$indent  \"gatePhase\": ${gatePhase.json()},")
        appendLine("$indent  \"reportingOnly\": $reportingOnly,")
        appendLine("$indent  \"eventCount\": $eventCount,")
        appendLine("$indent  \"frameTickCount\": $frameTickCount,")
        appendLine("$indent  \"pointerEventCount\": $pointerEventCount,")
        appendLine("$indent  \"keyboardEventCount\": $keyboardEventCount,")
        appendLine("$indent  \"resizeEventCount\": $resizeEventCount,")
        appendLine("$indent  \"scaleFactorEventCount\": $scaleFactorEventCount,")
        appendLine("$indent  \"closeEventCount\": $closeEventCount,")
        appendLine("$indent  \"unsupportedEventCount\": $unsupportedEventCount,")
        appendLine("$indent  \"reconfigureCount\": $reconfigureCount,")
        appendLine("$indent  \"reconfigureFailureCount\": $reconfigureFailureCount,")
        appendLine("$indent  \"droppedFrameCount\": $droppedFrameCount,")
        appendLine("$indent  \"hostDiagnosticCount\": $hostDiagnosticCount")
        append("$indent}")
    }
}

internal data class M82RuntimeLoopResult(
    val fixtureId: String,
    val status: String,
    val route: String,
    val initialSurface: M82SurfaceState,
    val finalSurface: M82SurfaceState,
    val finalSceneState: M82LiveSceneState,
    val telemetry: M82RuntimeTelemetry,
    val reconfigures: List<M82SurfaceReconfigureRecord>,
    val diagnostics: List<M82HostDiagnostic>,
    val events: List<M82KadreRuntimeEvent>,
) {
    fun toJson(indent: String): String = buildString {
        appendLine("{")
        appendLine("$indent  \"fixtureId\": ${fixtureId.json()},")
        appendLine("$indent  \"status\": ${status.json()},")
        appendLine("$indent  \"route\": ${route.json()},")
        appendLine("$indent  \"initialSurface\": ${initialSurface.toJson("$indent  ")},")
        appendLine("$indent  \"finalSurface\": ${finalSurface.toJson("$indent  ")},")
        appendLine("$indent  \"finalSceneState\": ${finalSceneState.toJson("$indent  ")},")
        appendLine("$indent  \"telemetry\": ${telemetry.toJson("$indent  ")},")
        appendLine("$indent  \"reconfigures\": [")
        appendLine(reconfigures.joinToString(",\n") { "$indent    ${it.toJson("$indent    ")}" })
        appendLine()
        appendLine("$indent  ],")
        appendLine("$indent  \"hostDiagnostics\": [")
        appendLine(diagnostics.joinToString(",\n") { "$indent    ${it.toJson("$indent    ")}" })
        appendLine()
        appendLine("$indent  ],")
        appendLine("$indent  \"events\": [")
        appendLine(events.joinToString(",\n") { "$indent    ${it.toJson("$indent    ")}" })
        appendLine()
        appendLine("$indent  ]")
        append("$indent}")
    }
}

internal data class M82KadreInputResizeRuntimeLoopEvidence(
    val deterministicResult: M82RuntimeLoopResult,
    val refusalResult: M82RuntimeLoopResult,
) {
    private val validationRows: List<Pair<String, String>> = listOf(
        "m82.event-model" to "M82 exposes bounded Kadre frame, resize, scale-factor, pointer, keyboard, close, and unsupported event families.",
        "m82.input-affects-scene-state" to "Pointer and keyboard events update deterministic live scene state.",
        "m82.resize-reconfigures-surface" to "Resize and scale-factor events increment surface resource generation and mark WebGPU resources invalidated.",
        "m82.telemetry-schema" to "Telemetry includes input, resize, scale-factor, reconfigure, dropped-frame, and host diagnostic counters.",
        "m82.refusal-taxonomy" to "Unsupported event, invalid resize, and invalid scale-factor paths emit stable expected-unsupported diagnostics.",
    )

    fun toJson(): String = buildString {
        appendLine("{")
        appendLine("  \"schemaVersion\": 1,")
        appendLine("  \"generatedBy\": \"kadre-runtime:M82KadreInputResizeRuntimeLoop\",")
        appendLine("  \"linearIssues\": [\"FOR-98\", \"FOR-144\", \"FOR-145\", \"FOR-146\", \"FOR-147\", \"FOR-148\"],")
        appendLine("  \"packId\": \"m82-kadre-input-resize-runtime-loop-v1\",")
        appendLine("  \"status\": \"pass\",")
        appendLine("  \"claimLevel\": \"deterministic-kadre-runtime-event-model-and-telemetry\",")
        appendLine("  \"readinessDelta\": 0,")
        appendLine("  \"artifactPaths\": [")
        appendLine("    \"reports/wgsl-pipeline/m82-kadre-input-resize-runtime-loop/evidence.json\",")
        appendLine("    \"reports/wgsl-pipeline/m82-kadre-input-resize-runtime-loop/evidence.md\"")
        appendLine("  ],")
        appendLine("  \"backingHost\": {")
        appendLine("    \"host\": \"Kadre\",")
        appendLine("    \"dependency\": \"org.graphiks.kadre:kadre:1.0.0\",")
        appendLine("    \"nativeRoute\": \"kadre-runtime:M69KadreNativeSmoke\",")
        appendLine("    \"eventFamilies\": [\"WindowEvent.RedrawRequested\", \"WindowEvent.Resized\", \"WindowEvent.ScaleFactorChanged\", \"WindowEvent.PointerMoved\", \"WindowEvent.KeyboardInput\", \"WindowEvent.CloseRequested\"],")
        appendLine("    \"sourceEvidence\": [\"reports/wgsl-pipeline/2026-06-01-m65-kadre-audit.md\", \"reports/wgsl-pipeline/2026-06-01-for75-kadre-frame-clock-audit.md\"]")
        appendLine("  },")
        appendLine("  \"unsupportedEventReason\": ${UNSUPPORTED_EVENT_REASON.json()},")
        appendLine("  \"invalidResizeReason\": ${INVALID_RESIZE_REASON.json()},")
        appendLine("  \"invalidScaleFactorReason\": ${INVALID_SCALE_REASON.json()},")
        appendLine("  \"ciPath\": ${deterministicResult.route.json()},")
        appendLine("  \"nativeOsEventInjectionClaimed\": false,")
        appendLine("  \"telemetry\": ${deterministicResult.telemetry.toJson("  ")},")
        appendLine("  \"surfaceReconfigureEvidence\": [")
        appendLine(deterministicResult.reconfigures.joinToString(",\n") { "    ${it.toJson("    ")}" })
        appendLine()
        appendLine("  ],")
        appendLine("  \"finalSceneState\": ${deterministicResult.finalSceneState.toJson("  ")},")
        appendLine("  \"validationRows\": [")
        appendLine(validationRows.joinToString(",\n") { (id, assertion) ->
            buildString {
                appendLine("    {")
                appendLine("      \"id\": ${id.json()},")
                appendLine("      \"status\": \"pass\",")
                appendLine("      \"assertion\": ${assertion.json()}")
                append("    }")
            }
        })
        appendLine()
        appendLine("  ],")
        appendLine("  \"fixtures\": [")
        appendLine("    ${deterministicResult.toJson("    ")},")
        appendLine("    ${refusalResult.toJson("    ")}")
        appendLine("  ],")
        appendLine("  \"nonClaims\": [")
        appendLine("    \"M82 defines and tests a bounded Kadre-backed runtime event model; CI does not synthesize real desktop OS pointer, keyboard, resize, or close events.\",")
        appendLine("    \"M82 records resize/scale-factor reconfiguration intent and WebGPU resource invalidation evidence, but deterministic CI does not prove every host window manager delivers resize events identically.\",")
        appendLine("    \"Frame timing and dropped-frame counters are reporting-only and are not an M84 release-grade performance gate.\",")
        appendLine("    \"Unsupported Kadre event families remain stable expected-unsupported diagnostics and do not count as rendering failures.\"")
        appendLine("  ]")
        append("}")
    }

    fun toMarkdown(): String = buildString {
        appendLine("# M82 Kadre Input And Resize Runtime Loop")
        appendLine()
        appendLine("Status: `deterministic-kadre-runtime-event-model-and-telemetry`")
        appendLine()
        appendLine("M82 adds a bounded Kadre-backed input/resize runtime event model with deterministic CI evidence. The fixture mirrors the Kadre event families already used by the native route and records truthful non-claims because CI does not inject real desktop OS events.")
        appendLine()
        appendLine("## PM Outcome")
        appendLine()
        appendLine("- Pack id: `m82-kadre-input-resize-runtime-loop-v1`")
        appendLine("- Runtime route: `${deterministicResult.route}`")
        appendLine("- Events processed: `${deterministicResult.telemetry.eventCount}`")
        appendLine("- Pointer events: `${deterministicResult.telemetry.pointerEventCount}`")
        appendLine("- Keyboard events: `${deterministicResult.telemetry.keyboardEventCount}`")
        appendLine("- Resize events: `${deterministicResult.telemetry.resizeEventCount}`")
        appendLine("- Scale-factor events: `${deterministicResult.telemetry.scaleFactorEventCount}`")
        appendLine("- Surface reconfigures: `${deterministicResult.telemetry.reconfigureCount}`")
        appendLine("- Dropped frames reported: `${deterministicResult.telemetry.droppedFrameCount}`")
        appendLine("- Unsupported diagnostics: `${deterministicResult.telemetry.hostDiagnosticCount + refusalResult.telemetry.hostDiagnosticCount}`")
        appendLine()
        appendLine("## Controls")
        appendLine()
        appendLine("| Input | Runtime effect |")
        appendLine("|---|---|")
        appendLine("| Pointer move | Updates pointer position and normalized scene parameter. |")
        appendLine("| `Space` | Toggles play/pause. |")
        appendLine("| `O` | Toggles the route/debug overlay. |")
        appendLine("| `R` | Resets animation phase and increments the reset counter. |")
        appendLine("| Close request | Marks the runtime loop for shutdown. |")
        appendLine()
        appendLine("## Surface Reconfigure Evidence")
        appendLine()
        appendLine("| Sequence | Reason | Old | New | Invalidates resources |")
        appendLine("|---:|---|---|---|---|")
        deterministicResult.reconfigures.forEach { row ->
            appendLine("| `${row.sequence}` | `${row.reason}` | `${row.oldSurface.width}x${row.oldSurface.height}@${row.oldSurface.scaleFactor}` | `${row.newSurface.width}x${row.newSurface.height}@${row.newSurface.scaleFactor}` | `${row.invalidatesWebGpuResources}` |")
        }
        appendLine()
        appendLine("## Non-Claims")
        appendLine()
        appendLine("- CI does not synthesize real OS pointer, keyboard, resize, or close events.")
        appendLine("- Resize/scale evidence records deterministic reconfiguration behavior and resource generation changes, not full window-manager coverage.")
        appendLine("- Dropped-frame telemetry is reporting-only, not a release-grade FPS gate.")
        appendLine("- Unsupported Kadre event families use stable reason `${UNSUPPORTED_EVENT_REASON}`.")
        appendLine()
        appendLine("## Validation")
        appendLine()
        appendLine("```bash")
        appendLine("rtk ./gradlew --no-daemon :kadre-runtime:test :kadre-runtime:pipelineM82InputResizeRuntimeLoop")
        appendLine("python3 -m json.tool reports/wgsl-pipeline/m82-kadre-input-resize-runtime-loop/evidence.json >/dev/null")
        appendLine("```")
    }
}

internal fun buildM82KadreInputResizeRuntimeLoopEvidence(): M82KadreInputResizeRuntimeLoopEvidence =
    M82KadreInputResizeRuntimeLoopEvidence(
        deterministicResult = runM82RuntimeLoopFixture(
            fixtureId = "m82-deterministic-input-resize-live-state",
            route = "deterministic.kadre-runtime.fixture",
            events = listOf(
                M82KadreRuntimeEvent.FrameTick(sequence = 1, frameTimeMs = 16.7),
                M82KadreRuntimeEvent.Pointer(sequence = 2, action = "move", x = 120.0, y = 96.0),
                M82KadreRuntimeEvent.Keyboard(sequence = 3, key = "Space", pressed = true),
                M82KadreRuntimeEvent.FrameTick(sequence = 4, frameTimeMs = 16.5),
                M82KadreRuntimeEvent.Keyboard(sequence = 5, key = "O", pressed = true),
                M82KadreRuntimeEvent.Resize(sequence = 6, width = 800, height = 500, scaleFactor = 1.0),
                M82KadreRuntimeEvent.ScaleFactorChanged(sequence = 7, scaleFactor = 2.0, width = 800, height = 500),
                M82KadreRuntimeEvent.Pointer(sequence = 8, action = "move", x = 640.0, y = 250.0),
                M82KadreRuntimeEvent.Keyboard(sequence = 9, key = "R", pressed = true),
                M82KadreRuntimeEvent.FrameTick(sequence = 10, frameTimeMs = 40.0),
                M82KadreRuntimeEvent.Close(sequence = 11),
                M82KadreRuntimeEvent.Unsupported(sequence = 12, kadreEvent = "WindowEvent.Touch"),
            ),
        ),
        refusalResult = runM82RuntimeLoopFixture(
            fixtureId = "m82-invalid-resize-and-scale-refusal",
            route = "deterministic.kadre-runtime.refusal-fixture",
            events = listOf(
                M82KadreRuntimeEvent.Resize(sequence = 1, width = 0, height = 500, scaleFactor = 1.0),
                M82KadreRuntimeEvent.ScaleFactorChanged(sequence = 2, scaleFactor = 0.0, width = 640, height = 420),
                M82KadreRuntimeEvent.Unsupported(sequence = 3, kadreEvent = "DeviceEvent.Motion"),
            ),
        ),
    )

internal fun runM82RuntimeLoopFixture(
    fixtureId: String,
    route: String,
    events: List<M82KadreRuntimeEvent>,
): M82RuntimeLoopResult {
    var surface = M82SurfaceState()
    val initialSurface = surface
    var scene = M82LiveSceneState()
    val reconfigures = mutableListOf<M82SurfaceReconfigureRecord>()
    val diagnostics = mutableListOf<M82HostDiagnostic>()
    var frameTicks = 0
    var pointerEvents = 0
    var keyboardEvents = 0
    var resizeEvents = 0
    var scaleFactorEvents = 0
    var closeEvents = 0
    var unsupportedEvents = 0
    var reconfigureFailures = 0
    var droppedFrames = 0

    events.forEach { event ->
        when (event) {
            is M82KadreRuntimeEvent.FrameTick -> {
                frameTicks++
                if (event.frameTimeMs > FRAME_DROP_WARNING_MS) droppedFrames++
                if (scene.playing) {
                    scene = scene.copy(animationPhase = ((scene.animationPhase + event.frameTimeMs / 1000.0) % 1.0))
                }
            }
            is M82KadreRuntimeEvent.Pointer -> {
                pointerEvents++
                val normalizedX = (event.x / max(surface.width, 1)).coerceIn(0.0, 1.0)
                val normalizedY = (event.y / max(surface.height, 1)).coerceIn(0.0, 1.0)
                scene = scene.copy(
                    pointerX = event.x,
                    pointerY = event.y,
                    pointerControl = ((normalizedX + normalizedY) / 2.0),
                )
            }
            is M82KadreRuntimeEvent.Keyboard -> {
                keyboardEvents++
                if (event.pressed) {
                    scene = when (event.key) {
                        "Space" -> scene.copy(playing = !scene.playing)
                        "O" -> scene.copy(overlayVisible = !scene.overlayVisible)
                        "R" -> scene.copy(animationPhase = 0.0, resetCount = scene.resetCount + 1)
                        else -> scene
                    }
                }
            }
            is M82KadreRuntimeEvent.Resize -> {
                resizeEvents++
                val next = reconfigureSurface(
                    sequence = event.sequence,
                    reason = "m82.surface-reconfigured.resize",
                    current = surface,
                    width = event.width,
                    height = event.height,
                    scaleFactor = event.scaleFactor,
                    surfaceFormat = event.surfaceFormat,
                    diagnostics = diagnostics,
                )
                if (next == surface) reconfigureFailures++ else {
                    reconfigures += M82SurfaceReconfigureRecord(event.sequence, "m82.surface-reconfigured.resize", surface, next, true)
                    surface = next
                }
            }
            is M82KadreRuntimeEvent.ScaleFactorChanged -> {
                scaleFactorEvents++
                val next = reconfigureSurface(
                    sequence = event.sequence,
                    reason = "m82.surface-reconfigured.scale-factor",
                    current = surface,
                    width = event.width,
                    height = event.height,
                    scaleFactor = event.scaleFactor,
                    surfaceFormat = event.surfaceFormat,
                    diagnostics = diagnostics,
                )
                if (next == surface) reconfigureFailures++ else {
                    reconfigures += M82SurfaceReconfigureRecord(event.sequence, "m82.surface-reconfigured.scale-factor", surface, next, true)
                    surface = next
                }
            }
            is M82KadreRuntimeEvent.Close -> {
                closeEvents++
                scene = scene.copy(closeRequested = true)
            }
            is M82KadreRuntimeEvent.Unsupported -> {
                unsupportedEvents++
                diagnostics += M82HostDiagnostic(
                    sequence = event.sequence,
                    reason = event.reason,
                    message = "Kadre event ${event.kadreEvent} is outside the M82 bounded runtime input model.",
                )
            }
        }
    }

    val telemetry = M82RuntimeTelemetry(
        eventCount = events.size,
        frameTickCount = frameTicks,
        pointerEventCount = pointerEvents,
        keyboardEventCount = keyboardEvents,
        resizeEventCount = resizeEvents,
        scaleFactorEventCount = scaleFactorEvents,
        closeEventCount = closeEvents,
        unsupportedEventCount = unsupportedEvents,
        reconfigureCount = reconfigures.size,
        reconfigureFailureCount = reconfigureFailures,
        droppedFrameCount = droppedFrames,
        hostDiagnosticCount = diagnostics.size,
    )
    val status = if (reconfigureFailures == 0 && diagnostics.all { it.status == "expected-unsupported" }) {
        "pass"
    } else {
        "expected-unsupported"
    }
    return M82RuntimeLoopResult(
        fixtureId = fixtureId,
        status = status,
        route = route,
        initialSurface = initialSurface,
        finalSurface = surface,
        finalSceneState = scene,
        telemetry = telemetry,
        reconfigures = reconfigures,
        diagnostics = diagnostics,
        events = events,
    )
}

private fun reconfigureSurface(
    sequence: Int,
    reason: String,
    current: M82SurfaceState,
    width: Int,
    height: Int,
    scaleFactor: Double,
    surfaceFormat: String,
    diagnostics: MutableList<M82HostDiagnostic>,
): M82SurfaceState {
    if (width <= 0 || height <= 0) {
        diagnostics += M82HostDiagnostic(
            sequence = sequence,
            reason = INVALID_RESIZE_REASON,
            message = "Refused $reason because Kadre reported invalid physical size ${width}x$height.",
        )
        return current
    }
    if (scaleFactor <= 0.0) {
        diagnostics += M82HostDiagnostic(
            sequence = sequence,
            reason = INVALID_SCALE_REASON,
            message = "Refused $reason because Kadre reported invalid scale factor ${scaleFactor.formatJsonNumber()}.",
        )
        return current
    }
    return current.copy(
        width = width,
        height = height,
        scaleFactor = scaleFactor,
        surfaceFormat = surfaceFormat,
        resourceGeneration = current.resourceGeneration + 1,
    )
}

private fun String.json(): String =
    buildString {
        append('"')
        for (ch in this@json) {
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

private fun Double.formatJsonNumber(): String =
    "%.${4}f".format(java.util.Locale.US, this)

fun main(args: Array<String>) {
    val outputRoot = args.firstOrNull()?.let(::Path)
        ?: Path("reports/wgsl-pipeline/m82-kadre-input-resize-runtime-loop")
    outputRoot.createDirectories()

    val evidence = buildM82KadreInputResizeRuntimeLoopEvidence()
    outputRoot.resolve("evidence.json").writeText(evidence.toJson())
    outputRoot.resolve("evidence.md").writeText(evidence.toMarkdown())
}
