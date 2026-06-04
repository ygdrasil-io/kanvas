// Repo-owned upstream Skia adjacent-cell renderer source for FOR-340.
// Intended to be built in a real upstream Skia checkout.

#include "include/core/SkCanvas.h"
#include "include/core/SkColor.h"
#include "include/core/SkColorSpace.h"
#include "include/core/SkImageInfo.h"
#include "include/core/SkPaint.h"
#include "include/core/SkPixmap.h"
#include "include/core/SkRefCnt.h"
#include "include/core/SkRect.h"
#include "include/core/SkStream.h"
#include "include/core/SkSurface.h"
#include "include/encode/SkPngEncoder.h"

static bool write_cell(const char* outputPath, SkScalar sweepDegrees) {
    const SkImageInfo imageInfo =
            SkImageInfo::MakeN32Premul(80, 80, SkColorSpace::MakeSRGB());
    sk_sp<SkSurface> surface = SkSurfaces::Raster(imageInfo);
    if (!surface) {
        return false;
    }

    SkCanvas* canvas = surface->getCanvas();
    canvas->clear(SK_ColorTRANSPARENT);

    const SkRect arcRect = SkRect::MakeLTRB(20, 20, 60, 60);

    SkPaint red;
    red.setAntiAlias(true);
    red.setStyle(SkPaint::kStroke_Style);
    red.setStrokeWidth(15);
    red.setStrokeCap(SkPaint::kButt_Cap);
    red.setColor(SkColorSetARGB(100, 255, 0, 0));

    SkPaint blue = red;
    blue.setColor(SkColorSetARGB(100, 0, 0, 255));

    canvas->drawArc(arcRect, 0, sweepDegrees, false, red);
    canvas->drawArc(arcRect, 0, -(360 - sweepDegrees), false, blue);

    SkPixmap pixmap;
    if (!surface->peekPixels(&pixmap)) {
        return false;
    }

    SkFILEWStream output(outputPath);
    if (!output.isValid()) {
        return false;
    }

    SkPngEncoder::Options options;
    return SkPngEncoder::Encode(&output, pixmap, options);
}

int main(int argc, char** argv) {
    const char* sweep45Path = argc > 1 ? argv[1] : "sweep45-skia.png";
    const char* sweep130Path = argc > 2 ? argv[2] : "sweep130-skia.png";

    if (!write_cell(sweep45Path, 45)) {
        return 1;
    }
    if (!write_cell(sweep130Path, 130)) {
        return 1;
    }
    return 0;
}
