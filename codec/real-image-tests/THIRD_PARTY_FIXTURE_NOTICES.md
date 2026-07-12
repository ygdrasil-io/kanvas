# Third-Party Real Image Fixture Notices

This file records redistribution notices for third-party files committed under
`codec-real-image-tests/src/test/resources/codec-real-images`.

The JPEG release conformance, fuzzing, and performance tests add no third-party
binary fixture: photo and graphic evidence reuse the Skia JPEG entries already
listed in `FIXTURES.md`; CMYK and large-image evidence are generated in memory.
The optional external JPEG oracle is never redistributed or invoked unless its
absolute path is explicitly supplied by the developer.

## PngSuite

- Fixture: `codec-real-images/libpng/pngsuite2.png`
- Source: `https://libpng.org/pub/png/img_png/pngsuite2.png`
- Author: Willem van Schaik
- Notice: PngSuite permits use, copy, modification, and distribution of these
  images for any purpose and without fee.
- License source: `http://www.schaik.com/pngsuite2011/PngSuite.LICENSE`

## ImageMagick

- Fixture: `codec-real-images/imagemagick/rose.png`
- Source: `https://imagemagick.org/image/rose.png`
- Attribution: ImageMagick Studio LLC
- License: ImageMagick License, `https://imagemagick.org/license/`
- Redistribution obligations recorded from the project license page:
  redistributions must include a copy of the license; redistributions must
  provide clear attribution to ImageMagick Studio LLC. ImageMagick marks must
  not imply endorsement or authorship by this repository.

## GIMP

- Fixture: `codec-real-images/gimp/gfx_by_gimp.png`
- Source: `https://www.gimp.org/images/gfx_by_gimp.png`
- Attribution: GIMP team
- License: Creative Commons Attribution-ShareAlike 4.0 International,
  `https://creativecommons.org/licenses/by-sa/4.0/`
- Redistribution obligations recorded from `https://www.gimp.org/about/linking.html`:
  mention the author, mention the license, and release modified versions under a
  compatible license.

## Wikimedia Commons Public Domain Camera Fixture

- Fixture: `codec-real-images/camera/motorola_moto_e6_play.jpg`
- Source: `https://commons.wikimedia.org/wiki/File:Vivitar_Vivicam_S126.jpg`
- License: public domain dedication by the copyright holder.
- Transformation: downloaded the original JPEG and downscaled it with
  `sips -Z 256`.
