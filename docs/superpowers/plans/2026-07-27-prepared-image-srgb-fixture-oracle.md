# Prepared-image sRGB Fixture Oracle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the shared FP-04 image fixtures mutation-safe and add an independent CPU oracle for the exact straight-sRGB-upload, WebGPU-sampling, premultiplication/tint, and sRGB-store contract.

**Architecture:** Keep raw RGBA byte helpers separate from the physical color oracle. Raw helpers own only byte-layout, texel-center, clamp, and geometry evidence; the physical oracle starts from immutable straight encoded sRGB upload bytes and reproduces the bounded GPU color calculation without reimplementing artifact preparation.

**Tech Stack:** Kotlin/JVM, `kotlin.test`, JUnit 5, Gradle 9.2, JDK 25.

## Global Constraints

- Follow `docs/superpowers/specs/2026-07-27-prepared-image-srgb-fixture-oracle-design.md`.
- Modify test fixtures and test-oracle code only; do not modify production rendering.
- Keep product routing, the image legacy allowlist, and `unsupported.image.animation` unchanged.
- Every public fixture byte-array access returns a fresh copy.
- Raw helpers use explicit `rawRgba...` names and are never treated as GPU sRGB references.
- The physical oracle consumes straight encoded sRGB bytes from `GPUPreparedImageUploadArtifact.tightRgba8BytesForUpload()`.
- Linear sampling uses `u * width - 0.5` and `v * height - 0.5`, with integer texel indices clamped to the edge.
- Source-rectangle UVs are absolute normalized full-image coordinates and are never remapped to `[0, 1]`.
- Color calculations use the IEC 61966-2-1 sRGB transfer, linear UNORM alpha, post-sampling premultiplication, one component-wise premultiplied tint, and nearest-integer RGBA8 quantization.
- Keep `maxChannelDelta <= 1` as the only tolerance for physical linear-sampling comparisons.
- Do not integrate commit `a1310239d`; its `.worktrees/` ignore change is unrelated.

---

### Task 1: Make every shared fixture access mutation-safe

**Files:**

- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageTestFixtures.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageTestFixturesTest.kt`

**Interfaces:**

- Consumes: the existing five fixture names and their current dimensions, color types, and bytes.
- Produces: the same five `...Bytes: ByteArray` properties, each returning a fresh copy.

- [ ] **Step 1: Replace the tautological hash test with one failing isolation test**

Replace `fixtures hash is stable` with:

```kotlin
@Test
fun `every fixture access returns an isolated byte snapshot`() {
    val fixtures = listOf<() -> ByteArray>(
        { GPUPreparedImageTestFixtures.rgbaPremul2x2Bytes },
        { GPUPreparedImageTestFixtures.bgraOpaque2x2Bytes },
        { GPUPreparedImageTestFixtures.a8_3x1Bytes },
        { GPUPreparedImageTestFixtures.imageNine6x6Bytes },
        { GPUPreparedImageTestFixtures.atlas4x4Bytes },
    )

    fixtures.forEachIndexed { index, fixture ->
        val expected = fixture().copyOf()
        val mutated = fixture()
        mutated[0] = (mutated[0].toInt() xor 0xff).toByte()
        assertArrayEquals(expected, fixture(), "fixture $index leaked a mutation")
    }
}
```

The production change this test catches is returning shared mutable storage
from any public fixture property.

- [ ] **Step 2: Run the isolation test and verify RED**

Run:

```bash
rtk proxy ./gradlew :kanvas:test \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUPreparedImageTestFixturesTest.every fixture access returns an isolated byte snapshot' \
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
```

Expected: FAIL because each existing property returns the same mutable
`ByteArray`.

- [ ] **Step 3: Hide fixture storage and return copies**

For literal fixtures, use private storage plus a copying getter:

```kotlin
private val rgbaPremul2x2Storage: ByteArray = byteArrayOf(
    128.toByte(), 0, 0, 128.toByte(),
    0, 128.toByte(), 0, 128.toByte(),
    0, 0, 128.toByte(), 128.toByte(),
    128.toByte(), 128.toByte(), 128.toByte(), 128.toByte(),
)

val rgbaPremul2x2Bytes: ByteArray
    get() = rgbaPremul2x2Storage.copyOf()
