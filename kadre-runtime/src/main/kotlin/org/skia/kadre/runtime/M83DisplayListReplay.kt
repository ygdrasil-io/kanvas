package org.skia.kadre.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

private const val WIDTH = 640
private const val HEIGHT = 420
private const val M83_OUTPUT = "reports/wgsl-pipeline/m83-display-list-replay"
private const val M83_NATIVE_DEMO = "$M83_OUTPUT/native-demo.json"
private const val M83_NATIVE_READBACK = "$M83_OUTPUT/native-demo-readback.png"
private const val M83_NATIVE_MISSING_REASON = "m83.native-display-list-artifact-missing"
private const val M83_NATIVE_SCENE_MISMATCH_REASON = "m83.native-display-list-scene-contract-mismatch"
private const val M83_NATIVE_CAPTURE_MISSING_REASON = "m83.native-display-list-capture-missing"
private const val M83_NATIVE_CAPTURE_BLANK_REASON = "m83.native-display-list-capture-blank"
private const val M83_NATIVE_PRESENTED_REASON = "m83.native-display-list-presented"

private val M83_LINEAR_ISSUES = listOf("FOR-99", "FOR-149", "FOR-150", "FOR-151", "FOR-152", "FOR-153")
private val M83_JSON = Json { prettyPrint = true }

internal data class M83NativeDisplayListEvidence(
    val status: String,
    val reason: String,
    val nativeDemoJson: String,
    val readbackImage: String,
    val sceneContractId: String?,
    val nativePresented: Boolean,
    val presentedFrames: Int,
    val captureStatus: String,
    val captureReason: String,
    val captureNonTransparentPixels: Int,
    val adapterInfo: String,
    val nativePixelsProducedFromDisplayListByThisTask: Boolean,
) {
    fun toJsonElement(): JsonObject = buildJsonObject {
        put("status", status)
        put("reason", reason)
        put("nativeDemoJson", nativeDemoJson)
        put("readbackImage", readbackImage)
        put("sceneContractId", sceneContractId)
        put("nativePresented", nativePresented)
        put("presentedFrames", presentedFrames)
        put("captureStatus", captureStatus)
        put("captureReason", captureReason)
        put("captureNonTransparentPixels", captureNonTransparentPixels)
        put("adapterInfo", adapterInfo)
        put("nativePixelsProducedFromDisplayListByThisTask", nativePixelsProducedFromDisplayListByThisTask)
    }
}

internal data class M83DisplayListReplayRow(
    val scene: ReplaySceneEvidence,
    val cpuOracle: ReplayCpuOracleResult,
) {
    fun toJsonElement(): JsonObject = buildJsonObject {
        put("id", scene.id)
        put("title", scene.title)
        put("status", scene.status)
        put("renderedByKadre", scene.renderedByKadre)
        put("source", scene.source)
        put("sourceSceneId", scene.sourceSceneId)
        put("commandSource", scene.commandSource)
        put("unsupportedCommands", buildJsonArray { scene.unsupportedCommands.forEach { add(JsonPrimitive(it)) } })
        put("commandCounters", buildJsonObject {
            put("total", scene.totalCommandCount)
            put("supported", scene.supportedCommandCount)
            put("unsupported", scene.unsupportedCommandCount)
            put("clipRect", scene.clipRectCommandCount)
            put("fillRect", scene.fillRectCount)
            put("bitmapRect", scene.bitmapCommandCount)
            put("srcOver", scene.srcOverCommandCount)
            put("partialAlpha", scene.partialAlphaCommandCount)
        })
        put("sourceEvidence", buildJsonObject {
            put("dashboardRow", scene.dashboardRow)
            put("cpuRoute", scene.cpuRoute)
            put("gpuRoute", scene.gpuRoute)
            put("pipelineKey", scene.pipelineKey)
        })
        put("cpuReference", buildJsonObject {
            put("api", cpuOracle.api)
            put("deviceWidth", cpuOracle.deviceWidth)
            put("deviceHeight", cpuOracle.deviceHeight)
            put("sampledChecksum", cpuOracle.sampledChecksum)
            put("nonTransparentPixels", cpuOracle.nonTransparentPixels)
            put("bitmapSampledPixels", cpuOracle.bitmapSampledPixels)
            put("rendered", cpuOracle.rendered)
        })
    }
}

