package org.graphiks.kanvas.skia

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.pipeline.RuntimeEffectWgsl4kWiring
import org.graphiks.kanvas.surface.RenderConfig
import kotlin.system.exitProcess

data class SkiaGmScanOptions(
    val from: Int = 0,
    val to: Int = Int.MAX_VALUE,
    val timeoutSeconds: Long = 30L,
    val outputPath: String? = null,
    val names: Set<String> = emptySet(),
)

data class SkiaGmScanSelection(
    val gms: List<IndexedValue<SkiaGm>>,
    val total: Int,
    val effectiveFrom: Int,
    val effectiveTo: Int,
) {
    val emptyDiagnostic: String
        get() = "[SKIP] --from=$effectiveFrom >= total=$total"
}

fun parseSkiaGmScanOptions(args: Array<String>): SkiaGmScanOptions {
    var from = 0
    var to = Int.MAX_VALUE
    var timeoutSeconds = 30L
    var outputPath: String? = null
    var names = emptySet<String>()

    var i = 0
    while (i < args.size) {
        when (val argument = args[i]) {
            "--from" -> from = args[++i].toInt()
            "--to" -> to = args[++i].toInt()
            "--timeout" -> timeoutSeconds = args[++i].toLong()
            "--output" -> outputPath = args[++i]
            "--names" -> names = args[++i].split(',').map(String::trim).filter(String::isNotEmpty).toSet()
            else -> if (argument.startsWith("--names=")) {
                names = argument.removePrefix("--names=").split(',').map(String::trim).filter(String::isNotEmpty).toSet()
            }
        }
        i++
    }

    return SkiaGmScanOptions(from, to, timeoutSeconds, outputPath, names)
}

fun resolveSkiaGmScanSelection(
    gms: List<SkiaGm>,
    options: SkiaGmScanOptions,
): SkiaGmScanSelection {
    val namedGms = gms.withIndex().filter { options.names.isEmpty() || it.value.name in options.names }
    val foundNames = namedGms.map { it.value.name }.toSet()
    val missingNames = options.names - foundNames
    require(missingNames.isEmpty()) { "Unknown Skia GM names: ${missingNames.joinToString(", ")}" }
    val effectiveFrom = options.from.coerceIn(0, namedGms.size)
    val effectiveTo = options.to.coerceIn(effectiveFrom, namedGms.size)
    return SkiaGmScanSelection(
        gms = namedGms.subList(effectiveFrom, effectiveTo),
        total = namedGms.size,
        effectiveFrom = effectiveFrom,
        effectiveTo = effectiveTo,
    )
}

fun selectSkiaGmsForScan(
    gms: List<SkiaGm>,
    options: SkiaGmScanOptions,
): List<IndexedValue<SkiaGm>> = resolveSkiaGmScanSelection(gms, options).gms

/**
 * Scans GMs individually with a per-GM watchdog timeout.  When a GM hangs
 * (WebGPU native freeze), the watchdog kills the entire JVM — the caller
 * (shell script) sees exit code 124 (timeout) and knows which GM was last
 * attempted.
 *
 * Args:
 *   --from=N     first GM index (default 0)
 *   --to=N       last GM index (default all)
 *   --timeout=N  seconds per GM (default 30)
 *   --output=PATH  append result lines to a file (default stdout only)
 *
 * Output (one line per GM):
 *   PASS|FAIL|TIMEOUT  <index>  <name>  <elapsedMs>
 */
fun main(args: Array<String>) {
    val options = parseSkiaGmScanOptions(args)
    RuntimeEffectWgsl4kWiring.install()
    val config = RenderConfig.fromEnvironment()
    val selection = resolveSkiaGmScanSelection(SkiaGmRegistry.all(), options)
    val selectedGms = selection.gms

    if (selectedGms.isEmpty()) {
        System.err.println(selection.emptyDiagnostic)
        exitProcess(0)
    }

    val outputFile = options.outputPath?.let { java.io.File(it) }
    var pass = 0
    var fail = 0
    var timeout = 0

    for ((idx, gm) in selectedGms) {
        val gmName = gm.name

        // Watchdog: kill the JVM if this GM exceeds the timeout.
        // We use Runtime.halt (immediate, no shutdown hooks) because
        // Thread.interrupt() cannot abort a native WebGPU hang.
        val watchdog = Thread {
            Thread.sleep(options.timeoutSeconds * 1000L)
            System.err.println("[TIMEOUT] $idx $gmName ${options.timeoutSeconds}s")
            outputFile?.appendText("TIMEOUT|$idx|$gmName|${options.timeoutSeconds}000\n")
            Runtime.getRuntime().halt(124)
        }
        watchdog.isDaemon = true
        watchdog.start()

        val t0 = System.nanoTime()
        try {
            val result = SkiaGmRenderer.render(gm, config = config)
            val elapsedMs = (System.nanoTime() - t0) / 1_000_000

            // Cancel watchdog (no-op if already passed the timeout)
            watchdog.interrupt()

            if (result.diagnostics.isNotEmpty()) {
                println("[DONE] $idx $gmName (${elapsedMs}ms) diags=${result.diagnostics.size}")
                result.diagnostics.forEach { d -> System.err.println("  $d") }
            } else {
                println("[DONE] $idx $gmName (${elapsedMs}ms)")
            }
            val line = "PASS|$idx|$gmName|$elapsedMs"
            outputFile?.appendText("$line\n")
            pass++
        } catch (e: Throwable) {
            val elapsedMs = (System.nanoTime() - t0) / 1_000_000
            watchdog.interrupt()
            println("[FAIL] $idx $gmName (${elapsedMs}ms) ${e.message}")
            val line = "FAIL|$idx|$gmName|$elapsedMs|${e.message}"
            outputFile?.appendText("$line\n")
            fail++
        }
    }

    GPUBackendRuntimeFactory.dispose()
    println("=== Scan [${selection.effectiveFrom}, ${selection.effectiveTo}) done: PASS=$pass FAIL=$fail TIMEOUT=$timeout ===")
    exitProcess(if (fail == 0 && timeout == 0) 0 else 1)
}
