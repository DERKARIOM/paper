# MakeACopy – Mode accessibilité

Ce guide explique étape par étape comment utiliser le mode Accessibilité dans MakeACopy. Ce mode est conçu pour utiliser l'appareil photo sans repères visuels — avec des annonces vocales claires et un retour haptique léger.

Remarque : lorsque ce guide mentionne « toucher », « double‑toucher » ou « bouton », il s'agit de l'usage typique avec un lecteur d'écran comme TalkBack.

---

Sommaire
- Que fait le mode Accessibilité ?
- Activer le mode Accessibilité
- Utiliser l'appareil photo (déroulé)
- Retours en détail
- Déclencher avec les boutons de volume
- Comportement en faible luminosité (lampe)
- Messages de succès et d'erreur après la capture
- Conseils pour de bons résultats
- Confidentialité et fonctionnement hors ligne
- Foire aux questions (FAQ)

---

Que fait le mode Accessibilité ?
- Met l'accent sur l'audio et l'haptique. La surcouche visuelle des coins suit l'option « Preview corner detection ».
- Annonce des états importants, par ex. « Caméra prête », « Faible luminosité », « Document détecté – prêt à capturer. »
- Donne des indications d'alignement si nécessaire, par ex. « Déplacer vers la gauche », « Déplacer vers le haut », « Rapprocher », « Pencher vers l'avant ».
- Permet de déclencher avec les boutons de volume pour éviter de viser un petit bouton à l'écran.
- Fonctionne entièrement hors ligne — pas de connexion de données, pas d'upload.

Activer le mode Accessibilité
1) Dans l'écran de l'appareil photo, ouvrez le bouton « Options » en bas.
2) Activez la case à cocher « Accessibility Mode ».
3) Confirmez avec « Confirm ».

À partir de maintenant :
- Vous recevrez des indications vocales et un retour haptique court. Si vous activez « Preview corner detection » dans Options, la surcouche des coins est affichée ; sinon, elle reste masquée.

Utiliser l'appareil photo (déroulé)
1) Ouvrir la caméra : après l'initialisation, vous entendrez « Caméra prête. Double‑toucher Scan pour capturer. »
2) Aligner : déplacez l'appareil au‑dessus du document. Le système analyse en continu l'image de la caméra.
3) Bon cadrage détecté : dès que le document est détecté de façon stable et correcte, vous entendrez un bref son de confirmation, une légère vibration et l'annonce « Document détecté – prêt à capturer. »
4) Capturer :
   - Double‑toucher le bouton « Scan » OU
   - Appuyer sur l'un des deux boutons de volume (voir la section ci‑dessous).

Retours en détail
- Caméra prête : annoncée dès que la caméra démarre.
- Document détecté (stable) :
  - Son court
  - Retour haptique léger
  - Annonce vocale « Document détecté – prêt à capturer. »
  - Remarque : ces signaux sont limités en fréquence afin de ne pas se répéter en continu.
- Faible luminosité : au lieu d'une boîte de dialogue, vous entendrez « Faible luminosité détectée. Double‑toucher Flash pour activer. »
- Lampe (on/off) : lors du changement d'état, vous entendrez « Lampe activée/désactivée ».

 Indications d'alignement (exemples)
 - Mouvement :
   - « Déplacer vers la gauche » / « Déplacer vers la droite »
   - « Déplacer vers le haut » / « Déplacer vers le bas »
 - Distance :
   - « Rapprocher » / « Éloigner »
   - « Rapprocher. Le document est trop loin. » (lorsque le document est détecté mais trop petit dans le cadre)
 - Inclinaison :
   - « Pencher à gauche » / « Pencher à droite »
   - « Pencher vers l'avant » / « Pencher vers l'arrière »
 - Alignement OK : « Document aligné »

 Notes sur la fréquence :
 - Les annonces sont volontairement calmes : elles sont prononcées seulement après une brève stabilité (hystérésis) et au plus environ toutes les 1–1,5 secondes (limitation).
 - « OK » (aligné) n'est généralement annoncé qu'au moment où l'on entre dans un bon état, ou après une période plus longue de silence, afin d'éviter les répétitions.

Déclencher avec les boutons de volume
- Quand le mode Accessibilité est actif, vous pouvez déclencher avec Volume + ou Volume −.
- L'application empêche la modification réelle du volume et déclenche la capture à la place.
- Une courte vibration est fournie lors de l'appui.
- Les entrées sont anti‑rebond : appuyer plusieurs fois rapidement (≈ 0,8 s) ne déclenche pas plusieurs captures.
- Pendant le traitement d'une image, les appuis sont ignorés afin d'éviter les conflits.

Comportement en faible luminosité (lampe)
- Si l'application détecte une faible luminosité, elle n'affiche pas d'invite bloquante en mode Accessibilité.
- À la place, vous entendrez une recommandation vocale d'activer la lampe.
- Double‑toucher le bouton « Flash » pour l'activer/la désactiver. Vous entendrez « Lampe activée » ou « Lampe désactivée ».


Messages de succès et d'erreur après la capture
- Enregistré avec succès : « Image capturée. » plus une courte vibration.
- Échec de la capture : « Échec de la capture. » — vous pouvez relancer la capture ensuite.


Conseils pour de bons résultats
- Distance : tenez l'appareil de sorte que le document tienne entièrement dans le cadre (typiquement 20–40 cm au‑dessus de la page).
- Stabilité : rester immobile un court instant aide la détection.
- Alignement : si possible, gardez l'appareil parallèle à la surface du document.
- Lumière : activez la lampe en cas d'ombre ou de très faible luminosité.

Confidentialité et fonctionnement hors ligne
- MakeACopy traite les images localement sur votre appareil.
- Aucun upload ni partage n'a lieu en mode Accessibilité. Aucune connexion Internet requise.

