package org.skia.core

import kotlin.Boolean
import kotlin.Char
import kotlin.UInt
import org.skia.foundation.SkFont
import org.skia.foundation.SkGlyphID
import org.skia.math.SkPoint
import org.skia.math.SkRSXform
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SK_SPI SkTextBlobRunIterator {
 * public:
 *     explicit SkTextBlobRunIterator(const SkTextBlob* blob);
 *
 *     enum GlyphPositioning : uint8_t {
 *         kDefault_Positioning      = 0, // Default glyph advances -- zero scalars per glyph.
 *         kHorizontal_Positioning   = 1, // Horizontal positioning -- one scalar per glyph.
 *         kFull_Positioning         = 2, // Point positioning -- two scalars per glyph.
 *         kRSXform_Positioning      = 3, // RSXform positioning -- four scalars per glyph.
 *     };
 *
 *     bool done() const {
 *         return !fCurrentRun;
 *     }
 *     void next();
 *
 *     uint32_t glyphCount() const {
 *         SkASSERT(!this->done());
 *         return fCurrentRun->glyphCount();
 *     }
 *     const SkGlyphID* glyphs() const {
 *         SkASSERT(!this->done());
 *         return fCurrentRun->glyphBuffer();
 *     }
 *     const SkScalar* pos() const {
 *         SkASSERT(!this->done());
 *         return fCurrentRun->posBuffer();
 *     }
 *     // alias for pos()
 *     const SkPoint* points() const {
 *         return fCurrentRun->pointBuffer();
 *     }
 *     // alias for pos()
 *     const SkRSXform* xforms() const {
 *         return fCurrentRun->xformBuffer();
 *     }
 *     const SkPoint& offset() const {
 *         SkASSERT(!this->done());
 *         return fCurrentRun->offset();
 *     }
 *     const SkFont& font() const {
 *         SkASSERT(!this->done());
 *         return fCurrentRun->font();
 *     }
 *     GlyphPositioning positioning() const;
 *     unsigned scalarsPerGlyph() const;
 *     uint32_t* clusters() const {
 *         SkASSERT(!this->done());
 *         return fCurrentRun->clusterBuffer();
 *     }
 *     uint32_t textSize() const {
 *         SkASSERT(!this->done());
 *         return fCurrentRun->textSize();
 *     }
 *     char* text() const {
 *         SkASSERT(!this->done());
 *         return fCurrentRun->textBuffer();
 *     }
 *
 *     bool isLCD() const;
 *
 * private:
 *     const SkTextBlob::RunRecord* fCurrentRun;
 *
 *     SkDEBUGCODE(const uint8_t* fStorageTop;)
 * }
 * ```
 */
public data class SkTextBlobRunIterator public constructor(
  /**
   * C++ original:
   * ```cpp
   * const SkTextBlob::RunRecord* fCurrentRun
   * ```
   */
  private val fCurrentRun: RunRecord?,
) {
  /**
   * C++ original:
   * ```cpp
   * bool done() const {
   *         return !fCurrentRun;
   *     }
   * ```
   */
  public fun done(): Boolean {
    TODO("Implement done")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTextBlobRunIterator::next() {
   *     SkASSERT(!this->done());
   *
   *     if (!this->done()) {
   *         SkDEBUGCODE(fCurrentRun->validate(fStorageTop);)
   *         fCurrentRun = SkTextBlob::RunRecord::Next(fCurrentRun);
   *     }
   * }
   * ```
   */
  public fun next() {
    TODO("Implement next")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t glyphCount() const {
   *         SkASSERT(!this->done());
   *         return fCurrentRun->glyphCount();
   *     }
   * ```
   */
  public fun glyphCount(): UInt {
    TODO("Implement glyphCount")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkGlyphID* glyphs() const {
   *         SkASSERT(!this->done());
   *         return fCurrentRun->glyphBuffer();
   *     }
   * ```
   */
  public fun glyphs(): SkGlyphID {
    TODO("Implement glyphs")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkScalar* pos() const {
   *         SkASSERT(!this->done());
   *         return fCurrentRun->posBuffer();
   *     }
   * ```
   */
  public fun pos(): SkScalar {
    TODO("Implement pos")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPoint* points() const {
   *         return fCurrentRun->pointBuffer();
   *     }
   * ```
   */
  public fun points(): SkPoint {
    TODO("Implement points")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkRSXform* xforms() const {
   *         return fCurrentRun->xformBuffer();
   *     }
   * ```
   */
  public fun xforms(): SkRSXform {
    TODO("Implement xforms")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPoint& offset() const {
   *         SkASSERT(!this->done());
   *         return fCurrentRun->offset();
   *     }
   * ```
   */
  public fun offset(): SkPoint {
    TODO("Implement offset")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkFont& font() const {
   *         SkASSERT(!this->done());
   *         return fCurrentRun->font();
   *     }
   * ```
   */
  public fun font(): SkFont {
    TODO("Implement font")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTextBlobRunIterator::GlyphPositioning SkTextBlobRunIterator::positioning() const {
   *     SkASSERT(!this->done());
   *     static_assert(static_cast<GlyphPositioning>(SkTextBlob::kDefault_Positioning) ==
   *                   kDefault_Positioning, "");
   *     static_assert(static_cast<GlyphPositioning>(SkTextBlob::kHorizontal_Positioning) ==
   *                   kHorizontal_Positioning, "");
   *     static_assert(static_cast<GlyphPositioning>(SkTextBlob::kFull_Positioning) ==
   *                   kFull_Positioning, "");
   *     static_assert(static_cast<GlyphPositioning>(SkTextBlob::kRSXform_Positioning) ==
   *                   kRSXform_Positioning, "");
   *
   *     return SkTo<GlyphPositioning>(fCurrentRun->positioning());
   * }
   * ```
   */
  public fun positioning(): GlyphPositioning {
    TODO("Implement positioning")
  }

  /**
   * C++ original:
   * ```cpp
   * unsigned SkTextBlobRunIterator::scalarsPerGlyph() const {
   *     return SkTextBlob::ScalarsPerGlyph(fCurrentRun->positioning());
   * }
   * ```
   */
  public fun scalarsPerGlyph(): UInt {
    TODO("Implement scalarsPerGlyph")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t* clusters() const {
   *         SkASSERT(!this->done());
   *         return fCurrentRun->clusterBuffer();
   *     }
   * ```
   */
  public fun clusters(): UInt {
    TODO("Implement clusters")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t textSize() const {
   *         SkASSERT(!this->done());
   *         return fCurrentRun->textSize();
   *     }
   * ```
   */
  public fun textSize(): UInt {
    TODO("Implement textSize")
  }

  /**
   * C++ original:
   * ```cpp
   * char* text() const {
   *         SkASSERT(!this->done());
   *         return fCurrentRun->textBuffer();
   *     }
   * ```
   */
  public fun text(): Char {
    TODO("Implement text")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTextBlobRunIterator::isLCD() const {
   *     return fCurrentRun->font().getEdging() == SkFont::Edging::kSubpixelAntiAlias;
   * }
   * ```
   */
  public fun isLCD(): Boolean {
    TODO("Implement isLCD")
  }

  public enum class GlyphPositioning {
    kDefault_Positioning,
    kHorizontal_Positioning,
    kFull_Positioning,
    kRSXform_Positioning,
  }
}
