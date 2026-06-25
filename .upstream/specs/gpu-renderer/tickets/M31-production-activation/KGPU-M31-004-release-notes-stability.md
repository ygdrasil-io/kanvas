---
id: KGPU-M31-004
title: "Release notes and API stability freeze"
status: review
milestone: M31
priority: P0
owner_area: product-validation
claim_impact: ImplementationCandidate
route_kind: CPUReferenceOnly
product_activation: false
release_blocking: true
adapter_required: true
depends_on: [KGPU-M31-003]
legacy_gate: null
---

# KGPU-M31-004 - Release notes and API stability freeze

## PM Note

Ce ticket gele l'API publique Kanvas et produit les notes de release pour la
version de production. Apres ce ticket, l'API `:kanvas` est stable, les
breaking changes sont interdits sans procedure formelle, et la documentation
de release est prete pour distribution.

## Problem

Kanvas is in production (M31-001) but the API surface has no stability guarantee.
Callers need confidence that the `:kanvas` module will not break without
notice. Release notes document what is supported, what is frozen, and what is
explicitly refused.

## Scope

- Declare `:kanvas` public API surface as stable/frozen
- Document API stability policy (semantic versioning, deprecation cycles)
- Write release notes covering M0-M31 feature set
- Document supported draw families, shader types, and codec formats
- Document explicit non-support (runtime effects, SkSL, CPU fallback)
- Document rollback procedure from operational runbook
- Add `@Deprecated` replacement annotations for legacy-API migration paths

## Non-Goals

- No API redesign or breaking changes
- No new documentation infrastructure
- No localization of release notes
- No external distribution or publishing pipeline

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/09-draw-family-support-matrix.md`
- `.upstream/specs/gpu-renderer/35-package-class-layout.md`
- `.upstream/specs/gpu-renderer/tickets/M29-kanvas-native-api/README.md`
- `.upstream/specs/gpu-renderer/tickets/M30-skia-wrapper-legacy-retirement/README.md`
- `SUPPORTED_CODECS.md`

## Design Sketch

```kotlin
// API stability annotations
@StableApi(since = "1.0.0")
class KanvasSurface { ... }

@StableApi(since = "1.0.0")
class KanvasCanvas { ... }
```

## Acceptance Criteria

- [ ] `:kanvas` public surface is annotated with stability guarantees
- [ ] API stability policy document committed (deprecation cycle, breaking change process)
- [ ] Release notes cover all M0-M31 features with support/non-support declarations
- [ ] Supported draw families matrix is current and accurate
- [ ] Supported codec formats documented and linked to `SUPPORTED_CODECS.md`
- [ ] Explicit non-support documented (runtime effects, SkSL, CPU fallback)
- [ ] Rollback procedure documented in operational context

## Required Evidence

- API stability annotations diff for `:kanvas` public classes
- API stability policy document
- Release notes markdown file covering M0-M31
- Draw-family support matrix (final)
- Non-support declarations document
- Rollback procedure (final)

## Fallback / Refusal Behavior

Breaking changes to the stable API require a formal deprecation cycle (one major
version) and explicit migration documentation. Unauthorized breaking changes
emit `api-stability-violation` diagnostic and are rejected at review.

## Dashboard Impact

- Expected row: `gpu-renderer.m31.release-notes-stability`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :kanvas:compileKotlinJvm
rtk ./gradlew --no-daemon :kanvas-skia:test
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk git diff --check
```

## Status Notes

- `proposed`: Initial ticket.
- `review` (2026-06-25): Produced. Release notes draft covering M0-M31 feature set, supported draw families, blend modes, shader types, explicit non-support declarations, and rollback procedure. API stability declared for core :kanvas types. Evidence at reports/gpu-renderer/2026-06-25-M31-004-release-notes.md.

## Linear Labels

- `gpu-renderer`
- `milestone:M31`
- `area:product-validation`
