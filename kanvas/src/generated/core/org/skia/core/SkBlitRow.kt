package org.skia.core

import kotlin.Array
import kotlin.Int
import kotlin.UInt
import org.skia.foundation.SkPMColor

/**
 * C++ original:
 * ```cpp
 * class SkBlitRow {
 * public:
 *     enum Flags32 {
 *         kGlobalAlpha_Flag32     = 1 << 0,
 *         kSrcPixelAlpha_Flag32   = 1 << 1
 *     };
 *
 *     /** Function pointer that blends 32bit colors onto a 32bit destination.
 *         @param dst  array of dst 32bit colors
 *         @param src  array of src 32bit colors (w/ or w/o alpha)
 *         @param count number of colors to blend
 *         @param alpha global alpha to be applied to all src colors
 *      */
 *     typedef void (*Proc32)(uint32_t dst[], const SkPMColor src[], int count, U8CPU alpha);
 *
 *     static Proc32 Factory32(unsigned flags32);
 *
 *     /** Blend a single color onto a row of S32 pixels, writing the result
 *         back to the same memory.
 *      */
 *     static void Color32(SkPMColor dst[], int count, SkPMColor color);
 * }
 * ```
 */
public open class SkBlitRow {
  public enum class Flags32 {
    kGlobalAlpha_Flag32,
    kSrcPixelAlpha_Flag32,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SkBlitRow::Proc32 SkBlitRow::Factory32(unsigned flags) {
     *     static const SkBlitRow::Proc32 kProcs[] = {
     *         blit_row_s32_opaque,
     *         blit_row_s32_blend,
     *         nullptr,  // blit_row_s32a_opaque is in SkOpts
     *         blit_row_s32a_blend
     *     };
     *
     *     SkASSERT(flags < std::size(kProcs));
     *     flags &= std::size(kProcs) - 1;  // just to be safe
     *
     *     return flags == Flags32::kSrcPixelAlpha_Flag32 ? SkOpts::blit_row_s32a_opaque : kProcs[flags];
     * }
     * ```
     */
    public fun factory32(flags32: UInt): SkBlitRowProc32 {
      TODO("Implement factory32")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkBlitRow::Color32(SkPMColor dst[], int count, SkPMColor color) {
     *     switch (SkGetPackedA32(color)) {
     *         case   0: /* Nothing to do */                  return;
     *         case 255: SkOpts::memset32(dst, color, count); return;
     *     }
     *     return SkOpts::blit_row_color32(dst, count, color);
     * }
     * ```
     */
    public fun color32(
      dst: Array<SkPMColor>,
      count: Int,
      color: SkPMColor,
    ) {
      TODO("Implement color32")
    }
  }
}
