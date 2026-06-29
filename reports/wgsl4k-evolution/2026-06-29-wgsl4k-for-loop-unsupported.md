# wgsl4k: `for` loop not supported — minimized evidence

Date: 2026-06-29
Observed in: `ygdrasil-io/wgsl4k` (version on `kanvas` classpath)

## Minimized Repro

The WGSL `for` loop construct — standard in current WGSL — is rejected by
wgsl4k's parser:

```wgsl
@fragment
fn fs_main(in: VertexOutput) -> @location(0) vec4<f32> {
    for (var i: u32 = 0u; i < 16u; i = i + 1u) {
        break;
    }
    return vec4<f32>(0.0, 0.0, 0.0, 1.0);
}
```

`validateColorWgsl(sourceId, wgslSource)` → `GPUColorWgslValidation.Rejected`
with `reason = "wgsl4k_parse_error"`, `message = "wgsl4k parse produced diagnostics: Expected SEMICOLON, found IDENTIFIER"`.

The older `loop {}` construct (also standard WGSL) is accepted:

```wgsl
    var i: u32 = 0u;
    loop {
        if (i >= 16u) { break; }
        i = i + 1u;
    }
```

→ `GPUColorWgslValidation.Validated`.

## Impact

All COLRv0 composite shaders in `kanvas` M34 use `loop {}` as a direct
substitute. The wgsl4k parser should eventually support `for` to avoid forcing
downgrades of standard WGSL constructs. The gradient snippets in `wgsl/`
(LinearGradientSnippet, etc.) currently use `for` and are presumed
unvalidated through wgsl4k.

## Evidence

- `gpu-renderer/.../text/GPUColorGlyphCompositeShader.kt` — the shader uses `loop {}` with a KDoc note documenting this limitation.
- `gpu-renderer/.../color/GPUColorWgsl.kt` — the validation entry point `validateColorWgsl` was the validation harness.
- Plan 3a commit `1bf82a7` — initial shader commit; debugging iterations confirmed `for` rejection and `loop` acceptance.
