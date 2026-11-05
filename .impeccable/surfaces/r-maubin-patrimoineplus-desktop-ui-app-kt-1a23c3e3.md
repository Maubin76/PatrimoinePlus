---
version: 1
slug: "r-maubin-patrimoineplus-desktop-ui-app-kt-1a23c3e3"
primary_target: "desktop/src/main/kotlin/fr/maubin/patrimoineplus/desktop/ui/App.kt"
related_targets: ["desktop/src/main/kotlin/fr/maubin/patrimoineplus/desktop/ui/screens/Dashboard.kt","ui/src/commonMain/kotlin/fr/maubin/patrimoineplus/ui/theme/DeskTheme.kt"]
---

# Appli PC : coque et pages (refonte « brume lumineuse », branche maybe-last-ui)

Portée : toute l'appli PC (Compose Desktop), coque, panneaux et pages ; le téléphone reçoit la même identité (fiche voisine). Mode : Operate.
Audience et tâche : Mathieu, revue posée sur grand écran (soldes, budget, objectifs, saisie en lot).
Décisions de l'utilisateur (2026-11-05) : référence image pinned (tableau de bord « OpsPulse », couleurs, proportions, police, tailles à suivre au plus près) ; style unique clair et sombre (Monochrome, Minimal, Coloré et effet verre retirés ; thèmes personnalisés = accents seulement) ; police Urbanist ; paliers validés (fondations + tableau de bord d'abord).
Reproche de l'utilisateur sur l'UI précédente : proportions incohérentes, tuiles toutes pareilles, lourd et répétitif.

## Direction contract

THESIS: Le patrimoine lu comme une salle de contrôle lumineuse : un fond de brume gris-bleu traversé d'un grand halo jaune, des panneaux de verre blanc de tailles différentes selon leur rôle ; refuse la grille de tuiles identiques et le rail latéral.

OWN-WORLD: Fond gris-bleu clair (#DCE3EA) avec halos flous jaune citron et bleu ciel ; panneaux blancs translucides, rayon 18 dp, liseré blanc 1 dp, ombre presque nulle ; encre #14171C, texte secondaire #6B7280 ; Urbanist fine (titres et chiffres en graisse légère, libellés en moyenne) ; un seul bouton plein jaune citron ; pilules translucides ; données en vert, rouge, orange, bleu ; pastilles de variation teintées ; barres fines.

STORY: Il lit le patrimoine net posé sur la lumière, voit d'un coup d'œil les objectifs du mois et la répartition, compare quatre indicateurs, puis saisit par le bouton jaune.

FIRST VIEWPORT: Cadre arrondi ; barre du haut : logo et nom à gauche, pilules d'icônes au centre (active blanche avec libellé), recherche, bascule clair/sombre, œil et profil à droite. Ligne de titre : « Tableau de bord » en grand fin et ligne d'état dessous ; pilules d'actions ; bouton jaune « Nouvelle opération » à droite. Rangée 1 : objectifs du mois (gauche), patrimoine net sur le fond (centre), répartition en jauge d'arc (droite). Rangée 2 : quatre indicateurs à mini-barres. Rangée 3 : budget du mois en barre segmentée et dernières opérations en tableau ; puis les comptes en tableau léger.

FORM: Direction imposée par l'utilisateur (référence fournie), pas de tirage ; construction pilotée par le code (pas de génération d'images), l'image de l'utilisateur sert de référence de critique. Signature : le patrimoine net posé à même le halo de lumière, sans panneau, ses chiffres roulant quand la valeur change.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
