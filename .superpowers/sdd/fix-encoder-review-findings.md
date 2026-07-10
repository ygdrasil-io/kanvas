# PNG encoder review findings 8, 9, 17

## Scope

- Production: `codec/png/src/main/kotlin/org/graphiks/kanvas/codec/png/PngEncoder.kt`
- Tests: `codec/png/src/test/kotlin/org/graphiks/kanvas/codec/png/PngEncoderTest.kt`
- No pixel conversion was added.

## TDD evidence

The first focused run was executed after adding regression tests and before changing production code:

```text
./gradlew :codec:png:test --tests org.graphiks.kanvas.codec.png.PngEncoderTest
18 tests completed, 4 failed
```

The expected failures covered unsupported profile acceptance, `SkPixmap` color-space loss, UTF-8 tEXt bytes, and missing tEXt validation.

After the implementation, the focused run passed all 18 tests:

```text
BUILD SUCCESSFUL
PngEncoderTest: 18 passed
```

## Implementation

- Encoding now preflights the color profile and refuses anything other than a supported RGB SDR matrix/TRC profile with serializable transfer function and matrix.
- ICC and tEXt payloads are prepared before the PNG signature is written, so validation refusal leaves `OutputStream` untouched and propagates through the other overloads.
- `SkPixmap` materialization preserves its color space and refuses unknown color types or a missing color space.
- tEXt keyword and text are encoded as exact ISO-8859-1 bytes. Keywords enforce the PNG 1-79 byte printable range and spacing rules; non-Latin-1 input is refused.

## Verification

- Focused encoder tests: pass, 18/18.
- Full `:codec:png:test`: 134/136 pass. The two failures are in concurrently modified, out-of-scope `PngDocumentTest.kt` tests:
  - `mDCV rejects non normative primary order with stable diagnostic`
  - `iTXt rejects malformed BCP47 language tags with stable diagnostic`
- Both unrelated failures reproduce when run in isolation.
- `git diff --check`: pass.
- Scope check: only the two owned encoder files and this report are included in the task commit.
