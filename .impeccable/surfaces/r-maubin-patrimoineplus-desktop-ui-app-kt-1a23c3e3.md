---
version: 1
slug: "r-maubin-patrimoineplus-desktop-ui-app-kt-1a23c3e3"
primary_target: "desktop/src/main/kotlin/fr/maubin/patrimoineplus/desktop/ui/App.kt"
related_targets: ["desktop/src/main/kotlin/fr/maubin/patrimoineplus/desktop/ui/components/Layout.kt","ui/src/commonMain/kotlin/fr/maubin/patrimoineplus/ui/components/Tile.kt"]
---

# Appli PC : coque et pages (refonte « pastel flottant »)

Scope : toute l'appli PC (Compose Desktop), coque, tuiles, panneaux. Mode : Operate. Android inchangé.
Audience et tâche : Mathieu, revue posée sur grand écran (soldes, budget, objectifs, saisie en lot).
Contraintes : 8 pages, thèmes Monochrome / Minimal / Coloré (couleurs des données), clair et sombre, mode discret, raccourcis Ctrl+N / Ctrl+F.
Décisions de l'utilisateur (2026-10-26) : référence image pinned ; rail d'icônes seul ; nouveau socle pour tous les thèmes ; police géométrique (Outfit).

## Direction contract

THESIS: Le patrimoine posé sur des panneaux blancs qui flottent au-dessus d'un ciel pastel ; refuse la grille d'administration bordée, à plat, avec volet latéral à libellés.

OWN-WORLD: Fond dégradé lavande / bleu brume / rose poudré (sombre : nuit indigo et prune) ; un grand cadre arrondi translucide porte l'appli ; panneaux blancs à 28 dp de rayon, sans bord, ombre douce teintée lavande ; encre presque noire en pilules et cercles pleins ; Outfit en graisse moyenne, chiffres tabulaires ; thème Coloré : tuiles en aplats pastel de la couleur du type de compte.

STORY: Il voit d'abord le titre de la page et son chiffre clé, navigue par le rail sans lire, saisit par la pilule noire « Nouvelle opération ».

FIRST VIEWPORT: Cadre arrondi à 14 dp des bords ; à gauche, logo rond de l’appli puis rail flottant de 8 icônes rondes, l'active en disque noir, bascule soleil / lune en bas ; en haut à droite, état discret, pilule noire « Nouvelle opération », cercles recherche et œil ; titre de page à 42 sp ; tuiles flottantes en dessous.

FORM: Coque à rail d'icônes et panneaux flottants (référence de l'utilisateur, direction pinned ; pas de tirage). Signature : le disque noir de la page active glisse d'une icône à l'autre dans le rail.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