```

Apply the same pattern to `bgraOpaque2x2Bytes` and `a8_3x1Bytes`. For generated
fixtures, build the storage once and copy from the getter:

```kotlin
private val imageNine6x6Storage: ByteArray = buildImageNineBytes()
val imageNine6x6Bytes: ByteArray
    get() = imageNine6x6Storage.copyOf()

private val atlas4x4Storage: ByteArray = buildAtlasBytes()
val atlas4x4Bytes: ByteArray
    get() = atlas4x4Storage.copyOf()
```

- [ ] **Step 4: Run the complete fixture class and verify GREEN**

Run:

```bash
rtk proxy ./gradlew :kanvas:test \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUPreparedImageTestFixturesTest' \
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
```

Expected: every existing content test and the new isolation test pass.

- [ ] **Step 5: Commit the fixture-ownership change**

```bash
rtk git add -- \
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageTestFixtures.kt \
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageTestFixturesTest.kt
rtk git commit -m 'test(surface): isolate prepared image fixtures'
```

---

### Task 2: Separate raw RGBA helpers and implement WebGPU texel coordinates

**Files:**

- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImagePixelOracle.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageTestFixturesTest.kt`

**Interfaces:**

- Consumes: raw RGBA buffers with exact `width * height * 4` length.
- Produces: `rawRgbaNearestSample`, `rawRgbaLinearSample`,
  `rawRgbaSourceRectSample`, and `rawRgbaApplyTint`.

- [ ] **Step 1: Add failing texel-center and source-rectangle tests against the existing API**

Add:

```kotlin
@Test
fun `raw linear sampling uses WebGPU texel centers`() {
    val bytes = ia(10, 10, 10, 255, 110, 110, 110, 255)

    assertArrayEquals(
        ia(10, 10, 10, 255),
        GPUPreparedImagePixelOracle.linearSample(bytes, 2, 1, 0.25f, 0.5f),
    )
    assertArrayEquals(
        ia(110, 110, 110, 255),
        GPUPreparedImagePixelOracle.linearSample(bytes, 2, 1, 0.75f, 0.5f),
    )
}

@Test
fun `source rect clamps absolute full image UV without remapping`() {
    val bytes = ia(
        10, 0, 0, 255,
        20, 0, 0, 255,
        30, 0, 0, 255,
        40, 0, 0, 255,
    )

    assertArrayEquals(
        ia(20, 0, 0, 255),
        GPUPreparedImagePixelOracle.sourceRectSample(
            bytes = bytes,
            width = 4,
            height = 1,
            srcL = 0.375f,
            srcT = 0f,
            srcR = 0.625f,
            srcB = 1f,
            u = -1f,
            v = 0.5f,
            sample = GPUPreparedImagePixelOracle.SampleKind.NEAREST,
        ),
    )
    assertArrayEquals(
        ia(30, 0, 0, 255),
        GPUPreparedImagePixelOracle.sourceRectSample(
            bytes = bytes,
            width = 4,
            height = 1,
            srcL = 0.375f,
            srcT = 0f,
            srcR = 0.625f,
            srcB = 1f,
            u = 2f,
            v = 0.5f,
            sample = GPUPreparedImagePixelOracle.SampleKind.NEAREST,
        ),
    )
}
```

- [ ] **Step 2: Run the two new tests and verify behavioral RED**

Run the complete `GPUPreparedImageTestFixturesTest` command from Task 1.

Expected: the class compiles, and the new texel-center and source-rectangle
assertions fail against the old `u * (width - 1)` and source-rectangle remap
formulas.

- [ ] **Step 3: Implement validation and WebGPU coordinate behavior under the existing names**

Add exact input guards:

```kotlin
private fun requireRawRgba(bytes: ByteArray, width: Int, height: Int) {
    require(width > 0 && height > 0)
    require(bytes.size.toLong() == width.toLong() * height.toLong() * 4L)
}

private fun requireFiniteUv(u: Float, v: Float) {
    require(u.isFinite() && v.isFinite())
}
```

Nearest sampling:

```kotlin
val cu = u.coerceIn(0f, 1f)
val cv = v.coerceIn(0f, 1f)
val x = floor(cu * width).toInt().coerceIn(0, width - 1)
val y = floor(cv * height).toInt().coerceIn(0, height - 1)
return readRawRgba(bytes, width, x, y)
```

