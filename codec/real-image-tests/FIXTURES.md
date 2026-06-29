# Real Image Fixture Provenance

This module keeps a deliberately small, versioned codec corpus under
`src/test/resources/codec-real-images`. The files are test fixtures, not
sample artwork. Every committed fixture must have a structured row in the
table below with source, license, and transformation details.

## License Rules

- `Skia upstream`: copied or reduced from Skia image resources. License:
  <https://skia.googlesource.com/skia/+/main/LICENSE>.
- `Repository generated`: original test data authored for this repository and
  redistributable with this repository's test suite.
- `Repository generated negative`: deliberately malformed or truncated original
  test data authored for this repository.
- `libpng/PngSuite`: copied from the libpng-hosted PngSuite page or Willem van
  Schaik's PngSuite archive; use the PngSuite permission statement recorded at
  `http://www.schaik.com/pngsuite2011/PngSuite.LICENSE`.
- `ImageMagick`: copied from ImageMagick project resources; use ImageMagick
  project pages and the ImageMagick License for provenance. Redistribution
  attribution and license obligations are recorded in
  `codec/real-image-tests/THIRD_PARTY_FIXTURE_NOTICES.md`.
- `GIMP`: copied from the official GIMP website; use the site's default
  CC-BY-SA 4.0 licensing rules unless a source page states otherwise.
  Redistribution attribution and ShareAlike obligations are recorded in
  `codec/real-image-tests/THIRD_PARTY_FIXTURE_NOTICES.md`.
- `Device camera`: copied or reduced from a source page that identifies a real
  capture device and redistribution license.
- `Browser`: rendered from a committed local browser source file and captured
  through a browser/web pipeline; redistributable with this repository's test
  suite.
- Do not add third-party photographs, icons, screenshots, device captures, or
  external tool output unless the source URL/path, license, and transformation
  are recorded in this file.

## Requested Tool Family Status

Issue #993 asks that real-image coverage include files from different tools or
sources such as libpng, ImageMagick, Photoshop/GIMP, devices, and browsers. The
current checked-in corpus intentionally documents only what can be traced:

- `libpng`: represented by the libpng-hosted PngSuite icon fixture.
- `ImageMagick`: represented by the ImageMagick built-in `ROSE` fixture
  published on the project site.
- `Photoshop/GIMP`: represented on the GIMP side by the official
  `gfx_by_gimp.png` website image. Photoshop remains unrepresented because no
  redistributable Photoshop-authored fixture has been audited here.
- `Device camera`: represented by a public-domain Wikimedia Commons JPEG whose
  metadata identifies a Motorola moto e6 play capture.
- `Browser`: represented by a committed HTML canvas source rendered and captured
  through a localhost browser session.

Third-party redistribution notices for the externally sourced fixtures are kept
in `codec/real-image-tests/THIRD_PARTY_FIXTURE_NOTICES.md`.

These statuses are intentionally conservative. They prevent the checklist from
claiming tool-family coverage that the repository cannot audit.

## Fixture Index

