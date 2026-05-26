# Spec 06: Runtime Effects Descriptor

Status: Draft
Target: `.upstream/target/high-performance-wgsl-pipeline-target.md`

## Purpose

Keep `SkRuntimeEffect` source-compatible for supported Kanvas call sites while
avoiding a SkSL compiler. Runtime effects are resolved through registered
descriptors and explicit Kotlin/WGSL implementations.

## Boundary

```mermaid
flowchart TD
    source["SkRuntimeEffect.Make*(source)"] --> canonical["Canonical source hash"]
    canonical --> registry["Descriptor registry"]
    registry -->|hit| desc["SkRuntimeEffectDescriptor"]
    registry -->|miss| diag["Explicit miss diagnostic"]
    desc --> cpu["CPU implementation id"]
    desc --> gpu["WGSL implementation id"]
    gpu --> validate["WGSL parser validation"]
```

Kanvas does not compile arbitrary SkSL. A supported effect is a registered
compatibility entry.

## Descriptor Contract

`SkRuntimeEffectDescriptor` records:

- stable id;
- effect kind: shader, color filter, blender, or image-filter helper;
- uniforms;
- children;
- flags;
- CPU implementation id;
- optional WGSL implementation id.

The descriptor is part of the compatibility support matrix. Adding, changing,
or removing an entry is a public Kanvas compatibility change even if it is not
an upstream Skia API change.

## Canonical Lookup

The registry resolves caller-provided source through canonical normalization
and hashing.

Rules:

- comment and whitespace differences may normalize to the same source;
- hash stability is test-covered;
- duplicate registration behavior is explicit;
- misses produce a diagnostic that includes the canonical hash or stable id;
- tests can clear registry state only through test-only helpers.

## CPU Implementation

CPU implementation ids refer to registered Kotlin implementations.

Rules:

- implementation behavior is tested against existing CPU fixtures or upstream
  GM-derived expectations;
- uniform and child metadata must match descriptor declarations;
- unsupported child kinds or flags return explicit diagnostics;
- runtime-effect CPU support must not require WGSL.

## GPU WGSL Implementation

GPU implementation ids refer to registered WGSL modules or fragments.

Rules:

- WGSL implementation parses successfully;
- reflection verifies uniform and child binding layout;
- generated pipeline selection can include the runtime-effect implementation id
  only when it changes code shape or layout;
- unsupported GPU runtime effect paths refuse or use an explicit compatibility
  route.

## Support Matrix

Each supported runtime effect entry must name:

- canonical source hash or stable id;
- effect kind;
- uniform declarations and layout facts;
- child declarations;
- CPU implementation id;
- WGSL implementation id or GPU unsupported reason;
- tests or GMs exercising the effect;
- fallback behavior for missing backends.

The support matrix can live in code plus generated/tested reports, but Linear
and PR evidence must point to it.

## Non-Goals

- Do not implement arbitrary SkSL parsing or compilation.
- Do not accept user-provided WGSL as the effect implementation.
- Do not silently approximate unregistered effects.
- Do not use runtime-effect support to bypass `KanvasPipelineIR` semantics.

## Acceptance Criteria

- Registered descriptor lookup is tested.
- Missing effect diagnostics are stable.
- CPU implementation id is validated by a CPU render or unit test.
- WGSL implementation id, when present, has parser/reflection evidence.
- CPU/GPU output comparison exists for the pilot effect when both backends are
  declared supported.
