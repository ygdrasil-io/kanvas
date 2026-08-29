# Image-filter blur préparé — blocage W30 (2026-08-28)

La route publique `Surface.drawImage` avec `ImageFilter.Blur` reste refusée.
Ce n'est pas une limite WebGPU : le dépôt possède déjà l'encodeur local
`GPUImageFilterDispatch.renderImageCommand`, qui sait créer les textures
source/horizontale/verticale et encoder source, blur horizontal, blur vertical
et composite scène.

Cet encodeur n'a aucune autorité sur une frame publique. La frontière
`GPUTaskList` préparée est scellée sur `CorePrimitive`, `SampledImage`,
`TextA8`, `ColorGlyph`, `Vertices` et `MaskBlur`. `GPUPreparedDrawImageLowerer`
refuse actuellement `Paint.imageFilter` avant le gathering sémantique; même si
ce refus était levé, `SampledImage` ne transporte ni le plan de blur ni ses
trois intermédiaires, et le materializer mixed ne sait produire qu'un upload et
un render run d'image.

Le correctif nécessaire est donc une lane propriétaire, pas un appel direct au
dispatcher dormant :

- `GPUDrawSemanticPayload.ImageFilterBlur` avec les faits immuables du plan,
  de l'artifact RGBA8, des bornes et du blend;
- un enregistreur top-level qui déclare trois ressources intermédiaires et
  quatre render passes dans la `GPUTaskList`;
- validation préflight, sélection de route et materializer dédiés;
- cache/lifetime des trois textures, telemetry et diagnostics;
- oracle CPU et preuve CPU/GPU seulement après soumission réelle.

Le contrat W29 reste intact : un blur mono-noeud CLAMP, sans input, sigma dans
`[0,12]`, transform identité et sortie au plus `2048`; les autres variantes
doivent conserver leurs refus stables. Aucune promotion W30 n'est faite. Ce
fichier est un index humain : le code, les tests et les artefacts restent la
source de vérité.
