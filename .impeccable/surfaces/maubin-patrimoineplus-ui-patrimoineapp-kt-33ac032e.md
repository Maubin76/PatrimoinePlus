---
version: 1
slug: "maubin-patrimoineplus-ui-patrimoineapp-kt-33ac032e"
primary_target: "app/src/main/java/fr/maubin/patrimoineplus/ui/PatrimoineApp.kt"
related_targets: ["app/src/main/java/fr/maubin/patrimoineplus/ui/home/HomeScreen.kt"]
---

# Coque de l'application et accueil

Portée : coque Compose (thème, barre de navigation, FAB, barres du haut), écran d'accueil et écrans provisoires des onglets. Mode : Operate.

Audience : Mathieu, seul utilisateur ; saisie rapide d'une main et revue posée. Chiffre clé à l'ouverture : le patrimoine total. FAB de saisie sur Accueil et Comptes seulement. À éviter : look banque en ligne, gamification. Mode discret (montants masqués) requis. Aucune donnée n'existe encore (base à l'étape 2) : les états vides doivent être de vrais états, pas des chiffres inventés.

## Direction contract

THESIS: Le patrimoine est le résultat d'une addition. L'écran montre les opérandes (les comptes) et le résultat (le total) après un « = » rond et jaune, comme la touche égale de la Braun ET66. Refuse l'arrangement par défaut de la catégorie : une carte colorée en tête, des tuiles de comptes ombrées et des icônes de tirelire.

OWN-WORLD: Palette fixe à la place de Material You. En clair, un fond aluminium gris chaud (#EEECE7), le texte en anthracite (#2A2724) et des filets gris tiède. En sombre, un corps brun-noir (#1C1A18) avec le texte en gris clair chaud. Un seul jaune « touche égale » (#E2A72E), réservé à l'action et au résultat. Un olive (#5F7F4A) et un ocre servent de séries de données. Formes : des touches rondes (FAB circulaire, indicateurs en points) et des filets de 1 dp ; ni ombre ni carte. Chiffres tabulaires partout.

STORY: À l'ouverture, Mathieu lit son patrimoine total, voit de quoi il est fait (comptes alignés comme une addition), et ajoute une opération d'un pouce. Il croit au chiffre parce qu'il voit qu'il est calculé.

FIRST VIEWPORT: Barre du haut sobre : le nom à gauche et l'œil du mode discret à droite. Sous la mention « Patrimoine », le total en displayLarge, précédé du rond jaune « = ». Ensuite une barre de répartition proportionnelle pleine largeur, puis les comptes en lignes : un point de type, le nom, et le montant aligné à droite. Un filet sépare les opérandes du résultat. Le FAB rond jaune est en bas à droite, au-dessus de la barre de navigation. État vide : l'addition s'écrit avec des tirets et un bouton « Créer mes comptes ».

FORM: Calculatrice Braun ET66 / Dieter Rams, candidat 4 de la liste ordonnée ; clé de tirage 25b6cd87. Mouvement signature : les chiffres du total roulent quand sa valeur change, comme un afficheur ; quand les animations du système sont coupées, le chiffre change d'un coup (Compose respecte l'échelle 0 du système, aucun fondu n'est possible). Ajouts retenus des challengers : hiérarchie par l'échelle seule (spécimen) ; un mois par écran, glissement aimanté (flux) ; échelle fixe entre périodes (folio) ; rampe proportionnelle unique (atlas) ; filets au lieu d'ombres (annuel).

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
