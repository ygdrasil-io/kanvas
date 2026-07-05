# Schemas Markdown

Ce fichier regroupe les schemas Mermaid et ASCII du rapport. Ils sont concus
pour etre lisibles dans un viewer Markdown compatible Mermaid.

## Flux actuel simplifie

```mermaid
flowchart TD
    A["DisplayList Kanvas"] --> B["GPURenderer"]
    B --> C["Dispatch par type d'operation"]
    C --> D["encodeOffscreenTexture"]
    D --> E["WgpuRenderRecorder"]
    E --> F["Creation locale: buffers, textures, samplers, bind groups"]
    F --> G["Render pass courte"]
    G --> H["queue.submit"]
    H --> I["Readback ou composition finale"]
```

Lecture : le chemin actuel est fonctionnel, mais beaucoup de decisions sont
prises pendant le dispatch concret. Cela limite le batching.

## Flux cible

```mermaid
flowchart TD
    A["DisplayList Kanvas"] --> B["Analyse"]
    B --> C["Material lowering"]
    C --> D["GPUDrawPacket"]
    D --> E["Resource planning"]
    E --> F["GpuPassBatcher"]
    F --> G["GPUPassCommandStream"]
    G --> H["WgpuCommandEncoder"]
    H --> I["WgpuQueueManager"]
    I --> J["Completion GPU"]
    J --> K["Resource recycling"]

    L["WgpuCaps"] --> C
    L --> E
    L --> H
    M["WgpuResourceProvider"] --> E
    M --> H
    I --> M
```

Lecture : les decisions deviennent explicites avant l'encodage WGPU.

## Ce que Dawn apporte comme reference

```mermaid
flowchart LR
    A["DawnCaps"] --> B["Pipeline keys"]
    A --> C["Render pass strategy"]
    A --> D["Resource usage"]

    E["DawnResourceProvider"] --> F["Uniform buffers"]
    E --> G["Bind group cache"]
    E --> H["Null buffer"]
    E --> I["Texture/sampler cache"]

    J["DawnCommandBuffer"] --> K["Render pass encoder"]
    J --> L["Compute pass encoder"]
    J --> M["Copy/readback commands"]

    N["DawnQueueManager"] --> O["Submit"]
    O --> P["Completion"]
    P --> Q["Release resources"]
```

Lecture : Kanvas peut reprendre ces responsabilites, mais avec ses propres
contrats et noms.

## Batching legal

```mermaid
flowchart TD
    A["Draw 1: solid rect"] --> B{"Compatible avec batch courant ?"}
    B -- "oui" --> C["Ajouter au batch"]
    B -- "non" --> D["Fermer batch courant"]
    D --> E["Demarrer nouveau batch"]
    C --> F{"Operation suivante"}
    E --> F
    F --> G["Draw suivant"]

    H["Destination-read"] --> I["Couper batch"]
    J["SaveLayer"] --> I
    K["Readback/copy"] --> I
    L["Target different"] --> I
```

Lecture : le batcher doit etre conservateur. Si une operation impose une
dependance forte, on coupe.

## Uniform slab

```text
GPUBuffer uniform slab

offset 0      offset 256    offset 512    offset 768
+------------+-------------+-------------+-------------+
| draw A     | draw B      | draw C      | libre       |
| uniforms   | uniforms    | uniforms    |             |
+------------+-------------+-------------+-------------+

Bind group layout stable
  binding 0 -> meme buffer
  draw A   -> dynamic offset 0
  draw B   -> dynamic offset 256
  draw C   -> dynamic offset 512
```

Lecture : plusieurs draws partagent le meme buffer, au lieu de creer un buffer
par draw.

## Resource lifetime par soumission

```mermaid
sequenceDiagram
    participant Encoder as WgpuCommandEncoder
    participant Provider as WgpuResourceProvider
    participant Queue as WgpuQueueManager
    participant GPU as GPU

    Encoder->>Provider: materialiser buffers/textures/bind groups
    Provider-->>Encoder: handles + refs retenues
    Encoder->>Queue: submit(commandBuffer, refs)
    Queue->>GPU: queue.submit
    Queue-->>Provider: retenir refs pour submissionId
    GPU-->>Queue: completion
    Queue-->>Provider: release/recycle refs
```

Lecture : la ressource vit jusqu'a completion GPU, pas seulement jusqu'a la fin
du bloc Kotlin qui l'a creee.

## Planner destination-read

```mermaid
flowchart TD
    A["Operation avec blend avance"] --> B{"Lit la destination ?"}
    B -- "non" --> C["Batch normal possible"]
    B -- "oui" --> D["Snapshot destination"]
    D --> E["Rendre source dans texture src"]
    E --> F["Appliquer blend/filter"]
    F --> G["Composer dans scene"]
    G --> H["Diagnostics + telemetry"]
```

Lecture : `scene`, `src` et `snap` deviennent un plan explicite.

## Roadmap

```mermaid
flowchart LR
    A["0 Baseline"] --> B["1 WgpuCaps"]
    B --> C["2 WgpuResourceProvider"]
    C --> D["3 Queue/lifetime"]
    D --> E["4 Pass batching simple"]
    E --> F["5 Intermediate planner"]
    F --> G["6 Migration familles GM"]
```

## Comparaison ASCII

```text
Aujourd'hui
----------
operation -> encoder -> ressources locales -> pass courte -> submit
operation -> encoder -> ressources locales -> pass courte -> submit
operation -> encoder -> ressources locales -> pass courte -> submit

Cible
-----
operations -> analyse -> batch compatible -> ressources provider -> pass longue -> submit
operations complexes -> planner intermediaire -> passes explicites -> submit
```

## Frontiere Graphite/Kanvas

```mermaid
flowchart TD
    A["Graphite"] --> B["Idees utiles: tri, batching, draw pass, resource planning"]
    A --> C["A ne pas copier: classes, ownership, SkSL, multi-backend"]
    B --> D["Contrats Kanvas"]
    D --> E["GPUDrawPacket"]
    D --> F["GPUPassCommandStream"]
    D --> G["GPUResourceProvider"]
    D --> H["Wgpu backend"]
```

Lecture : Graphite reste une reference algorithmique. Kanvas reste proprietaire
de ses objets.
