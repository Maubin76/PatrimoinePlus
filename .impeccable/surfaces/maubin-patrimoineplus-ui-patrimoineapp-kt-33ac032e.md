---
version: 1
slug: "maubin-patrimoineplus-ui-patrimoineapp-kt-33ac032e"
primary_target: "app/src/main/java/fr/maubin/patrimoineplus/ui/PatrimoineApp.kt"
related_targets: ["app/src/main/java/fr/maubin/patrimoineplus/ui/home/HomeScreen.kt"]
---

# Coque de l'application Android et accueil (refonte « brume lumineuse », branche maybe-last-ui)

Portée : coque Compose du téléphone (thème, pilule de navigation, bouton de saisie, barres du haut) et écran d'accueil ; mêmes jetons que l'appli PC. Mode : Operate.
Audience : Mathieu, saisie d'une main en quelques secondes et revues posées. Mode discret requis. États vides réels, jamais de chiffres inventés.
Décisions de l'utilisateur (2026-11-05) : même référence que le PC ; style unique clair et sombre ; Urbanist ; navigation en pilule flottante en bas (à portée de pouce) ; paliers validés.

## Direction contract

THESIS: Le même ciel de brume que sur le PC, ramené à la main : un chiffre léger sur la lumière, des panneaux de verre empilés par rôle ; refuse la carte colorée en tête et la barre Material pleine largeur.

OWN-WORLD: Fond gris-bleu (#DCE3EA) et halo jaune flou ; panneaux blancs translucides de 18 dp, liseré blanc ; Urbanist fine ; pastilles de variation vertes et rouges ; mini-barres ; bouton jaune citron rond pour la saisie ; pilule de navigation translucide, onglet actif blanc avec libellé.

STORY: À l'ouverture, il lit le patrimoine sur la lumière, vérifie le mois en cours et ses comptes, puis note une dépense du pouce avec le rond jaune.

FIRST VIEWPORT: Barre du haut : titre « Patrimoine » à gauche, œil à droite. Patrimoine net en grand fin sur le fond, pastille de variation du mois et « Calculé au … ». Panneau « Ce mois-ci » (entrées, dépenses, mini-barres) ; panneau des comptes en liste légère ; répartition en barre segmentée. Pilule flottante en bas ; rond jaune « + » au-dessus à droite.

FORM: Direction imposée par l'utilisateur, pas de tirage ; construction pilotée par le code. Signature : le chiffre posé à même la lumière, qui roule quand la valeur change (coupé si les animations du système le sont).

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
