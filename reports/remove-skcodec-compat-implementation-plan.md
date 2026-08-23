# Retirer `SkCodecCompat` Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Retirer `SkCodecCompat.kt` en faisant de `ImageInfo`, `Bitmap`,
`Pixmap`, `ColorType` et `ImageColorSpace` les seuls contrats image exposés
par les codecs Kanvas.

**Architecture:** Le catalogue `ColorType` contient tous les formats cibles,
mais seuls les formats de migration ont des capacités mémoire et CPU actives.
Chaque codec déclare ses formats decode/encode réellement implémentés et
refuse les autres sans fallback. Les métadonnées ICC restent dans
`ImageColorSpace` jusqu’à la frontière explicite vers l’`Image` rendable.

**Tech Stack:** Kotlin/JVM, Gradle, `:kanvas`, `:color-management`, codecs
Kotlin et JUnit 5.

**Spec:** `reports/remove-skcodec-compat-design.md`

## Global Constraints

* Travailler dans le worktree local existant et ne jamais inclure
  `integration-tests/skia/test-similarity-scores.properties` dans un commit.
* Aucune façade, alias, overload ou import public `Sk*` ne survit dans le
  domaine codec.
* `UNKNOWN` est une sentinelle ; un format catalogue sans capacité livrée est
  refusé explicitement et n’est jamais converti silencieusement en
  `RGBA_8888`.
* `ImageInfo` et `Bitmap` préservent `ImageColorSpace`, y compris ICC et un
  statut de profil non supporté. Seule une frontière renderer explicite peut
  le projeter vers `ColorSpace`.
* Les tests existants sont adaptés ; ce plan ne crée pas de suite de
  régression distincte.
* Ne pas attribuer les échecs GPU/package-boundary connus de `./gradlew test`
  à ce chantier sans preuve causale.

---

## File Map

| Fichier | Responsabilité après migration |
|---|---|
| `kanvas/.../image/ColorType.kt` | Catalogue exhaustif, taille physique et alpha par défaut. |
| `kanvas/.../image/ColorTypeCapabilities.kt` | Capacités allocation/CPU de chaque format du catalogue. |
| `kanvas/.../image/ImageInfo.kt` | Métadonnées codec incluant `ImageColorSpace`. |
| `kanvas/.../image/Bitmap.kt` | Buffer possédé, primitives ARGB/F16 et frontière vers `Image`. |
| `kanvas/.../image/Pixmap.kt` | Vue `ByteBuffer` stridée non possédée. |
| `codec/api/.../Codec.kt` | Contrat public `ImageInfo`/`Bitmap`. |
| `codec/api/.../CodecImageDecoder.kt` | Pont codec vers `Image` rendable avec refus de profil explicite. |
| `codec/common/.../PixmapUtils.kt` | Orientation pure Kanvas. |
| `codec/{png,jpeg}/...` | Décodeurs et encodeurs avec profils ICC, F16 et `Pixmap`. |
| `codec/{bmp,gif,wbmp,webp,jpeg-ls,jpeg2000,jpegxl}/...` | Décodage/encodage des formats de migration avec `Bitmap` Kanvas. |
| `codec/{android,animated,image-generator,extended,ico}/...` | Consommateurs périphériques sans référence `Sk*`. |
| `integration-tests/...` et `codec/test-fixtures/...` | Fixtures et consommateurs adaptés aux types Kanvas. |
| `kanvas/.../foundation/SkCodecCompat.kt` | Supprimé. |
| `codec/api/.../KanvasCodec.kt` | Supprimé. |

### Task 1: Établir le catalogue image Kanvas et les métadonnées ICC

**Files:**

* Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/image/ColorType.kt`
* Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/image/ColorTypeCapabilities.kt`
* Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/image/Image.kt:1-39`
* Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/image/ImageInfo.kt:1-101`
* Modify: `.upstream/specs/kanvas/09-image-and-text.md:36-57`
* Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/image/ImageMetadataTest.kt:1-60`

**Interfaces:**

* Produces `enum class ColorType` with the 28 names in the approved design,
  `bytesPerPixel`, `defaultAlphaType()` and `isConcrete()`.
* Produces `data class ColorTypeCapabilities(val allocatable: Boolean, val cpuReadableWritable: Boolean)` and `fun ColorType.capabilities()`.
* Changes `ImageInfo.colorSpace` and every convenience factory argument to
  `ImageColorSpace`, defaulting to `ImageColorSpace.sRGB()`.

- [ ] **Step 1: Adapt the existing metadata test to the new contract**

  In `ImageMetadataTest`, replace `ColorSpace.SRGB` with
  `ImageColorSpace.sRGB()` and add the catalogue assertion to the existing
  test class:

  ```kotlin
  assertEquals(28, ColorType.entries.size)
  assertFalse(ColorType.UNKNOWN.isConcrete())
  assertEquals(8, ColorType.RGBA_F16_NORM.bytesPerPixel)
  assertFalse(ColorType.RGBA_1010102.capabilities().allocatable)
  assertTrue(ColorType.RGBA_8888.capabilities().cpuReadableWritable)
  ```

- [ ] **Step 2: Run the focused test and observe the contract is absent**

  Run: `rtk ./gradlew :kanvas:test --tests org.graphiks.kanvas.image.ImageMetadataTest`

  Expected: compilation failure because the catalogue and
  `ImageColorSpace`-based signature do not yet exist.

- [ ] **Step 3: Extract `ColorType` and define the complete catalogue**

  Move the enum and the two private default-alpha functions out of `Image.kt`
  and `ImageInfo.kt` into `ColorType.kt`. Declare exactly:

  ```kotlin
  UNKNOWN(0), ALPHA_8(1), RGB_565(2), ARGB_4444(2),
  RGBA_8888(4), RGB_888X(4), BGRA_8888(4), RGBA_1010102(4),
  BGRA_1010102(4), RGB_101010X(4), BGR_101010X(4),
  BGR_101010X_XR(4), BGRA_10101010_XR(8), RGBA_10X6(8),
  GRAY_8(1), RGBA_F16_NORM(8), RGBA_F16(8),
  RGB_F16F16F16X(8), RGBA_F32(16), R8G8_UNORM(2),
  A16_FLOAT(2), R16G16_FLOAT(4), A16_UNORM(2), R16_UNORM(2),
  R16G16_UNORM(4), R16G16B16A16_UNORM(8), SRGBA_8888(4), R8_UNORM(1)
  ```

  Make `defaultAlphaType()` return `UNKNOWN` for `UNKNOWN`, `OPAQUE` for
  opaque formats, and preserve the current defaults for every format that is
  currently decoded. `RGBA_F16_NORM` must remain distinct from `RGBA_F16`.

- [ ] **Step 4: Add the generic capability table and migrate `ImageInfo`**

  Mark only `ALPHA_8`, `RGB_565`, `ARGB_4444`, `RGBA_8888`, `BGRA_8888`,
  `GRAY_8`, `RGBA_F16` and `RGBA_F16_NORM` as allocatable and CPU-accessible.
  Keep every other concrete format enumerable but unavailable. Change all
  `ImageInfo.make*` factories and `makeColorSpace` to accept
  `ImageColorSpace`; update the Kanvas image API specification with the same
  catalogue and the explicit refusal rule.

- [ ] **Step 5: Run focused Kanvas metadata verification**

  Run: `rtk ./gradlew :kanvas:test --tests org.graphiks.kanvas.image.ImageMetadataTest`

  Expected: PASS.

- [ ] **Step 6: Commit the contract layer**

  ```bash
  git add kanvas/src/main/kotlin/org/graphiks/kanvas/image/ColorType.kt \
    kanvas/src/main/kotlin/org/graphiks/kanvas/image/ColorTypeCapabilities.kt \
    kanvas/src/main/kotlin/org/graphiks/kanvas/image/Image.kt \
    kanvas/src/main/kotlin/org/graphiks/kanvas/image/ImageInfo.kt \
    kanvas/src/test/kotlin/org/graphiks/kanvas/image/ImageMetadataTest.kt \
    .upstream/specs/kanvas/09-image-and-text.md
  git commit -m "feat(image): catalog canonical pixel formats"
  ```

### Task 2: Faire de `Bitmap` et `Pixmap` les buffers codec canoniques

**Files:**

* Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/image/Pixmap.kt`
* Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/image/Bitmap.kt:1-294`
* Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/image/BitmapTest.kt:1-142`
* Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/image/PixmapTest.kt`
* Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUImagePixels.kt:30-48`

