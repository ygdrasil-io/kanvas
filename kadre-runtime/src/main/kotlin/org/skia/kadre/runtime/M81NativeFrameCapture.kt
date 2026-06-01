package org.skia.kadre.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

private const val M81_NATIVE_DEMO = "reports/wgsl-pipeline/m70-kadre-native/native-demo.json"
private const val M81_ROUTE_STATUS = "reports/wgsl-pipeline/m70-kadre-live-runtime/route-status.json"
private const val M81_NATIVE_SMOKE = "reports/wgsl-pipeline/m69-kadre-native/native-smoke.json"
private const val M81_OUTPUT = "reports/wgsl-pipeline/m81-native-frame-capture"
private const val M81_WINDOW_SURFACE_REFUSAL = "m81.window-surface-readback-not-implemented"
private const val M81_HOST_UNSUPPORTED = "m81.host-unsupported"
private const val M81_ADAPTER_UNAVAILABLE = "m81.adapter-unavailable"
private const val M81_CAPTURE_UNSUPPORTED = "m81.capture-unsupported"
private const val M81_BLANK_ARTIFACT = "m81.blank-artifact"
private const val M81_INFRASTRUCTURE_SOURCE_MISSING = "m81.infrastructure-source-evidence-missing"
private const val M81_OFFSCREEN_REASON = "m70.native-offscreen-texture-readback"

private val M81_LINEAR_ISSUES = listOf("FOR-97", "FOR-139", "FOR-140", "FOR-141", "FOR-142", "FOR-143")
private val M81_JSON = Json { prettyPrint = true }

