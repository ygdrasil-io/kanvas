@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.skia

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.pipeline.RuntimeEffectWgsl4kWiring
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.test.ComparisonUtils
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/** Fresh public-Surface evidence. Never reads historical scores or changes reference images. */
fun main(args: Array<String>) {
    require(args.size == 6) { "Usage: SkiaGmParityCheckpoint <directory> <from> <to> <timeoutSeconds> <rendererCommit> <images>" }
    val output = File(args[0]).also { it.mkdirs() }
    val from = args[1].toInt()
    val requestedTo = args[2].toInt()
    val timeoutSeconds = args[3].toLong().also { require(it > 0) }
    val rendererCommit = args[4].also { require(it.matches(Regex("[0-9a-f]{40}"))) }
    val saveImages = args[5].toBooleanStrict()
    RuntimeEffectWgsl4kWiring.install()
    val entries = SkiaGmRegistry.entries().sortedBy { it.gm?.name ?: it.provider }
    val to = minOf(requestedTo, entries.size)
    require(from in 0..to)
    val journal = output.resolve("slice-$from-$to.jsonl")
    require(!journal.exists()) { "Refusing to overwrite checkpoint evidence: $journal" }
    fun emit(row: Map<String, Any?>) = journal.appendText(checkpointJson(row) + "\n")
    emit(linkedMapOf("kind" to "run", "schema" to "skia-parity-v1", "rendererCommit" to rendererCommit,
        "startedAt" to Instant.now().toString(), "os" to System.getProperty("os.name"),
        "arch" to System.getProperty("os.arch"), "java" to System.getProperty("java.version"),
        "registryCount" to entries.size, "from" to from, "to" to to, "timeoutSeconds" to timeoutSeconds,
        "registrySha256" to checkpointSha(entries.joinToString("\n") { "${it.provider}:${it.gm?.name}" }.toByteArray())))
    val watchdog = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "skia-parity-watchdog").apply { isDaemon = true }
    }
    try {
        for (index in from until to) {
            val entry = entries[index]
            val gm = entry.gm
            val row = linkedMapOf<String, Any?>("kind" to "gm", "index" to index,
                "name" to (gm?.name ?: entry.provider), "provider" to entry.provider)
            if (gm == null) {
                row.putAll(mapOf("scope" to "unclassified", "outcome" to "provider_failed", "diagnostic" to entry.diagnostic))
                emit(row)
                continue
            }
            val initialDecision = SkiaGmConformance.decisionFor(gm)
            row.putAll(mapOf("family" to gm.renderFamily.name, "width" to gm.width, "height" to gm.height,
                "referenceName" to gm.referenceName, "tolerance" to gm.tolerance,
                "minSimilarity" to gm.minSimilarity, "requiresZeroRefusals" to gm.requiresZeroRefusals,
                "initialScope" to initialDecision.scope.wireName, "compositionDomain" to gm.compositionDomain.name))
            val referenceFile = File("src/test/resources/reference/${gm.referenceName}.png")
            row["referenceStatus"] = when {
                !referenceFile.isFile -> "missing"
                gm.referenceStatus.untrustable -> "untrustable"
                else -> "trusted"
            }
            row["referencePngSha256"] = referenceFile.takeIf(File::isFile)?.readBytes()?.let(::checkpointSha)
            // Keep provenance even if native work never returns. Never let the watchdog read row.
            val timeoutIdentity = row.toMap()
            val done = AtomicBoolean(false)
            val stage = AtomicReference("setup")
            val currentScope = AtomicReference(initialDecision.scope.wireName)
            val started = System.nanoTime()
            val timeout = watchdog.schedule({
                if (done.compareAndSet(false, true)) {
                    // Use a separate row: the main thread may still be mutating its diagnostic row.
                    emit(timeoutIdentity + mapOf(
                        "scope" to currentScope.get(), "outcome" to "timeout", "stage" to stage.get(),
                        "elapsedMs" to timeoutSeconds * 1000))
                    System.err.println("[TIMEOUT] $index ${gm.name} stage=${stage.get()}")
                    // As in SkiaGmScanner, interrupting a stuck native call cannot bound this process.
                    Runtime.getRuntime().halt(124)
                }
            }, timeoutSeconds, TimeUnit.SECONDS)
            var result: RenderResult? = null
            var renderMs: Long? = null
            try {
                val evidence = captureInventoryEvidence(gm) {
                    val surface = Surface(gm.width, gm.height, config = gm.compositionConfig())
                    object : InventorySurfaceCapture {
                        override fun canvas() = surface.canvas()
                        override fun snapshotOperationCount() = surface.snapshotOps().size
                        override fun render(): RenderResult {
                            stage.set("render")
                            val t0 = System.nanoTime()
                            return try { surface.render().also { result = it } }
                            finally { renderMs = (System.nanoTime() - t0) / 1_000_000 }
                        }
                    }
                }
                currentScope.set(evidence.conformanceDecision.scope.wireName)
                row.putAll(mapOf("scope" to currentScope.get(), "scopeReason" to evidence.conformanceDecision.reason,
                    "scopeOwner" to evidence.conformanceDecision.owner, "setupState" to evidence.setupState.name,
                    "attempted" to evidence.attempted, "rendered" to evidence.renderSucceeded,
                    "operationCount" to evidence.operationCount, "renderMs" to renderMs,
                    "diagnostic" to (evidence.setupDiagnostic ?: evidence.diagnostics.firstOrNull()),
                    "outcome" to when {
                        !evidence.conformanceDecision.mustAttempt -> "excluded"
                        evidence.setupState == InventorySetupState.FAILED -> "setup_failed"
                        !evidence.renderSucceeded -> "render_failed"
                        else -> "rendered_uncompared"
                    }))
                result?.let { rendered ->
                    row["dispatched"] = rendered.stats.opsDispatched
                    row["refused"] = rendered.stats.opsRefused
                    val actual = rendered.pixels.map { it.toByte() }.toByteArray()
                    row["actualRgbaSha256"] = checkpointSha(actual)
                    if (row["referenceStatus"] == "trusted") {
                        stage.set("comparison")
                        // Equal buffer lengths alone do not prove matching dimensions (e.g. 2x8 vs 4x4).
                        val referenceImage = ComparisonUtils.readPngAsSrgbBufferedImage(referenceFile)
                        row["referenceWidth"] = referenceImage.width
                        row["referenceHeight"] = referenceImage.height
                        if (referenceImage.width != gm.width || referenceImage.height != gm.height) {
                            row["outcome"] = "reference_dimension_mismatch"
                            row["diagnostic"] = "GM=${gm.width}x${gm.height}, reference=${referenceImage.width}x${referenceImage.height}"
                            return@let
                        }
                        val reference = ComparisonUtils.bufferedImageToRgba(referenceImage)
                        val comparison = ComparisonUtils.compareRgba(actual, reference, gm.width, gm.height, 0, 100.0)
                        var withinTwo = 0
                        var withinDeclared = 0
                        for (pixel in 0 until gm.width * gm.height) {
                            val maxError = (0..3).maxOf { channel ->
                                val offset = pixel * 4 + channel
                                abs((actual[offset].toInt() and 255) - (reference[offset].toInt() and 255))
                            }
                            if (maxError <= 2) withinTwo++
                            if (maxError <= gm.tolerance) withinDeclared++
                        }
                        val declaredMatch = withinDeclared * 100.0 / comparison.totalPixels
                        row.putAll(mapOf("outcome" to "compared", "exactPixelMatch" to comparison.pixelMatch,
                            "pixelMatchTolerance2" to withinTwo * 100.0 / comparison.totalPixels,
                            "pixelMatchDeclaredTolerance" to declaredMatch, "ssimLuminance" to comparison.ssim,
                            "meanAbsoluteChannelErrorNormalized" to comparison.meanChannelError,
                            "maxChannelDelta" to comparison.maxDiff.toList(),
                            "declaredContractPass" to (declaredMatch >= gm.minSimilarity &&
                                (!gm.requiresZeroRefusals || rendered.stats.opsRefused == 0))))
                        if (saveImages) {
                            stage.set("write-images")
                            val imageDir = output.resolve("images/${gm.name}")
                            ComparisonUtils.saveRgbaAsPng(actual, gm.width, gm.height, imageDir.resolve("actual.png"))
                            comparison.diffRgba?.let {
                                ComparisonUtils.saveRgbaAsPng(it, gm.width, gm.height, imageDir.resolve("diff.png"))
                            }
                        }
                    }
                }
            } catch (failure: Exception) {
                row["scope"] = currentScope.get()
                row["outcome"] = "measurement_failed"
                row["stage"] = stage.get()
                row["diagnostic"] = "${failure.javaClass.simpleName}: ${failure.message}"
            } finally {
                row["elapsedMs"] = (System.nanoTime() - started) / 1_000_000
                if (done.compareAndSet(false, true)) {
                    timeout.cancel(false)
                    emit(row)
                    println("[PARITY] $index ${gm.name} ${row["scope"]} ${row["outcome"]} pixel2=${row["pixelMatchTolerance2"]} ${row["elapsedMs"]}ms")
                }
            }
        }
        emit(mapOf("kind" to "complete", "from" to from, "to" to to, "completedAt" to Instant.now().toString()))
    } finally {
        watchdog.shutdownNow()
        GPUBackendRuntimeFactory.dispose()
    }
}

private fun checkpointSha(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

private fun checkpointJson(value: Any?): String = when (value) {
    null -> "null"
    is String -> buildString {
        append('"')
        value.forEach { char -> when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            in '\u0000'..'\u001f' -> append("\\u%04x".format(char.code))
            else -> append(char)
        } }
        append('"')
    }
    is Number -> value.toString().also { require(value.toDouble().isFinite()) }
    is Boolean -> value.toString()
    is Map<*, *> -> value.entries.joinToString(",", "{", "}") { "${checkpointJson(it.key)}:${checkpointJson(it.value)}" }
    is List<*> -> value.joinToString(",", "[", "]") { checkpointJson(it) }
    else -> error("Unsupported checkpoint value: ${value.javaClass}")
}
