package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Port stub for upstream Skia's `gm/graphitestart.cpp::GraphiteStartGM`
 * (`DEF_GM(return new GraphiteStartGM;)`, name `graphitestart`).
 *
 * **Not implemented** — upstream is a Graphite-targeted "everything"
 * GM (image shader / gradient / colour-filter swatches /
 * `kSrcOver`-on-clipped-rrect) used as the first integration test for
 * the Skia Graphite backend. kanvas-skia has no Graphite renderer ;
 * the WebGPU pipeline in [`gpu-raster`](../../../../../gpu-raster)
 * does not implement the same per-tile compositor draws.
 *
 * The associated [GraphiteStartTest] is `@Ignore`'d. Rendered as a
 * black 384×384 frame so the GM at least has a well-defined size
 * (3 × 128 wide × 3 × 128 tall).
 */
public class GraphiteStartGM : GM() {

    init {
        setBGColor(0xFF000000.toInt())
    }

    override fun getName(): String = "graphitestart"
    override fun getISize(): SkISize = SkISize.Make(K_WIDTH, K_HEIGHT)

    override fun onDraw(canvas: SkCanvas?) {
        // No-op : the Graphite backend dispatch is unimplemented.
        // Output is the GM's bgColor (black). Test @Ignore'd.
    }

    private companion object {
        const val K_TILE_WIDTH: Int = 128
        const val K_TILE_HEIGHT: Int = 128
        const val K_WIDTH: Int = 3 * K_TILE_WIDTH
        const val K_HEIGHT: Int = 3 * K_TILE_HEIGHT
    }
}
