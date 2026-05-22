package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Port stub for upstream Skia's `gm/graphite_replay.cpp::GraphiteReplayGM`
 * (`DEF_GM(return new GraphiteReplayGM;)`, name `graphite-replay`).
 *
 * **Not implemented** — upstream relies on the Skia Graphite renderer
 * (`skgpu::graphite::Recorder` + `Recording::Replay`) to record a
 * sequence of draws into a recording then replay it at a translated
 * offset to produce a 3×2 grid of mandrill tiles. kanvas-skia has no
 * Graphite backend (the WebGPU renderer is a separate, divergent
 * implementation in [`gpu-raster`](../../../../../gpu-raster) that
 * does not expose a record/replay API).
 *
 * The associated [GraphiteReplayTest] is `@Ignore`'d : it would
 * unconditionally fall through the upstream `drawNonGraphite` branch
 * (which only renders a "Graphite not supported" error string).
 *
 * Rendered as a black 384×256 frame so the GM is at least valid in
 * shape (3 × 128 × 2 × 128).
 */
public class GraphiteReplayGM : GM() {

    init {
        setBGColor(0xFF000000.toInt())
    }

    override fun getName(): String = "graphite-replay"
    override fun getISize(): SkISize = SkISize.Make(K_TILE_WIDTH * 3, K_TILE_HEIGHT * 2)

    override fun onDraw(canvas: SkCanvas?) {
        // No-op : the Graphite Recorder/Replay path is unimplemented.
        // Output is the GM's bgColor (black) — matches `drawNonGraphite`
        // shape (the "Graphite not supported" error path renders text on
        // black but the test is @Ignore'd so the pixels aren't asserted).
    }

    private companion object {
        const val K_TILE_WIDTH: Int = 128
        const val K_TILE_HEIGHT: Int = 128
    }
}