| Fixture | Family | Source URL/path | License | Transformation | Notes |
| --- | --- | --- | --- | --- | --- |
| `codec-real-images/png/mandrill_64.png` | Skia upstream | `skia-integration-tests/src/test/resources/images/mandrill_64.png`; lineage copied from Skia image resources in repo history | Skia license | Copied into `codec/real-image-tests`; kept at 64x64 to stay small | RGB PNG with ICC coverage |
| `codec-real-images/png/mandrill_128_icc.png` | Skia upstream | `skia-integration-tests/src/test/resources/images/mandrill_128.png`; original import from `/resources/images/mandrill_128.png` in upstream Skia checkout | Skia license | Copied into codec corpus and kept as ICC-bearing 128x128 PNG | RGB PNG with ICC profile |
| `codec-real-images/png/color_wheel.png` | Skia upstream | `skia-integration-tests/src/test/resources/images/color_wheel.png`; lineage copied from Skia image resources in repo history | Skia license | Copied into codec corpus as reduced RGBA fixture | Alpha PNG coverage |
| `codec-real-images/png/ducky_rgba_icc.png` | Skia upstream | `skia-integration-tests/src/test/resources/images/ducky.png`; original import from `/resources/images/ducky.png` in upstream Skia checkout | Skia license | Copied into codec corpus and retained with ICC metadata | RGBA PNG with ICC profile |
| `codec-real-images/png/grayscale_8.png` | Repository generated | `CodecAllKotlinRealImageTest` structural fixture set | Repository test fixture | Generated as 3x2 8-bit grayscale PNG | Grayscale PNG |
| `codec-real-images/png/grayscale_16.png` | Repository generated | `CodecAllKotlinRealImageTest` structural fixture set | Repository test fixture | Generated as 2x2 16-bit grayscale PNG | 16 bpc PNG |
| `codec-real-images/png/palette_alpha.png` | Repository generated | `CodecAllKotlinRealImageTest` structural fixture set | Repository test fixture | Generated as 3x2 palette PNG with tRNS alpha | Palette transparency |
| `codec-real-images/libpng/pngsuite2.png` | libpng/PngSuite | `https://libpng.org/pub/png/img_png/pngsuite2.png`; suite page `https://libpng.org/pub/png/pngsuite.html`; license `http://www.schaik.com/pngsuite2011/PngSuite.LICENSE`; notices `codec/real-image-tests/THIRD_PARTY_FIXTURE_NOTICES.md` | PngSuite permission grant | Copied unchanged into codec corpus | 48x48 interlaced palette PNG with alpha |
| `codec-real-images/imagemagick/rose.png` | ImageMagick | `https://imagemagick.org/image/rose.png`; built-in image documented as `ROSE` at `https://imagemagick.org/formats/`; license `https://imagemagick.org/license/`; notices `codec/real-image-tests/THIRD_PARTY_FIXTURE_NOTICES.md` | ImageMagick License | Copied unchanged into codec corpus | 70x46 RGB PNG from ImageMagick project resources; attribution: ImageMagick Studio LLC |
| `codec-real-images/gimp/gfx_by_gimp.png` | GIMP | `https://www.gimp.org/images/gfx_by_gimp.png`; site reuse policy `https://www.gimp.org/about/linking.html`; notices `codec/real-image-tests/THIRD_PARTY_FIXTURE_NOTICES.md` | CC-BY-SA 4.0 unless otherwise noted by GIMP website | Copied unchanged into codec corpus | 90x36 palette PNG from official GIMP website; attribution: GIMP team |
| `codec-real-images/jpeg/dog.jpg` | Skia upstream | `skia-integration-tests/src/test/resources/images/dog.jpg`; original import from `/resources/images/dog.jpg` in upstream Skia checkout | Skia license | Copied into codec corpus unchanged | Baseline JFIF JPEG |
| `codec-real-images/jpeg/ducky_icc_exif.jpg` | Skia upstream | `skia-integration-tests/src/test/resources/images/ducky.jpg`; original import from `/resources/images/ducky.jpg` in upstream Skia checkout | Skia license | Copied into codec corpus with ICC/EXIF-bearing metadata retained | Baseline JPEG with ICC and EXIF |
| `codec-real-images/jpeg/color_wheel_420.jpg` | Skia upstream | `skia-integration-tests/src/test/resources/images/color_wheel.jpg`; lineage copied from Skia image resources in repo history | Skia license | Copied into codec corpus as 4:2:0 JPEG fixture | Baseline 4:2:0 JPEG |
| `codec-real-images/jpeg/mandrill_h1v1_444.jpg` | Skia upstream | `skia-integration-tests/src/test/resources/images/mandrill_h1v1.jpg`; original import from `/resources/images/mandrill_h1v1.jpg` in upstream Skia checkout | Skia license | Copied into codec corpus unchanged | Baseline 4:4:4 JPEG |
| `codec-real-images/jpeg/grayscale_progressive.jpg` | Repository generated | `CodecAllKotlinRealImageTest` structural fixture set | Repository test fixture | Generated as 128x128 progressive grayscale JPEG | Progressive grayscale JPEG |
| `codec-real-images/jpeg/grayscale_progressive_legacy.jpg` | Repository generated | Legacy grayscale progressive JPEG fixture from repo history | Repository test fixture | Copied into codec corpus for regression compatibility | Legacy progressive grayscale JPEG |
| `codec-real-images/camera/motorola_moto_e6_play.jpg` | Device camera | `https://commons.wikimedia.org/wiki/File:Vivitar_Vivicam_S126.jpg`; original file `https://upload.wikimedia.org/wikipedia/commons/2/23/Vivitar_Vivicam_S126.jpg` | Public domain dedication by copyright holder | Downloaded original and downscaled with `sips -Z 256` to keep the fixture small | JPEG EXIF metadata identifies `motorola` / `moto e6 play` |
| `codec-real-images/browser/canvas_checker.jpg` | Browser | `codec/real-image-tests/sources/browser/canvas_checker_source.html`; rendered at `http://localhost:8765/canvas_checker_source.html` in the Codex in-app browser | Repository test fixture | Captured 24x16 viewport screenshot from the committed HTML canvas source | Browser/web pipeline JPEG fixture |
| `codec-real-images/jpeg/orientation/1_444.jpg` | Repository generated | EXIF orientation fixture set for issue #993 | Repository test fixture | Generated 100x80 4:4:4 JPEG with EXIF orientation value 1 | EXIF orientation 1 |
| `codec-real-images/jpeg/orientation/2_444.jpg` | Repository generated | EXIF orientation fixture set for issue #993 | Repository test fixture | Generated 100x80 4:4:4 JPEG with EXIF orientation value 2 | EXIF orientation 2 |
| `codec-real-images/jpeg/orientation/3_444.jpg` | Repository generated | EXIF orientation fixture set for issue #993 | Repository test fixture | Generated 100x80 4:4:4 JPEG with EXIF orientation value 3 | EXIF orientation 3 |
| `codec-real-images/jpeg/orientation/4_444.jpg` | Repository generated | EXIF orientation fixture set for issue #993 | Repository test fixture | Generated 100x80 4:4:4 JPEG with EXIF orientation value 4 | EXIF orientation 4 |
| `codec-real-images/jpeg/orientation/5_444.jpg` | Repository generated | EXIF orientation fixture set for issue #993 | Repository test fixture | Generated 100x80 4:4:4 JPEG with EXIF orientation value 5 | EXIF orientation 5 |
| `codec-real-images/jpeg/orientation/6_444.jpg` | Repository generated | EXIF orientation fixture set for issue #993 | Repository test fixture | Generated 100x80 4:4:4 JPEG with EXIF orientation value 6 | EXIF orientation 6 |
| `codec-real-images/jpeg/orientation/7_444.jpg` | Repository generated | EXIF orientation fixture set for issue #993 | Repository test fixture | Generated 100x80 4:4:4 JPEG with EXIF orientation value 7 | EXIF orientation 7 |
| `codec-real-images/jpeg/orientation/8_444.jpg` | Repository generated | EXIF orientation fixture set for issue #993 | Repository test fixture | Generated 100x80 4:4:4 JPEG with EXIF orientation value 8 | EXIF orientation 8 |
| `codec-real-images/gif/box.gif` | Skia upstream | `skia-integration-tests/src/test/resources/images/box.gif`; lineage copied from Skia image resources in repo history | Skia license | Copied into codec corpus unchanged | Static GIF |
| `codec-real-images/gif/test640x479.gif` | Skia upstream | `skia-integration-tests/src/test/resources/images/test640x479.gif`; lineage copied from Skia image resources in repo history | Skia license | Copied into codec corpus unchanged | Animated GIF frame/delay coverage |
| `codec-real-images/gif/disposal_methods_3x1.gif` | Repository generated | GIF disposal fixture set for issue #993 | Repository test fixture | Generated as 3x1 multi-frame GIF with disposal dependencies | GIF disposal and frame dependency |
| `codec-real-images/bmp/bottom_up_24.bmp` | Repository generated | BMP structural fixture set for issue #993 | Repository test fixture | Generated as 3x2 bottom-up 24 bpp BMP | Bottom-up BMP |
| `codec-real-images/bmp/top_down_32_alpha.bmp` | Repository generated | BMP structural fixture set for issue #993 | Repository test fixture | Generated as 2x2 top-down 32 bpp BMP | BMP alpha |
| `codec-real-images/bmp/palette_8.bmp` | Repository generated | BMP structural fixture set for issue #993 | Repository test fixture | Generated as indexed 8 bpp BMP | Palette BMP |
| `codec-real-images/wbmp/type0_3x2.wbmp` | Repository generated | WBMP structural fixture set for issue #993 | Repository test fixture | Generated as type 0 3x2 WBMP | WBMP checker |
| `codec-real-images/wbmp/invalid_truncated_header.wbmp` | Repository generated negative | WBMP negative fixture set for issue #993 | Repository test fixture | Truncated generated WBMP header | Negative WBMP |
| `codec-real-images/wbmp/invalid_truncated_raster.wbmp` | Repository generated negative | WBMP negative fixture set for issue #993 | Repository test fixture | Truncated generated WBMP raster | Negative WBMP |
| `codec-real-images/ico/embedded_png.ico` | Repository generated | ICO structural fixture set for issue #993 | Repository test fixture | Generated ICO directory wrapping a small PNG payload | ICO embedded PNG |
| `codec-real-images/ico/embedded_bmp.ico` | Repository generated | ICO structural fixture set for issue #993 | Repository test fixture | Generated ICO directory wrapping a small BMP payload | ICO embedded BMP |
| `codec-real-images/ico/largest_entry_prefers_bmp.ico` | Repository generated | ICO structural fixture set for issue #993 | Repository test fixture | Generated multi-entry ICO for selection order coverage | ICO selection |
| `codec-real-images/ico/tie_prefers_png.ico` | Repository generated | ICO structural fixture set for issue #993 | Repository test fixture | Generated multi-entry ICO for tie-break coverage | ICO tie-break |
| `codec-real-images/webp/vp8l_lossless_2x1.webp` | Repository generated | WebP structural fixture set for issue #993 | Repository test fixture | Generated 2x1 VP8L lossless fixture | WebP lossless |
| `codec-real-images/webp/vp8_lossy_gray_2x2.webp` | Repository generated | WebP structural fixture set for issue #993 | Repository test fixture | Generated 2x2 VP8 lossy grayscale fixture | WebP lossy |
| `codec-real-images/webp/animated_vp8l_blend_dispose_4x1.webp` | Repository generated | WebP animation fixture set for issue #993 | Repository test fixture | Generated animated VP8L blend/dispose fixture | Animated WebP |
| `codec-real-images/webp/stoplight.webp` | Skia upstream | `cpu-raster/src/test/resources/codec-fixtures/stoplight.webp`; original import from upstream Skia `resources/images/stoplight.webp` | Skia license | Copied into codec corpus unchanged | Animated WebP metadata and frame coverage |
| `codec-real-images/webp/invalid_truncated_riff.webp` | Repository generated negative | WebP negative fixture set for issue #993 | Repository test fixture | Truncated generated RIFF header | Negative WebP |
| `codec-real-images/webp/invalid_truncated_vp8x.webp` | Repository generated negative | WebP negative fixture set for issue #993 | Repository test fixture | Truncated generated VP8X chunk | Negative WebP |
| `codec-real-images/webp/invalid_truncated_vp8l.webp` | Repository generated negative | WebP negative fixture set for issue #993 | Repository test fixture | Truncated generated VP8L chunk | Negative WebP |
| `codec-real-images/webp/invalid_truncated_vp8.webp` | Repository generated negative | WebP negative fixture set for issue #993 | Repository test fixture | Truncated generated VP8 chunk | Negative WebP |
