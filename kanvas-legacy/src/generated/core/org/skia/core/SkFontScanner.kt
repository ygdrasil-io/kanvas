package org.skia.core

import AxisDefinitions
import VariationPosition
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkFontArguments
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkNoncopyable
import org.skia.foundation.SkStreamAsset

/**
 * C++ original:
 * ```cpp
 * class SkFontScanner : public SkNoncopyable {
 * public:
 *     virtual ~SkFontScanner() = default;
 *     using AxisDefinitions = skia_private::STArray<4, SkFontParameters::Variation::Axis, true>;
 *     using VariationPosition = skia_private::STArray<4, SkFontArguments::VariationPosition::Coordinate, true>;
 *
 *     virtual bool scanFile(SkStreamAsset* stream, int* numFaces) const = 0;
 *     virtual bool scanFace(SkStreamAsset* stream, int faceIndex, int* numInstances) const = 0;
 *     /* instanceIndex 0 is the default instance, 1 to numInstances are the named instances. */
 *     virtual bool scanInstance(SkStreamAsset* stream,
 *                               int faceIndex,
 *                               int instanceIndex,
 *                               SkString* name,
 *                               SkFontStyle* style,
 *                               bool* isFixedPitch,
 *                               AxisDefinitions* axes,
 *                               VariationPosition* position) const = 0;
 *     virtual sk_sp<SkTypeface> MakeFromStream(std::unique_ptr<SkStreamAsset> stream,
 *                                              const SkFontArguments& args) const = 0;
 *     virtual SkFourByteTag getFactoryId() const = 0;
 * }
 * ```
 */
public abstract class SkFontScanner : SkNoncopyable() {
  /**
   * C++ original:
   * ```cpp
   * virtual bool scanFile(SkStreamAsset* stream, int* numFaces) const = 0
   * ```
   */
  public abstract fun scanFile(stream: SkStreamAsset?, numFaces: Int?): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool scanFace(SkStreamAsset* stream, int faceIndex, int* numInstances) const = 0
   * ```
   */
  public abstract fun scanFace(
    stream: SkStreamAsset?,
    faceIndex: Int,
    numInstances: Int?,
  ): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool scanInstance(SkStreamAsset* stream,
   *                               int faceIndex,
   *                               int instanceIndex,
   *                               SkString* name,
   *                               SkFontStyle* style,
   *                               bool* isFixedPitch,
   *                               AxisDefinitions* axes,
   *                               VariationPosition* position) const = 0
   * ```
   */
  public abstract fun scanInstance(
    stream: SkStreamAsset?,
    faceIndex: Int,
    instanceIndex: Int,
    name: String?,
    style: SkFontStyle?,
    isFixedPitch: Boolean?,
    axes: AxisDefinitions?,
    position: VariationPosition?,
  ): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkTypeface> MakeFromStream(std::unique_ptr<SkStreamAsset> stream,
   *                                              const SkFontArguments& args) const = 0
   * ```
   */
  public abstract fun makeFromStream(stream: SkStreamAsset?, args: SkFontArguments): Int

  /**
   * C++ original:
   * ```cpp
   * virtual SkFourByteTag getFactoryId() const = 0
   * ```
   */
  public abstract fun getFactoryId(): Int
}