**Interfaces:**

* Consumes `ImageInfo`, `ColorType.capabilities()` and `ImageColorSpace` from
  Task 1.
* Produces `Bitmap(val info: ImageInfo)` with derived `width`, `height`,
  `colorType`, `alphaType` and `colorSpace` properties.
* Produces `Pixmap(info: ImageInfo, data: ByteBuffer, rowBytes: Int)` with
  `width()`, `height()`, `colorType()`, `alphaType()`, `colorSpace()`,
  `addr()` and `getArgb(x, y)`.

- [ ] **Step 1: Add focused tests to the existing bitmap suite and the new pixmap suite**

  Add the following checks; they replace assumptions about an `IntArray`:

  ```kotlin
  val info = ImageInfo.make(2, 1, ColorType.RGBA_8888, AlphaType.UNPREMUL, ImageColorSpace.sRGB())
  val bitmap = Bitmap(info)
  bitmap.setArgb(0, 0, 0x7F123456)
  assertEquals(0x7F123456, bitmap.getArgb(0, 0))

  val pixmap = Pixmap(info, ByteBuffer.allocate(info.minRowBytes()), info.minRowBytes())
  assertEquals(info, pixmap.info)
  assertEquals(info.minRowBytes(), pixmap.rowBytes)
  ```

  Also assert that `Pixmap` rejects a non-empty image whose `rowBytes` is
  smaller than `info.minRowBytes()`.

- [ ] **Step 2: Run the focused tests and observe missing symbols**

  Run: `rtk ./gradlew :kanvas:test --tests org.graphiks.kanvas.image.BitmapTest --tests org.graphiks.kanvas.image.PixmapTest`

  Expected: compilation failure because `Bitmap(ImageInfo)` and `Pixmap` do
  not yet exist.

- [ ] **Step 3: Rework `Bitmap` around `ImageInfo` without changing pixel semantics**

  Make `Bitmap(info)` the primary constructor and retain convenience
  constructors that build an `ImageInfo`. Allocate only if
  `colorType.capabilities().allocatable`; reject all other concrete formats
  before allocating. Preserve `getPixel`, `setPixel`, `getArgb`, `setArgb`,
  `eraseColor`, subset and shader behavior for the migration formats. Add
  public guarded F16 methods:

  ```kotlin
  fun setPremulRgbaF16(x: Int, y: Int, r: Float, g: Float, b: Float, a: Float)
  fun getPremulRgbaF16(x: Int, y: Int, out: FloatArray): Boolean
  ```

  They must reject non-F16 formats and retain the old premultiplied storage
  convention. Do not expose an ARGB `IntArray`.

- [ ] **Step 4: Implement the immutable `Pixmap` view**

  Duplicate the supplied buffer in little-endian order, validate stride, and
  implement byte-size and ARGB reads only for capabilities that are active.
  An unavailable format or out-of-bounds pixel returns the documented codec
  refusal value through its caller; `Pixmap` itself does not convert it to
  another format.

- [ ] **Step 5: Keep the renderer boundary explicit**

  Make `Bitmap.toImage()` use `ImageColorSpace.toColorSpaceOrNull()`. Return
  a nullable result (or a typed failure result) when the profile is not
  representable; update `GPUImagePixels.kt` to handle that result without
  retagging it as sRGB.

- [ ] **Step 6: Run Kanvas image tests**

  Run: `rtk ./gradlew :kanvas:test --tests org.graphiks.kanvas.image.BitmapTest --tests org.graphiks.kanvas.image.PixmapTest --tests org.graphiks.kanvas.image.ImageMetadataTest`

  Expected: PASS.