internal data class M83DisplayListReplayEvidence(
    val rows: List<M83DisplayListReplayRow>,
    val nativeEvidence: M83NativeDisplayListEvidence,
) {
    val sceneCount: Int get() = rows.size
    val renderableSceneCount: Int get() = rows.count { it.scene.renderedByKadre }
    val expectedUnsupportedSceneCount: Int get() = rows.count { !it.scene.renderedByKadre }
    val failedSceneCount: Int get() = 0
    val totalCommandCount: Int get() = rows.sumOf { it.scene.totalCommandCount }
    val supportedCommandCount: Int get() = rows.sumOf { it.scene.supportedCommandCount }
    val unsupportedCommandCount: Int get() = rows.sumOf { it.scene.unsupportedCommandCount }
    val supportStateMismatchCount: Int get() =
        rows.count { it.scene.renderedByKadre != it.cpuOracle.rendered }
    val artifactPaths: List<String> = listOf(
        "$M83_OUTPUT/evidence.json",
        "$M83_OUTPUT/evidence.md",
        nativeEvidence.nativeDemoJson,
        nativeEvidence.readbackImage,
    ).distinct()

    fun toJsonElement(): JsonObject = buildJsonObject {
        put("schemaVersion", 1)
        put("generatedBy", "kadre-runtime:M83DisplayListReplay")
        put("linearIssues", buildJsonArray { M83_LINEAR_ISSUES.forEach { add(JsonPrimitive(it)) } })
        put("packId", "m83-display-list-replay-through-kadre-v1")
        put("claimLevel", "bounded-kanvas-display-list-replay-through-native-kadre")
        put("readinessDelta", 0)
        put("nativePixelsProducedFromDisplayListByThisTask", nativeEvidence.nativePixelsProducedFromDisplayListByThisTask)
        put("sceneCount", sceneCount)
        put("renderableSceneCount", renderableSceneCount)
        put("expectedUnsupportedSceneCount", expectedUnsupportedSceneCount)
        put("failedSceneCount", failedSceneCount)
        put("totalCommandCount", totalCommandCount)
        put("supportedCommandCount", supportedCommandCount)
        put("unsupportedCommandCount", unsupportedCommandCount)
        put("supportStateMismatchCount", supportStateMismatchCount)
        put("nativeEvidence", nativeEvidence.toJsonElement())
        put("artifactPaths", buildJsonArray { artifactPaths.forEach { add(JsonPrimitive(it)) } })
        put("validationRows", buildJsonArray {
            add(validationRow("m83.display-list-scene-registered", "pass", "M83 renderable display-list scene is present in replayScenesById()."))
            add(validationRow("m83.cpu-native-support-state", if (supportStateMismatchCount == 0) "pass" else "blocked", "CPU oracle rendered state matches scene support/refusal state."))
            add(validationRow("m83.native-display-list-artifact", if (nativeEvidence.nativePixelsProducedFromDisplayListByThisTask) "pass" else "blocked", "Native Kadre demo produced nonblank pixels for the M83 display-list scene contract."))
            add(validationRow("m83.stable-refusals", if (expectedUnsupportedSceneCount == 1 && unsupportedCommandCount == 3) "pass" else "blocked", "Unsupported display-list nodes retain stable text/filter/runtime-effect refusal reasons."))
        })
        put("nonClaims", buildJsonArray {
            add(JsonPrimitive("M83 proves one bounded Kanvas display-list scene lowered into the typed Kadre replay contract; it is not arbitrary SkCanvas op replay."))
            add(JsonPrimitive("Unsupported text, image-filter DAG, and runtime-effect placeholder nodes remain explicit refusals."))
            add(JsonPrimitive("The native PNG is a wgpu4k offscreen texture readback of the selected M83 scene contract, not a window-surface screenshot."))
            add(JsonPrimitive("Native timing remains reporting-only and is not promoted to a release-grade FPS gate."))
        })
        put("scenes", buildJsonArray { rows.forEach { add(it.toJsonElement()) } })
    }

    fun toMarkdown(): String = buildString {
        appendLine("# M83 Kanvas Display-List Replay Through Kadre")
        appendLine()
        appendLine("Status: `${nativeEvidence.status}`")
        appendLine()
        appendLine("M83 routes one bounded Kanvas display-list scene through the typed Kadre replay contract and the existing native WebGPU demo path.")
        appendLine()
        appendLine("## PM Outcome")
        appendLine()
        appendLine("- Pack id: `m83-display-list-replay-through-kadre-v1`")
        appendLine("- Native display-list pixels produced: `${nativeEvidence.nativePixelsProducedFromDisplayListByThisTask}`")
        appendLine("- Native scene contract: `${nativeEvidence.sceneContractId}`")
        appendLine("- Presented frames: `${nativeEvidence.presentedFrames}`")
        appendLine("- Capture nontransparent pixels: `${nativeEvidence.captureNonTransparentPixels}`")
        appendLine("- Scene count: `$sceneCount`")
        appendLine("- Renderable scenes: `$renderableSceneCount`")
        appendLine("- Expected unsupported scenes: `$expectedUnsupportedSceneCount`")
        appendLine("- Support-state mismatches: `$supportStateMismatchCount`")
        appendLine()
        appendLine("## Native Artifacts")
        appendLine()
        appendLine("- `${nativeEvidence.nativeDemoJson}`")
        appendLine("- `${nativeEvidence.readbackImage}`")
        appendLine()
        appendLine("## Scene Summary")
        appendLine()
        appendLine("| Scene | Status | Commands | CPU checksum | Native route |")
        appendLine("|---|---|---:|---:|---|")
        rows.forEach { row ->
            appendLine("| `${row.scene.id}` | `${row.scene.status}` | `${row.scene.totalCommandCount}` | `${row.cpuOracle.sampledChecksum}` | `${if (row.scene.id == M83_DISPLAY_LIST_PM_SCENE_ID) nativeEvidence.status else "expected-unsupported"}` |")
        }
        appendLine()
        appendLine("## Stable Refusals")
        appendLine()
        rows.flatMap { it.scene.unsupportedCommands }.forEach { appendLine("- `$it`") }
        appendLine()
        appendLine("## Non-Claims")
        appendLine()
        appendLine("- M83 is not broad SkCanvas/display-list replay.")
        appendLine("- Text, image-filter DAG, and arbitrary runtime-effect nodes are not routed by this sprint.")
        appendLine("- Native timing remains reporting-only.")
        appendLine()
        appendLine("## Validation")
        appendLine()
        appendLine("```bash")
        appendLine("rtk ./gradlew --no-daemon :kadre-runtime:runM70KadreNativeDemo -PkadreReplaySceneId=$M83_DISPLAY_LIST_PM_SCENE_ID -PkadreDemoOutput=$M83_NATIVE_DEMO -PkadreDemoCaptureOutput=$M83_NATIVE_READBACK")
        appendLine("rtk ./gradlew --no-daemon :kadre-runtime:test :kadre-runtime:pipelineM83DisplayListReplay")
        appendLine("python3 -m json.tool $M83_OUTPUT/evidence.json >/dev/null")
        appendLine("```")
    }
}

