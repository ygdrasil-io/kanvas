# Audit des GMs stub et des API manquantes

Date : 2026-07-13

## Périmètre et méthode

- Registre actif : `META-INF/services/org.graphiks.kanvas.skia.SkiaGm`, soit
  **622 providers**.
- Un GM est considéré comme un stub lorsqu'il lève explicitement
  `NotImplementedError`/`TODO`, ou lorsque son `draw()` est vide et ne produit
  donc aucune opération de dessin.
- `SkiaGmRenderer` remplit la surface en blanc avant d'appeler `draw()`. Un
  stub silencieux produit par conséquent seulement ce fond blanc.
- Validation exécutée :

  ```sh
  rtk ./gradlew :integration-tests:skia:test \
    --tests org.graphiks.kanvas.skia.SkiaGmRegistryTest
  ```

  Résultat : `BUILD SUCCESSFUL`; chaque provider du registre est chargeable.

## Résultat : GMs actifs

**21 des 622 GMs actifs sont des stubs** : 6 bloquants et 15 silencieux.

### Stubs bloquants (échec explicite)

| GM | API ou capacité manquante | Nature du blocage |
| --- | --- | --- |
| `mesh_zero_init` | Initialisation à zéro des ressources mesh GPU | Capacité WebGPU/backend à spécifier et valider. |
| `convex-polygon-inset` | Inset de polygone convexe (`SkInsetConvexPolygon`) | Utilitaire Skia interne ; exiger une alternative Kanvas ou un refus explicite. |
| `fancy_gradients` | Composition de `PictureShader` avec gradients sweep/radial | Composition de shader absente. |
| `macaatest` | CoreText macOS | Dépendance de plateforme, non portable. |
| `pathops_blend` | Opérations booléennes de paths (`SkPathOps::Op`) | Ne pas porter l'API interne Skia telle quelle. |
| `pathopsinverse` | PathOps avec fill inverse | Même contrainte, avec fill types inverses. |

### Stubs silencieux (corps de `draw()` vide)

| GMs | API ou capacité manquante explicitement documentée |
| --- | --- |
| `save_behind` | `saveBehind` / `drawBehind` sur `GmCanvas`. |
| `skbug_14554` | `PictureRecorder`, `drawVertices`, `drawAtlas`. |
| `new_texture_image` | Création d'images depuis sources raster, encodées, picture et texture avec contexte GPU. |
| `poster_circle` | Matrice 3D `SkM44` et pipeline de projection perspective. |
| `scale-pixels` | `SkPixmap.scalePixels`. |
| `scaled_tilemodes`, `scaled_tilemodes_npot` | `RGB_565`, cubic resampler, mipmaps et sampling anisotrope. |
| `wacky_yuv_formats`, `yuv420_odd_dim`, `yuv420_odd_dim_repeat` | Images YUVA multi-planaires avec alpha. |
| `palette` | Accès/manipulation des tables OpenType COLR/CPAL. |
| `rsx_blob_shader` | Transforms RSX de `TextBlob`. |
| `skbug_12212` | Surface A8 offscreen avec blend mode `Src`. |
| `typefacerendering_pfa`, `typefacerendering_pfb` | Fixtures de typeface PFA/PFB. |

Les GMs PFA/PFB restent explicitement hors périmètre de ce port : le code
confirme l'absence de dessin, sans qu'il soit justifié d'inférer une API
manquante unique ou d'introduire un substitut de fixture.

## Sources stub non actives

**22 sources stub supplémentaires ne sont pas enregistrées** dans le service
actif et ne font donc pas partie des 622 GMs ci-dessus.

| Domaine | GMs | API ou capacité manquante |
| --- | --- | --- |
| Backdrop/blur | `backdrop_scalefactor`, `crbug_1174354`, `crbug_1313579`, `savelayer_with_backdrop` | `SaveLayerRec` avec backdrop `SkImageFilter`, y compris blur/crop et facteur d'échelle. |
| Composite | `shadow_utils_gaussian_colorfilter` | `SkColorFilterPriv.makeGaussian`, API Skia interne à ne pas porter telle quelle. |
| Image/YUVA | `image_from_yuv_textures`, `yuv_make_color_space`, `yuv_splitter`, `yuv_to_rgb_subset_effect` | Image YUVA multi-planaire. |
| Image/pixels | `readpixels`, `workingspace_input_output` | `SkImage.readPixels`, fixture/décodeur ICO ; overload quatre arguments de `SkWorkingColorSpaceShader`. |
| Path | `drawable` | `SkDrawable.drawDrawable`. |
| Texte/glyphes | `drawglyphs`, `fancyblobunderline`, `lcdtext`, `lcdtextsize`, `savelayerpreservelcdtext`, `textblobmixedsizes_df`, `varied_text_clipped_lcd`, `varied_text_clipped_no_lcd`, `varied_text_ignorable_clip_lcd`, `varied_text_ignorable_clip_no_lcd` | Glyph drawing/RSX, décoration de texte, `Font.edging` LCD, flags de `saveLayer`, SDF text et interaction texte/clip. |

`fiddle` est également vide, mais il reproduit volontairement le GM upstream
destiné à recevoir un snippet de reproduction ; il n'est pas compté comme une
API manquante.

## Cas exclus du comptage

`compressed_textures` et `exoticformats` interceptent certains
`NotImplementedError`, mais rendent des cellules et contours de fallback. Ils
ne sont donc pas des stubs de rendu.

Les références à des APIs Skia internes dans cette liste décrivent les
préconditions des ports ; elles ne constituent pas une recommandation de les
porter. Les alternatives Kanvas/WebGPU ou une politique de refus stable restent
requises.
