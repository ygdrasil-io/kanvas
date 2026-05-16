package org.skia.foundation

import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.UShort

/**
 * C++ original:
 * ```cpp
 * struct SkFontParameters {
 *     struct Variation {
 *         // Parameters in a variation font axis.
 *         struct Axis {
 *             constexpr Axis() : tag(0), min(0), def(0), max(0), flags(0) {}
 *             constexpr Axis(SkFourByteTag tag, float min, float def, float max, bool hidden) :
 *                 tag(tag), min(min), def(def), max(max), flags(hidden ? HIDDEN : 0) {}
 *
 *             // Four character identifier of the font axis (weight, width, slant, italic...).
 *             SkFourByteTag tag;
 *             // Minimum value supported by this axis.
 *             float min;
 *             // Default value set by this axis.
 *             float def;
 *             // Maximum value supported by this axis. The maximum can equal the minimum.
 *             float max;
 *             // Return whether this axis is recommended to be remain hidden in user interfaces.
 *             bool isHidden() const { return flags & HIDDEN; }
 *             // Set this axis to be remain hidden in user interfaces.
 *             void setHidden(bool hidden) { flags = hidden ? (flags | HIDDEN) : (flags & ~HIDDEN); }
 *         private:
 *             static constexpr uint16_t HIDDEN = 0x0001;
 *             // Attributes for a font axis.
 *             uint16_t flags;
 *         };
 *     };
 * }
 * ```
 */
public open class SkFontParameters {
  public open class Variation {
    public data class Axis public constructor(
      public var tag: Int,
      public var min: Float,
      public var def: Float,
      public var max: Float,
      private var flags: UShort,
    ) {
      public fun isHidden(): Boolean {
        TODO("Implement isHidden")
      }

      public fun setHidden(hidden: Boolean) {
        TODO("Implement setHidden")
      }

      public companion object {
        private val hidden: UShort = TODO("Initialize hidden")
      }
    }
  }
}