Foire aux questions (FAQ)
Voir aussi : FAQ du site → Numérisation (Caméra) : docs/index_fr.html#faq-scanning
Q : J'entends « Document détecté » très souvent.
R : L'application limite déjà les répétitions. Éloignez légèrement l'appareil puis réalignez. Lorsque la détection est stable, vous obtenez un son + une vibration + une annonce.

Q : Les boutons de volume modifient toujours le volume.
R : Cela ne devrait pas arriver en mode Accessibilité tant que l'aperçu de la caméra est visible. Vérifiez que le mode Accessibilité est activé et que la caméra est à l'écran. Pendant le traitement, les appuis sont ignorés.

Q : Il fait très sombre et la détection ne fonctionne pas.
R : Activez la lampe. Essayez d'éclairer le document de façon aussi uniforme et sans ombre que possible.


Q : Dois‑je activer la « Preview corner detection » (analyse visuelle) pour que le mode Accessibilité fonctionne ?
R : Non. Le mode Accessibilité exécute l'analyse nécessaire en interne même si l'option d'analyse visuelle est désactivée. L'application continue d'analyser les images pour fournir un retour audio/haptique.

Q : Si « Preview corner detection » (analyse en direct) et le mode Accessibilité sont tous deux activés, l'aperçu caméra est‑il visible ?
R : Oui. L'aperçu normal de la caméra reste visible. Si « Preview corner detection » est activé, la surcouche visuelle des coins est également affichée en mode Accessibilité. L'analyse s'exécute de toute façon en arrière‑plan pour le score et les retours.

En mode Accessibilité, l'application ne se base pas sur un « score du modèle » unique. Elle utilise plutôt la forme du document détecté (un quadrilatère) ainsi que la stabilité sur plusieurs images pour décider quand le guidage est utile et quand le document est « prêt ».

Q : Le mode Accessibilité fonctionne‑t‑il sans TalkBack ?
R : Partiellement. La sortie vocale nécessite un lecteur d'écran actif (par ex. TalkBack ou « Sélectionner pour prononcer »). Sans lecteur d'écran, vous recevez toujours des sons et une haptique légère, mais pas de synthèse vocale.

<a id="guide-fr-directional-hints-landscape"></a>
Q : Que signifient « gauche/droite/haut/bas » si je tiens le téléphone en mode paysage ?
R : Remarque sur la tenue du téléphone (paysage) : l'écran caméra reste en orientation portrait. Les indications directionnelles (« gauche/droite/haut/bas ») se réfèrent à l'aperçu aligné « à l'endroit ».

Si vous tenez le téléphone de côté, selon l'appareil/la version d'Android,
- les indications peuvent rester celles du mode portrait (car l'interface ne passe pas en paysage), ou
- les axes d'analyse internes peuvent suivre la rotation de l'affichage.

Si « gauche/droite/haut/bas » semble illogique, revenez en portrait ou faites pivoter le téléphone de 180° et voyez si les indications deviennent plus cohérentes.

<a id="guide-fr-orientation-tip"></a>
Q : L'application suggère‑t‑elle portrait ou paysage ?
R : Oui. En mode Accessibilité, l'application peut suggérer si le portrait ou le paysage semble mieux convenir à la page actuelle.

Le conseil n'est donné que si
- l'estimation est suffisamment fiable (confiance ≥ 0,30), et
- aucun document plausible n'est actuellement détecté (pour ne pas masquer les indications normales).

Pour rester discret, ce conseil passe par la même logique de guidage que les autres annonces (stabilité brève sur plusieurs images) et il est limité en fréquence. Vous pouvez entendre, par exemple, « Cela ressemble à du portrait … » ou « … à du paysage … ». Les indications directionnelles normales restent inchangées.

<a id="guide-fr-ready-decision"></a>
Q : Comment l'application décide‑t‑elle qu'un document est « prêt à capturer » ?
R : L'application recherche une forme de document plausible (un quadrilatère) et vérifie si elle reste stable pendant un court instant. Ce n'est qu'alors qu'elle émet un bref son + une légère vibration et annonce « Document détecté – prêt à capturer. »

Si la détection est instable (par ex. parce que vous bougez, que la page est partiellement hors cadre ou que l'éclairage est difficile), vous entendrez plutôt des indications directionnelles.

Q : Comment améliorer la détection et le guidage ?
R : Utilisez un éclairage homogène et suffisamment fort, évitez les reflets ; tenez le téléphone parallèle à la page ; gardez les quatre coins visibles avec une petite marge ; si vous êtes trop près, reculez un peu et recadrez ensuite ; posez le papier sur un fond mat et contrasté ; restez brièvement immobile pour stabiliser la détection ; choisissez une orientation adaptée (A4/Letter : le portrait convient souvent).

Astuce : une explication plus détaillée avec des exemples est disponible sur le site : docs/index.html → FAQ → « Scanning (Camera) ».

<a id="guide-fr-move-back"></a>
Q : J'entends constamment « Éloignez‑vous/Move back ».
R : Les invites de distance sont supprimées en l'absence de document plausible et les répétitions sont limitées. Améliorez l'éclairage, incluez toute la page, et restez brièvement immobile.

—

Note technique (pour les contributeurs)

Pour une explication détaillée du concept sous‑jacent « plausibilité + métriques de cadrage + stabilité » (y compris le spec‑sheet et les scénarios QA), voir : `docs/accessibility_mode_docquad_concept.md`.

—

Contact
Si quelque chose n'est pas clair ou si vous avez des suggestions pour améliorer l'accessibilité, nous apprécions vos retours via les avis sur le store ou dans le dépôt du projet.
