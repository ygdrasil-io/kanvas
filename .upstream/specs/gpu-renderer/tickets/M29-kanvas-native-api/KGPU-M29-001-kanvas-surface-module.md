---
id: KGPU-M29-001
title: "Kanvas API module skeleton + KanvasSurface"
status: done
milestone: M29
priority: P0
owner_area: kanvas-api
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: false
depends_on: []
legacy_gate: null
---

# KGPU-M29-001 - Kanvas API module skeleton + KanvasSurface

## PM Note

Avant de pouvoir exposer les operations de dessin natives Kanvas, il faut un module
Kotlin autonome `:kanvas` avec une surface de rendu. Ce ticket cree le squelette
du module et la classe `KanvasSurface` pour que le PM voie la fondation de l'API
publique.

## Problem

No public Kanvas native API module exists. Current rendering paths go through
`:gpu-renderer` internals, Skia wrappers, or legacy `gpu-raster`. A new
`:kanvas` module with `KanvasSurface` is the entry point for native Kanvas
rendering without Skia indirection.

## Scope

- Create `:kanvas` Gradle module with Kotlin multiplatform layout
- Define `KanvasSurface` class with width, height, and GPU backend handle
- Wire minimal build configuration (no rendering ops yet)
- Ensure the module compiles as a standalone artifact

## Non-Goals

- No drawing operations (KGPU-M29-002)
- No paint or shader definitions (KGPU-M29-003, KGPU-M29-005)
- No path or text support
- No GPU submission (KGPU-M29-008)
- No Skia bridge integration (M30)

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`
- `.upstream/specs/gpu-renderer/35-package-class-layout.md`
- `.upstream/specs/gpu-renderer/tickets/M24-gpu-native-rendering/README.md`

## Design Sketch

```kotlin
// :kanvas/src/commonMain/kotlin/.../kanvas/KanvasSurface.kt
class KanvasSurface(
    val width: Int,
    val height: Int,
    internal val backend: GPUDevice,
)

// :kanvas/build.gradle.kts
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":gpu-renderer"))
            }
        }
    }
}
```

## Acceptance Criteria

- [ ] `:kanvas` module compiles in `commonMain`, `jvmMain`, `appleMain`
- [ ] `KanvasSurface` holds width, height, and backend reference
- [ ] Module is registered in `settings.gradle.kts`
- [ ] No rendering operations or drawing logic in this ticket

## Required Evidence

- `kanvas-api/build.gradle.kts` committed
- `KanvasSurface.kt` committed
- `settings.gradle.kts` module registration diff
- Compilation log showing the module builds successfully

## Fallback / Refusal Behavior

If the GPU backend is unavailable, `KanvasSurface` construction emits a
`gpu-unavailable` diagnostic. No silent fallback to CPU rendering.

## Dashboard Impact

- Expected row: `gpu-renderer.m29.kanvas-surface-module`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :kanvas:compileKotlinJvm
rtk ./gradlew --no-daemon :kanvas:compileKotlinMacosArm64
```

## Status Notes

- `proposed`: Initial ticket.
- `done`: Module created with build.gradle.kts, settings.gradle.kts include, KanvasSurface.kt, KanvasPixelFormat.kt, KanvasRect.kt, KanvasFrame.kt. Module compiles against :gpu-renderer.

## Linear Labels

- `gpu-renderer`
- `milestone:M29`
- `area:kanvas`
