# Carte des sources consultees

Ce fichier liste les principales sources utilisees pour l'analyse. Les chemins
absolus permettent de retrouver rapidement le code local.

## Skia / Dawn

### Capacites backend

- [/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnCaps.cpp:105](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnCaps.cpp:105)
  Initialisation de la table de formats.
- [/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnCaps.cpp:269](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnCaps.cpp:269)
  Initialisation des caps backend.
- [/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnCaps.cpp:490](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnCaps.cpp:490)
  Construction d'une cle de render pass pour pipeline.
- [/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnCaps.cpp:527](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnCaps.cpp:527)
  Construction de cle graphics pipeline.

### Command buffer

- [/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnCommandBuffer.cpp:247](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnCommandBuffer.cpp:247)
  Ajout d'une render pass.
- [/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnCommandBuffer.cpp:321](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnCommandBuffer.cpp:321)
  Debut de render pass avec attachments/load/store.
- [/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnCommandBuffer.cpp:953](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnCommandBuffer.cpp:953)
  Synchronisation des uniform buffers et bind groups.
- [/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnCommandBuffer.cpp:193](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnCommandBuffer.cpp:193)
  Liberation des intrinsic buffers en fin d'encodage.

### Resource provider

- [/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnResourceProvider.cpp:158](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnResourceProvider.cpp:158)
  `IntrinsicConstantsManager`.
- [/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnResourceProvider.cpp:607](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnResourceProvider.cpp:607)
  Creation/reuse du null buffer.
- [/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnResourceProvider.cpp:637](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnResourceProvider.cpp:637)
  Cache single uniform bind group.
- [/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnResourceProvider.cpp:683](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnResourceProvider.cpp:683)
  Cache texture+sampler bind group.
- [/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnResourceProvider.cpp:724](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnResourceProvider.cpp:724)
  Release des intrinsic buffers pending.

### Queue manager

- [/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnQueueManager.cpp:39](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnQueueManager.cpp:39)
  Completion asynchrone via `OnSubmittedWorkDone`.
- [/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnQueueManager.cpp:116](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnQueueManager.cpp:116)
  Soumission GPU.
- [/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnQueueManager.cpp:125](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnQueueManager.cpp:125)
  `Queue.Submit`.

## Kanvas runtime WGPU

### Session, target, submit

- [/Users/chaos/.codex/worktrees/416c/kanvas/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt:242](/Users/chaos/.codex/worktrees/416c/kanvas/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt:242)
  Session WGPU.
- [/Users/chaos/.codex/worktrees/416c/kanvas/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt:289](/Users/chaos/.codex/worktrees/416c/kanvas/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt:289)
  Target offscreen.
- [/Users/chaos/.codex/worktrees/416c/kanvas/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt:419](/Users/chaos/.codex/worktrees/416c/kanvas/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt:419)
  Soumission de command buffer.
- [/Users/chaos/.codex/worktrees/416c/kanvas/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt:502](/Users/chaos/.codex/worktrees/416c/kanvas/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt:502)
  Encodage offscreen texture interne.

### Recorder et ressources locales

- [/Users/chaos/.codex/worktrees/416c/kanvas/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt:714](/Users/chaos/.codex/worktrees/416c/kanvas/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt:714)
  `WgpuRenderRecorder`.
- [/Users/chaos/.codex/worktrees/416c/kanvas/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt:1928](/Users/chaos/.codex/worktrees/416c/kanvas/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt:1928)
  Fullscreen uniform pass.
- [/Users/chaos/.codex/worktrees/416c/kanvas/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt:2001](/Users/chaos/.codex/worktrees/416c/kanvas/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt:2001)
  Creation texture locale.
- [/Users/chaos/.codex/worktrees/416c/kanvas/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt:2038](/Users/chaos/.codex/worktrees/416c/kanvas/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt:2038)
  Creation uniform buffer locale.
- [/Users/chaos/.codex/worktrees/416c/kanvas/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt:2409](/Users/chaos/.codex/worktrees/416c/kanvas/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt:2409)
  Caches WGPU d'execution.

## Kanvas contrats

- [/Users/chaos/.codex/worktrees/416c/kanvas/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/pipelines/PipelineContracts.kt:55](/Users/chaos/.codex/worktrees/416c/kanvas/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/pipelines/PipelineContracts.kt:55)
  Preimage render pipeline.
- [/Users/chaos/.codex/worktrees/416c/kanvas/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/pipelines/PipelineContracts.kt:95](/Users/chaos/.codex/worktrees/416c/kanvas/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/pipelines/PipelineContracts.kt:95)
  Canonicalisation de preimage.
- [/Users/chaos/.codex/worktrees/416c/kanvas/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/ExecutionCacheContracts.kt:105](/Users/chaos/.codex/worktrees/416c/kanvas/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/ExecutionCacheContracts.kt:105)
  `GPUExecutionObjectCache`.