internal data class M81NativeFrameCaptureEvidence(
    val nativeDemo: JsonObject,
    val routeStatus: JsonObject,
    val nativeSmoke: JsonObject,
) {
    private val capture: JsonObject = nativeDemo.objectField("capture")
    private val surface: JsonObject = nativeDemo.objectField("surface")
    private val routeCapture: JsonObject = routeStatus.objectField("capture")
    private val adapterInfo: String = nativeDemo.stringField("adapterInfo")
    private val sourceEvidenceMissing: Boolean = nativeDemo.isEmpty() || routeStatus.isEmpty() || nativeSmoke.isEmpty()
    private val captureImagePath: String = capture.stringField("imagePath")
    private val captureNonTransparentPixels: Int = capture.intField("nonTransparentPixels")

    val frameCount: Int = nativeDemo.intField("presentedFrames")
    val requestedFrameCount: Int = nativeDemo.intField("requestedFrames")
    val nativePresented: Boolean = nativeDemo.boolField("nativePresented")
    val realOffscreenReadback: Boolean = capture.boolField("realNativeReadback") && capture.stringField("reason") == M81_OFFSCREEN_REASON
    val windowSurfaceReadback: Boolean = capture.boolField("windowSurfaceReadback")
    val unsupportedCaptureReasons: List<String> = buildList {
        if (sourceEvidenceMissing) add(M81_INFRASTRUCTURE_SOURCE_MISSING)
        if (!nativePresented) add(M81_HOST_UNSUPPORTED)
        if (adapterInfo.isBlank()) add(M81_ADAPTER_UNAVAILABLE)
        if (!realOffscreenReadback && !windowSurfaceReadback) add(M81_CAPTURE_UNSUPPORTED)
        if ((realOffscreenReadback || windowSurfaceReadback) && captureNonTransparentPixels <= 0) add(M81_BLANK_ARTIFACT)
        if (!windowSurfaceReadback) add(M81_WINDOW_SURFACE_REFUSAL)
    }.distinct()
    val captureStatus: String = when {
        sourceEvidenceMissing -> "refused-infrastructure-source-missing"
        !nativePresented -> "refused-host-unsupported"
        adapterInfo.isBlank() -> "refused-adapter-unavailable"
        (realOffscreenReadback || windowSurfaceReadback) && captureNonTransparentPixels <= 0 -> "refused-blank-artifact"
        realOffscreenReadback && !windowSurfaceReadback -> "offscreen-texture-readback-produced"
        windowSurfaceReadback -> "window-surface-readback-produced"
        else -> "refused-capture-unsupported"
    }
    val unsupportedCaptureCount: Int = unsupportedCaptureReasons.size
    val artifactPaths: List<String> = listOfNotNull(
        captureImagePath.takeIf { it.isNotBlank() },
        M81_NATIVE_DEMO,
        M81_ROUTE_STATUS,
        M81_NATIVE_SMOKE,
        "$M81_OUTPUT/evidence.json",
        "$M81_OUTPUT/evidence.md",
    ).distinct()

    fun toJsonElement(): JsonObject = buildJsonObject {
        put("schemaVersion", 1)
        put("generatedBy", "kadre-runtime:M81NativeFrameCapture")
        put("linearIssues", buildJsonArray { M81_LINEAR_ISSUES.forEach { add(JsonPrimitive(it)) } })
        put("packId", "m81-native-frame-capture-v1")
        put("claimLevel", "native-frame-artifact-capture-evidence")
        put("readinessDelta", 0)
        put("captureStatus", captureStatus)
        put("nativePresented", nativePresented)
        put("frameCount", frameCount)
        put("requestedFrameCount", requestedFrameCount)
        put("realNativeWindowSurfaceReadback", windowSurfaceReadback)
        put("realNativeOffscreenTextureReadback", realOffscreenReadback)
        put("unsupportedCaptureCount", unsupportedCaptureCount)
        put("unsupportedCaptureReasons", buildJsonArray { unsupportedCaptureReasons.forEach { add(JsonPrimitive(it)) } })
        put("artifactPaths", buildJsonArray { artifactPaths.forEach { add(JsonPrimitive(it)) } })
        put("hostPlatform", hostPlatformJson())
        put("adapter", buildJsonObject {
            put("info", adapterInfo)
            put("backend", if (adapterInfo.isBlank()) "unavailable" else "wgpu4k-native")
        })
        put("surface", buildJsonObject {
            put("width", surface.intField("width"))
            put("height", surface.intField("height"))
            put("format", surface.stringField("format"))
        })
        put("capture", buildJsonObject {
            put("status", capture.stringField("status").ifBlank { "unavailable" })
            put("reason", capture.stringField("reason").ifBlank { "m81.capture-source-missing" })
            put("source", capture.stringField("source"))
            put("imagePath", capture.stringField("imagePath"))
            put("format", capture.stringField("format"))
            put("width", capture.intField("width"))
            put("height", capture.intField("height"))
            put("bytes", capture.longField("bytes"))
            put("checksum", capture.longField("checksum"))
            put("nonTransparentPixels", capture.intField("nonTransparentPixels"))
            put("realNativeReadback", capture.boolField("realNativeReadback"))
            put("windowSurfaceReadback", capture.boolField("windowSurfaceReadback"))
        })
        put("sourceEvidence", buildJsonObject {
            put("nativeDemoJson", M81_NATIVE_DEMO)
            put("routeStatusJson", M81_ROUTE_STATUS)
            put("nativeSmokeJson", M81_NATIVE_SMOKE)
            put("routeStatus", routeStatus.stringField("status"))
            put("routeReason", routeStatus.stringField("reason"))
            put("m69NativeSmokeStatus", nativeSmoke.stringField("status"))
            put("m69NativeSmokePresentedFrames", nativeSmoke.intField("presentedFrames"))
            put("routeCaptureStatus", routeCapture.stringField("status"))
            put("routeCaptureReason", routeCapture.stringField("reason"))
        })
        put("validationRows", buildJsonArray {
            add(validationRow("m81.capture-status-schema", "pass", "M81 evidence exposes capture status, frame count, artifacts, host/platform, adapter, surface, and capture mode fields."))
            add(validationRow("m81.offscreen-readback-truthfulness", if (realOffscreenReadback || captureStatus.startsWith("refused-")) "pass" else "blocked", "M81 preserves M70-C as wgpu4k offscreen texture readback evidence when present, otherwise reports a stable refusal."))
            add(validationRow("m81.window-surface-refusal", if (!windowSurfaceReadback) "pass" else "blocked", "Window-surface screenshot/readback remains unsupported unless a source capture explicitly proves it."))
            add(validationRow("m81.refusal-taxonomy", if (unsupportedCaptureReasons.isNotEmpty()) "pass" else "blocked", "M81 serializes stable refusal reasons for ineligible capture paths."))
        })
        put("nonClaims", buildJsonArray {
            add(JsonPrimitive("M81 does not claim system screenshot capture or window-surface readback."))
            add(JsonPrimitive("M81 re-publishes current M69/M70 Kadre/WebGPU evidence for PM review; it does not run a new native capture path inside pipelinePmBundle."))
            add(JsonPrimitive("The current produced PNG is a wgpu4k native offscreen texture readback of the selected scene contract."))
            add(JsonPrimitive("Native timing remains reporting-only and is not a release-grade FPS gate."))
        })
    }

    fun toMarkdown(): String = buildString {
        appendLine("# M81 Native Frame Artifact Capture")
        appendLine()
        appendLine("Status: `$captureStatus`")
        appendLine()
        appendLine("M81 packages the current M69/M70 Kadre/WebGPU native evidence into one PM-visible frame capture artifact set.")
        appendLine("The produced PNG is a real `wgpu4k` native offscreen texture readback. It is not a window-surface screenshot or readback.")
        appendLine()
        appendLine("## PM Outcome")
        appendLine()
        appendLine("- Capture status: `$captureStatus`")
        appendLine("- Presented frames: `$frameCount` / `$requestedFrameCount`")
        appendLine("- Native/window-surface readback: `$windowSurfaceReadback`")
        appendLine("- Offscreen texture readback: `$realOffscreenReadback`")
        appendLine("- Refusal reason: `$M81_WINDOW_SURFACE_REFUSAL`")
        appendLine("- Refusal reasons: `${unsupportedCaptureReasons.joinToString()}`")
        appendLine("- Adapter/backend: `$adapterInfo` / `${if (adapterInfo.isBlank()) "unavailable" else "wgpu4k-native"}`")
        appendLine("- Surface: `${surface.intField("width")}` x `${surface.intField("height")}` `${surface.stringField("format")}`")
        appendLine("- Capture image: `${capture.stringField("imagePath")}`")
        appendLine("- Capture format: `${capture.stringField("format")}`")
        appendLine("- Capture checksum/nontransparent pixels: `${capture.longField("checksum")}` / `${capture.intField("nonTransparentPixels")}`")
        appendLine()
        appendLine("## Linear Scope")
        appendLine()
        M81_LINEAR_ISSUES.forEach { appendLine("- `$it`") }
        appendLine()
        appendLine("## Artifacts")
        appendLine()
        artifactPaths.forEach { appendLine("- `$it`") }
        appendLine()
        appendLine("## Non-Claims")
        appendLine()
        appendLine("- No system screenshot capture is claimed.")
        appendLine("- No window-surface readback is claimed.")
        appendLine("- No new release-grade frame/FPS gate is claimed.")
        appendLine()
        appendLine("## Validation")
        appendLine()
        appendLine("```bash")
        appendLine("rtk ./gradlew --no-daemon :kadre-runtime:test :kadre-runtime:pipelineM81NativeFrameCapture")
        appendLine("python3 -m json.tool reports/wgsl-pipeline/m81-native-frame-capture/evidence.json >/dev/null")
        appendLine("```")
    }
}

