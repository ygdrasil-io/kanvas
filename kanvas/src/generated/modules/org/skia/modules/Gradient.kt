package org.skia.modules

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.collections.List
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkScalar
import undefined.ColorStops
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * class Gradient : public Shader {
 * public:
 *     struct ColorStop {
 *         SkScalar  fPosition;
 *         SkColor4f fColor;
 *
 *         bool operator==(const ColorStop& other) const {
 *             return fPosition == other.fPosition && fColor == other.fColor;
 *         }
 *     };
 *
 *     SG_ATTRIBUTE(ColorStops, std::vector<ColorStop>, fColorStops)
 *     SG_ATTRIBUTE(TileMode  , SkTileMode            , fTileMode  )
 *
 * protected:
 *     sk_sp<SkShader> onRevalidateShader() final;
 *
 *     virtual sk_sp<SkShader> onMakeShader(const std::vector<SkColor4f>& colors,
 *                                          const std::vector<SkScalar >& positions) const = 0;
 *
 * protected:
 *     Gradient() = default;
 *
 * private:
 *     std::vector<ColorStop> fColorStops;
 *     SkTileMode             fTileMode = SkTileMode::kClamp;
 *
 *     using INHERITED = Shader;
 * }
 * ```
 */
public abstract class Gradient public constructor() : Shader() {
  /**
   * C++ original:
   * ```cpp
   * std::vector<ColorStop> fColorStops
   * ```
   */
  private var fColorStops: List<org.skia.`external`.ColorStop> = TODO("Initialize fColorStops")

  /**
   * C++ original:
   * ```cpp
   * SkTileMode             fTileMode
   * ```
   */
  private var fTileMode: Int = TODO("Initialize fTileMode")

  /**
   * C++ original:
   * ```cpp
   * SG_ATTRIBUTE(ColorStops, std::vector<ColorStop>, fColorStops)
   * ```
   */
  public fun sgATTRIBUTE(param0: ColorStops, param1: List<org.skia.`external`.ColorStop>): Int {
    TODO("Implement sgATTRIBUTE")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkShader> onMakeShader(const std::vector<SkColor4f>& colors,
   *                                          const std::vector<SkScalar >& positions) const = 0
   * ```
   */
  protected abstract fun onMakeShader(colors: List<SkColor4f>, positions: List<SkScalar>): Int

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> Gradient::onRevalidateShader() {
   *     if (fColorStops.empty()) {
   *         return nullptr;
   *     }
   *
   *     std::vector<SkColor4f> colors;
   *     std::vector<SkScalar>  positions;
   *     colors.reserve(fColorStops.size());
   *     positions.reserve(fColorStops.size());
   *
   *     SkScalar position = 0;
   *     for (const auto& stop : fColorStops) {
   *         colors.push_back(stop.fColor);
   *         position = SkTPin(stop.fPosition, position, 1.0f);
   *         positions.push_back(position);
   *     }
   *
   *     // TODO: detect even stop distributions, pass null for positions.
   *     return this->onMakeShader(colors, positions);
   * }
   * ```
   */
  public override fun onRevalidateShader(): SkSp<SkShader> {
    TODO("Implement onRevalidateShader")
  }

  public data class ColorStop public constructor(
    public var fPosition: Int,
    public var fColor: Int,
  ) {
    public override operator fun equals(other: Any?): Boolean {
      TODO("Implement equals")
    }
  }
}

public typealias LinearGradientINHERITED = Gradient

public typealias RadialGradientINHERITED = Gradient