- [ ] **Step 7: Commit bitmap and pixmap migration**

  ```bash
  git add kanvas/src/main/kotlin/org/graphiks/kanvas/image \
    kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUImagePixels.kt \
    kanvas/src/test/kotlin/org/graphiks/kanvas/image
  git commit -m "feat(image): add canonical codec pixmap"
  ```

### Task 3: Basculer le contrat public `Codec` vers les types Kanvas

**Files:**

* Modify: `codec/api/src/main/kotlin/org/graphiks/kanvas/codec/Codec.kt:1-300`
* Modify: `codec/api/src/main/kotlin/org/graphiks/kanvas/codec/CodecImageDecoder.kt:1-55`
* Delete: `codec/api/src/main/kotlin/org/graphiks/kanvas/codec/KanvasCodec.kt`
* Modify: `codec/api/src/test/kotlin/org/graphiks/kanvas/codec/CodecImageDecoderColorSpaceTest.kt:1-134`
* Delete: `codec/api/src/test/kotlin/org/graphiks/kanvas/codec/KanvasCodecColorSpaceTest.kt`
* Modify: `codec/api/src/test/kotlin/org/graphiks/kanvas/codec/CodecStreamLimitTest.kt:1-180`

**Interfaces:**

* Consumes the Task 1 image metadata and Task 2 bitmap.
* Produces `abstract fun getInfo(): ImageInfo` and
  `abstract fun getPixels(info: ImageInfo, dst: Bitmap): Codec.Result`.
* Produces `fun getImage(info: ImageInfo = getInfo()): Pair<Bitmap?, Result>`.

- [ ] **Step 1: Convert API fakes in existing tests before production signatures**

  In `CodecImageDecoderColorSpaceTest`, make `FakeCodec` return an
  `ImageInfo` and write its sample through the canonical method:

  ```kotlin
  override fun getInfo(): ImageInfo = ImageInfo.make(
      width = 1, height = 1, colorType = ColorType.RGBA_8888,
      alphaType = AlphaType.UNPREMUL, colorSpace = colorSpace,
  )

  override fun getPixels(info: ImageInfo, dst: Bitmap): Result {
      dst.setArgb(0, 0, SAMPLE_ARGB)
      return Result.kSuccess
  }
  ```

  Replace the adapter tests with direct assertions on `ImageInfo.colorSpace`.

- [ ] **Step 2: Run the API test task and observe the old signatures fail**

  Run: `rtk ./gradlew :codec:api:test`

  Expected: compilation failure because `Codec` still requires `SkImageInfo`
  and `SkBitmap`.

- [ ] **Step 3: Change `Codec` and preserve all result semantics**

  Replace imports, signatures, allocation in `getImage`, KDoc links and
  validation language. `getPixels` must keep the exact distinction:
  destination mismatch → `kInvalidParameters`; declared but unavailable
  conversion → `kInvalidConversion`; geometry scaling unsupported →
  `kInvalidScale`.

- [ ] **Step 4: Simplify `CodecImageDecoder` and remove the identity adapter**

  Delete `KanvasCodec.kt`. In `CodecImageDecoder`, obtain the renderer color
  only through `bitmap.colorSpace.toColorSpaceOrNull()` and return
  `ImageDecodeResult.Failure("codec.color-space-unsupported:<reason>")` when
  it is absent. Copy only a canonical RGBA bitmap into `Image.pixels`; other
  target types return their codec refusal instead of being reinterpreted.

- [ ] **Step 5: Run the codec API tests**

  Run: `rtk ./gradlew :codec:api:test`

  Expected: PASS.

- [ ] **Step 6: Commit the codec API migration**

  ```bash
  git add codec/api/src/main codec/api/src/test
  git commit -m "refactor(codec): expose canonical image types"
  ```

### Task 4: Migrer PNG et JPEG, y compris ICC, F16 et orientation

**Files:**

