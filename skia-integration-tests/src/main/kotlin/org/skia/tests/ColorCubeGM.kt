package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder port of upstream Skia `gm/colorcube.cpp::ColorCubeGM` (or
 * `gm/jpg_color_cube.cpp` variant — both draw 3D LUT color-cube
 * filtering applied to an image).
 *
 * Original drives the `SkColorFilters::HSLAMatrix` / 3D-LUT cube color
 * filter (a 256³ lookup texture). Used to verify the GPU 3D-LUT shader
 * sampling math.
 *
 * TODO: missing API — `SkColorFilters::HSLAMatrix` / 3D-LUT cube filter
 * with GPU shader path. Flag-planting stub.
 */
public class ColorCubeGM : GM() {
    override fun getName(): String = "jpg_color_cube"
    override fun getISize(): SkISize = SkISize.Make(512, 512)

    override fun onDraw(canvas: SkCanvas?) {
        // TODO: missing API — 3D LUT color-cube filter.
    }
}
