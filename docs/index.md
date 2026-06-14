# kanvas-skia

Port Kotlin de la bibliothèque graphique 2D [Skia](https://skia.org/).

## Modules

- **[math](api/math/index.md)** — couche math pure (points, rectangles, matrices, vecteurs, couleurs, scalaires). Namespace `org.graphiks.math`, indépendant de l'écosystème Skia, réutilisable hors port.
- `:kanvas-skia` — abstractions API (types data + interfaces). _Doc API à venir._
- `:cpu-raster` — implémentations raster CPU. _Doc API à venir._
- `:gpu-raster` — implémentations GPU (WebGPU). _Doc API à venir._

## Guides

- [Pure Kotlin OpenType font backend](opentype-font-backend.md) — périmètre supporté, limites sans shaping/native, et commandes de validation.
- [Pure Kotlin font system roadmap](pure-kotlin-font-system-roadmap.md) — roadmap complète vers un système font/text/glyph pure Kotlin avec gates de preuve.
- [Font GM post-AWT rebaseline](font-gm-post-awt-rebaseline.md) — classification des GM fonts après retrait du backend font AWT.

## Correspondance Kotlin ↔ Skia upstream

Chaque symbole Kotlin qui mirror un symbole Skia upstream est listé dans la **map de correspondance** [`.upstream/source/map/`](https://github.com/ygdrasil-io/kanvas/tree/master/.upstream/source/map). Format TSV requêtable depuis le terminal — voir la [spec](https://github.com/ygdrasil-io/kanvas/tree/master/.upstream/source/map/README.md).