* Modify: `codec/png/src/main/kotlin/org/graphiks/kanvas/codec/png/PngCodec.kt:35-300`
* Modify: `codec/png/src/main/kotlin/org/graphiks/kanvas/codec/png/PngEncoder.kt:40-420`
* Modify: `codec/jpeg/src/main/kotlin/org/graphiks/kanvas/codec/jpeg/JpegCodec.kt:24-140`
* Modify: `codec/jpeg/src/main/kotlin/org/graphiks/kanvas/codec/jpeg/JpegEncoder.kt:1-170`
* Modify: `codec/jpeg/src/main/kotlin/org/graphiks/kanvas/codec/jpeg/JpegHierarchy.kt:540-630`
* Modify: `codec/common/src/main/kotlin/org/skia/utils/PixmapUtils.kt:1-156`
* Modify: codec PNG/JPEG tests that import `SkBitmap`, `SkImageInfo`,
  `SkColorType` or `SkPixmap`.

**Interfaces:**

* Consumes `Codec.getPixels(ImageInfo, Bitmap)` and `Pixmap`.
* Produces `PngCodec` and `JpegCodec` target checks against
  `ColorType.RGBA_8888` and `ColorType.RGBA_F16_NORM` only.
* Produces pure Kanvas `PixmapUtils.orient(dst: Bitmap, src: Bitmap, origin)`
  and `swapWidthHeight(info: ImageInfo)` in a Kanvas package.

- [ ] **Step 1: Convert the existing PNG/JPEG tests to `ImageInfo` and `Bitmap`**

  Replace construction such as `SkImageInfo.Make(...kRGBA_F16Norm...)` with:

  ```kotlin
  val info = ImageInfo.make(
      width = 2, height = 1,
      colorType = ColorType.RGBA_F16_NORM,
      alphaType = AlphaType.PREMUL,
      colorSpace = ImageColorSpace.sRGB(),
  )
  val dst = Bitmap(info)
  ```

  Replace direct ARGB arrays with `setArgb`/`getArgb`; replace test pixmaps
  with `Pixmap(info, byteBuffer, rowBytes)`.

- [ ] **Step 2: Run both codec tasks and observe compilation failures**

  Run: `rtk ./gradlew :codec:png:test :codec:jpeg:test`

  Expected: compilation failure until the production codecs use the canonical
  signatures.

- [ ] **Step 3: Migrate PNG decode and encode paths**

  Make `cachedInfo` an `ImageInfo`. Keep identity comparison of
  `ImageColorSpace`, validate bitmap `info` before decoding, and query the
  declared PNG decode targets. Replace F16 writes with
  `setPremulRgbaF16`. Replace PNG `SkPixmap` overloads with `Pixmap`; copy
  source rows using its stride, not a tightly packed assumption.

- [ ] **Step 4: Migrate JPEG decode, hierarchy and encode paths**

  Preserve JPEG’s oriented allocation, F16-normalized decode target and ICC
  writing. Convert `DecodedPixels.rgba8888` through `Bitmap.setArgb` and
  `DecodedPixels.rgbaF16` through `setPremulRgbaF16`. Route EXIF orientation
  through the relocated Kanvas `PixmapUtils`; use `Pixmap` for encoder
  overloads.

- [ ] **Step 5: Run PNG/JPEG verification**

  Run: `rtk ./gradlew :codec:png:test :codec:jpeg:test :codec:common:test`

  Expected: PASS.

- [ ] **Step 6: Commit the ICC/F16 codec paths**

  ```bash
  git add codec/png codec/jpeg codec/common
  git commit -m "refactor(codec): migrate png and jpeg raster contracts"
  ```

### Task 5: Migrer les décodeurs de formats enregistrés restants

**Files:**

* Modify: `codec/bmp/src/main/kotlin/org/graphiks/kanvas/codec/bmp/BmpCodec.kt`
* Modify: `codec/gif/src/main/kotlin/org/graphiks/kanvas/codec/gif/GifCodec.kt`
* Modify: `codec/wbmp/src/main/kotlin/org/graphiks/kanvas/codec/wbmp/WbmpCodec.kt`
* Modify: `codec/webp/src/main/kotlin/org/graphiks/kanvas/codec/webp/WebpCodec.kt`
* Modify: `codec/jpeg-ls/src/main/kotlin/org/graphiks/kanvas/codec/jpegls/JpegLsCodec.kt`
* Modify: `codec/jpeg2000/src/main/kotlin/org/graphiks/kanvas/codec/jpeg2000/Jpeg2000Codec.kt`
* Modify: `codec/jpegxl/src/main/kotlin/org/graphiks/kanvas/codec/jpegxl/JpegXlCodec.kt`
* Modify: associated `src/test/kotlin` files in each listed module.

