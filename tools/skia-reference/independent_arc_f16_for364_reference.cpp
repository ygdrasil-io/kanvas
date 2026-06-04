// Repo-owned upstream Skia independent arc Rec.2020 F16 reference source for FOR-364.
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

static constexpr int kWidth = 72;
static constexpr int kHeight = 72;

struct Sample {
    const char* name;
    const char* zone;
    int x;
    int y;
};

static constexpr Sample kSamples[] = {
        {"for364_background_top_left", "background", 0, 0},
        {"for364_arc_diagonal_stroke_center", "stroke-center", 16, 16},
        {"for364_arc_top_stroke_center", "stroke-center", 32, 10},
        {"for364_arc_interior_clear", "interior-clear", 32, 32},
};

static void draw_scene(SkCanvas* canvas, SkColor clearColor) {
    canvas->clear(clearColor);

    SkPaint red;
    red.setAntiAlias(false);
    red.setStyle(SkPaint::kStroke_Style);
    red.setStrokeWidth(6);
    red.setStrokeCap(SkPaint::kButt_Cap);
    red.setBlendMode(SkBlendMode::kSrcOver);
    red.setColor(SkColorSetARGB(128, 255, 0, 0));
    canvas->drawArc(SkRect::MakeXYWH(10, 10, 44, 44), 180, 100, false, red);
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
    output.writeText("  \"sceneId\": \"f16-independent-comparable-arc-evidence-for364\",\n");
    output.writeText("  \"sourceType\": \"isolated-skia-independent-arc-rec2020-f16-for364-butt-cap\",\n");
    output.writeText("  \"dimensions\": {\"width\": 72, \"height\": 72},\n");
    output.writeText("  \"colorType\": \"kRGBA_F16Norm\",\n");
    output.writeText("  \"colorSpace\": \"Rec.2020\",\n");
    output.writeText("  \"blendMode\": \"kSrcOver\",\n");
    output.writeText("  \"arcScene\": true,\n");
    output.writeText("  \"independentFromFor361\": true,\n");
    output.writeText("  \"independentFromFor340For341AdjacentGroups\": true,\n");
    output.writeText("  \"excludedScene\": \"for361_and_circular_arcs_stroke_butt_adjacent_groups\",\n");
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
    const char* jsonPath = argc > 1 ? argv[1] : "f16-independent-comparable-arc-evidence-for364-skia.json";
    const char* pngPath = argc > 2 ? argv[2] : "f16-independent-comparable-arc-evidence-for364-skia.png";
    return write_reference(jsonPath, pngPath) ? 0 : 1;
}