internal fun buildM81NativeFrameCaptureEvidence(projectRoot: java.nio.file.Path): M81NativeFrameCaptureEvidence =
    M81NativeFrameCaptureEvidence(
        nativeDemo = readJsonObjectOrEmpty(projectRoot.resolve(M81_NATIVE_DEMO)),
        routeStatus = readJsonObjectOrEmpty(projectRoot.resolve(M81_ROUTE_STATUS)),
        nativeSmoke = readJsonObjectOrEmpty(projectRoot.resolve(M81_NATIVE_SMOKE)),
    )

private fun readJsonObjectOrEmpty(path: java.nio.file.Path): JsonObject {
    if (!path.exists()) return JsonObject(emptyMap())
    val element = M81_JSON.parseToJsonElement(path.readText())
    return element as? JsonObject ?: error("M81 source evidence is not a JSON object: $path")
}

private fun JsonObject.objectField(name: String): JsonObject = this[name] as? JsonObject ?: JsonObject(emptyMap())
private fun JsonObject.stringField(name: String): String = this[name]?.jsonPrimitive?.contentOrNull.orEmpty()
private fun JsonObject.intField(name: String): Int = this[name]?.jsonPrimitive?.intOrNull ?: 0
private fun JsonObject.longField(name: String): Long = this[name]?.jsonPrimitive?.longOrNull ?: 0L
private fun JsonObject.boolField(name: String): Boolean = this[name]?.jsonPrimitive?.booleanOrNull ?: false

private fun validationRow(id: String, status: String, assertion: String): JsonElement = buildJsonObject {
    put("id", id)
    put("status", status)
    put("assertion", assertion)
}

private fun hostPlatformJson(): JsonObject = buildJsonObject {
    put("osName", System.getProperty("os.name", "unknown"))
    put("osVersion", System.getProperty("os.version", "unknown"))
    put("osArch", System.getProperty("os.arch", "unknown"))
    put("javaVersion", System.getProperty("java.version", "unknown"))
}

fun main(args: Array<String>) {
    val projectRoot = args.getOrNull(0)?.let(::Path) ?: Path(".")
    val outputRoot = args.getOrNull(1)?.let(::Path) ?: projectRoot.resolve(M81_OUTPUT)
    outputRoot.createDirectories()

    val evidence = buildM81NativeFrameCaptureEvidence(projectRoot)
    outputRoot.resolve("evidence.json").writeText(M81_JSON.encodeToString(JsonElement.serializer(), evidence.toJsonElement()) + "\n")
    outputRoot.resolve("evidence.md").writeText(evidence.toMarkdown())

    val imagePath = evidence.toJsonElement().objectField("capture").stringField("imagePath")
    if (imagePath.isNotBlank()) {
        val image = projectRoot.resolve(imagePath)
        require(Files.isRegularFile(image)) { "M81 capture image path is missing: $imagePath" }
    }
}
