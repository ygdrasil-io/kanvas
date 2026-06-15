# Spec 02: wgsl4k Contribution Packet

Status: Draft
Date: 2026-06-15
Repository: `https://github.com/ygdrasil-io/wgsl4k.git`

## Purpose

Define the fixture-first packet Kanvas will use to contribute missing
validation and reflection behavior to `wgsl4k`.

This packet is intended to become a wgsl4k branch and pull request after the
submodule is imported and the implementation plan is approved.

## Workflow

1. Import `https://github.com/ygdrasil-io/wgsl4k.git` as a tracked submodule,
   defaulting to `external/wgsl4k`.
2. Create a wgsl4k branch for the Kanvas reflection contract.
3. Add failing or pending wgsl4k tests for the fixtures in this spec.
4. Implement or fix wgsl4k parser/reflection behavior until those tests pass.
5. Open a wgsl4k pull request.
6. Consume the reviewed wgsl4k SHA in Kanvas only after merge or explicit user
   approval of the branch/SHA.

## Fixture Set

Fixtures should be minimized. Each case must isolate one reflection requirement
or one diagnostic expectation.

| Fixture | Purpose |
|---|---|
| `a8_text_mask.wgsl` | Fragment module with text atlas sampled texture, sampler, text uniform params, and A8 mask output. |
| `runtime_effect_solid_color.wgsl` | Registered runtime-effect-style module with one uniform struct and deterministic fragment output. |
| `compute_buffer_copy.wgsl` | Compute module with storage buffers and workgroup size reflection. |
| `invalid_syntax.wgsl` | Negative parser diagnostic. |
| `binding_kind_mismatch.wgsl` | Negative binding/resource-kind comparison fixture for Kanvas. |
| `layout_unrepresented.wgsl` | Valid WGSL form that wgsl4k cannot yet reflect, expected to produce an explicit unsupported-feature diagnostic. |
| `unregistered_module.json` | Kanvas-side negative fixture proving WGSL validation does not bypass descriptor registration. |

## Expected Outputs

Each positive fixture must have an expected report with:

- schema version;
- source id;
- module hash;
- validation success;
- entry points;
- bindings;
- uniform and storage layouts;
- unsupported features, empty when none are expected.

Each negative fixture must have an expected report with:

- validation failure or comparison failure;
- stable diagnostic reason code;
- span data when available;
- no product-support claim.

## Text Fixture Requirements

`a8_text_mask.wgsl` must expose enough facts for Kanvas to compare:

- glyph atlas sampled texture binding;
- glyph atlas sampler binding;
- text params uniform binding;
- atlas coordinate or scale fields;
- instance input expectations when the route uses instance data;
- SDF params as absent or explicitly present, depending on the fixture variant.

This fixture is a validation/reflection gate only. It does not promote A8,
SDF, color glyph, emoji, bitmap, SVG, LCD, or CPU-rendered texture text routes.

## Runtime-Effect Fixture Requirements

`runtime_effect_solid_color.wgsl` must expose enough facts for Kanvas to
compare:

- descriptor-owned uniform schema;
- uniform block binding;
- complete module entry points;
- fragment output shape;
- resource and child-slot absence.

This fixture does not authorize arbitrary SkSL, arbitrary WGSL, child effects,
primitive blenders, destination reads, or runtime code loading.

## Compute Fixture Requirements

`compute_buffer_copy.wgsl` must expose enough facts for Kanvas to compare:

- compute entry point name;
- workgroup size;
- read-only and read-write storage buffers;
- storage layout facts when structs are used;
- access modes.

This fixture exists because future coverage, atlas, filter, and cache routes
may use compute modules. It does not promote any compute-backed Kanvas route.

## wgsl4k PR Acceptance Criteria

The wgsl4k PR is acceptable for Kanvas consumption only when:

- positive fixtures validate and reflect the expected facts;
- negative fixtures produce deterministic diagnostics;
- unsupported-but-valid WGSL forms are reported explicitly;
- JSON or equivalent structured output preserves the Kanvas-required facts;
- the PR documents any facts wgsl4k still cannot represent;
- the reviewed SHA is recorded by the Kanvas consuming ticket.

## Kanvas Consumption Criteria

Kanvas may consume the reviewed wgsl4k SHA only after:

- `WGSL4K-EVO-002` records the submodule URL, path, and pinned commit;
- `WGSL4K-EVO-003` links the wgsl4k branch/PR and its validation output;
- the user approves the wgsl4k PR or reviewed SHA;
- consuming Kanvas reports are generated freshly by `WGSL4K-EVO-004`.

After consumption, individual GPU/Text tickets must still provide their own
CPU, GPU, reference, route diagnostic, and artifact evidence before support can
be promoted.

## Non-Goals

- Do not implement Kanvas route promotion inside the wgsl4k PR.
- Do not add Kanvas-specific hidden behavior to wgsl4k.
- Do not require wgsl4k to know Kanvas product semantics.
- Do not treat fixture success as visual correctness proof.
