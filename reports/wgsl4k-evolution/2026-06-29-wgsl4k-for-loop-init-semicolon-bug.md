# wgsl4k: `for (var i ...; ...; ...)` rejected by a parser init-semicolon bug

Date: 2026-06-29
Component: `org.graphiks:parser-jvm:1.0.0-SNAPSHOT` (`ygdrasil-io/wgsl4k`)
Submodule: `external/wgsl4k` @ `72a35b58` (uninitialized in kanvas; published snapshot is what builds)

## Correction of an earlier assumption

An earlier kanvas note assumed "wgsl4k does not support `for` loops, only `loop {}`."
That is **wrong**. wgsl4k **does** implement `for`:
- Lexer: `Lexer.kt:898` — `"for" -> TokenKind.FOR`
- AST: `Statement.kt:117` — `data class ForStatement(init, condition, update, body, span)`
- Parser dispatch: `Parser.kt:2165` — `TokenKind.FOR -> parseForStatement()`
- Lowering: `Lowerer.kt:766` — desugars `for` into `loop { if (cond) { body; update } else { break } }`

There are even parser/lowering tests for `for` (see "Test gap" below).

## The real bug — double-semicolon consumption in `parseForStatement`

`Parser.kt:2443-2504`, the init clause:

```kotlin
val init = if (currentKind() != TokenKind.SEMICOLON) {
    val stmt = parseVariableDeclStatement()              // already consumes the trailing ';'
    expectOrError(TokenKind.SEMICOLON, "Expected ';'")   // <-- BUG: expects a SECOND ';'
    stmt
} else { advance(); null }
```

`parseVariableDeclStatement()` (`Parser.kt:2549`) consumes the `;` that ends
`var i: u32 = 0u`. `parseForStatement` then calls `expectOrError(SEMICOLON, ...)`
again, but the next token is the condition's first identifier (`i`), producing:

> `Expected SEMICOLON, found IDENTIFIER`

This matches exactly the error kanvas observed validating
`for (var i: u32 = 0u; i < 16u; i = i + 1u) { ... }`.

## Minimized repro

Rejected by `parseWgslResult(...).isSuccess == false`:
```wgsl
fn f() { for (var i: u32 = 0u; i < 16u; i = i + 1u) { break; } }
```
Accepted (used as the kanvas workaround — standard WGSL):
```wgsl
fn f() { var i: u32 = 0u; loop { if (i >= 16u) { break; } i = i + 1u; } }
```

## Test gap

wgsl4k has `for` tests but **none assert zero parse errors**:
- `ControlFlowParserTest.kt:47` — `parse for without parentheses` (uses `Parser.parse()`, ignores `parser.errors`)
- `LoopLoweringTest.kt:12` — `for_loop_should_generate_ir_loop` (uses `lowerWgsl()`, ignores errors)
- `StatementLoweringTest.kt:121` — `for` with increment update (ignores errors)

Because the AST is still built (the spurious error is recorded but not fatal to
AST construction), these tests pass while the error slips through to any caller
that checks `isSuccess`/`errors` — like kanvas `validateColorWgsl`.

## Suggested fix (for a wgsl4k PR)

In `parseForStatement` (`Parser.kt:~2452`), remove the redundant
`expectOrError(TokenKind.SEMICOLON, "Expected ';'")` after
`parseVariableDeclStatement()` (which already consumes its terminator). Add a
parser test that asserts `parser.errors.isEmpty()` for
`for (var i: u32 = 0u; i < 10u; i = i + 1u) { }`.

## Resolution (2026-06-29)

- Fix merged: `ygdrasil-io/wgsl4k` PR #13 → `master` (`6f6521c`). Independent review APPROVE; 731 parser tests green incl. the new regression test.
- Republished snapshot: the wgsl4k publish workflow publishes under the
  **renamed** coordinates `org.graphiks:wgsl-parser-jvm` / `wgsl-core-jvm`
  (build `1.0.0-20260629.231604-1`, with the fix). The old `parser-jvm` /
  `core-jvm` coordinates stayed stale at the May-27 buggy build (`1.0.0-20260527.155507-1`).
- Kanvas updated `gpu-renderer/build.gradle.kts` to the new coordinates, and the
  COLRv0 composite shader (`GPUColorGlyphCompositeShader.kt`) now uses standard
  `for (...)` — it validates against the fixed parser. The `loop {}` workaround
  is no longer needed.

