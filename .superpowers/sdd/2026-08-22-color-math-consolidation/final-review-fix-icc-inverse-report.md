# Final review fix — ICC inverse authority

## Scope

Fixed only the final-review finding in `IccProfileParser`: ICC matrix
representability retains its explicit finite-coefficient check, then delegates
invertibility and Float-range acceptance to
`ColorMatrix3x3F32.inverseOrNull() != null`. The parser no longer contains a
second 3-by-3 inverse implementation. The typed refusal remains
`icc.profile.matrix`.

## Test-first evidence

Added `IccProfileParserTest.rejects finite RGB matrix with dependent columns`.
It makes the red XYZ column exactly equal to the non-zero green XYZ column in a
real sRGB matrix/TRC ICC fixture, then asserts the observable typed parsing
refusal `icc.profile.matrix`.

Because this is an authority-only refactor and the existing implementation
already enforced the required rejection policy, the new characterization test
was GREEN on the pre-refactor implementation; there is no behavior-level RED
that distinguishes the duplicate kernel from the shared kernel without testing
private implementation details. This preserves the established ICC contract
rather than inventing a changed policy solely to manufacture a RED result.

## GREEN verification

```text
./gradlew :color-management:test --tests \
  'org.graphiks.kanvas.color.icc.IccProfileParserTest.rejects finite RGB matrix with dependent columns' \
  --no-daemon
BUILD SUCCESSFUL

./gradlew :color-management:test --no-daemon
BUILD SUCCESSFUL
```

`git diff --check` completed with no output before commit.

## Commit

`fix(color): delegate ICC matrix inversion to math color`

The resulting commit SHA is reported with this fix's handoff; it cannot be
self-embedded in the committed report because including the SHA changes the
commit object ID.