**Interfaces:**

* Consumes canonical `ImageInfo`, `Bitmap`, `ColorType` and `AlphaType`.
* Produces no direct `org.skia.foundation` import in a registered decoder.

- [ ] **Step 1: Update existing decoder fixtures to use semantic pixel helpers**

  Replace patterns such as `bitmap.pixels8888[index] = argb` with:

  ```kotlin
  bitmap.setArgb(index % bitmap.width, index / bitmap.width, argb)
  ```

  For whole-image equality, compare `(x, y) → getArgb(x, y)` rather than the
  removed backing `IntArray`.

- [ ] **Step 2: Run the affected test tasks and observe old type references**

  Run: `rtk ./gradlew :codec:bmp:test :codec:gif:test :codec:wbmp:test :codec:webp:test :codec:jpeg-ls:test :codec:jpeg2000:test :codec:jpegxl:test`

  Expected: compilation failure until each decoder override accepts canonical
  metadata and bitmap types.

- [ ] **Step 3: Migrate metadata and destination validation in every decoder**

  Replace each cached `SkImageInfo` with `ImageInfo.make`, each destination
  allocation with `Bitmap(info)`, and each `SkColorType` comparison with the
  matching canonical `ColorType`. Preserve existing `Result` distinctions and
  make every non-migration target return `kInvalidConversion`.

- [ ] **Step 4: Migrate frame/copy data flow without ARGB-array aliases**

  GIF/WebP animation copies use a loop over `setArgb`/`getArgb`. JPEG-LS,
  JPEG 2000 and JPEG XL sample writers construct a `Bitmap` with source
  metadata and write semantic pixels. Do not expose a replacement `IntArray`
  property merely to retain `System.arraycopy`.

- [ ] **Step 5: Run the registered decoder matrix**

  Run: `rtk ./gradlew :codec:bmp:test :codec:gif:test :codec:wbmp:test :codec:webp:test :codec:jpeg-ls:test :codec:jpeg2000:test :codec:jpegxl:test`

  Expected: PASS.

- [ ] **Step 6: Commit registered decoder migration**

  ```bash
  git add codec/bmp codec/gif codec/wbmp codec/webp codec/jpeg-ls codec/jpeg2000 codec/jpegxl
  git commit -m "refactor(codec): migrate registered decoders to Bitmap"
  ```

### Task 6: Migrer les encodeurs et les appels `Pixmap`

**Files:**

* Modify: `codec/bmp/src/main/kotlin/org/graphiks/kanvas/codec/bmp/BmpEncoder.kt`
* Modify: `codec/gif/src/main/kotlin/org/graphiks/kanvas/codec/gif/GifEncoder.kt`
* Modify: `codec/ico/src/main/kotlin/org/graphiks/kanvas/codec/ico/IcoEncoder.kt`
* Modify: `codec/jpeg-ls/src/main/kotlin/org/graphiks/kanvas/codec/jpegls/JpegLsEncoder.kt`
* Modify: `codec/wbmp/src/main/kotlin/org/graphiks/kanvas/codec/wbmp/WbmpEncoder.kt`
* Modify: `codec/webp/src/main/kotlin/org/graphiks/kanvas/codec/webp/WebpEncoder.kt`
* Modify: associated encoder tests under `codec/**/src/test/kotlin`.

**Interfaces:**

* Consumes `Bitmap` and, for PNG/JPEG already migrated in Task 4, `Pixmap`.
* Produces encoder refusals for `UNKNOWN` and formats not in each encoder’s
  declared source set.

- [ ] **Step 1: Update current encoder test fixtures to semantic writes**

  Replace direct mutations such as `src.pixels[index] = 0xFF808080.toInt()`
  with `src.setArgb(x, y, 0xFF808080.toInt())`. Preserve the existing fixture
  dimensions, alpha values and byte-level encoded expectations.

