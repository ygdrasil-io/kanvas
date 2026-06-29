# Supported Codecs

This matrix is the official support contract for the portable codec and image
encode paths.

Validation command:

```bash
./gradlew checkCodecImageComplete
```

That task runs the pure Kotlin codec tests, real image tests, runtime assembly
checks, image encode tests, image encode guard checks, production classpath
checks, and AWT/ImageIO guards. Format-specific rows below call out limitations
that are intentional for the current implementation, either as temporary roadmap
gaps or as out-of-scope behavior.

## Decode Matrix

| Format | Decode | Animation | ICC / color metadata | EXIF / orientation | Alpha | 16 bpc / F16 | Progressive / interlace | Unsupported variants and notes |
|---|---|---|---|---|---|---|---|---|
| PNG | Supported through `codec/png`. Covers grayscale, RGB, indexed, grayscale+alpha, and RGBA PNGs. | Not applicable. | `iCCP` is parsed best effort and exposed when supported. `sRGB`, `gAMA`, and `cHRM` are structurally validated but do not synthesize a custom ICC profile yet. | Not applicable. | Supported through alpha channels and `tRNS` for grayscale, RGB, and indexed PNGs. | 16-bit grayscale/RGB/grayscale+alpha/RGBA decode to `RGBA_F16Norm`. | Non-interlaced and Adam7 interlaced PNGs are supported. | Critical unknown chunks, invalid CRCs, malformed color metadata, truncated IDAT streams, and unsupported color/depth combinations are rejected. |
| JPEG | Supported through `codec/jpeg`. Covers sequential 8-bit grayscale, 3-component YCbCr baseline JPEG, and baseline Adobe CMYK/YCCK. | Not applicable. | Multi-segment APP2 ICC profiles are parsed and exposed when complete and parseable. | EXIF orientation 1 through 8 is parsed and applied to output dimensions/pixels. | JPEG has no alpha channel. Output is opaque RGBA. | Decode can write `RGBA_F16Norm`, sourced from 8-bit JPEG samples. | Baseline color JPEG is supported. Progressive grayscale JPEG is implemented; progressive color and progressive CMYK are not official support. Restart intervals are handled on the supported baseline paths. | Unsupported SOF modes, 12-bit JPEG, arithmetic coding, unsupported component sampling, malformed APP markers, incomplete ICC segment sets, and unsupported progressive scans are rejected or return `kUnimplemented`. |
| GIF | Supported through `codec/gif`. Covers GIF87a/GIF89a indexed frames. | Supported for decoded frame count, frame rects, frame durations, transparency, interlaced frames, disposal modes used by the current tests, and Netscape loop count metadata exposed through `Codec.getRepetitionCount()`. | GIF ICC/color profiles are not exposed. Output uses sRGB. | Not applicable. | Supported through transparent color indexes. | Not applicable. Output is `RGBA_8888`. | GIF interlacing is supported. | Malformed LZW streams, invalid frame bounds, missing color tables, zero-sized canvases, and unsupported extension semantics are rejected. Malformed loop extension payloads are skipped unless they corrupt frame data. |
| BMP | Supported through `codec/bmp`. Covers Windows/OS2 `BM` files with BITMAPINFOHEADER-compatible DIBs. | Not applicable. | V4/V5 embedded ICC profiles are parsed and exposed when parseable. Output uses sRGB. | Not applicable. | 32 bpp alpha masks are supported when present; otherwise pixels are opaque. | Not applicable. Output is `RGBA_8888`. | Not applicable. | BI_RGB, BI_BITFIELDS, BI_RLE8, and BI_RLE4 are supported for 1/4/8/16/24/32 bpp where valid. Top-down RLE is rejected. Unsupported compression modes, invalid masks, malformed palettes, and truncated pixel data are rejected. |
| WBMP | Supported through `codec/wbmp`. Covers WAP type-0 monochrome WBMP. | Not applicable. | Not applicable. Output uses sRGB. | Not applicable. | Not supported by the format. Output is opaque black/white RGBA. | Not applicable. Output is `RGBA_8888`. | Not applicable. | Non-type-0 headers, zero dimensions, VLQ overflow, excessive dimensions, and truncated pixel data are rejected. |
| ICO / CUR | Supported through `codec/ico` as a container decoder. It selects the largest entry and delegates PNG payloads to PNG decode or DIB payloads to BMP decode. | Multi-entry selection is supported; ICO is not exposed as animation. | Follows the selected payload decoder. | Follows the selected payload decoder. | PNG alpha, BMP alpha masks, and supported ICO AND masks are applied through the selected payload path. | Follows the selected payload decoder. | Follows the selected payload decoder. | Dimensions encoded as 0 are treated as 256. Invalid directories, truncated entries, unsupported payloads, and undecodable selected payloads are rejected. |
| WebP | Supported through `codec/webp` for container metadata and the implemented pixel paths. VP8L lossless and a VP8 lossy keyframe subset are covered by real-image tests. | Animated WebP metadata, ANIM loop count exposed through `Codec.getRepetitionCount()`, and frame composition are implemented when each frame uses a supported pixel encoding. Unsupported frame encodings return `kUnimplemented`. | VP8X ICC chunks are parsed and exposed when parseable. EXIF and XMP chunks are preserved internally as metadata. Output color space remains sRGB. | EXIF bytes are parsed as metadata only; EXIF orientation is not applied. | VP8L alpha is supported. VP8 alpha metadata is parsed; unsupported alpha compression/features return `kUnimplemented`. | Not applicable. Output is `RGBA_8888`. | Not applicable. | VP8 lossy features outside the implemented subset return `kUnimplemented`. Truncated RIFF/VP8X/VP8L/VP8 streams, invalid dimensions, inconsistent flags, and unsupported animation frame encodings are rejected or return `kUnimplemented`. |
| AVIF / JPEG XL / RAW / video | Not supported in the portable pure Kotlin runtime. `codec/extended` reserves provider stubs for explicit future dependency deliveries. | Out of scope. | Out of scope. | Out of scope. | Out of scope. | Out of scope. | Out of scope. | These formats must not silently fall back to AWT/ImageIO/JNI in production portable paths. |