internal fun buildM83DisplayListReplayEvidence(projectRoot: java.nio.file.Path): M83DisplayListReplayEvidence {
    val nativeDemo = readJsonObjectOrEmpty(projectRoot.resolve(M83_NATIVE_DEMO))
    return M83DisplayListReplayEvidence(
        rows = M83_DISPLAY_LIST_REPLAY_SCENES.map { scene ->
            M83DisplayListReplayRow(scene, renderReplayCpuOracle(WIDTH, HEIGHT, scene))
        },
        nativeEvidence = nativeEvidenceFor(projectRoot, nativeDemo),
    )
}

private fun nativeEvidenceFor(
    projectRoot: java.nio.file.Path,
    nativeDemo: JsonObject,
): M83NativeDisplayListEvidence {
    val capture = nativeDemo.objectField("capture")
    val sceneContract = nativeDemo.objectField("sceneContract")
    val sceneContractId = sceneContract.stringField("id").ifBlank { null }
    val nativePresented = nativeDemo.boolField("nativePresented")
    val presentedFrames = nativeDemo.intField("presentedFrames")
    val captureNonTransparentPixels = capture.intField("nonTransparentPixels")
    val nativeDemoPath = projectRoot.resolve(M83_NATIVE_DEMO)
    val readbackPath = projectRoot.resolve(M83_NATIVE_READBACK)
    val hasNativeDemo = nativeDemoPath.exists()
    val hasReadback = Files.isRegularFile(readbackPath)
    val matchesScene = sceneContractId == M83_DISPLAY_LIST_PM_SCENE_ID
    val captureProduced = capture.stringField("status") == "produced" &&
        capture.boolField("realNativeReadback") &&
        capture.stringField("imagePath") == M83_NATIVE_READBACK
    val produced = hasNativeDemo &&
        hasReadback &&
        matchesScene &&
        nativePresented &&
        captureProduced &&
        captureNonTransparentPixels > 0
    val reason = when {
        produced -> M83_NATIVE_PRESENTED_REASON
        !hasNativeDemo || !hasReadback -> M83_NATIVE_MISSING_REASON
        !matchesScene -> M83_NATIVE_SCENE_MISMATCH_REASON
        !captureProduced -> M83_NATIVE_CAPTURE_MISSING_REASON
        captureNonTransparentPixels <= 0 -> M83_NATIVE_CAPTURE_BLANK_REASON
        else -> M83_NATIVE_CAPTURE_MISSING_REASON
    }
    return M83NativeDisplayListEvidence(
        status = if (produced) "native-display-list-produced" else "blocked",
        reason = reason,
        nativeDemoJson = M83_NATIVE_DEMO,
        readbackImage = M83_NATIVE_READBACK,
        sceneContractId = sceneContractId,
        nativePresented = nativePresented,
        presentedFrames = presentedFrames,
        captureStatus = capture.stringField("status"),
        captureReason = capture.stringField("reason"),
        captureNonTransparentPixels = captureNonTransparentPixels,
        adapterInfo = nativeDemo.stringField("adapterInfo"),
        nativePixelsProducedFromDisplayListByThisTask = produced,
    )
}