- [ ] **Step 2: Run encoder-owning module tests and observe obsolete types**

  Run: `rtk ./gradlew :codec:bmp:test :codec:gif:test :codec:ico:test :codec:jpeg-ls:test :codec:wbmp:test :codec:webp:test`

  Expected: compilation failure while an encoder still takes `SkBitmap`,
  `SkImage` or `SkData`.

- [ ] **Step 3: Change every encoder boundary to pure Kanvas data**

  Accept `Bitmap` for raster input. Change WebP `encodeAsData` to return a
  defensively copied `ByteArray?`; change image overloads to Kanvas `Image`
  only where a renderer image is semantically required. Convert image pixels
  through `Bitmap.fromImage` and its explicit color-space boundary. Keep a
  failed format request side-effect free.

- [ ] **Step 4: Declare encoder format support at the encoder, not in `:kanvas`**

  Every `encode` entry point checks its own supported source `ColorType` set
  before touching an output stream. For example, an encoder that only writes
  RGBA8 returns `null`/`false` for `RGBA_1010102`, even though the enum names
  that deferred target.

- [ ] **Step 5: Run encoder verification**

  Run: `rtk ./gradlew :codec:bmp:test :codec:gif:test :codec:ico:test :codec:jpeg-ls:test :codec:wbmp:test :codec:webp:test`

  Expected: PASS.

- [ ] **Step 6: Commit encoder migration**

  ```bash
  git add codec/bmp codec/gif codec/ico codec/jpeg-ls codec/wbmp codec/webp
  git commit -m "refactor(codec): migrate encoder raster inputs"
  ```

### Task 7: Nettoyer les consommateurs périphériques et les intégrations

**Files:**

* Modify: `codec/android/src/main/kotlin/org/graphiks/kanvas/codec/AndroidCodec.kt`
* Modify: `codec/animated/src/main/kotlin/org/graphiks/kanvas/codec/AnimatedImage.kt`
* Modify: `codec/image-generator/src/main/kotlin/org/graphiks/kanvas/codec/ImageCodecs.kt`
* Modify: `codec/image-generator/src/main/kotlin/org/graphiks/kanvas/codec/ImageGeneratorImages.kt`
* Modify: `codec/extended/src/main/kotlin/org/graphiks/kanvas/codec/{AvifDecoder,JpegxlDecoder,RawDecoder,VideoDecoder}.kt`
* Modify: `codec/ico/src/main/kotlin/org/graphiks/kanvas/codec/IcoDecoder.kt`
* Modify: `codec/test-fixtures/src/main/kotlin/org/graphiks/kanvas/codec/test/CodecTestFixtures.kt`
* Modify: `integration-tests/skia/src/test/kotlin/org/skia/codec/SkAnimCodecPlayer.kt`
* Modify: `integration-tests/test-utils/src/main/kotlin/org/graphiks/kanvas/test/ComparisonUtils.kt`
* Modify: the tests paired with Android, animation, image-generator and ICO.

**Interfaces:**

* Consumes only canonical image classes and `ByteArray`.
* Produces no source-level dependency on the deleted compatibility file, even
  in Gradle modules that are currently not registered.

- [ ] **Step 1: Replace `SkData` and image snapshots in peripheral APIs**

  Use `ByteArray` for AVIF, JPEG XL, RAW and ICO decode entry points. Make
  animation and generator snapshots return Kanvas `Image` or `Bitmap` through
  the explicit `ImageColorSpace` projection. Delete stale calls such as
  `displayFrame.asImage()` that depend on methods absent from the canonical
  API.

- [ ] **Step 2: Migrate Android and fixture destination allocation**

  Construct `Bitmap(ImageInfo)` and preserve Android’s wire-layout handling
  with `ColorType` branches. Replace `SkPixmap` references in prose and code
  with `Pixmap` or `Bitmap` as appropriate.

- [ ] **Step 3: Migrate integration helpers without changing generated scores**

  Convert `SkAnimCodecPlayer` and `ComparisonUtils` to semantic bitmap/F16
  access. Do not run a score regeneration task and do not stage
  `test-similarity-scores.properties`.

