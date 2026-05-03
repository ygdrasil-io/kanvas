package org.skia.tests

import org.skia.core.SkTextBlob
import org.skia.core.SkVertices
import org.skia.foundation.SkPath
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * struct DrawData {
 *     DrawData() {
 *         static constexpr int kMaskTextFontSize = 16;
 *         // A large font size can bump text to be drawn as a path.
 *         static constexpr int kPathTextFontSize = 300;
 *
 *         SkFont font(ToolUtils::DefaultPortableTypeface(), kMaskTextFontSize);
 *
 *         SkFont lcdFont(ToolUtils::DefaultPortableTypeface(), kMaskTextFontSize);
 *         lcdFont.setSubpixel(true);
 *         lcdFont.setEdging(SkFont::Edging::kSubpixelAntiAlias);
 *
 *         ToolUtils::EmojiTestSample emojiTestSample =
 *                 ToolUtils::EmojiSample(ToolUtils::EmojiFontFormat::ColrV0);
 *         SkFont emojiFont(emojiTestSample.typeface);
 *
 *         SkFont pathFont(ToolUtils::DefaultPortableTypeface(), kPathTextFontSize);
 *
 *         const char text[] = "hambur1";
 *
 *         constexpr int kNumVerts = 4;
 *         constexpr SkPoint kPositions[kNumVerts] { { 0, 0 }, { 0, 16 }, { 16, 16 }, { 16, 0 } };
 *         constexpr SkColor kColors[kNumVerts] = { SK_ColorBLUE, SK_ColorGREEN,
 *                                                  SK_ColorCYAN, SK_ColorYELLOW };
 *
 *         fPath = make_path();
 *         fBlob = SkTextBlob::MakeFromText(text, strlen(text), font);
 *         fLCDBlob = SkTextBlob::MakeFromText(text, strlen(text), lcdFont);
 *         fEmojiBlob = SkTextBlob::MakeFromText(emojiTestSample.sampleText,
 *                                               strlen(emojiTestSample.sampleText),
 *                                               emojiFont);
 *         fPathBlob = SkTextBlob::MakeFromText(text, strlen(text), pathFont);
 *
 *         fVertsWithColors = SkVertices::MakeCopy(SkVertices::kTriangleFan_VertexMode, kNumVerts,
 *                                                 kPositions, kPositions, kColors);
 *         fVertsWithOutColors = SkVertices::MakeCopy(SkVertices::kTriangleFan_VertexMode, kNumVerts,
 *                                                    kPositions, kPositions, /* colors= */ nullptr);
 *     }
 *
 *     SkPath fPath;
 *     sk_sp<SkTextBlob> fBlob;
 *     sk_sp<SkTextBlob> fLCDBlob;
 *     sk_sp<SkTextBlob> fEmojiBlob;
 *     sk_sp<SkTextBlob> fPathBlob;
 *     sk_sp<SkVertices> fVertsWithColors;
 *     sk_sp<SkVertices> fVertsWithOutColors;
 * }
 * ```
 */
public data class DrawData public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkPath fPath
   * ```
   */
  public var fPath: SkPath,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTextBlob> fBlob
   * ```
   */
  public var fBlob: SkSp<SkTextBlob>,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTextBlob> fLCDBlob
   * ```
   */
  public var fLCDBlob: SkSp<SkTextBlob>,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTextBlob> fEmojiBlob
   * ```
   */
  public var fEmojiBlob: SkSp<SkTextBlob>,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTextBlob> fPathBlob
   * ```
   */
  public var fPathBlob: SkSp<SkTextBlob>,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkVertices> fVertsWithColors
   * ```
   */
  public var fVertsWithColors: SkSp<SkVertices>,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkVertices> fVertsWithOutColors
   * ```
   */
  public var fVertsWithOutColors: SkSp<SkVertices>,
)
