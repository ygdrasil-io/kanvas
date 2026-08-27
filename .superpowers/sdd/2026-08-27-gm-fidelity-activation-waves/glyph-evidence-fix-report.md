# Glyph evidence fix — Task 10 (2026-08-27)

## Résultat

La preuve Task 10 compare maintenant les buffers complets CPU et WebGPU pour
les trois glyph-runs bornés de `LiberationSans-Regular.ttf`. La police et la
portée restent inchangées : aucun GM, seuil de similarity, budget ou route de
fallback n’a été modifié.

L’oracle CPU ne relit pas le readback GPU : il interprète en Kotlin le payload
TextA8 déjà scellé côté CPU. Il échantillonne les octets de l’atlas A8,
reconstruit chaque device quad/UV nearest, applique les uniforms de material,
compose source-over en linear premultiplied, puis encode RGBA8 sRGB. Chaque
sortie fait 96 × 48 × 4 = 18 432 bytes. `FontTypeface.getGlyphPath` demeure
l’oracle CPU séparé d’existence des outlines, tandis que le chemin GPU reste
`FontTypeface.preparedTextOutline -> TextA8 -> WebGPU`.

## Snapshots byte-à-byte

| row | hash CPU | hash GPU | bytes/pixels différents | delta max / somme |
| --- | --- | --- | --- | --- |
| `gradtext.glyph-run.linear-clamp.v1` | `9666e292ad51f7754d2fb0ccebb6c557988dc36f0edb89a32d0c2897e106c354` | `3e82d101c0a894f7a06da71e24a397841903ddca083053aff58e50ffc21db24a` | 9 / 9 | 1 / 9 |
| `text-scale-skew.glyph-run.affine.v1` | `6ea03c02678f30606226c83d7f263404d82e31c49c856935775c916b977cb0ae` | identique | 0 / 0 | 0 / 0 |
| `fontscaler.glyph-run.size-18.v1` | `16cf0ecf39f03cb3d0f8eaf22387d350cfd26f649c2fd7b1deefd2075e9de7d6` | identique | 0 / 0 | 0 / 0 |

Les neuf différences du gradient sont uniquement des arrondis UNORM finaux
d’un LSB, sur des canaux rouge/bleu. Elles sont des valeurs de snapshot exactes
(hashes et métriques), non un seuil : toute autre dérive échoue le test.

Les artefacts cohérents sont sous
`reports/gpu-renderer/evidence/delivered-font-glyph-run-2026-08-27/` :
`cpu.json`, `gpu.json`, `diff.json`, `route.json` et `stats.json`.

## Statut GM

- `gradtext` reste refusé pour `MIRROR`; seul le sous-ensemble `CLAMP` est
  prouvé.
- `text_scale_skew` reste non promu : 77,75 % est sous le seuil inchangé de
  80,0 %.
- `fontscaler` reste refusé par `invalid.surface.prepared.text-command` hors
  du run 18 px borné.

## Vérification

```sh
rtk ./gradlew --no-daemon :kanvas:test --rerun-tasks \
  --tests org.graphiks.kanvas.surface.gpu.GPUDeliveredFontGlyphRunEvidenceTest
```

La vérification exécute le renderer WebGPU headless/offscreen et les trois
comparaisons intégrales. Elle n’utilise ni `gpu-renderer-scenes`, ni Ganesh,
Graphite, SkSL dynamique ou fenêtrage natif.
