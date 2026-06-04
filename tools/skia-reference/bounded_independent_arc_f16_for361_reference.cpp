// Repo-owned upstream Skia bounded independent arc Rec.2020 F16 reference source for FOR-361.
// Intended to be built in a real upstream Skia checkout.

#include "include/core/SkBitmap.h"
#include "include/core/SkCanvas.h"
#include "include/core/SkColor.h"
#include "include/core/SkColorSpace.h"
#include "include/core/SkColorType.h"
#include "include/core/SkImageInfo.h"
#include "include/core/SkPaint.h"
#include "include/core/SkPixmap.h"
#include "include/core/SkRect.h"
#include "include/core/SkStream.h"
#include "include/core/SkSurface.h"
#include "include/encode/SkPngEncoder.h"

#include <cstdio>
#include <iterator>

static constexpr int kWidth = 64;
static constexpr int kHeight = 64;

struct Sample {
    const char* name;
    const char* zone;
    int x;
    int y;
};

static constexpr Sample kSamples[] = {
        {"for361_background_top_left", "background", 0, 0},
        {"for361_arc_right_stroke_center", "stroke-center", 52, 32},
        {"for361_arc_lower_right_stroke", "stroke-edge", 44, 46},
        {"for361_arc_interior_clear", "interior-clear", 32, 32},
};

static void draw_scene(SkCanvas* canvas, SkColor clearColor) {
    canvas->clear(clearColor);

    SkPaint blue;
    blue.setAntiAlias(false);
    blue.setStyle(SkPaint::kStroke_Style);
    blue.setStrokeWidth(8);
    blue.setStrokeCap(SkPaint::kRound_Cap);
    blue.setBlendMode(SkBlendMode::kSrcOver);
    blue.setColor(SkColorSetARGB(100, 0, 0, 255));
    canvas->drawArc(SkRect::MakeXYWH(12, 12, 40, 40), 0, 120, false, blue);
}

static bool read_srgb_surface(sk_sp<SkSurface> surface, SkBitmap* bitmap, SkPixmap* pixmap) {
    const SkImageInfo srgbInfo =
            SkImageInfo::MakeN32Premul(kWidth, kHeight, SkColorSpace::MakeSRGB());
    if (!bitmap->tryAllocPixels(srgbInfo)) {
        return false;
    }
    if (!surface->readPixels(*bitmap, 0, 0)) {
        return false;
    }
    return bitmap->peekPixels(pixmap);
}

static bool write_sample_json(
        const char* outputPath,
        const SkPixmap& rawPixmap,
        const SkPixmap& overWhitePixmap) {
    SkFILEWStream output(outputPath);
    if (!output.isValid()) {
        return false;
    }

    output.writeText("{\n");
    output.writeText("  \"sceneId\": \"f16-bounded-independent-arc-capture-for361\",\n");
    output.writeText("  \"sourceType\": \"isolated-skia-bounded-independent-arc-rec2020-f16-for361-round-cap\",\n");
    output.writeText("  \"dimensions\": {\"width\": 64, \"height\": 64},\n");
    output.writeText("  \"colorType\": \"kRGBA_F16Norm\",\n");
    output.writeText("  \"colorSpace\": \"Rec.2020\",\n");
    output.writeText("  \"blendMode\": \"kSrcOver\",\n");
    output.writeText("  \"arcScene\": true,\n");
    output.writeText("  \"independentFromFor340For341AdjacentGroups\": true,\n");
    output.writeText("  \"excludedScene\": \"circular_arcs_stroke_butt_adjacent_groups\",\n");
    output.writeText("  \"samples\": [\n");

    for (size_t i = 0; i < std::size(kSamples); ++i) {
        const Sample& sample = kSamples[i];
        const SkColor raw = rawPixmap.getColor(sample.x, sample.y);
        const SkColor overWhite = overWhitePixmap.getColor(sample.x, sample.y);
        char line[384];
        std::snprintf(
                line,
                sizeof(line),
                "    {\"name\": \"%s\", \"zone\": \"%s\", \"x\": %d, \"y\": %d, "
                "\"rawReferenceRgba\": [%u, %u, %u, %u], "
                "\"referenceSrgbRgba\": [%u, %u, %u, %u]}%s\n",
                sample.name,
                sample.zone,
                sample.x,
                sample.y,
                SkColorGetR(raw),
                SkColorGetG(raw),
                SkColorGetB(raw),
                SkColorGetA(raw),
                SkColorGetR(overWhite),
                SkColorGetG(overWhite),
                SkColorGetB(overWhite),
                SkColorGetA(overWhite),
                i + 1 == std::size(kSamples) ? "" : ",");
        output.writeText(line);
    }

    output.writeText("  ]\n");
    output.writeText("}\n");
    return true;
}

static bool write_reference(const char* jsonPath, const char* pngPath) {
    sk_sp<SkColorSpace> rec2020 =
            SkColorSpace::MakeRGB(SkNamedTransferFn::kRec2020, SkNamedGamut::kRec2020);
    const SkImageInfo imageInfo = SkImageInfo::Make(
            kWidth,
            kHeight,
            kRGBA_F16Norm_SkColorType,
            kPremul_SkAlphaType,
            rec2020);

    sk_sp<SkSurface> rawSurface = SkSurfaces::Raster(imageInfo);
    sk_sp<SkSurface> overWhiteSurface = SkSurfaces::Raster(imageInfo);
    if (!rawSurface || !overWhiteSurface) {
        return false;
    }

    draw_scene(rawSurface->getCanvas(), SK_ColorTRANSPARENT);
    draw_scene(overWhiteSurface->getCanvas(), SK_ColorWHITE);

    SkBitmap rawBitmap;
    SkPixmap rawPixmap;
    if (!read_srgb_surface(rawSurface, &rawBitmap, &rawPixmap)) {
        return false;
    }
    SkBitmap overWhiteBitmap;
    SkPixmap overWhitePixmap;
    if (!read_srgb_surface(overWhiteSurface, &overWhiteBitmap, &overWhitePixmap)) {
        return false;
    }

    if (!write_sample_json(jsonPath, rawPixmap, overWhitePixmap)) {
        return false;
    }

    SkFILEWStream pngOutput(pngPath);
    if (!pngOutput.isValid()) {
        return false;
    }
    SkPngEncoder::Options options;
    return SkPngEncoder::Encode(&pngOutput, overWhitePixmap, options);
}

int main(int argc, char** argv) {
    const char* jsonPath = argc > 1 ? argv[1] : "f16-bounded-independent-arc-capture-for361-skia.json";
    const char* pngPath = argc > 2 ? argv[2] : "f16-bounded-independent-arc-capture-for361-skia.png";
    return write_reference(jsonPath, pngPath) ? 0 : 1;
}
