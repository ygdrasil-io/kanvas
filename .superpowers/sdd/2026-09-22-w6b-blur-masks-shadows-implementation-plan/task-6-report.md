# Task 6 — W6b Drop Shadows, Budgets and Atomic Recovery

Base : `8e4dd831897424dc34d4d78f5fe89eccc11bc7b1`  
Commit prévu : `feat(gpu): close w6b blur masks and shadows`

Statut : **DONE_WITH_CONCERNS**. Les assertions JUnit vérifiables sont vertes;
les sorties natives 133, reçues après ces assertions, restent **UNKNOWN** sans
attribution.

## RED → GREEN

1. Les deux nouveaux tests publics ont d'abord échoué sur
   `w6b.filter.native_execution_unimplemented` : le graphe Task 2 publiait
   les opérations shadow, mais l'admission native et le materializer ne les
   exécutaient pas. Ce RED est distinct des exits 133 qui suivent les échecs.
2. GREEN : l'admission accepte seulement les deux kinds shadow gelés. Le
   materializer exécute la chaîne déjà publiée : source scellée, X/Y blur,
   colorisation/offset, puis composition spécifique au mode. Il ne lit pas la
   scène ni ne planifie de source, pass, bounds, slot ou budget.
3. GREEN `SHADOW_ONLY` : aucun operand original n'est créé; le halo s'étend
   dans le domaine transparent avant l'offset et la source originale reste
   absente. GREEN `COMPOSITE` : le shader lit shadow + source originale puis
   `source + shadow * (1-source.a)` dans la cible gelée; seul le terminal
   parent applique le blend capturé.
4. GREEN Picture/layer/replay : le test public utilise un layer explicite
   dans un `Picture`, puis l'objet mémoire et son round-trip wire. Les deux
   résultats conservent la source opaque et le même shadow.

## Ruling TileMode et couleur

Avant d'ajuster l'oracle, la sémantique a été vérifiée contre le contrat public
et Skia, pas contre les pixels actuels :

- `ImageFilter.DropShadow` ne porte pas de `TileMode` public.
- Dans [SkDropShadowImageFilter.cpp](https://skia.googlesource.com/skia.git/%2B/d2b9e48baf1697760afc1dc8ea3ad40110b8cacc/src/effects/imagefilters/SkDropShadowImageFilter.cpp), Skia construit `SkImageFilters::Blur(sigma, input)`.
- La surcharge correspondante dans
  [SkImageFilters.h](https://skia.googlesource.com/skia/%2B/chrome/m89/include/effects/SkImageFilters.h)
  a `kDecal` par défaut.

Le `TileMode.CLAMP` préexistant dans `W6bFilterGraphConstruction` n'était donc
pas contractuel. Il est corrigé plan-side en `DECAL`; le renderer ne choisit
aucun tile mode. L'oracle étend son entrée transparente d'un rayon 3σ avant la
convolution afin de préserver le halo hors Surface avant l'offset. La couleur
shadow est décodée sRGB vers linéaire, prémultipliée par alpha, puis encodée à
l'écriture; cette convention suit W3 RGBA8 sRGB prémultiplié.

## Budgets et atomicité

La fixture publique 2×1 dérive, sans consultation du planner ou allocation
native :

`B = root 8 + sealed aggregate/filter source 2×4 + X/Y/color 3×4 + composite 8 + five uniform slots 5×16 + readback 256 = 372`.

Un layer explicite 2×1 ajoute 8, donc `B_nested = 380`. B passe; B−1 refuse
avant allocation avec `w6b.filter.frame_budget_exceeded`. Le test couvre aussi
un second sibling shadow refusé tardivement : le sentinel de `readPixels` ne
change pas, puis `discardRecordedOperations` et la nouvelle capture sur le
même `Surface` rendent les pixels attendus. Aucun fake device, reflection,
compteur interne ou test d'infrastructure n'est utilisé.

## Custody des gates

Les compilations sérielles
`:math:geometry:compileKotlinJvm`, `:math:matrix:compileKotlinJvm`,
`:render-ir:compileKotlin`, `:gpu-plan:compileKotlin`,
`:gpu-renderer:compileKotlin`, `:kanvas:compileKotlin` et
`:kanvas:compileTestKotlin` sont GREEN (exit 0).

Les shards sériels ont tous leurs XML JUnit verts avant la fin native :

| Shard | XML PASS | Fin Gradle/native |
| --- | ---: | --- |
| `W6bFilterPictureTest` | 10/10 | exit 133 / UNKNOWN |
| `W6bFilterAdmissionRecoverySurfaceTest` | 25/25 | exit 133 / UNKNOWN |
| `W6bImageBlurSurfacePixelTest` | 10/10 | exit 133 / UNKNOWN |
| `W6bMaskBlurAutoLayerSurfacePixelTest` | 9/9 | exit 133 / UNKNOWN |
| `W6bMaskShaderTableSurfacePixelTest` | 12/12 | exit 133 / UNKNOWN |
| `W6bDropShadowSurfacePixelTest` | 2/2 | exit 133 / UNKNOWN |
| `W6bBudgetRecoverySurfacePixelTest` | 2/2 | exit 133 / UNKNOWN |
| `W6aLayerBudgetRecoverySurfacePixelTest` | 10/10 | exit 133 / UNKNOWN |
| `W6aLayerW4W5SurfacePixelTest` | 19/19 | exit 133 / UNKNOWN |
| `W6aNestedLayerSurfacePixelTest` | 10/10 | exit 133 / UNKNOWN |

Les XML sont sous `kanvas/build/test-results/test/`. Le code 133 est conservé
séparément et n'est pas transformé en verdict de production.

## Audit et exclusions

La route modifiée est limitée à `GPUW6aLayerFramePlan`,
`GPUW6aEncoderScopesV1` et `GPUWgpu4kW6aLayerFramePayloadMaterializer`. Audit
manuel : pas de référence depuis cette route vers
`GPUSeparableBlurRectFrameRecorder`, `GPUTopLevelMaskBlurFrameRecording`,
`GPUPreparedFilterDAGPlanner`, `GPUPreparedMaskFilterLowerer` ou
`GPUDropShadow`; ces dernières définitions sont legacy/W8. Aucun `.toInt()` de
bounds W6b et aucune construction post-freeze de `PlanPass`/`PlanResource`/
budget ne sont ajoutés. Aucune assertion de forme/source n'a été ajoutée.

Restent explicitement hors périmètre : backdrop, filtered previous, F16/HDR,
familles W6c/W6d, fonts, codecs, GMs, dashboard, renders, baselines, scores,
Skia global, `jpg-color-cube` et device-loss/visibilité native. W6c reçoit les
tables, keys, bounds I32 scellés depuis les calculs F64 plan-side, resources,
schedule, lifetimes et terminal contracts inchangés.

## Self-review

- Les assertions renderer vérifient les arités colorize/composite, les bounds
  I32, les origins publiées et l'operand source `COMPOSITE`; le graph witness
  Task 2 reste l'autorité pour generations, F64 et sealing I32.
- Le correctif `DECAL` est plan-side, documenté par la sémantique Skia et
  couvert par l'oracle transparent indépendant; il ne crée pas une décision
  renderer-local.
- Les Tasks 3–5 restent couverts par les cinq shards W6b de préservation et
  les trois shards W6a; aucun full suite, review, push ou PR n'a été lancé.
- Concern restant : tous les shards GPU finissent par native 133. Ce fait est
  **UNKNOWN**, non attribué, et ne fonde aucune claim ISO/globale.
