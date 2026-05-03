package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.collections.List
import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkNoncopyable
import org.skia.foundation.SkPath
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSp
import org.skia.foundation.SkTypeface
import org.skia.math.SkSize
import undefined.GlyphCompMap

/**
 * C++ original:
 * ```cpp
 * class CustomFont final : SkNoncopyable {
 * public:
 *     ~CustomFont();
 *
 *     using GlyphCompMap = skia_private::THashMap<SkGlyphID, sk_sp<sksg::RenderNode>>;
 *
 *     class Builder final : SkNoncopyable {
 *     public:
 *         bool parseGlyph(const AnimationBuilder*, const skjson::ObjectValue&);
 *         std::unique_ptr<CustomFont> detach();
 *
 *     private:
 *         static bool ParseGlyphPath(const AnimationBuilder*, const skjson::ObjectValue&, SkPath*);
 *         static sk_sp<sksg::RenderNode> ParseGlyphComp(const AnimationBuilder*,
 *                                                       const skjson::ObjectValue&,
 *                                                       SkSize*);
 *
 *         GlyphCompMap            fGlyphComps;
 *         SkCustomTypefaceBuilder fCustomBuilder;
 *     };
 *
 *     // Helper for resolving (SkTypeface, SkGlyphID) tuples to a composition root.
 *     // Used post-shaping, to substitute composition glyphs in the rendering tree.
 *     class GlyphCompMapper final : public SkRefCnt {
 *     public:
 *         explicit GlyphCompMapper(std::vector<std::unique_ptr<CustomFont>>&& fonts)
 *             : fFonts(std::move(fonts)) {}
 *
 *         ~GlyphCompMapper() override = default;
 *
 *         sk_sp<sksg::RenderNode> getGlyphComp(const SkTypeface*, SkGlyphID) const;
 *
 *     private:
 *         const std::vector<std::unique_ptr<CustomFont>> fFonts;
 *     };
 *
 *     const sk_sp<SkTypeface>& typeface() const { return fTypeface; }
 *
 *     int glyphCompCount() const { return fGlyphComps.count(); }
 *
 * private:
 *     CustomFont(GlyphCompMap&&, sk_sp<SkTypeface> tf);
 *
 *     const GlyphCompMap      fGlyphComps;
 *     const sk_sp<SkTypeface> fTypeface;
 * }
 * ```
 */
public class CustomFont public constructor(
  glyphComps: GlyphCompMap,
  tf: SkSp<SkTypeface>,
) : SkNoncopyable() {
  /**
   * C++ original:
   * ```cpp
   * const GlyphCompMap      fGlyphComps
   * ```
   */
  private val fGlyphComps: Int = TODO("Initialize fGlyphComps")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<SkTypeface> fTypeface
   * ```
   */
  private val fTypeface: Int = TODO("Initialize fTypeface")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<SkTypeface>& typeface() const { return fTypeface; }
   * ```
   */
  private fun typeface(): Int {
    TODO("Implement typeface")
  }

  /**
   * C++ original:
   * ```cpp
   * int glyphCompCount() const { return fGlyphComps.count(); }
   * ```
   */
  private fun glyphCompCount(): Int {
    TODO("Implement glyphCompCount")
  }

  public class Builder : SkNoncopyable() {
    private var fGlyphComps: Int = TODO("Initialize fGlyphComps")

    private var fCustomBuilder: Int = TODO("Initialize fCustomBuilder")

    public fun parseGlyph(abuilder: AnimationBuilder?, jchar: ObjectValue): Boolean {
      TODO("Implement parseGlyph")
    }

    public fun detach(): Int {
      TODO("Implement detach")
    }

    public companion object {
      private fun parseGlyphPath(
        abuilder: AnimationBuilder?,
        jdata: ObjectValue,
        path: SkPath?,
      ): Boolean {
        TODO("Implement parseGlyphPath")
      }

      private fun parseGlyphComp(
        abuilder: AnimationBuilder?,
        jdata: ObjectValue,
        glyphSize: SkSize?,
      ): Int {
        TODO("Implement parseGlyphComp")
      }
    }
  }

  public class GlyphCompMapper public constructor(
    fonts: List<CustomFont?>,
  ) : SkRefCnt() {
    public fun getGlyphComp(tf: SkTypeface?, gid: SkGlyphID): Int {
      TODO("Implement getGlyphComp")
    }
  }
}
