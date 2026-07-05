# Kanvas

Moteur graphique Kotlin 2D avec API, codecs et pipeline de rendu propres à Kanvas.

## Modules

- **[math](api/math/index.md)** — couche math pure (points, rectangles, matrices, vecteurs, couleurs, scalaires). Namespace `org.graphiks.math`, indépendant de l'écosystème Skia, réutilisable hors port.
- `:kanvas` — API graphique et types image/couleur centraux.
- `:codec:*` — codecs image pure Kotlin branchés sur les types Kanvas.
- `:gpu-renderer` — pipeline GPU/WebGPU.

## Guides

- [Pure Kotlin font system roadmap](https://github.com/ygdrasil-io/kanvas/blob/master/.upstream/specs/pure-kotlin-text/ROADMAP.md) — roadmap complète vers un système font/text/glyph pure Kotlin avec gates de preuve.
- [Font GM post-AWT rebaseline](font-gm-post-awt-rebaseline.md) — classification des GM fonts après retrait du backend font AWT.

## Correspondance Kotlin ↔ Skia upstream

Chaque symbole Kotlin qui mirror un symbole Skia upstream est listé dans la **map de correspondance** [`.upstream/source/map/`](https://github.com/ygdrasil-io/kanvas/tree/master/.upstream/source/map). Format TSV requêtable depuis le terminal — voir la [spec](https://github.com/ygdrasil-io/kanvas/tree/master/.upstream/source/map/README.md).