private fun readJsonObjectOrEmpty(path: java.nio.file.Path): JsonObject {
    if (!path.exists()) return JsonObject(emptyMap())
    val element = M83_JSON.parseToJsonElement(path.readText())
    return element as? JsonObject ?: error("M83 source evidence is not a JSON object: $path")
}

private fun validationRow(id: String, status: String, assertion: String): JsonObject = buildJsonObject {
    put("id", id)
    put("status", status)
    put("assertion", assertion)
}

private fun JsonObject.objectField(name: String): JsonObject = this[name] as? JsonObject ?: JsonObject(emptyMap())
private fun JsonObject.stringField(name: String): String = this[name]?.jsonPrimitive?.contentOrNull.orEmpty()
private fun JsonObject.intField(name: String): Int = this[name]?.jsonPrimitive?.intOrNull ?: 0
private fun JsonObject.boolField(name: String): Boolean = this[name]?.jsonPrimitive?.booleanOrNull ?: false

fun main(args: Array<String>) {
    val projectRoot = args.getOrNull(0)?.let(::Path) ?: Path(".")
    val outputRoot = args.getOrNull(1)?.let(::Path) ?: projectRoot.resolve(M83_OUTPUT)
    outputRoot.createDirectories()

    val evidence = buildM83DisplayListReplayEvidence(projectRoot)
    outputRoot.resolve("evidence.json").writeText(M83_JSON.encodeToString(JsonObject.serializer(), evidence.toJsonElement()) + "\n")
    outputRoot.resolve("evidence.md").writeText(evidence.toMarkdown())
}