Linear sampling:

```kotlin
val fx = u.coerceIn(0f, 1f) * width - 0.5f
val fy = v.coerceIn(0f, 1f) * height - 0.5f
val rawX0 = floor(fx).toInt()
val rawY0 = floor(fy).toInt()
val wx1 = fx - rawX0
val wy1 = fy - rawY0
val x0 = rawX0.coerceIn(0, width - 1)
val y0 = rawY0.coerceIn(0, height - 1)
val x1 = (rawX0 + 1).coerceIn(0, width - 1)
val y1 = (rawY0 + 1).coerceIn(0, height - 1)
```

Interpolate unsigned byte channels with `(value + 0.5f).toInt()`.

Source-rectangle sampling:

```kotlin
require(srcL.isFinite() && srcT.isFinite() && srcR.isFinite() && srcB.isFinite())
require(srcL in 0f..1f && srcT in 0f..1f)
require(srcR in 0f..1f && srcB in 0f..1f)
require(srcL < srcR && srcT < srcB)
val imageU = u.coerceIn(srcL, srcR)
val imageV = v.coerceIn(srcT, srcB)
```

Dispatch `imageU`/`imageV` directly to the selected raw sampler.

- [ ] **Step 4: Rerun the complete fixture class and verify coordinate GREEN**

Run the complete class command from Task 1.

Expected: both new coordinate tests and every existing raw sampling test pass.

- [ ] **Step 5: Rename the four raw helpers and every call site**

Rename:

```text
nearestSample    -> rawRgbaNearestSample
linearSample     -> rawRgbaLinearSample
sourceRectSample -> rawRgbaSourceRectSample
applyTint        -> rawRgbaApplyTint
```

Update KDoc links and every test call. Do not keep deprecated aliases: a stale
call must fail to compile rather than silently use a raw helper as a physical
GPU oracle.

- [ ] **Step 6: Rerun the complete fixture class after the API rename**

Run the complete class command from Task 1.

Expected: all raw byte, texel-center, source-rectangle, and tint tests pass
without any old helper declaration or call.

- [ ] **Step 7: Commit the raw-oracle correction**

```bash
rtk git add -- \
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImagePixelOracle.kt \
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageTestFixturesTest.kt
rtk git commit -m 'test(surface): correct raw image sampling oracles'
```

---

### Task 3: Add explicit byte-comparison authority

**Files:**

- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImagePixelOracle.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageTestFixturesTest.kt`

**Interfaces:**

- Consumes: two equal-length byte arrays.
- Produces: `rawExactMatch`, `maxChannelDelta`, and
  `matchesWithinOneLsb`.

- [ ] **Step 1: Add a failing unsigned-delta test**

Add:

```kotlin
@Test
fun `maximum channel delta is unsigned and one LSB bound is strict`() {
    val a = ia(0, 20, 255, 40)
    assertEquals(1, GPUPreparedImagePixelOracle.maxChannelDelta(a, ia(1, 19, 254, 41)))
    assertTrue(GPUPreparedImagePixelOracle.matchesWithinOneLsb(a, ia(1, 19, 254, 41)))
    assertEquals(2, GPUPreparedImagePixelOracle.maxChannelDelta(a, ia(2, 20, 255, 40)))
    assertTrue(!GPUPreparedImagePixelOracle.matchesWithinOneLsb(a, ia(2, 20, 255, 40)))
}
```

Rename the two existing exact-comparison calls from `exactMatch` to
`rawExactMatch`. Leave `linearMatch` calls unchanged until the new comparator
exists.

- [ ] **Step 2: Run the class and verify RED**

Run the complete class command from Task 1.

Expected: compilation fails because `rawExactMatch`, `maxChannelDelta`, and
`matchesWithinOneLsb` do not exist.

- [ ] **Step 3: Implement the three comparison helpers**

```kotlin
fun rawExactMatch(a: ByteArray, b: ByteArray): Boolean =
    a.contentEquals(b)

