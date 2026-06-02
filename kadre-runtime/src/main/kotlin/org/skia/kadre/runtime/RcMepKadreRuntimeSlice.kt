package org.skia.kadre.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

private const val RC_MEP_REPORT_ID = "rc-mep-kadre-runtime-slice-v1"
private const val RC_MEP_REPORT = "reports/wgsl-pipeline/2026-06-02-rc-mep-kadre-runtime-slice.md"
private const val M83_EVIDENCE = "reports/wgsl-pipeline/m83-display-list-replay/evidence.json"
private const val M83_NATIVE_DEMO = "reports/wgsl-pipeline/m83-display-list-replay/native-demo.json"
private const val M83_READBACK = "reports/wgsl-pipeline/m83-display-list-replay/native-demo-readback.png"
private const val M84_EVIDENCE = "reports/wgsl-pipeline/m84-native-frame-timing/evidence.json"
private val RC_MEP_JSON = Json { ignoreUnknownKeys = true }

internal data class RcMepKadreRuntimeSliceEvidence(
    val m83Evidence: JsonObject,
    val nativeDemo: JsonObject,
    val m84Evidence: JsonObject,
) {
    private val nativeEvidence = m83Evidence.objectField("nativeEvidence")
    private val sceneReplay = nativeDemo.objectField("sceneReplay")
    private val sceneSourceEvidence = sceneReplay.objectField("sourceEvidence")
    private val commandCounters = sceneReplay.objectField("commandCounters")
    private val frameTiming = nativeDemo.objectField("frameTiming")
    private val runtimeTelemetry = nativeDemo.objectField("runtimeTelemetry")
    private val runtimeLifecycle = nativeDemo.objectField("runtimeLifecycle")
    private val measuredPayload = m84Evidence.objectField("measuredPayload")
    private val eligibility = m84Evidence.objectField("eligibility")
    private val host = m84Evidence.objectField("host")
    private val adapter = m84Evidence.objectField("adapter")

    val sceneId: String = sceneReplay.stringField("id").ifBlank { nativeEvidence.stringField("sceneContractId") }
    val nativeStatus: String = nativeEvidence.stringField("status").ifBlank { nativeDemo.stringField("status") }
    val nativePresented: Boolean = nativeDemo.boolField("nativePresented")
    val presentedFrames: Int = nativeDemo.intField("presentedFrames")
    val readbackPixels: Int = nativeDemo.objectField("capture").intField("nonTransparentPixels")
    val dashboardRow: String = sceneSourceEvidence.stringField("dashboardRow")
    val cpuRoute: String = sceneSourceEvidence.stringField("cpuRoute")
    val gpuRoute: String = sceneSourceEvidence.stringField("gpuRoute")
    val pipelineKey: String = sceneSourceEvidence.stringField("pipelineKey")
    val gateStatus: String = m84Evidence.stringField("gateStatus")
    val gatePhase: String = m84Evidence.stringField("gatePhase")
    val releaseBlocking: Boolean = m84Evidence.boolField("releaseBlocking")
    val countedAsMeasuredGate: Boolean = m84Evidence.boolField("countedAsMeasuredGate")
    val warmupFrames: Int = measuredPayload.intField("warmupFrameCount")
    val measuredSamples: Int = measuredPayload.intField("measuredSampleCount")
    val p50Ms: Double = measuredPayload.doubleField("p50Ms")
    val p95Ms: Double = measuredPayload.doubleField("p95Ms")
    val worstMs: Double = measuredPayload.doubleField("worstMs")
    val pmLongDemoCommand: String = runtimeLifecycle.stringField("pmLongDemoCommand")
        .ifBlank { "./gradlew --no-daemon :kadre-runtime:runRcMepKadreNativePmDemo" }

    fun toMarkdown(): String = buildString {
        appendLine("# RC-MEP Kadre Runtime Slice")
        appendLine()
        appendLine("Report id: `$RC_MEP_REPORT_ID`")
        appendLine()
        appendLine("Scope: `FOR-180`, `FOR-181`, `FOR-182`.")
        appendLine()
        appendLine("This report promotes the existing M83 complex display-list scene as the PM native Kadre scene, and keeps `frame.kadre-windowed` as an explicit candidate/reporting performance lane. It does not claim release-grade FPS.")
        appendLine()
        appendLine("## FOR-180 Native Window And Lifecycle")
        appendLine()
        appendLine("- PM native command: `$pmLongDemoCommand`")
        appendLine("- Default PM duration: `3600` frames with `120` warmup frames.")
        appendLine("- Selected scene: `$sceneId`")
        appendLine("- Native status: `$nativeStatus`")
        appendLine("- Native presented: `$nativePresented`")
        appendLine("- Presented frames in checked-in evidence: `$presentedFrames`")
        appendLine("- Close policy: `${runtimeLifecycle.stringField("closePolicy").ifBlank { "Window close exits the event loop after resource dispose." }}`")
        appendLine("- Resize policy: `${runtimeLifecycle.stringField("resizePolicy").ifBlank { "Positive-size resize reconfigures the WGPU surface." }}`")
        appendLine("- Scale-factor policy: `${runtimeLifecycle.stringField("scaleFactorPolicy").ifBlank { "Scale-factor changes reconfigure from current inner size." }}`")
        appendLine("- Warning policy: `${runtimeLifecycle.stringField("warningPolicy").ifBlank { "Warnings are non-blocking unless status is blocked or error is non-null." }}`")
        appendLine()
        appendLine("Operational note: the long PM command is intentionally manual because it opens a native window. Automated evidence generation uses the shorter checked-in M83/M84 artifacts.")
        appendLine()
        appendLine("## FOR-181 Complex Scene Promotion")
        appendLine()
        appendLine("- Scene contract: `$sceneId`")
        appendLine("- Dashboard row: `$dashboardRow`")
        appendLine("- CPU route: `$cpuRoute`")
        appendLine("- GPU/native route: `$gpuRoute`")
        appendLine("- Pipeline key: `$pipelineKey`")
        appendLine("- Readback image: `$M83_READBACK`")
        appendLine("- Readback nontransparent pixels: `$readbackPixels`")
        appendLine()
        appendLine("Supported command mix:")
        appendLine()
        appendLine("| Command | Count | PM meaning |")
        appendLine("|---|---:|---|")
        appendLine("| `clear` | `${commandCounters.intField("backgroundClear")}` | background clear establishes a deterministic frame base |")
        appendLine("| `clipRect` | `${commandCounters.intField("clipRect")}` | bounded clip is applied before drawing scene content |")
        appendLine("| `linearGradient` | `${sceneReplay.commandsWithFamily("linearGradientRect")}` | gradient panel proves non-solid shader-like paint |")
        appendLine("| `bitmapRect` | `${commandCounters.intField("bitmapRect")}` | deterministic fixture-backed image sampling |")
        appendLine("| `alpha overlay` | `${commandCounters.intField("partialAlpha")}` | SrcOver partial-alpha composition |")
        appendLine()
        appendLine("Non-claims:")
        appendLine()
        appendLine("- This is a bounded scene contract, not broad SkCanvas/display-list replay.")
        appendLine("- Text, image-filter DAGs, unregistered runtime effects and arbitrary shader inputs remain explicit unsupported/refusal paths.")
        appendLine("- The image artifact is a native offscreen WGPU readback for the selected scene, not a system screenshot of the window surface.")
        appendLine()
        appendLine("## FOR-182 `frame.kadre-windowed` Candidate Gate")
        appendLine()
        appendLine("- Lane: `${m84Evidence.stringField("lane")}`")
        appendLine("- Gate status: `$gateStatus`")
        appendLine("- Gate phase: `$gatePhase`")
        appendLine("- Release blocking: `$releaseBlocking`")
        appendLine("- Counted as measured gate: `$countedAsMeasuredGate`")
        appendLine("- Warmup frames: `$warmupFrames`")
        appendLine("- Measured samples: `$measuredSamples`")
        appendLine("- p50 / p95 / worst: `${p50Ms.ms()}` / `${p95Ms.ms()}` / `${worstMs.ms()}`")
        appendLine("- Host: `${host.stringField("osName")} ${host.stringField("osVersion")} ${host.stringField("osArch")}`, Java `${host.stringField("javaVersion")}`")
        appendLine("- Adapter family: `${adapter.stringField("ownedBaselineFamily")}`")
        appendLine()
        appendLine("Proposed budgets for candidate observation:")
        appendLine()
        appendLine("| Metric | Candidate budget | Current evidence | Status |")
        appendLine("|---|---:|---:|---|")
        appendLine("| `measuredSampleCount` | `>= 120` | `$measuredSamples` | `${if (measuredSamples >= 120) "pass" else "quarantine"}` |")
        appendLine("| `p50Ms` | `<= 18.0 ms` | `${p50Ms.ms()}` | `${if (p50Ms <= 18.0) "pass" else "quarantine"}` |")
        appendLine("| `p95Ms` | `<= 22.0 ms` | `${p95Ms.ms()}` | `${if (p95Ms <= 22.0) "pass" else "quarantine"}` |")
        appendLine("| `worstMs` | `<= 30.0 ms` | `${worstMs.ms()}` | `${if (worstMs <= 30.0) "pass" else "quarantine"}` |")
        appendLine()
        appendLine("Gate decision: keep `frame.kadre-windowed` as `candidate-reporting-only`. It can become release-blocking only after adapter/JDK variance is accepted and the native smoke can be run reproducibly in the target release environment.")
        appendLine()
        appendLine("Quarantine/reporting reasons:")
        appendLine()
        eligibility.arrayField("quarantineReasons").forEach { reason ->
            appendLine("- `${reason.jsonPrimitive.contentOrNull}`")
        }
        appendLine()
        appendLine("## Artifacts")
        appendLine()
        appendLine("- `$M83_EVIDENCE`")
        appendLine("- `$M83_NATIVE_DEMO`")
        appendLine("- `$M83_READBACK`")
        appendLine("- `$M84_EVIDENCE`")
        appendLine("- `reports/wgsl-pipeline/m84-native-frame-timing/negative-fixture.json`")
        appendLine("- `$RC_MEP_REPORT`")
        appendLine()
        appendLine("## Validation")
        appendLine()
        appendLine("```bash")
        appendLine("./gradlew --no-daemon :kadre-runtime:pipelineM83DisplayListReplay :kadre-runtime:pipelineM84NativeFrameTimingCandidate :kadre-runtime:pipelineRcMepKadreRuntimeSlice")
        appendLine("python3 -m json.tool $M83_EVIDENCE >/dev/null")
        appendLine("python3 -m json.tool $M83_NATIVE_DEMO >/dev/null")
        appendLine("python3 -m json.tool $M84_EVIDENCE >/dev/null")
        appendLine("# Manual native PM window:")
        appendLine("$pmLongDemoCommand")
        appendLine("```")
    }
}