- [ ] **Step 4: Compile declared support modules and statically check the unregistered ones**

  Run: `rtk ./gradlew :codec:ico:test :codec:test-fixtures:test :codec:extended:test`

  Run: `rtk rg -n --glob '*.kt' '^import org\.skia\.foundation' codec/android codec/animated codec/image-generator codec/extended codec/ico codec/test-fixtures integration-tests`

  Expected: declared modules PASS; the search returns no compatibility import
  in the listed source roots.

- [ ] **Step 5: Commit peripheral cleanup**

  ```bash
  git add codec/android codec/animated codec/image-generator codec/extended \
    codec/ico codec/test-fixtures integration-tests/skia integration-tests/test-utils
  git commit -m "refactor(codec): remove peripheral Skia compatibility types"
  ```

### Task 8: Supprimer la compatibilité et effectuer la validation finale

**Files:**

* Delete: `kanvas/src/main/kotlin/org/skia/foundation/SkCodecCompat.kt`
* Delete: `codec/api/src/main/kotlin/org/graphiks/kanvas/codec/KanvasCodec.kt` if not deleted in Task 3.
* Modify: every remaining Kotlin source returned by the static scan in this
  task.

**Interfaces:**

* Consumes all canonical contracts produced by Tasks 1–7.
* Produces a source tree where codec functionality compiles without
  `org.skia.foundation`.

- [ ] **Step 1: Run the removal scan before deleting the file**

  Run:

  ```bash
  rtk rg -n --glob '*.kt' '^import org\.skia\.foundation' codec kanvas integration-tests
  ```

  Expected: only explicitly identified remaining migration imports are
  returned. KDoc may still mention upstream Skia names for provenance.

- [ ] **Step 2: Delete compatibility code and resolve every remaining call site**

  Remove `SkCodecCompat.kt`, delete the adapter file if it remains, and repeat
  the scan. KDoc may mention Skia upstream for provenance, but no import,
  type signature or executable reference may remain.

- [ ] **Step 3: Run the full declared codec matrix**

  Run:

  ```bash
  rtk ./gradlew :codec:api:test :codec:bmp:test :codec:common:test \
    :codec:core:test :codec:extended:test :codec:gif:test :codec:ico:test \
    :codec:jpeg:test :codec:jpeg-ls:test :codec:jpeg2000:test \
    :codec:jpegxl:test :codec:png:test :codec:test-fixtures:test \
    :codec:wbmp:test :codec:webp:test
  ```

  Expected: PASS.

- [ ] **Step 4: Verify source cleanliness and diff integrity**

  Run:

  ```bash
  rtk rg -n --glob '*.kt' '^import org\.skia\.foundation' codec kanvas integration-tests
  rtk test ! -e kanvas/src/main/kotlin/org/skia/foundation/SkCodecCompat.kt
  rtk git diff --check
  rtk git status --short
  ```

  Expected: the compatibility import search has no result and the deleted file
  is absent; `git diff --check` is clean; generated Skia score changes are
  unstaged and excluded.

- [ ] **Step 5: Commit removal and request review**

  ```bash
  git add kanvas codec integration-tests
  git restore --staged integration-tests/skia/test-similarity-scores.properties
  git commit -m "refactor(codec): remove SkCodecCompat"
  ```

  Do not use `git restore` on the working copy of the score file; only ensure
  it is absent from the index.

## Plan Self-Review

* **Spec coverage:** Task 1 delivers the exhaustive catalogue and ICC
  metadata; Task 2 delivers owned and viewed raster storage; Task 3 changes
  the public codec API and renderer boundary; Tasks 4–6 migrate registered
  codecs; Task 7 covers unregistered and integration consumers; Task 8
  removes the compatibility implementation and validates the result.
* **No-placeholder scan:** Every implementation task names concrete files,
  methods, test command and commit. Deferred formats are intentionally
  catalogued but explicitly unavailable; they are not unspecified work.
* **Type consistency:** `ImageInfo`, `Bitmap`, `Pixmap`, `ColorType`,
  `ImageColorSpace` and `ByteArray` are the only replacement vocabulary used
  by later tasks; the codec API signatures from Task 3 match every later
  decoder/encoder task.