## Encode Matrix

| Format | Encode | ICC / color metadata | EXIF / orientation | Alpha | 16 bpc / F16 | Progressive / interlace | Unsupported variants and notes |
|---|---|---|---|---|---|---|---|
| PNG | Supported through `SkPngEncoder`. | The encoder writes sRGB 8-bit RGBA/truecolor output and does not currently serialize `iCCP`, `sRGB`, `gAMA`, or `cHRM` from `SkColorSpace`. | Not applicable. | Supported. | F16 and 16 bpc preservation are not official support; input pixels are projected through the current 8-bit pixmap/bitmap path. | Interlaced PNG encode is not supported; output is non-interlaced. | Compression and filters are pure Kotlin and covered by existing encode tests. Real-corpus quality/conformance expansion remains a roadmap item. |
| JPEG | Supported through `SkJpegEncoder` for baseline RGB output with quality `0..100`. | ICC APP2 writing is not supported yet. | EXIF writing and orientation metadata are not supported yet. | JPEG output is opaque; source alpha is composited according to the current encoder behavior before writing RGB samples. | F16 preservation is not supported; input pixels are projected to 8-bit samples. | Progressive encode and restart marker options are not supported. | Quality mapping and baseline interoperability are implemented, but broader real-corpus compatibility remains a roadmap item. |
| BMP | Supported through `BmpEncoder`. Implemented in `codec/bmp/`. | V5 embedded ICC profiles are written when supplied via `Options.iccProfile`. Output defaults to sRGB. | Not applicable. | Supported when writing 32 bpp output; otherwise format variants are opaque. | Not supported. | Not applicable. | Supports uncompressed BGRA_8888 and BGR_888, plus RLE8/RLE4 compression for palette-indexable input. |
| WBMP | Supported through `SkWbmpEncoder`. | Not applicable. | Not applicable. | Not supported by the format. Pixels are encoded as 1-bit black/white. | Not applicable. | Not applicable. | Output is WAP type-0 monochrome WBMP. |
| WebP | Supported through `SkWebpEncoder` for lossless VP8L. | ICC/EXIF/XMP writing is not supported. | EXIF orientation writing is not supported. | Supported for lossless VP8L. | Not supported; input pixels are projected to 8-bit samples. | Not applicable. | Lossy VP8 encode intentionally returns `null` until a pure Kotlin lossy encoder is implemented or explicitly kept out of scope. |
| GIF / ICO / AVIF / HEIF / JPEG XL / RAW / video | Not supported by the public portable encoder path. | Out of scope. | Out of scope. | Out of scope. | Out of scope. | Out of scope. | Public encode APIs must return `null` or a documented stub behavior for unsupported formats; they must not use AWT/ImageIO/JNI fallbacks. |

## Guardrails

- Production portable codec runtime is `:codec`.
- The runtime and image encode production paths must not depend on AWT,
  ImageIO, or JNI codec backends.
- Unsupported variants should fail explicitly by returning `null`,
  `kUnimplemented`, or a specific `Codec.Result` error rather than silently
  selecting another backend.
- New support claims should land with tests in the owning codec module or
  `codec/real-image-tests`, then this matrix should be updated in the same PR.