fun maxChannelDelta(a: ByteArray, b: ByteArray): Int {
    require(a.size == b.size)
    return a.indices.maxOfOrNull { index ->
        abs((a[index].toInt() and 0xff) - (b[index].toInt() and 0xff))
    } ?: 0
}

fun matchesWithinOneLsb(a: ByteArray, b: ByteArray): Boolean =
    maxChannelDelta(a, b) <= 1
```

Replace all remaining `linearMatch` calls with `matchesWithinOneLsb` and
remove the old `exactMatch`/`linearMatch` declarations.

- [ ] **Step 4: Run the complete fixture class and verify GREEN**

Run the complete class command from Task 1.

Expected: exact comparison, unsigned maximum delta, and the strict one-LSB
boundary all pass.

- [ ] **Step 5: Commit the comparison authority**

```bash
rtk git add -- \
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImagePixelOracle.kt \
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageTestFixturesTest.kt
rtk git commit -m 'test(surface): expose prepared image pixel deltas'
```

---

### Task 4: Add the physical straight-sRGB sampling oracle

**Files:**

- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImagePixelOracle.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageTestFixturesTest.kt`

**Interfaces:**

- Consumes: straight encoded sRGB RGBA8 bytes, dimensions, finite UV,
  `SampleKind`, and one premultiplied RGBA tint.
- Produces: encoded premultiplied sRGB RGBA8 bytes from
  `sampleSrgbStraightToEncodedPremul`.

- [ ] **Step 1: Add failing independent color-vector tests**

Add `import kotlin.test.assertFailsWith`, then add:

```kotlin
@Test
fun `linear sRGB midpoint encodes near 188 instead of raw 128`() {
    val result = GPUPreparedImagePixelOracle.sampleSrgbStraightToEncodedPremul(
        straightEncodedSrgb = ia(
            0, 0, 0, 255,
            255, 255, 255, 255,
        ),
        width = 2,
        height = 1,
        u = 0.5f,
        v = 0.5f,
        sample = GPUPreparedImagePixelOracle.SampleKind.LINEAR,
        tintPremultipliedRgba = floatArrayOf(1f, 1f, 1f, 1f),
    )

    assertTrue(
        GPUPreparedImagePixelOracle.matchesWithinOneLsb(
            ia(188, 188, 188, 255),
            result,
        ),
    )
}

@Test
fun `native translucent vector applies premultiplication and paint alpha once`() {
    val result = GPUPreparedImagePixelOracle.sampleSrgbStraightToEncodedPremul(
        straightEncodedSrgb = ia(40, 120, 210, 160),
        width = 1,
        height = 1,
        u = 0.5f,
        v = 0.5f,
        sample = GPUPreparedImagePixelOracle.SampleKind.NEAREST,
        tintPremultipliedRgba = floatArrayOf(0.75f, 0.75f, 0.75f, 0.75f),
    )

    assertTrue(
        GPUPreparedImagePixelOracle.matchesWithinOneLsb(
            ia(25, 84, 150, 120),
            result,
        ),
    )
}

@Test
fun `opaque nearest color survives the physical oracle exactly`() {
    assertArrayEquals(
        ia(40, 120, 210, 255),
        GPUPreparedImagePixelOracle.sampleSrgbStraightToEncodedPremul(
            straightEncodedSrgb = ia(40, 120, 210, 255),
            width = 1,
            height = 1,
            u = 0.5f,
            v = 0.5f,
            sample = GPUPreparedImagePixelOracle.SampleKind.NEAREST,
            tintPremultipliedRgba = floatArrayOf(1f, 1f, 1f, 1f),
        ),
    )
}
```

Add malformed-input coverage:

```kotlin
@Test
fun `physical oracle rejects non premultiplied tint`() {
    assertFailsWith<IllegalArgumentException> {
        GPUPreparedImagePixelOracle.sampleSrgbStraightToEncodedPremul(
            ia(0, 0, 0, 255), 1, 1, 0.5f, 0.5f,
            GPUPreparedImagePixelOracle.SampleKind.NEAREST,
            floatArrayOf(1f, 0f, 0f, 0.5f),
        )
    }
}
```

- [ ] **Step 2: Run the complete fixture class and verify RED**

Run the complete class command from Task 1.

Expected: compilation fails because
`sampleSrgbStraightToEncodedPremul` does not exist.

