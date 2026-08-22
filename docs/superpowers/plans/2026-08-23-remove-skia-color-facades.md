# Remove Skia Color Facades Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `SkICC`, `SkcmsICCProfile`, and `SkColorSpace` with native color-management types without changing current codec acceptance and refusal behavior.

**Architecture:** `IccProfile` owns immutable encoded ICC provenance and delegates parsing/writing to the established parser and writer. `ImageColorSpace` owns image-facing profile metadata and derives Matrix/TRC support from `ColorProfile`. Kanvas and every codec then depend on these native types; no production source retains a Skia color facade.

**Tech Stack:** Kotlin/JVM, Gradle, `color-management`, `kanvas`, format codecs, Kotlin test/JUnit 5.

**Spec:** `docs/superpowers/specs/2026-08-23-native-color-and-icc-types-design.md`

## Global Constraints

- Breaking public API is allowed because the project is in incubation.
- Retain all real numerical operations in `math:color`.
- Do not add a dependency from `color-management` to `kanvas` or codec modules.
- Snapshot ICC bytes on input and return a fresh copy from all public byte getters.
- Preserve existing Matrix/TRC support and refusal codes; HDR/LUT support expansion is out of scope.
- Do not retain production imports or declarations of `SkICC`, `SkcmsICCProfile`, or `SkColorSpace`.

---

### Task 1: Add immutable native ICC provenance

**Files:**
- Create: `color-management/src/main/kotlin/org/graphiks/kanvas/color/icc/IccProfile.kt`
- Create: `color-management/src/test/kotlin/org/graphiks/kanvas/color/icc/IccProfileTest.kt`
- Modify: `color-management/src/main/kotlin/org/graphiks/kanvas/color/icc/IccProfileWriter.kt` only if its existing API cannot create an `IccProfile` directly.

**Interfaces:**
- Consumes: `ColorProfile`, `ColorProfileParseResult`, `IccProfileParser`, `IccProfileWriter`, and `IccParseLimits`.
- Produces: `IccProfile.parse(bytes, limits): IccProfileParseResult`, `IccProfile.fromMatrixTrc(profile): IccProfile`, immutable `bytes`, `size`, `tagCount`, `hasTrc`, and `hasToXyzD50`.

- [ ] **Step 1: Write the failing parsing and byte-ownership test**

```kotlin
@Test fun `parses and retains an ICC byte snapshot`() {
    val source = IccProfileWriter.writeMatrixTrc(ColorProfiles.displayP3())
    val profile = IccProfile.parse(source, IccParseLimits()).getOrThrow()
    val expected = profile.bytes
    source[0] = 0
    val returned = profile.bytes
    returned[1] = 0
    assertEquals(ColorSpace.DISPLAY_P3, profile.colorProfile.toColorSpaceOrNull())
    assertContentEquals(expected, profile.bytes)
}
```

- [ ] **Step 2: Verify RED**

Run: `./gradlew :color-management:test --tests org.graphiks.kanvas.color.icc.IccProfileTest --no-daemon --console=plain`

Expected: compilation fails because `IccProfile` does not exist.

- [ ] **Step 3: Implement the native ICC artifact**

```kotlin
public class IccProfile private constructor(
    public val colorProfile: ColorProfile,
    bytes: ByteArray,
) {
    private val originalBytes = bytes.copyOf()
    public val bytes: ByteArray get() = originalBytes.copyOf()
    public val size: Int get() = originalBytes.size
    public companion object {
        public fun parse(bytes: ByteArray, limits: IccParseLimits = IccParseLimits()): IccProfileParseResult =
            IccProfileParser.parse(bytes.copyOf(), limits).asIccProfileParseResult(bytes)
        public fun fromMatrixTrc(profile: ColorProfile): IccProfile =
            parse(IccProfileWriter.writeMatrixTrc(profile)).getOrThrow()
    }
}
```

Wrap parser failures without changing their code/message and calculate `tagCount` from the immutable snapshot only after checking the ICC header length.

- [ ] **Step 4: Verify GREEN**

Run: `./gradlew :color-management:test --tests org.graphiks.kanvas.color.icc.IccProfileTest --tests org.graphiks.kanvas.color.icc.IccProfileParserTest --tests org.graphiks.kanvas.color.icc.IccProfileWriterTest --no-daemon --console=plain`

Expected: PASS.

