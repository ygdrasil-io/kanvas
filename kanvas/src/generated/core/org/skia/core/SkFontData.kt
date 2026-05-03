package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.math.SkFixed

/**
 * C++ original:
 * ```cpp
 * class SkFontData {
 * public:
 *     /** Makes a copy of the data in 'axis'. */
 *     SkFontData(std::unique_ptr<SkStreamAsset> stream, int index, int paletteIndex,
 *                const SkFixed* axis, int axisCount,
 *                const SkFontArguments::Palette::Override* paletteOverrides, int paletteOverrideCount)
 *         : fStream(std::move(stream))
 *         , fIndex(index)
 *         , fPaletteIndex(paletteIndex)
 *         , fAxisCount(axisCount)
 *         , fPaletteOverrideCount(paletteOverrideCount)
 *         , fAxis(fAxisCount)
 *         , fPaletteOverrides(fPaletteOverrideCount)
 *     {
 *         for (int i = 0; i < fAxisCount; ++i) {
 *             fAxis[i] = axis[i];
 *         }
 *         for (int i = 0; i < fPaletteOverrideCount; ++i) {
 *             fPaletteOverrides[i] = paletteOverrides[i];
 *         }
 *     }
 *
 *     SkFontData(const SkFontData& that)
 *         : fStream(that.fStream->duplicate())
 *         , fIndex(that.fIndex)
 *         , fPaletteIndex(that.fPaletteIndex)
 *         , fAxisCount(that.fAxisCount)
 *         , fPaletteOverrideCount(that.fPaletteOverrideCount)
 *         , fAxis(fAxisCount)
 *         , fPaletteOverrides(fPaletteOverrideCount)
 *     {
 *         for (int i = 0; i < fAxisCount; ++i) {
 *             fAxis[i] = that.fAxis[i];
 *         }
 *         for (int i = 0; i < fPaletteOverrideCount; ++i) {
 *             fPaletteOverrides[i] = that.fPaletteOverrides[i];
 *         }
 *     }
 *     bool hasStream() const { return fStream != nullptr; }
 *     std::unique_ptr<SkStreamAsset> detachStream() { return std::move(fStream); }
 *     SkStreamAsset* getStream() { return fStream.get(); }
 *     SkStreamAsset const* getStream() const { return fStream.get(); }
 *     int getIndex() const { return fIndex; }
 *     int getAxisCount() const { return fAxisCount; }
 *     const SkFixed* getAxis() const { return fAxis.get(); }
 *     int getPaletteIndex() const { return fPaletteIndex; }
 *     int getPaletteOverrideCount() const { return fPaletteOverrideCount; }
 *     const SkFontArguments::Palette::Override* getPaletteOverrides() const {
 *         return fPaletteOverrides.get();
 *     }
 *
 * private:
 *     std::unique_ptr<SkStreamAsset> fStream;
 *     int fIndex;
 *     int fPaletteIndex;
 *     int fAxisCount;
 *     int fPaletteOverrideCount;
 *     skia_private::AutoSTMalloc<4, SkFixed> fAxis;
 *     skia_private::AutoSTMalloc<4, SkFontArguments::Palette::Override> fPaletteOverrides;
 * }
 * ```
 */
public data class SkFontData public constructor(
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkStreamAsset> fStream
   * ```
   */
  private var fStream: Int,
  /**
   * C++ original:
   * ```cpp
   * int fIndex
   * ```
   */
  private var fIndex: Int,
  /**
   * C++ original:
   * ```cpp
   * int fPaletteIndex
   * ```
   */
  private var fPaletteIndex: Int,
  /**
   * C++ original:
   * ```cpp
   * int fAxisCount
   * ```
   */
  private var fAxisCount: Int,
  /**
   * C++ original:
   * ```cpp
   * int fPaletteOverrideCount
   * ```
   */
  private var fPaletteOverrideCount: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::AutoSTMalloc<4, SkFixed> fAxis
   * ```
   */
  private var fAxis: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::AutoSTMalloc<4, SkFontArguments::Palette::Override> fPaletteOverrides
   * ```
   */
  private var fPaletteOverrides: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool hasStream() const { return fStream != nullptr; }
   * ```
   */
  public fun hasStream(): Boolean {
    TODO("Implement hasStream")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkStreamAsset> detachStream() { return std::move(fStream); }
   * ```
   */
  public fun detachStream(): Int {
    TODO("Implement detachStream")
  }

  /**
   * C++ original:
   * ```cpp
   * SkStreamAsset* getStream() { return fStream.get(); }
   * ```
   */
  public fun getStream(): SkStreamAsset {
    TODO("Implement getStream")
  }

  /**
   * C++ original:
   * ```cpp
   * SkStreamAsset const* getStream() const { return fStream.get(); }
   * ```
   */
  public fun getIndex(): Int {
    TODO("Implement getIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * int getIndex() const { return fIndex; }
   * ```
   */
  public fun getAxisCount(): Int {
    TODO("Implement getAxisCount")
  }

  /**
   * C++ original:
   * ```cpp
   * int getAxisCount() const { return fAxisCount; }
   * ```
   */
  public fun getAxis(): SkFixed {
    TODO("Implement getAxis")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkFixed* getAxis() const { return fAxis.get(); }
   * ```
   */
  public fun getPaletteIndex(): Int {
    TODO("Implement getPaletteIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * int getPaletteIndex() const { return fPaletteIndex; }
   * ```
   */
  public fun getPaletteOverrideCount(): Int {
    TODO("Implement getPaletteOverrideCount")
  }

  /**
   * C++ original:
   * ```cpp
   * int getPaletteOverrideCount() const { return fPaletteOverrideCount; }
   * ```
   */
  public fun getPaletteOverrides(): SkFontArguments.Palette.Override {
    TODO("Implement getPaletteOverrides")
  }
}
