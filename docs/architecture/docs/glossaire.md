# Glossaire

| Terme | Définition |
|-------|-----------|
| **DisplayList** | Séquence ordonnée de `DisplayOp` produite par la Surface. Indépendante du GPU. |
| **DisplayOp** | Opération de dessin élémentaire (rectangle, image, texte, saveLayer...). |
| **GPUOpMapper** | Traduit les `DisplayOp` en `NormalizedDrawCommand`. Résout l'état mutable. |
| **NormalizedDrawCommand** | Commande normalisée, immuable, sans handles GPU. |
| **GPURecorder** | Analyse et enregistre les commandes normalisées. Produit un `GPURecording`. |
| **GPUDrawAnalysis** | Analyse par commande : route, calque, matériau, render step. |
| **GPURecording** | Enregistrement complet de la frame. Immuable, scellé. |
| **GPUTaskList** | Autorité de dépendance. Liste ordonnée de tâches GPU. |
| **GPUBlendPlan** | Autorité canonique de blend (29 modes). |
| **GPUDestinationReadPlan** | Stratégie de lecture destination. |
| **GPUColorPlan** | Classification alpha source, compatibilité format cible. |
| **GPUGeometryPlan** | Chemin de rendu pour une géométrie. |
| **GPUClipPlan** | Plan de couverture (scissor, stencil, MSAA, shader). |
| **GPUFramePlanner** | Produit un GPUFramePlan sans handles. |
| **GPUFramePlan** | Plan d'exécution linéaire et immuable. |
| **GPUFrameCoordinator** | Point d'entrée unique planification → pré-vol → exécution. |
| **GPUFramePreflighter** | Seule frontière de matérialisation GPU. |
| **PreparedGPUFrame** | Frame prête à exécuter, scellée, sans handles exposés. |
| **GPUFrameExecutor** | Exécute un PreparedGPUFrame. Un submit par frame. |
| **GPUQueueCompletionAdapter** | Complétion asynchrone post-soumission. |
| **GPUPreparedSurfaceSession** | Session réutilisable entre frames. |
| **GPUResourceProvider** | Propriétaire des ressources GPU concrètes. |
| **GPURuntimeResourceAdapter** | Registre privé de ressources par frame. |
| **GPUSceneTarget** | Texture canonique de rendu. |
| **GPUFrameMemoryBudgetPlan** | Comptabilité mémoire de la frame. |
| **Fixed-function blend** | Blend hardware WebGPU. Zéro coût shader. |
| **Shader blend** | Blend par shader WGSL. |
| **Snapshot destination** | Copie bornée de la cible pour lecture shader. |
| **Coverage** | Proportion de pixel recouverte. |
| **Render pass** | Groupe de draws même cible/format/sample count. |
| **Command encoder** | Objet WebGPU qui enregistre passes et copies. |
| **Canvas** | API publique de dessin Kanvas. |
| **Surface** | Surface de rendu Kanvas. |
| **Kadre** | Bibliothèque native de fenêtrage (ygdrasil-io/poc-koreos). |
| **wgsl4k** | Bibliothèque Kotlin pour parsing/réflexion/génération WGSL. |
