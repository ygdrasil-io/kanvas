package org.skia.tests

import kotlin.Array
import kotlin.Int
import kotlin.UInt
import org.skia.core.SkTextBlobBuilder
import org.skia.core.SkTextBlobRunIterator
import org.skia.foundation.SkFont
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class TextBlobTester {
 * public:
 *     // This unit test feeds an SkTextBlobBuilder various runs then checks to see if
 *     // the result contains the provided data and merges runs when appropriate.
 *     static void TestBuilder(skiatest::Reporter* reporter) {
 *         SkTextBlobBuilder builder;
 *
 *         // empty run set
 *         RunBuilderTest(reporter, builder, nullptr, 0, nullptr, 0);
 *
 *         RunDef set1[] = {
 *             { 128, SkTextBlobRunIterator::kDefault_Positioning, 100, 100 },
 *         };
 *         RunBuilderTest(reporter, builder, set1, std::size(set1), set1, std::size(set1));
 *
 *         RunDef set2[] = {
 *             { 128, SkTextBlobRunIterator::kHorizontal_Positioning, 100, 100 },
 *         };
 *         RunBuilderTest(reporter, builder, set2, std::size(set2), set2, std::size(set2));
 *
 *         RunDef set3[] = {
 *             { 128, SkTextBlobRunIterator::kFull_Positioning, 100, 100 },
 *         };
 *         RunBuilderTest(reporter, builder, set3, std::size(set3), set3, std::size(set3));
 *
 *         RunDef set4[] = {
 *             { 128, SkTextBlobRunIterator::kDefault_Positioning, 100, 150 },
 *             { 128, SkTextBlobRunIterator::kDefault_Positioning, 100, 150 },
 *             { 128, SkTextBlobRunIterator::kDefault_Positioning, 100, 150 },
 *         };
 *         RunBuilderTest(reporter, builder, set4, std::size(set4), set4, std::size(set4));
 *
 *         RunDef set5[] = {
 *             { 128, SkTextBlobRunIterator::kHorizontal_Positioning, 100, 150 },
 *             { 128, SkTextBlobRunIterator::kHorizontal_Positioning, 200, 150 },
 *             { 128, SkTextBlobRunIterator::kHorizontal_Positioning, 300, 250 },
 *         };
 *         RunDef mergedSet5[] = {
 *             { 256, SkTextBlobRunIterator::kHorizontal_Positioning, 0, 150 },
 *             { 128, SkTextBlobRunIterator::kHorizontal_Positioning, 0, 250 },
 *         };
 *         RunBuilderTest(reporter, builder, set5, std::size(set5), mergedSet5,
 *                        std::size(mergedSet5));
 *
 *         RunDef set6[] = {
 *             { 128, SkTextBlobRunIterator::kFull_Positioning, 100, 100 },
 *             { 128, SkTextBlobRunIterator::kFull_Positioning, 200, 200 },
 *             { 128, SkTextBlobRunIterator::kFull_Positioning, 300, 300 },
 *         };
 *         RunDef mergedSet6[] = {
 *             { 384, SkTextBlobRunIterator::kFull_Positioning, 0, 0 },
 *         };
 *         RunBuilderTest(reporter, builder, set6, std::size(set6), mergedSet6,
 *                        std::size(mergedSet6));
 *
 *         RunDef set7[] = {
 *             { 128, SkTextBlobRunIterator::kDefault_Positioning, 100, 150 },
 *             { 128, SkTextBlobRunIterator::kDefault_Positioning, 100, 150 },
 *             { 128, SkTextBlobRunIterator::kHorizontal_Positioning, 100, 150 },
 *             { 128, SkTextBlobRunIterator::kHorizontal_Positioning, 200, 150 },
 *             { 128, SkTextBlobRunIterator::kFull_Positioning, 400, 350 },
 *             { 128, SkTextBlobRunIterator::kFull_Positioning, 400, 350 },
 *             { 128, SkTextBlobRunIterator::kDefault_Positioning, 100, 450 },
 *             { 128, SkTextBlobRunIterator::kDefault_Positioning, 100, 450 },
 *             { 128, SkTextBlobRunIterator::kHorizontal_Positioning, 100, 550 },
 *             { 128, SkTextBlobRunIterator::kHorizontal_Positioning, 200, 650 },
 *             { 128, SkTextBlobRunIterator::kFull_Positioning, 400, 750 },
 *             { 128, SkTextBlobRunIterator::kFull_Positioning, 400, 850 },
 *         };
 *         RunDef mergedSet7[] = {
 *             { 128, SkTextBlobRunIterator::kDefault_Positioning, 100, 150 },
 *             { 128, SkTextBlobRunIterator::kDefault_Positioning, 100, 150 },
 *             { 256, SkTextBlobRunIterator::kHorizontal_Positioning, 0, 150 },
 *             { 256, SkTextBlobRunIterator::kFull_Positioning, 0, 0 },
 *             { 128, SkTextBlobRunIterator::kDefault_Positioning, 100, 450 },
 *             { 128, SkTextBlobRunIterator::kDefault_Positioning, 100, 450 },
 *             { 128, SkTextBlobRunIterator::kHorizontal_Positioning, 0, 550 },
 *             { 128, SkTextBlobRunIterator::kHorizontal_Positioning, 0, 650 },
 *             { 256, SkTextBlobRunIterator::kFull_Positioning, 0, 0 },
 *         };
 *         RunBuilderTest(reporter, builder, set7, std::size(set7), mergedSet7,
 *                        std::size(mergedSet7));
 *     }
 *
 *     // This unit test verifies blob bounds computation.
 *     static void TestBounds(skiatest::Reporter* reporter) {
 *         SkTextBlobBuilder builder;
 *         SkFont font = ToolUtils::DefaultFont();
 *
 *         // Explicit bounds.
 *         {
 *             sk_sp<SkTextBlob> blob(builder.make());
 *             REPORTER_ASSERT(reporter, !blob);
 *         }
 *
 *         {
 *             SkRect r1 = SkRect::MakeXYWH(10, 10, 20, 20);
 *             builder.allocRun(font, 16, 0, 0, &r1);
 *             sk_sp<SkTextBlob> blob(builder.make());
 *             REPORTER_ASSERT(reporter, blob->bounds() == r1);
 *         }
 *
 *         {
 *             SkRect r1 = SkRect::MakeXYWH(10, 10, 20, 20);
 *             builder.allocRunPosH(font, 16, 0, &r1);
 *             sk_sp<SkTextBlob> blob(builder.make());
 *             REPORTER_ASSERT(reporter, blob->bounds() == r1);
 *         }
 *
 *         {
 *             SkRect r1 = SkRect::MakeXYWH(10, 10, 20, 20);
 *             builder.allocRunPos(font, 16, &r1);
 *             sk_sp<SkTextBlob> blob(builder.make());
 *             REPORTER_ASSERT(reporter, blob->bounds() == r1);
 *         }
 *
 *         {
 *             SkRect r1 = SkRect::MakeXYWH(10, 10, 20, 20);
 *             SkRect r2 = SkRect::MakeXYWH(15, 20, 50, 50);
 *             SkRect r3 = SkRect::MakeXYWH(0, 5, 10, 5);
 *
 *             builder.allocRun(font, 16, 0, 0, &r1);
 *             builder.allocRunPosH(font, 16, 0, &r2);
 *             builder.allocRunPos(font, 16, &r3);
 *
 *             sk_sp<SkTextBlob> blob(builder.make());
 *             REPORTER_ASSERT(reporter, blob->bounds() == SkRect::MakeXYWH(0, 5, 65, 65));
 *         }
 *
 *         {
 *             sk_sp<SkTextBlob> blob(builder.make());
 *             REPORTER_ASSERT(reporter, !blob);
 *         }
 *
 *         // Implicit bounds
 *
 *         {
 *             // Exercise the empty bounds path, and ensure that RunRecord-aligned pos buffers
 *             // don't trigger asserts (http://crbug.com/542643).
 *             font.setSize(0);
 *
 *             const char* txt = "BOOO";
 *             const size_t txtLen = strlen(txt);
 *             const size_t glyphCount = font.countText(txt, txtLen, SkTextEncoding::kUTF8);
 *             const SkTextBlobBuilder::RunBuffer& buffer = builder.allocRunPos(font, glyphCount);
 *
 *             font.textToGlyphs(txt, txtLen, SkTextEncoding::kUTF8, {buffer.glyphs, glyphCount});
 *
 *             memset(buffer.pos, 0, sizeof(SkScalar) * glyphCount * 2);
 *             sk_sp<SkTextBlob> blob(builder.make());
 *             REPORTER_ASSERT(reporter, blob->bounds().isEmpty());
 *         }
 *     }
 *
 *     // Verify that text-related properties are captured in run paints.
 *     static void TestPaintProps(skiatest::Reporter* reporter) {
 *         SkFont font;
 *         // Kitchen sink font.
 *         font.setSize(42);
 *         font.setScaleX(4.2f);
 *         font.setTypeface(ToolUtils::CreatePortableTypeface("Sans", SkFontStyle::Bold()));
 *         font.setSkewX(0.42f);
 *         font.setHinting(SkFontHinting::kFull);
 *         font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
 *         font.setEmbolden(true);
 *         font.setLinearMetrics(true);
 *         font.setSubpixel(true);
 *         font.setEmbeddedBitmaps(true);
 *         font.setForceAutoHinting(true);
 *
 *         // Ensure we didn't pick default values by mistake.
 *         SkFont defaultFont = ToolUtils::DefaultFont();
 *         REPORTER_ASSERT(reporter, defaultFont.getSize() != font.getSize());
 *         REPORTER_ASSERT(reporter, defaultFont.getScaleX() != font.getScaleX());
 *         REPORTER_ASSERT(reporter, defaultFont.getTypeface() != font.getTypeface());
 *         REPORTER_ASSERT(reporter, defaultFont.getSkewX() != font.getSkewX());
 *         REPORTER_ASSERT(reporter, defaultFont.getHinting() != font.getHinting());
 *         REPORTER_ASSERT(reporter, defaultFont.getEdging() != font.getEdging());
 *         REPORTER_ASSERT(reporter, defaultFont.isEmbolden() != font.isEmbolden());
 *         REPORTER_ASSERT(reporter, defaultFont.isLinearMetrics() != font.isLinearMetrics());
 *         REPORTER_ASSERT(reporter, defaultFont.isSubpixel() != font.isSubpixel());
 *         REPORTER_ASSERT(reporter,
 *                         defaultFont.isEmbeddedBitmaps() != font.isEmbeddedBitmaps());
 *         REPORTER_ASSERT(reporter, defaultFont.isForceAutoHinting() != font.isForceAutoHinting());
 *
 *         SkTextBlobBuilder builder;
 *         AddRun(font, 1, SkTextBlobRunIterator::kDefault_Positioning, SkPoint::Make(0, 0), builder);
 *         AddRun(font, 1, SkTextBlobRunIterator::kHorizontal_Positioning, SkPoint::Make(0, 0),
 *                builder);
 *         AddRun(font, 1, SkTextBlobRunIterator::kFull_Positioning, SkPoint::Make(0, 0), builder);
 *         sk_sp<SkTextBlob> blob(builder.make());
 *
 *         SkTextBlobRunIterator it(blob.get());
 *         while (!it.done()) {
 *             REPORTER_ASSERT(reporter, it.font() == font);
 *             it.next();
 *         }
 *
 *     }
 *
 * private:
 *     struct RunDef {
 *         unsigned                                count;
 *         SkTextBlobRunIterator::GlyphPositioning pos;
 *         SkScalar                                x, y;
 *     };
 *
 *     static void RunBuilderTest(skiatest::Reporter* reporter, SkTextBlobBuilder& builder,
 *                                const RunDef in[], unsigned inCount,
 *                                const RunDef out[], unsigned outCount) {
 *         SkFont font = ToolUtils::DefaultFont();
 *
 *         for (unsigned i = 0; i < inCount; ++i) {
 *             AddRun(font, in[i].count, in[i].pos, SkPoint::Make(in[i].x, in[i].y), builder);
 *         }
 *
 *         sk_sp<SkTextBlob> blob(builder.make());
 *         REPORTER_ASSERT(reporter, (inCount > 0) == SkToBool(blob));
 *         if (!blob) {
 *             return;
 *         }
 *
 *         SkTextBlobRunIterator it(blob.get());
 *         for (unsigned i = 0; i < outCount; ++i) {
 *             REPORTER_ASSERT(reporter, !it.done());
 *             REPORTER_ASSERT(reporter, out[i].pos == it.positioning());
 *             REPORTER_ASSERT(reporter, out[i].count == it.glyphCount());
 *             if (SkTextBlobRunIterator::kDefault_Positioning == out[i].pos) {
 *                 REPORTER_ASSERT(reporter, out[i].x == it.offset().x());
 *                 REPORTER_ASSERT(reporter, out[i].y == it.offset().y());
 *             } else if (SkTextBlobRunIterator::kHorizontal_Positioning == out[i].pos) {
 *                 REPORTER_ASSERT(reporter, out[i].y == it.offset().y());
 *             }
 *
 *             for (unsigned k = 0; k < it.glyphCount(); ++k) {
 *                 REPORTER_ASSERT(reporter, k % 128 == it.glyphs()[k]);
 *                 if (SkTextBlobRunIterator::kHorizontal_Positioning == it.positioning()) {
 *                     REPORTER_ASSERT(reporter, SkIntToScalar(k % 128) == it.pos()[k]);
 *                 } else if (SkTextBlobRunIterator::kFull_Positioning == it.positioning()) {
 *                     REPORTER_ASSERT(reporter, SkIntToScalar(k % 128) == it.pos()[k * 2]);
 *                     REPORTER_ASSERT(reporter, -SkIntToScalar(k % 128) == it.pos()[k * 2 + 1]);
 *                 }
 *             }
 *
 *             it.next();
 *         }
 *
 *         REPORTER_ASSERT(reporter, it.done());
 *     }
 *
 *     static void AddRun(const SkFont& font, int count, SkTextBlobRunIterator::GlyphPositioning pos,
 *                        const SkPoint& offset, SkTextBlobBuilder& builder,
 *                        const SkRect* bounds = nullptr) {
 *         switch (pos) {
 *         case SkTextBlobRunIterator::kDefault_Positioning: {
 *             const SkTextBlobBuilder::RunBuffer& rb = builder.allocRun(font, count, offset.x(),
 *                                                                       offset.y(), bounds);
 *             for (int i = 0; i < count; ++i) {
 *                 rb.glyphs[i] = i;
 *             }
 *         } break;
 *         case SkTextBlobRunIterator::kHorizontal_Positioning: {
 *             const SkTextBlobBuilder::RunBuffer& rb = builder.allocRunPosH(font, count, offset.y(),
 *                                                                           bounds);
 *             for (int i = 0; i < count; ++i) {
 *                 rb.glyphs[i] = i;
 *                 rb.pos[i] = SkIntToScalar(i);
 *             }
 *         } break;
 *         case SkTextBlobRunIterator::kFull_Positioning: {
 *             const SkTextBlobBuilder::RunBuffer& rb = builder.allocRunPos(font, count, bounds);
 *             for (int i = 0; i < count; ++i) {
 *                 rb.glyphs[i] = i;
 *                 rb.pos[i * 2] = SkIntToScalar(i);
 *                 rb.pos[i * 2 + 1] = -SkIntToScalar(i);
 *             }
 *         } break;
 *         default:
 *             SK_ABORT("unhandled positioning value");
 *         }
 *     }
 * }
 * ```
 */
public open class TextBlobTester {
  public data class RunDef public constructor(
    public var count: UInt,
    public var pos: SkTextBlobRunIterator.GlyphPositioning,
    public var x: SkScalar,
    public var y: SkScalar,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static void TestBuilder(skiatest::Reporter* reporter) {
     *         SkTextBlobBuilder builder;
     *
     *         // empty run set
     *         RunBuilderTest(reporter, builder, nullptr, 0, nullptr, 0);
     *
     *         RunDef set1[] = {
     *             { 128, SkTextBlobRunIterator::kDefault_Positioning, 100, 100 },
     *         };
     *         RunBuilderTest(reporter, builder, set1, std::size(set1), set1, std::size(set1));
     *
     *         RunDef set2[] = {
     *             { 128, SkTextBlobRunIterator::kHorizontal_Positioning, 100, 100 },
     *         };
     *         RunBuilderTest(reporter, builder, set2, std::size(set2), set2, std::size(set2));
     *
     *         RunDef set3[] = {
     *             { 128, SkTextBlobRunIterator::kFull_Positioning, 100, 100 },
     *         };
     *         RunBuilderTest(reporter, builder, set3, std::size(set3), set3, std::size(set3));
     *
     *         RunDef set4[] = {
     *             { 128, SkTextBlobRunIterator::kDefault_Positioning, 100, 150 },
     *             { 128, SkTextBlobRunIterator::kDefault_Positioning, 100, 150 },
     *             { 128, SkTextBlobRunIterator::kDefault_Positioning, 100, 150 },
     *         };
     *         RunBuilderTest(reporter, builder, set4, std::size(set4), set4, std::size(set4));
     *
     *         RunDef set5[] = {
     *             { 128, SkTextBlobRunIterator::kHorizontal_Positioning, 100, 150 },
     *             { 128, SkTextBlobRunIterator::kHorizontal_Positioning, 200, 150 },
     *             { 128, SkTextBlobRunIterator::kHorizontal_Positioning, 300, 250 },
     *         };
     *         RunDef mergedSet5[] = {
     *             { 256, SkTextBlobRunIterator::kHorizontal_Positioning, 0, 150 },
     *             { 128, SkTextBlobRunIterator::kHorizontal_Positioning, 0, 250 },
     *         };
     *         RunBuilderTest(reporter, builder, set5, std::size(set5), mergedSet5,
     *                        std::size(mergedSet5));
     *
     *         RunDef set6[] = {
     *             { 128, SkTextBlobRunIterator::kFull_Positioning, 100, 100 },
     *             { 128, SkTextBlobRunIterator::kFull_Positioning, 200, 200 },
     *             { 128, SkTextBlobRunIterator::kFull_Positioning, 300, 300 },
     *         };
     *         RunDef mergedSet6[] = {
     *             { 384, SkTextBlobRunIterator::kFull_Positioning, 0, 0 },
     *         };
     *         RunBuilderTest(reporter, builder, set6, std::size(set6), mergedSet6,
     *                        std::size(mergedSet6));
     *
     *         RunDef set7[] = {
     *             { 128, SkTextBlobRunIterator::kDefault_Positioning, 100, 150 },
     *             { 128, SkTextBlobRunIterator::kDefault_Positioning, 100, 150 },
     *             { 128, SkTextBlobRunIterator::kHorizontal_Positioning, 100, 150 },
     *             { 128, SkTextBlobRunIterator::kHorizontal_Positioning, 200, 150 },
     *             { 128, SkTextBlobRunIterator::kFull_Positioning, 400, 350 },
     *             { 128, SkTextBlobRunIterator::kFull_Positioning, 400, 350 },
     *             { 128, SkTextBlobRunIterator::kDefault_Positioning, 100, 450 },
     *             { 128, SkTextBlobRunIterator::kDefault_Positioning, 100, 450 },
     *             { 128, SkTextBlobRunIterator::kHorizontal_Positioning, 100, 550 },
     *             { 128, SkTextBlobRunIterator::kHorizontal_Positioning, 200, 650 },
     *             { 128, SkTextBlobRunIterator::kFull_Positioning, 400, 750 },
     *             { 128, SkTextBlobRunIterator::kFull_Positioning, 400, 850 },
     *         };
     *         RunDef mergedSet7[] = {
     *             { 128, SkTextBlobRunIterator::kDefault_Positioning, 100, 150 },
     *             { 128, SkTextBlobRunIterator::kDefault_Positioning, 100, 150 },
     *             { 256, SkTextBlobRunIterator::kHorizontal_Positioning, 0, 150 },
     *             { 256, SkTextBlobRunIterator::kFull_Positioning, 0, 0 },
     *             { 128, SkTextBlobRunIterator::kDefault_Positioning, 100, 450 },
     *             { 128, SkTextBlobRunIterator::kDefault_Positioning, 100, 450 },
     *             { 128, SkTextBlobRunIterator::kHorizontal_Positioning, 0, 550 },
     *             { 128, SkTextBlobRunIterator::kHorizontal_Positioning, 0, 650 },
     *             { 256, SkTextBlobRunIterator::kFull_Positioning, 0, 0 },
     *         };
     *         RunBuilderTest(reporter, builder, set7, std::size(set7), mergedSet7,
     *                        std::size(mergedSet7));
     *     }
     * ```
     */
    public fun testBuilder(reporter: Reporter?) {
      TODO("Implement testBuilder")
    }

    /**
     * C++ original:
     * ```cpp
     * static void TestBounds(skiatest::Reporter* reporter) {
     *         SkTextBlobBuilder builder;
     *         SkFont font = ToolUtils::DefaultFont();
     *
     *         // Explicit bounds.
     *         {
     *             sk_sp<SkTextBlob> blob(builder.make());
     *             REPORTER_ASSERT(reporter, !blob);
     *         }
     *
     *         {
     *             SkRect r1 = SkRect::MakeXYWH(10, 10, 20, 20);
     *             builder.allocRun(font, 16, 0, 0, &r1);
     *             sk_sp<SkTextBlob> blob(builder.make());
     *             REPORTER_ASSERT(reporter, blob->bounds() == r1);
     *         }
     *
     *         {
     *             SkRect r1 = SkRect::MakeXYWH(10, 10, 20, 20);
     *             builder.allocRunPosH(font, 16, 0, &r1);
     *             sk_sp<SkTextBlob> blob(builder.make());
     *             REPORTER_ASSERT(reporter, blob->bounds() == r1);
     *         }
     *
     *         {
     *             SkRect r1 = SkRect::MakeXYWH(10, 10, 20, 20);
     *             builder.allocRunPos(font, 16, &r1);
     *             sk_sp<SkTextBlob> blob(builder.make());
     *             REPORTER_ASSERT(reporter, blob->bounds() == r1);
     *         }
     *
     *         {
     *             SkRect r1 = SkRect::MakeXYWH(10, 10, 20, 20);
     *             SkRect r2 = SkRect::MakeXYWH(15, 20, 50, 50);
     *             SkRect r3 = SkRect::MakeXYWH(0, 5, 10, 5);
     *
     *             builder.allocRun(font, 16, 0, 0, &r1);
     *             builder.allocRunPosH(font, 16, 0, &r2);
     *             builder.allocRunPos(font, 16, &r3);
     *
     *             sk_sp<SkTextBlob> blob(builder.make());
     *             REPORTER_ASSERT(reporter, blob->bounds() == SkRect::MakeXYWH(0, 5, 65, 65));
     *         }
     *
     *         {
     *             sk_sp<SkTextBlob> blob(builder.make());
     *             REPORTER_ASSERT(reporter, !blob);
     *         }
     *
     *         // Implicit bounds
     *
     *         {
     *             // Exercise the empty bounds path, and ensure that RunRecord-aligned pos buffers
     *             // don't trigger asserts (http://crbug.com/542643).
     *             font.setSize(0);
     *
     *             const char* txt = "BOOO";
     *             const size_t txtLen = strlen(txt);
     *             const size_t glyphCount = font.countText(txt, txtLen, SkTextEncoding::kUTF8);
     *             const SkTextBlobBuilder::RunBuffer& buffer = builder.allocRunPos(font, glyphCount);
     *
     *             font.textToGlyphs(txt, txtLen, SkTextEncoding::kUTF8, {buffer.glyphs, glyphCount});
     *
     *             memset(buffer.pos, 0, sizeof(SkScalar) * glyphCount * 2);
     *             sk_sp<SkTextBlob> blob(builder.make());
     *             REPORTER_ASSERT(reporter, blob->bounds().isEmpty());
     *         }
     *     }
     * ```
     */
    public fun testBounds(reporter: Reporter?) {
      TODO("Implement testBounds")
    }

    /**
     * C++ original:
     * ```cpp
     * static void TestPaintProps(skiatest::Reporter* reporter) {
     *         SkFont font;
     *         // Kitchen sink font.
     *         font.setSize(42);
     *         font.setScaleX(4.2f);
     *         font.setTypeface(ToolUtils::CreatePortableTypeface("Sans", SkFontStyle::Bold()));
     *         font.setSkewX(0.42f);
     *         font.setHinting(SkFontHinting::kFull);
     *         font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
     *         font.setEmbolden(true);
     *         font.setLinearMetrics(true);
     *         font.setSubpixel(true);
     *         font.setEmbeddedBitmaps(true);
     *         font.setForceAutoHinting(true);
     *
     *         // Ensure we didn't pick default values by mistake.
     *         SkFont defaultFont = ToolUtils::DefaultFont();
     *         REPORTER_ASSERT(reporter, defaultFont.getSize() != font.getSize());
     *         REPORTER_ASSERT(reporter, defaultFont.getScaleX() != font.getScaleX());
     *         REPORTER_ASSERT(reporter, defaultFont.getTypeface() != font.getTypeface());
     *         REPORTER_ASSERT(reporter, defaultFont.getSkewX() != font.getSkewX());
     *         REPORTER_ASSERT(reporter, defaultFont.getHinting() != font.getHinting());
     *         REPORTER_ASSERT(reporter, defaultFont.getEdging() != font.getEdging());
     *         REPORTER_ASSERT(reporter, defaultFont.isEmbolden() != font.isEmbolden());
     *         REPORTER_ASSERT(reporter, defaultFont.isLinearMetrics() != font.isLinearMetrics());
     *         REPORTER_ASSERT(reporter, defaultFont.isSubpixel() != font.isSubpixel());
     *         REPORTER_ASSERT(reporter,
     *                         defaultFont.isEmbeddedBitmaps() != font.isEmbeddedBitmaps());
     *         REPORTER_ASSERT(reporter, defaultFont.isForceAutoHinting() != font.isForceAutoHinting());
     *
     *         SkTextBlobBuilder builder;
     *         AddRun(font, 1, SkTextBlobRunIterator::kDefault_Positioning, SkPoint::Make(0, 0), builder);
     *         AddRun(font, 1, SkTextBlobRunIterator::kHorizontal_Positioning, SkPoint::Make(0, 0),
     *                builder);
     *         AddRun(font, 1, SkTextBlobRunIterator::kFull_Positioning, SkPoint::Make(0, 0), builder);
     *         sk_sp<SkTextBlob> blob(builder.make());
     *
     *         SkTextBlobRunIterator it(blob.get());
     *         while (!it.done()) {
     *             REPORTER_ASSERT(reporter, it.font() == font);
     *             it.next();
     *         }
     *
     *     }
     * ```
     */
    public fun testPaintProps(reporter: Reporter?) {
      TODO("Implement testPaintProps")
    }

    /**
     * C++ original:
     * ```cpp
     * static void RunBuilderTest(skiatest::Reporter* reporter, SkTextBlobBuilder& builder,
     *                                const RunDef in[], unsigned inCount,
     *                                const RunDef out[], unsigned outCount) {
     *         SkFont font = ToolUtils::DefaultFont();
     *
     *         for (unsigned i = 0; i < inCount; ++i) {
     *             AddRun(font, in[i].count, in[i].pos, SkPoint::Make(in[i].x, in[i].y), builder);
     *         }
     *
     *         sk_sp<SkTextBlob> blob(builder.make());
     *         REPORTER_ASSERT(reporter, (inCount > 0) == SkToBool(blob));
     *         if (!blob) {
     *             return;
     *         }
     *
     *         SkTextBlobRunIterator it(blob.get());
     *         for (unsigned i = 0; i < outCount; ++i) {
     *             REPORTER_ASSERT(reporter, !it.done());
     *             REPORTER_ASSERT(reporter, out[i].pos == it.positioning());
     *             REPORTER_ASSERT(reporter, out[i].count == it.glyphCount());
     *             if (SkTextBlobRunIterator::kDefault_Positioning == out[i].pos) {
     *                 REPORTER_ASSERT(reporter, out[i].x == it.offset().x());
     *                 REPORTER_ASSERT(reporter, out[i].y == it.offset().y());
     *             } else if (SkTextBlobRunIterator::kHorizontal_Positioning == out[i].pos) {
     *                 REPORTER_ASSERT(reporter, out[i].y == it.offset().y());
     *             }
     *
     *             for (unsigned k = 0; k < it.glyphCount(); ++k) {
     *                 REPORTER_ASSERT(reporter, k % 128 == it.glyphs()[k]);
     *                 if (SkTextBlobRunIterator::kHorizontal_Positioning == it.positioning()) {
     *                     REPORTER_ASSERT(reporter, SkIntToScalar(k % 128) == it.pos()[k]);
     *                 } else if (SkTextBlobRunIterator::kFull_Positioning == it.positioning()) {
     *                     REPORTER_ASSERT(reporter, SkIntToScalar(k % 128) == it.pos()[k * 2]);
     *                     REPORTER_ASSERT(reporter, -SkIntToScalar(k % 128) == it.pos()[k * 2 + 1]);
     *                 }
     *             }
     *
     *             it.next();
     *         }
     *
     *         REPORTER_ASSERT(reporter, it.done());
     *     }
     * ```
     */
    private fun runBuilderTest(
      reporter: Reporter?,
      builder: SkTextBlobBuilder,
      `in`: Array<RunDef>,
      inCount: UInt,
      `out`: Array<RunDef>,
      outCount: UInt,
    ) {
      TODO("Implement runBuilderTest")
    }

    /**
     * C++ original:
     * ```cpp
     * static void AddRun(const SkFont& font, int count, SkTextBlobRunIterator::GlyphPositioning pos,
     *                        const SkPoint& offset, SkTextBlobBuilder& builder,
     *                        const SkRect* bounds = nullptr) {
     *         switch (pos) {
     *         case SkTextBlobRunIterator::kDefault_Positioning: {
     *             const SkTextBlobBuilder::RunBuffer& rb = builder.allocRun(font, count, offset.x(),
     *                                                                       offset.y(), bounds);
     *             for (int i = 0; i < count; ++i) {
     *                 rb.glyphs[i] = i;
     *             }
     *         } break;
     *         case SkTextBlobRunIterator::kHorizontal_Positioning: {
     *             const SkTextBlobBuilder::RunBuffer& rb = builder.allocRunPosH(font, count, offset.y(),
     *                                                                           bounds);
     *             for (int i = 0; i < count; ++i) {
     *                 rb.glyphs[i] = i;
     *                 rb.pos[i] = SkIntToScalar(i);
     *             }
     *         } break;
     *         case SkTextBlobRunIterator::kFull_Positioning: {
     *             const SkTextBlobBuilder::RunBuffer& rb = builder.allocRunPos(font, count, bounds);
     *             for (int i = 0; i < count; ++i) {
     *                 rb.glyphs[i] = i;
     *                 rb.pos[i * 2] = SkIntToScalar(i);
     *                 rb.pos[i * 2 + 1] = -SkIntToScalar(i);
     *             }
     *         } break;
     *         default:
     *             SK_ABORT("unhandled positioning value");
     *         }
     *     }
     * ```
     */
    private fun addRun(
      font: SkFont,
      count: Int,
      pos: SkTextBlobRunIterator.GlyphPositioning,
      offset: SkPoint,
      builder: SkTextBlobBuilder,
      bounds: SkRect? = TODO(),
    ) {
      TODO("Implement addRun")
    }
  }
}
