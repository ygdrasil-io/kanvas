package org.skia.modules

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.collections.List
import org.skia.foundation.SkFontStyle
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * struct StrutStyle {
 *     StrutStyle();
 *
 *     const std::vector<SkString>& getFontFamilies() const { return fFontFamilies; }
 *     void setFontFamilies(std::vector<SkString> families) { fFontFamilies = std::move(families); }
 *
 *     SkFontStyle getFontStyle() const { return fFontStyle; }
 *     void setFontStyle(SkFontStyle fontStyle) { fFontStyle = fontStyle; }
 *
 *     SkScalar getFontSize() const { return fFontSize; }
 *     void setFontSize(SkScalar size) { fFontSize = size; }
 *
 *     void setHeight(SkScalar height) { fHeight = height; }
 *     SkScalar getHeight() const { return fHeight; }
 *
 *     void setLeading(SkScalar Leading) { fLeading = Leading; }
 *     SkScalar getLeading() const { return fLeading; }
 *
 *     bool getStrutEnabled() const { return fEnabled; }
 *     void setStrutEnabled(bool v) { fEnabled = v; }
 *
 *     bool getForceStrutHeight() const { return fForceHeight; }
 *     void setForceStrutHeight(bool v) { fForceHeight = v; }
 *
 *     bool getHeightOverride() const { return fHeightOverride; }
 *     void setHeightOverride(bool v) { fHeightOverride = v; }
 *
 *     void setHalfLeading(bool halfLeading) { fHalfLeading = halfLeading; }
 *     bool getHalfLeading() const { return fHalfLeading; }
 *
 *     bool operator==(const StrutStyle& rhs) const {
 *         return this->fEnabled == rhs.fEnabled &&
 *                this->fHeightOverride == rhs.fHeightOverride &&
 *                this->fForceHeight == rhs.fForceHeight &&
 *                this->fHalfLeading == rhs.fHalfLeading &&
 *                nearlyEqual(this->fLeading, rhs.fLeading) &&
 *                nearlyEqual(this->fHeight, rhs.fHeight) &&
 *                nearlyEqual(this->fFontSize, rhs.fFontSize) &&
 *                this->fFontStyle == rhs.fFontStyle &&
 *                this->fFontFamilies == rhs.fFontFamilies;
 *     }
 *
 * private:
 *
 *     std::vector<SkString> fFontFamilies;
 *     SkFontStyle fFontStyle;
 *     SkScalar fFontSize;
 *     SkScalar fHeight;
 *     SkScalar fLeading;
 *     bool fForceHeight;
 *     bool fEnabled;
 *     bool fHeightOverride;
 *     // true: half leading.
 *     // false: scale ascent/descent with fHeight.
 *     bool fHalfLeading;
 * }
 * ```
 */
public data class StrutStyle public constructor(
  /**
   * C++ original:
   * ```cpp
   * std::vector<SkString> fFontFamilies
   * ```
   */
  private var fFontFamilies: Int,
  /**
   * C++ original:
   * ```cpp
   * SkFontStyle fFontStyle
   * ```
   */
  private var fFontStyle: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fFontSize
   * ```
   */
  private var fFontSize: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fHeight
   * ```
   */
  private var fHeight: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fLeading
   * ```
   */
  private var fLeading: Int,
  /**
   * C++ original:
   * ```cpp
   * bool fForceHeight
   * ```
   */
  private var fForceHeight: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fEnabled
   * ```
   */
  private var fEnabled: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fHeightOverride
   * ```
   */
  private var fHeightOverride: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fHalfLeading
   * ```
   */
  private var fHalfLeading: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * const std::vector<SkString>& getFontFamilies() const { return fFontFamilies; }
   * ```
   */
  public fun getFontFamilies(): Int {
    TODO("Implement getFontFamilies")
  }

  /**
   * C++ original:
   * ```cpp
   * void setFontFamilies(std::vector<SkString> families) { fFontFamilies = std::move(families); }
   * ```
   */
  public fun setFontFamilies(families: List<String>) {
    TODO("Implement setFontFamilies")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFontStyle getFontStyle() const { return fFontStyle; }
   * ```
   */
  public fun getFontStyle(): Int {
    TODO("Implement getFontStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * void setFontStyle(SkFontStyle fontStyle) { fFontStyle = fontStyle; }
   * ```
   */
  public fun setFontStyle(fontStyle: SkFontStyle) {
    TODO("Implement setFontStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar getFontSize() const { return fFontSize; }
   * ```
   */
  public fun getFontSize(): Int {
    TODO("Implement getFontSize")
  }

  /**
   * C++ original:
   * ```cpp
   * void setFontSize(SkScalar size) { fFontSize = size; }
   * ```
   */
  public fun setFontSize(size: SkScalar) {
    TODO("Implement setFontSize")
  }

  /**
   * C++ original:
   * ```cpp
   * void setHeight(SkScalar height) { fHeight = height; }
   * ```
   */
  public fun setHeight(height: SkScalar) {
    TODO("Implement setHeight")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar getHeight() const { return fHeight; }
   * ```
   */
  public fun getHeight(): Int {
    TODO("Implement getHeight")
  }

  /**
   * C++ original:
   * ```cpp
   * void setLeading(SkScalar Leading) { fLeading = Leading; }
   * ```
   */
  public fun setLeading(leading: SkScalar) {
    TODO("Implement setLeading")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar getLeading() const { return fLeading; }
   * ```
   */
  public fun getLeading(): Int {
    TODO("Implement getLeading")
  }

  /**
   * C++ original:
   * ```cpp
   * bool getStrutEnabled() const { return fEnabled; }
   * ```
   */
  public fun getStrutEnabled(): Boolean {
    TODO("Implement getStrutEnabled")
  }

  /**
   * C++ original:
   * ```cpp
   * void setStrutEnabled(bool v) { fEnabled = v; }
   * ```
   */
  public fun setStrutEnabled(v: Boolean) {
    TODO("Implement setStrutEnabled")
  }

  /**
   * C++ original:
   * ```cpp
   * bool getForceStrutHeight() const { return fForceHeight; }
   * ```
   */
  public fun getForceStrutHeight(): Boolean {
    TODO("Implement getForceStrutHeight")
  }

  /**
   * C++ original:
   * ```cpp
   * void setForceStrutHeight(bool v) { fForceHeight = v; }
   * ```
   */
  public fun setForceStrutHeight(v: Boolean) {
    TODO("Implement setForceStrutHeight")
  }

  /**
   * C++ original:
   * ```cpp
   * bool getHeightOverride() const { return fHeightOverride; }
   * ```
   */
  public fun getHeightOverride(): Boolean {
    TODO("Implement getHeightOverride")
  }

  /**
   * C++ original:
   * ```cpp
   * void setHeightOverride(bool v) { fHeightOverride = v; }
   * ```
   */
  public fun setHeightOverride(v: Boolean) {
    TODO("Implement setHeightOverride")
  }

  /**
   * C++ original:
   * ```cpp
   * void setHalfLeading(bool halfLeading) { fHalfLeading = halfLeading; }
   * ```
   */
  public fun setHalfLeading(halfLeading: Boolean) {
    TODO("Implement setHalfLeading")
  }

  /**
   * C++ original:
   * ```cpp
   * bool getHalfLeading() const { return fHalfLeading; }
   * ```
   */
  public fun getHalfLeading(): Boolean {
    TODO("Implement getHalfLeading")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator==(const StrutStyle& rhs) const {
   *         return this->fEnabled == rhs.fEnabled &&
   *                this->fHeightOverride == rhs.fHeightOverride &&
   *                this->fForceHeight == rhs.fForceHeight &&
   *                this->fHalfLeading == rhs.fHalfLeading &&
   *                nearlyEqual(this->fLeading, rhs.fLeading) &&
   *                nearlyEqual(this->fHeight, rhs.fHeight) &&
   *                nearlyEqual(this->fFontSize, rhs.fFontSize) &&
   *                this->fFontStyle == rhs.fFontStyle &&
   *                this->fFontFamilies == rhs.fFontFamilies;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }
}