- [ ] **Step 5: Commit**

Run: `git add color-management/src/main/kotlin/org/graphiks/kanvas/color/icc/IccProfile.kt color-management/src/test/kotlin/org/graphiks/kanvas/color/icc/IccProfileTest.kt && git commit -m "feat(color): add immutable ICC profiles"`

### Task 2: Add native image color metadata

**Files:**
- Create: `color-management/src/main/kotlin/org/graphiks/kanvas/color/ImageColorSpace.kt`
- Create: `color-management/src/test/kotlin/org/graphiks/kanvas/color/ImageColorSpaceTest.kt`

**Interfaces:**
- Consumes: `ColorProfile`, `ColorProfiles`, `IccProfile`, `ColorSpaceClassification`, `ColorTransferFunction`, and `ColorMatrix3x3F32`.
- Produces: `ImageColorSpace`, `ImageColorSpaceProfileStatus`, `sRGB()`, `linearSrgb()`, `fromMatrixTrc(...)`, `fromColorProfile(...)`, `fromIccProfile(...)`, `isSrgb()`, `isLinear()`, and `toColorSpaceOrNull()`.

- [ ] **Step 1: Write failing support and refusal tests**

```kotlin
@Test fun `retains a LUT ICC profile with its existing refusal code`() {
    val space = ImageColorSpace.fromIccProfile(parseResource("rgb-lut-a2b-b2a.icc"))
    assertEquals(ImageColorSpaceProfileStatus.UNSUPPORTED, space.profileStatus)
    assertEquals("icc.profile.shape.unsupported", space.profileRefusalCode)
    assertNotNull(space.iccProfile)
}

@Test fun `creates supported custom Matrix TRC image color spaces`() {
    val space = requireNotNull(ImageColorSpace.fromMatrixTrc(ColorTransferFunction.sRgb, p3Matrix))
    assertEquals(ImageColorSpaceProfileStatus.SUPPORTED, space.profileStatus)
    assertEquals(ColorSpace.DISPLAY_P3, space.toColorSpaceOrNull())
}
```

- [ ] **Step 2: Verify RED**

Run: `./gradlew :color-management:test --tests org.graphiks.kanvas.color.ImageColorSpaceTest --no-daemon --console=plain`

Expected: compilation fails because `ImageColorSpace` does not exist.

- [ ] **Step 3: Implement the profile-derived metadata type**

The class retains `colorProfile` and optional `iccProfile`, derives non-null Matrix/TRC access only for `SUPPORTED` profiles, classifies grayscale/HDR/LUT/explicitly unsupported input with the stable existing codes, and delegates named-space mapping to `ColorProfile.toColorSpaceOrNull()`.

- [ ] **Step 4: Verify GREEN**

Run: `./gradlew :color-management:test --tests org.graphiks.kanvas.color.ImageColorSpaceTest --tests org.graphiks.kanvas.color.ColorSpaceTest --no-daemon --console=plain`

Expected: PASS.

- [ ] **Step 5: Commit**

Run: `git add color-management/src/main/kotlin/org/graphiks/kanvas/color/ImageColorSpace.kt color-management/src/test/kotlin/org/graphiks/kanvas/color/ImageColorSpaceTest.kt && git commit -m "feat(color): add native image color spaces"`

### Task 3: Migrate image and codec production APIs

**Files:**
- Delete: `kanvas/src/main/kotlin/org/skia/foundation/SkICC.kt`
- Delete: `kanvas/src/main/kotlin/org/skia/foundation/skcms/SkcmsCompat.kt`
- Modify: `kanvas/src/main/kotlin/org/skia/foundation/SkCodecCompat.kt`
- Modify: `codec/api/src/main/kotlin/org/graphiks/kanvas/codec/Codec.kt`
- Modify: `codec/api/src/main/kotlin/org/graphiks/kanvas/codec/KanvasCodec.kt`
- Modify: `codec/android/src/main/kotlin/org/graphiks/kanvas/codec/AndroidCodec.kt`
- Modify: `codec/{bmp,gif,jpeg-ls,jpeg,jpeg2000,jpegxl,png,wbmp,webp}/src/main/kotlin/**/*.kt`
- Modify: `integration-tests/test-utils/src/main/kotlin/org/graphiks/kanvas/test/ComparisonUtils.kt`

