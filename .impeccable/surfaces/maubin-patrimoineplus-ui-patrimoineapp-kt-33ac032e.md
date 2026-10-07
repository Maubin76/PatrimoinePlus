---
version: 1
slug: "maubin-patrimoineplus-ui-patrimoineapp-kt-33ac032e"
primary_target: "app/src/main/java/fr/maubin/patrimoineplus/ui/PatrimoineApp.kt"
related_targets: ["app/src/main/java/fr/maubin/patrimoineplus/ui/home/HomeScreen.kt"]
---

# Coque de l'application et accueil

Portée : coque Compose (thème, navigation, bouton de saisie, barres du haut), écran d'accueil et écrans provisoires des onglets. Mode : Operate.

Audience : Mathieu, seul utilisateur, qui saisit vite d'une main et fait aussi des revues posées. Chiffre clé à l'ouverture : le patrimoine total. Bouton de saisie sur Accueil et Comptes seulement. À éviter : le look banque en ligne et la gamification. Le mode discret (montants masqués) est requis. Aucune donnée n'existe encore (la base arrive à l'étape 2) : les états vides doivent être de vrais états, pas des chiffres inventés.

Direction imposée par l'utilisateur le 2026-10-07. Elle remplace la direction « Calculatrice Braun ». Références : deux maquettes d'applis financières monochromes. La première montre un solde centré en très grand, une pastille dessous et une navigation en pilule flottante. La seconde montre des cartes bordées, des titres de section en gras avec un lien souligné à droite, et des chiffres secondaires atténués. Choix confirmés :
- total centré ;
- pilule flottante à icônes seules ;
- bouton rond noir flottant ;
- monochrome, avec un mode sombre qui suit le système.

## Direction contract

THESIS: Noir sur blanc, rien d'autre. Le total est le seul élément en très grand, centré comme un solde ; tout le reste est du texte calme et des filets. Refuse la carte colorée en tête, les dégradés, les tuiles d'icônes et la barre Material pleine largeur.

OWN-WORLD: Fond blanc pur, encre presque noire (#0A0A0A), gris secondaire (#6B6B6B), filets et bordures gris très clair (#EBEBEB). Mode sombre inversé : fond #0B0B0B, encre #F5F5F5. Aucune couleur d'accent. Un rouge discret est réservé aux montants négatifs, dont le signe reste écrit. Grotesque unique, Hanken Grotesk : titres en demi-gras, total en demi-gras très serré. Formes : pilules (navigation, pastille, boutons), cartes bordées de 1 dp aux coins de 16 dp, bouton de saisie rond noir. Séries de données en niveaux de gris : noir, gris moyen, gris clair.

STORY: À l'ouverture, Mathieu lit le total centré, voit ses comptes listés dans une carte nette et leur répartition, puis ajoute une opération du pouce avec le rond noir.

FIRST VIEWPORT: Barre du haut avec le titre « Patrimoine » centré et l'œil du mode discret à droite. En dessous, le total en displayLarge demi-gras, centré, et sous lui une pastille bordée « Calculé au … ». Puis la section « Comptes » (titre en gras à gauche, lien souligné « Gérer » à droite) dans une carte bordée : une ligne par compte, avec le nom, le type en gris et le montant à droite. Ensuite la section « Répartition » : une barre en niveaux de gris et sa légende. La pilule flottante blanche à ombre douce est centrée en bas ; l'onglet actif y est dans une pastille grise. Le rond noir « + » est au-dessus, à droite. État vide : afficheur « – – –,– – € » atténué, carte « Aucun compte » avec un bouton noir « Voir les comptes » (la création arrive à l’étape 3).

FORM: Direction imposée par l'utilisateur (références fournies), donc aucun tirage ; la clé du tirage précédent, 25b6cd87, est abandonnée. Mouvement signature : le total centré en très grand, dont les chiffres roulent quand la valeur change. Quand les animations du système sont coupées, le chiffre change d'un coup.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