internal fun buildRcMepKadreRuntimeSliceEvidence(projectRoot: java.nio.file.Path): RcMepKadreRuntimeSliceEvidence =
    RcMepKadreRuntimeSliceEvidence(
        m83Evidence = readRcMepJson(projectRoot.resolve(M83_EVIDENCE)),
        nativeDemo = readRcMepJson(projectRoot.resolve(M83_NATIVE_DEMO)),
        m84Evidence = readRcMepJson(projectRoot.resolve(M84_EVIDENCE)),
    )

private fun readRcMepJson(path: java.nio.file.Path): JsonObject {
    require(path.exists()) { "Missing RC-MEP source artifact: $path" }
    return RC_MEP_JSON.parseToJsonElement(path.readText()).jsonObject
}

private fun JsonObject.objectField(name: String): JsonObject = this[name] as? JsonObject ?: JsonObject(emptyMap())
private fun JsonObject.arrayField(name: String): JsonArray = this[name] as? JsonArray ?: JsonArray(emptyList())
private fun JsonObject.stringField(name: String): String = this[name]?.jsonPrimitive?.contentOrNull.orEmpty()
private fun JsonObject.intField(name: String): Int = this[name]?.jsonPrimitive?.intOrNull ?: 0
private fun JsonObject.doubleField(name: String): Double = this[name]?.jsonPrimitive?.doubleOrNull ?: 0.0
private fun JsonObject.boolField(name: String): Boolean = this[name]?.jsonPrimitive?.booleanOrNull ?: false
private fun Double.ms(): String = "%.4f ms".format(java.util.Locale.US, this)

private fun JsonObject.commandsWithFamily(family: String): Int =
    arrayField("commands").count { command ->
        (command as? JsonObject)?.stringField("family") == family
    }

fun main(args: Array<String>) {
    val projectRoot = args.getOrNull(0)?.let(::Path) ?: Path(".")
    val outputPath = args.getOrNull(1)?.let(::Path) ?: projectRoot.resolve(RC_MEP_REPORT)
    outputPath.parent?.createDirectories()
    outputPath.writeText(buildRcMepKadreRuntimeSliceEvidence(projectRoot).toMarkdown())
}
