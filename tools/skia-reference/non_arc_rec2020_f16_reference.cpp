// Repo-owned upstream Skia non-arc Rec.2020 F16 reference source for FOR-345.
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

static constexpr int kWidth = 32;
static constexpr int kHeight = 32;

struct Sample {
    const char* name;
    int x;
    int y;
};

static constexpr Sample kSamples[] = {
        {"background_top_left", 0, 0},
        {"rect_center", 16, 16},
        {"rect_left_inside", 8, 16},
        {"rect_right_inside", 23, 16},
};

static bool write_sample_json(const char* outputPath, const SkPixmap& pixmap) {
    SkFILEWStream output(outputPath);
    if (!output.isValid()) {
        return false;
    }

    output.writeText("{\n");
    output.writeText("  \"sceneId\": \"non-arc-rec2020-f16-reference-row-for345\",\n");
    output.writeText("  \"sourceType\": \"isolated-skia-non-arc-rec2020-f16-src-over-rect\",\n");
    output.writeText("  \"dimensions\": {\"width\": 32, \"height\": 32},\n");
    output.writeText("  \"colorType\": \"kRGBA_F16Norm\",\n");
    output.writeText("  \"colorSpace\": \"Rec.2020\",\n");
    output.writeText("  \"blendMode\": \"kSrcOver\",\n");
    output.writeText("  \"nonArc\": true,\n");
    output.writeText("  \"excludedScene\": \"circular_arcs_stroke_butt\",\n");
    output.writeText("  \"samples\": [\n");

    for (size_t i = 0; i < std::size(kSamples); ++i) {
        const Sample& sample = kSamples[i];
        const SkColor color = pixmap.getColor(sample.x, sample.y);
        char line[256];
        std::snprintf(
                line,
                sizeof(line),
                "    {\"name\": \"%s\", \"x\": %d, \"y\": %d, "
                "\"referenceSrgbRgba\": [%u, %u, %u, %u]}%s\n",
                sample.name,
                sample.x,
                sample.y,
                SkColorGetR(color),
                SkColorGetG(color),
                SkColorGetB(color),
                SkColorGetA(color),
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
    sk_sp<SkSurface> surface = SkSurfaces::Raster(imageInfo);
    if (!surface) {
        return false;
    }

    SkCanvas* canvas = surface->getCanvas();
    canvas->clear(SK_ColorWHITE);

    SkPaint blue;
    blue.setAntiAlias(false);
    blue.setStyle(SkPaint::kFill_Style);
    blue.setBlendMode(SkBlendMode::kSrcOver);
    blue.setColor(SkColorSetARGB(100, 0, 0, 255));
    canvas->drawRect(SkRect::MakeXYWH(8, 8, 16, 16), blue);

    SkBitmap srgbBitmap;
    const SkImageInfo srgbInfo =
            SkImageInfo::MakeN32Premul(kWidth, kHeight, SkColorSpace::MakeSRGB());
    if (!srgbBitmap.tryAllocPixels(srgbInfo)) {
        return false;
    }
    if (!surface->readPixels(srgbBitmap, 0, 0)) {
        return false;
    }

    SkPixmap srgbPixmap;
    if (!srgbBitmap.peekPixels(&srgbPixmap)) {
        return false;
    }

    if (!write_sample_json(jsonPath, srgbPixmap)) {
        return false;
    }

    SkFILEWStream pngOutput(pngPath);
    if (!pngOutput.isValid()) {
        return false;
    }
    SkPngEncoder::Options options;
    return SkPngEncoder::Encode(&pngOutput, srgbPixmap, options);
}

int main(int argc, char** argv) {
    const char* jsonPath = argc > 1 ? argv[1] : "non-arc-rec2020-f16-reference-row-for345-skia.json";
    const char* pngPath = argc > 2 ? argv[2] : "non-arc-rec2020-f16-reference-row-for345-skia.png";
    return write_reference(jsonPath, pngPath) ? 0 : 1;
}
