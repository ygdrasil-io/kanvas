## Summary

`parseWgslResult`/`Lowerer` accept `fn main() {}` as a compilable module, and a consumer's runtime-effect wiring therefore compiles it successfully. This makes a downstream test order-dependent (the test passes only when the wgsl4k wiring hook has not been installed in that JVM before the test runs).

## Minimal snippet

```wgsl
fn main() {}
```

## Observed behavior

`parseWgslResult("fn main() {}")` returns success and `Lowerer().lower(...)` yields a module with a non-empty function/entry-point set, so a consumer can build a shader module from it. The snippet contains no `@fragment`/`@vertex` entry-point attributes and no pipeline-relevant code (no return type, no outputs, no bindings).

Probe evidence (consumer JVM, hook installed):

```
PROBE sample=<fn main() {}> isSuccess=true isFailure=false err=none
```

## Consumer impact (Kanvas)

Kanvas wires wgsl4k into `RuntimeEffect.compile` via `RuntimeEffectWgsl4kWiring.install()`:

```kotlin
val parsed = parseWgslResult(wgsl)
if (!parsed.isSuccess) return null
val module = Lowerer().lower(parsed.translationUnit)
val entryName = if (module.entryPoints.isNotEmpty()) module.entryPoints.first().name
                else if (module.functions.isNotEmpty()) module.functions.first().name
                else return null
```

With the hook installed, `RuntimeEffect.compile("fn main() {}")` succeeds. Kanvas's `PipelineTypesTest` asserts `RuntimeEffect.compile("fn main() {}").isFailure` (`kanvas/src/test/kotlin/org/graphiks/kanvas/pipeline/PipelineTypesTest.kt:13`). The assertion holds only when the hook is not installed in that test JVM; whether a hook-installing test class ran earlier in the same fork depends on test-class execution order, so the test outcome is fork/order-dependent (observed failing in full-suite runs, passing in isolation).

## Expected behavior

`fn main() {}` should be rejected — it has no entry-point attributes and no pipeline-relevant code. At minimum, acceptance of a module should not vary depending on whether wgsl4k is wired into the consumer. A parse- or validation-level rejection of `fn main() {}` would keep consumer behavior consistent regardless of hook installation.

## Environment

- wgsl4k 1.0.0-20260629.231604-1 (`org.graphiks:wgsl-core-jvm`, `org.graphiks:wgsl-parser-jvm`; `gradle/libs.versions.toml`)
- Consumer code path: `kanvas/src/main/kotlin/org/graphiks/kanvas/pipeline/RuntimeEffectWgsl4kWiring.kt:36-43`
- Kanvas repo, branch `codex/graphite-dawn-frame-fp13`
