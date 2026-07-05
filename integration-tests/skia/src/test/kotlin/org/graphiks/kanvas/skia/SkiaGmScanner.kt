package org.graphiks.kanvas.skia

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.pipeline.RuntimeEffectWgsl4kWiring
import org.graphiks.kanvas.surface.RenderConfig
import kotlin.system.exitProcess

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
    var from = 0
    var to = Int.MAX_VALUE
    var perGmTimeoutSec = 30L
    var outputPath: String? = null

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--from" -> from = args[++i].toInt()
            "--to" -> to = args[++i].toInt()
            "--timeout" -> perGmTimeoutSec = args[++i].toLong()
            "--output" -> outputPath = args[++i]
        }
        i++
    }

    RuntimeEffectWgsl4kWiring.install()
    val config = RenderConfig.fromEnvironment()
    val gms = SkiaGmRegistry.all()

    if (from >= gms.size) {
        System.err.println("[SKIP] --from=$from >= total=${gms.size}")
        exitProcess(0)
    }
    if (to > gms.size) to = gms.size

    val outputFile = outputPath?.let { java.io.File(it) }
    var pass = 0
    var fail = 0
    var timeout = 0

    for (idx in from until to) {
        val gm = gms[idx]
        val gmName = gm.name

        // Watchdog: kill the JVM if this GM exceeds the timeout.
        // We use Runtime.halt (immediate, no shutdown hooks) because
        // Thread.interrupt() cannot abort a native WebGPU hang.
        val watchdog = Thread {
            Thread.sleep(perGmTimeoutSec * 1000L)
            System.err.println("[TIMEOUT] $idx $gmName ${perGmTimeoutSec}s")
            outputFile?.appendText("TIMEOUT|$idx|$gmName|${perGmTimeoutSec}000\n")
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
    println("=== Scan [$from, $to) done: PASS=$pass FAIL=$fail TIMEOUT=$timeout ===")
    exitProcess(if (fail == 0 && timeout == 0) 0 else 1)
}