- [ ] **Step 3: Implement the sRGB transfer and linear sampler**

Add `import kotlin.math.pow`, then add:

```kotlin
private fun decodeSrgb(encoded: Float): Float =
    if (encoded <= 0.04045f) {
        encoded / 12.92f
    } else {
        ((encoded + 0.055f) / 1.055f).pow(2.4f)
    }

private fun encodeSrgb(linear: Float): Float =
    if (linear <= 0.0031308f) {
        linear * 12.92f
    } else {
        1.055f * linear.pow(1f / 2.4f) - 0.055f
    }
```

Decode each texel to straight linear RGBA:

```kotlin
private fun readStraightLinearRgba(
    bytes: ByteArray,
    width: Int,
    x: Int,
    y: Int,
): FloatArray {
    val offset = (y * width + x) * 4
    return floatArrayOf(
        decodeSrgb((bytes[offset].toInt() and 0xff) / 255f),
        decodeSrgb((bytes[offset + 1].toInt() and 0xff) / 255f),
        decodeSrgb((bytes[offset + 2].toInt() and 0xff) / 255f),
        (bytes[offset + 3].toInt() and 0xff) / 255f,
    )
}
```

Reuse the Task 2 texel-coordinate calculation, but interpolate the decoded
`FloatArray` channels instead of bytes. After sampling:

```kotlin
val alpha = sampled[3]
val linearPremul = floatArrayOf(
    sampled[0] * alpha * tintPremultipliedRgba[0],
    sampled[1] * alpha * tintPremultipliedRgba[1],
    sampled[2] * alpha * tintPremultipliedRgba[2],
    alpha * tintPremultipliedRgba[3],
)
return byteArrayOf(
    quantizeUnorm(encodeSrgb(linearPremul[0].coerceIn(0f, 1f))),
    quantizeUnorm(encodeSrgb(linearPremul[1].coerceIn(0f, 1f))),
    quantizeUnorm(encodeSrgb(linearPremul[2].coerceIn(0f, 1f))),
    quantizeUnorm(linearPremul[3].coerceIn(0f, 1f)),
)
```

Use:

```kotlin
private fun quantizeUnorm(value: Float): Byte =
    (value * 255f).roundToInt().coerceIn(0, 255).toByte()
```

Before decoding, require exact RGBA length, finite UVs, and:

```kotlin
require(tintPremultipliedRgba.size == 4)
require(tintPremultipliedRgba.all { it.isFinite() && it in 0f..1f })
require(tintPremultipliedRgba[0] <= tintPremultipliedRgba[3])
require(tintPremultipliedRgba[1] <= tintPremultipliedRgba[3])
require(tintPremultipliedRgba[2] <= tintPremultipliedRgba[3])
```

- [ ] **Step 4: Run focused GREEN and verify the exact evidence**

Run the complete class command from Task 1.

Expected:

- midpoint is within one LSB of `[188, 188, 188, 255]`;
- native vector is within one LSB of `[25, 84, 150, 120]`;
- opaque nearest is exact;
- malformed tint refuses;
- every legacy fixture/content/raw geometry test remains green.

- [ ] **Step 5: Run final branch checks**

```bash
rtk git diff --check
rtk rg -n '\\b(nearestSample|linearSample|sourceRectSample|applyTint|exactMatch|linearMatch)\\b' \
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu
rtk proxy ./gradlew :kanvas:test \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUPreparedImageTestFixturesTest' \
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
```

Expected: `git diff --check` is clean; the symbol search returns no old helper
call; the focused class passes with zero failure or error.

- [ ] **Step 6: Commit the physical oracle**

```bash
rtk git add -- \
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImagePixelOracle.kt \
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageTestFixturesTest.kt
rtk git commit -m 'test(surface): add prepared image sRGB oracle'
```

---

## Final review gate

Before integration:

1. request a read-only independent review of every commit after `726a9851d`;
2. fix every legitimate P0-P3 finding and rerun the affected RED/GREEN cycle;
3. confirm the branch is clean;
4. integrate `726a9851d`, `fc26a20`, the plan commit, and the four
   implementation commits without `a1310239d`;
5. rerun `GPUPreparedImageTestFixturesTest` from the target branch before
   integrating the DrawImage lowerer.