**Interfaces:**
- Consumes: `IccProfile` and `ImageColorSpace` from `color-management`.
- Produces: `Codec.getICCProfile(): IccProfile?`; `SkImageInfo` and `SkBitmap` carry `ImageColorSpace`; all format codec metadata stores `IccProfile?`.

- [ ] **Step 1: Add a failing codec API test for the native return type**

```kotlin
@Test fun `codec exposes an immutable native ICC profile`() {
    val embedded = IccProfile.fromMatrixTrc(ColorProfiles.displayP3())
    val codec = FakeCodec(ImageColorSpace.fromIccProfile(embedded), embedded)
    val expected = assertNotNull(codec.getICCProfile()).bytes
    codec.getICCProfile()!!.bytes[0] = 0
    assertContentEquals(expected, codec.getICCProfile()!!.bytes)
}
```

- [ ] **Step 2: Verify RED**

Run: `./gradlew :codec:api:test --tests org.graphiks.kanvas.codec.CodecImageDecoderColorSpaceTest --no-daemon --console=plain`

Expected: compilation fails while `getICCProfile()` returns `SkcmsICCProfile`.

- [ ] **Step 3: Migrate production sources mechanically while preserving behavior**

Replace `SkColorSpace.makeSRGB()` / `makeSRGBLinear()` / `makeProfileAware()` with native `ImageColorSpace` factories. Replace `SkcmsICCProfile` parser and storage with `IccProfile`. Use `IccProfileWriter.writeMatrixTrc(ColorProfile(...))` in JPEG and PNG encoders. Do not leave compatibility aliases.

- [ ] **Step 4: Compile consumers and run codec tests**

Run: `./gradlew :kanvas:compileTestKotlin :codec:api:test :codec:bmp:test :codec:jpeg:test :codec:png:test :codec:webp:test :integration-tests:test-utils:compileTestKotlin --no-daemon --console=plain`

Expected: PASS, with no source declaration/import of the retired facades.

- [ ] **Step 5: Commit**

Run: `git add kanvas codec integration-tests && git commit -m "refactor(color): retire Skia ICC facades"`

### Task 4: Retire facade tests and validate the full boundary

**Files:**
- Delete: `kanvas/src/test/kotlin/org/skia/foundation/SkICCTest.kt`
- Delete: `kanvas/src/test/kotlin/org/skia/foundation/SkColorSpaceCompatTest.kt`
- Delete: `kanvas/src/test/kotlin/org/skia/foundation/skcms/SkcmsCompatTest.kt`
- Modify: all codec tests importing the retired facades, replacing fixtures with `IccProfile`, `ImageColorSpace`, `ColorProfile`, and `IccProfileWriter`.

**Interfaces:**
- Consumes: completed native production API from Tasks 1–3.
- Produces: test-only color fixtures with no Skia color facade references.

- [ ] **Step 1: Replace one facade test with a native behavior test**

```kotlin
@Test fun `matrix TRC writing is available without a Skia facade`() {
    val icc = IccProfile.fromMatrixTrc(ColorProfiles.displayP3())
    assertEquals(ColorProfiles.displayP3(), icc.colorProfile)
    assertTrue(icc.bytes.isNotEmpty())
}
```

- [ ] **Step 2: Verify the native replacement test**

Run: `./gradlew :color-management:test --tests org.graphiks.kanvas.color.icc.IccProfileTest --no-daemon --console=plain`

Expected: PASS.

- [ ] **Step 3: Delete facade tests and migrate remaining test imports**

Run: `rg -n '\\b(SkICC|SkcmsICCProfile|SkColorSpace)\\b' --glob '*.kt'`

Expected: no output after all test fixtures are native.

- [ ] **Step 4: Run final scoped verification**

Run: `./gradlew :math:color:jvmTest :color-management:test :kanvas:test :codec:api:test :codec:bmp:test :codec:jpeg:test :codec:png:test :codec:webp:test :integration-tests:skia:compileTestKotlin --no-daemon --console=plain`

Expected: PASS. If an unrelated Kanvas test fails, establish whether it also fails on `origin/master` before reporting a baseline failure.

- [ ] **Step 5: Audit and commit**

Run: `rg -n '\\b(SkICC|SkcmsICCProfile|SkColorSpace)\\b' --glob '*.kt' && git diff --check origin/master..HEAD && git add kanvas codec color-management integration-tests && git commit -m "test(color): remove Skia color facade coverage"`