- [/Users/chaos/.codex/worktrees/416c/kanvas/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/ResourceContracts.kt:912](/Users/chaos/.codex/worktrees/416c/kanvas/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/ResourceContracts.kt:912)
  `GPUResourceProvider`.
- [/Users/chaos/.codex/worktrees/416c/kanvas/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/PassContracts.kt:261](/Users/chaos/.codex/worktrees/416c/kanvas/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/PassContracts.kt:261)
  `GPUDrawPacket`.

## Kanvas surface GPU

- [/Users/chaos/.codex/worktrees/416c/kanvas/kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt:47](/Users/chaos/.codex/worktrees/416c/kanvas/kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt:47)
  Entree `renderViaGpu`.
- [/Users/chaos/.codex/worktrees/416c/kanvas/kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt:72](/Users/chaos/.codex/worktrees/416c/kanvas/kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt:72)
  Creation des textures `scene`, `src`, `snap`.
- [/Users/chaos/.codex/worktrees/416c/kanvas/kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt:94](/Users/chaos/.codex/worktrees/416c/kanvas/kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt:94)
  Snapshot destination.
- [/Users/chaos/.codex/worktrees/416c/kanvas/kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt:121](/Users/chaos/.codex/worktrees/416c/kanvas/kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt:121)
  Blend/composite via offscreen.
- [/Users/chaos/.codex/worktrees/416c/kanvas/kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUMaterialMapper.kt:144](/Users/chaos/.codex/worktrees/416c/kanvas/kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUMaterialMapper.kt:144)
  `Shader.WithLocalMatrix` simplifie.
- [/Users/chaos/.codex/worktrees/416c/kanvas/kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUMaterialMapper.kt:145](/Users/chaos/.codex/worktrees/416c/kanvas/kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUMaterialMapper.kt:145)
  `Shader.WithColorFilter` simplifie.
- [/Users/chaos/.codex/worktrees/416c/kanvas/kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUMaterialMapper.kt:207](/Users/chaos/.codex/worktrees/416c/kanvas/kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUMaterialMapper.kt:207)
  Working color space simplifie.

## Specs Kanvas

- [/Users/chaos/.codex/worktrees/416c/kanvas/.upstream/target/high-performance-wgsl-pipeline-target.md:19](/Users/chaos/.codex/worktrees/416c/kanvas/.upstream/target/high-performance-wgsl-pipeline-target.md:19)
  Interdiction de porter Ganesh/Graphite.
- [/Users/chaos/.codex/worktrees/416c/kanvas/.upstream/target/high-performance-wgsl-pipeline-target.md:21](/Users/chaos/.codex/worktrees/416c/kanvas/.upstream/target/high-performance-wgsl-pipeline-target.md:21)
  WebGPU reste backend GPU.
- [/Users/chaos/.codex/worktrees/416c/kanvas/.upstream/target/skia-like-realtime-renderer-target.md:19](/Users/chaos/.codex/worktrees/416c/kanvas/.upstream/target/skia-like-realtime-renderer-target.md:19)
  Le renderer temps reel n'est pas un port Ganesh/Graphite.
- [/Users/chaos/.codex/worktrees/416c/kanvas/.upstream/specs/gpu-renderer/00-architecture-kernel.md:16](/Users/chaos/.codex/worktrees/416c/kanvas/.upstream/specs/gpu-renderer/00-architecture-kernel.md:16)
  Direction Graphite-inspired mais inline.
- [/Users/chaos/.codex/worktrees/416c/kanvas/.upstream/specs/gpu-renderer/10-gpu-execution-context-submission.md:142](/Users/chaos/.codex/worktrees/416c/kanvas/.upstream/specs/gpu-renderer/10-gpu-execution-context-submission.md:142)
  Les render tasks doivent encoder depuis `GPUPassCommandStream`.
- [/Users/chaos/.codex/worktrees/416c/kanvas/.upstream/specs/gpu-renderer/37-draw-packet-command-stream.md:16](/Users/chaos/.codex/worktrees/416c/kanvas/.upstream/specs/gpu-renderer/37-draw-packet-command-stream.md:16)
  Graphite et Dawn comme references algorithmiques seulement.
- [/Users/chaos/.codex/worktrees/416c/kanvas/.upstream/specs/gpu-renderer/37-draw-packet-command-stream.md:306](/Users/chaos/.codex/worktrees/416c/kanvas/.upstream/specs/gpu-renderer/37-draw-packet-command-stream.md:306)
  Ne pas copier les classes Dawn/Graphite.

## Dashboard GM

- [/Users/chaos/.codex/worktrees/416c/kanvas/integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json](/Users/chaos/.codex/worktrees/416c/kanvas/integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json)
  Donnees du dashboard local.
- [/Users/chaos/.codex/worktrees/416c/kanvas/integration-tests/skia/build/reports/skia-gm-dashboard/index.html](/Users/chaos/.codex/worktrees/416c/kanvas/integration-tests/skia/build/reports/skia-gm-dashboard/index.html)
  Dashboard HTML ouvert dans le navigateur integre.
