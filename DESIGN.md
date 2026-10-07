---
name: PatrimoinePlus
description: Patrimoine et dépenses personnels, en noir sur blanc ; le total centré en très grand est le seul élément qui élève la voix.
colors:
  white: "#FFFFFF"
  ink: "#0A0A0A"
  grey-secondary: "#6B6B6B"
  rule: "#EBEBEB"
  outline: "#9E9E9E"
  grey-selection: "#EDEDED"
  series-mid: "#8A8A8A"
  series-light: "#C4C4C4"
  negative: "#C62828"
  black: "#0B0B0B"
  paper: "#F5F5F5"
  grey-secondary-dark: "#A3A3A3"
  rule-dark: "#262626"
  outline-dark: "#6E6E6E"
  night-pill: "#1A1A1A"
  grey-selection-dark: "#333333"
  series-mid-dark: "#9E9E9E"
  series-low-dark: "#5C5C5C"
  negative-dark: "#EF9A9A"
typography:
  display-large:
    fontFamily: "Hanken Grotesk, sans-serif"
    fontSize: "57sp"
    fontWeight: 600
    lineHeight: "64sp"
    letterSpacing: "-0.035em"
  display-medium:
    fontFamily: "Hanken Grotesk, sans-serif"
    fontSize: "45sp"
    fontWeight: 600
    lineHeight: "52sp"
    letterSpacing: "-0.03em"
  display-small:
    fontFamily: "Hanken Grotesk, sans-serif"
    fontSize: "36sp"
    fontWeight: 600
    lineHeight: "44sp"
    letterSpacing: "-0.025em"
  headline-large:
    fontFamily: "Hanken Grotesk, sans-serif"
    fontSize: "32sp"
    fontWeight: 600
    lineHeight: "40sp"
    letterSpacing: "-0.02em"
  headline-medium:
    fontFamily: "Hanken Grotesk, sans-serif"
    fontSize: "28sp"
    fontWeight: 600
    lineHeight: "36sp"
    letterSpacing: "-0.015em"
  headline-small:
    fontFamily: "Hanken Grotesk, sans-serif"
    fontSize: "24sp"
    fontWeight: 600
    lineHeight: "32sp"
    letterSpacing: "-0.01em"
  title-medium:
    fontFamily: "Hanken Grotesk, sans-serif"
    fontSize: "16sp"
    fontWeight: 500
    lineHeight: "24sp"
    letterSpacing: "0"
  body-large:
    fontFamily: "Hanken Grotesk, sans-serif"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: "24sp"
    letterSpacing: "0"
  body-medium:
    fontFamily: "Hanken Grotesk, sans-serif"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: "20sp"
    letterSpacing: "0"
  label-large:
    fontFamily: "Hanken Grotesk, sans-serif"
    fontSize: "14sp"
    fontWeight: 500
    lineHeight: "20sp"
    letterSpacing: "0"
rounded:
  card: "16dp"
  full: "9999dp"
spacing:
  hairline: "1dp"
  tight: "2dp"
  segment-gap: "3dp"
  stack: "6dp"
  near: "8dp"
  dot-gap: "12dp"
  gutter: "16dp"
  margin: "20dp"
  section: "24dp"
  section-after-total: "28dp"
  section-settings: "32dp"
  pill-clearance: "96dp"
  entry-clearance: "72dp"
components:
  total-display:
    textColor: "{colors.ink}"
    typography: "{typography.display-large}"
  info-pill:
    backgroundColor: "{colors.white}"
    textColor: "{colors.grey-secondary}"
    typography: "{typography.label-large}"
    rounded: "{rounded.full}"
    padding: "8dp 16dp"
  section-header:
    textColor: "{colors.ink}"
    typography: "{typography.headline-small}"
    height: "48dp"
  section-link:
    textColor: "{colors.ink}"
    typography: "{typography.label-large}"
    height: "48dp"
  bordered-card:
    backgroundColor: "{colors.white}"
    rounded: "{rounded.card}"
    width: "100%"
  account-row:
    textColor: "{colors.ink}"
    typography: "{typography.title-medium}"
    padding: "10dp 20dp"
    height: "56dp"
  button-primary:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.white}"
    typography: "{typography.label-large}"
    rounded: "{rounded.full}"
    padding: "12dp 24dp"
  entry-fab:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.white}"
    rounded: "{rounded.full}"
    size: "56dp"
  nav-pill:
    backgroundColor: "{colors.white}"
    rounded: "{rounded.full}"
    padding: "8dp"
    height: "64dp"
  nav-tab:
    backgroundColor: "{colors.white}"
    textColor: "{colors.ink}"
    rounded: "{rounded.full}"
    width: "68dp"
    height: "48dp"
  nav-tab-active:
    backgroundColor: "{colors.grey-selection}"
    textColor: "{colors.ink}"
    rounded: "{rounded.full}"
  proportion-bar:
    backgroundColor: "{colors.grey-selection}"
    rounded: "{rounded.full}"
    height: "10dp"
    width: "100%"
  series-dot:
    rounded: "{rounded.full}"
    size: "10dp"
  top-bar:
    backgroundColor: "{colors.white}"
    textColor: "{colors.ink}"
    typography: "{typography.title-medium}"
    height: "64dp"
---

# Design System: PatrimoinePlus

## Overview

**Creative North Star: "Le Solde à l'encre"**

Noir sur blanc, rien d'autre. Le patrimoine total est le seul élément en très grand, centré comme le solde d'un relevé ; ses chiffres roulent quand la valeur change. Tout le reste est du texte calme en une seule grotesque, des filets gris très clair et des cartes bordées de 1 dp. Il n'y a aucune couleur d'accent : la hiérarchie vient de la taille, de la graisse (demi-gras pour les titres) et du gris secondaire. Le mode sombre suit le système et inverse simplement l'encre et le papier.

La navigation est une pilule blanche flottante, posée en bas au centre avec une ombre douce, aux icônes au trait fin ; l'onglet actif devient une capsule à l'encre qui s'élargit pour afficher son nom. Le bouton de saisie est un rond noir plat, au-dessus de la pilule à droite, présent sur Accueil et Comptes seulement. Les écrans sont des colonnes uniques : titres de section en gras à gauche, lien souligné à droite, puis une carte bordée qui regroupe les lignes.

Rejets confirmés par le contrat de direction : la carte colorée en tête, les dégradés, les tuiles d'icônes décoratives et la barre de navigation Material pleine largeur.

**Key Characteristics:**
- Monochrome strict : encre presque noire, blanc pur, trois gris fonctionnels ; un rouge discret réservé aux montants négatifs.
- Une grotesque unique, Hanken Grotesk, en 400, 500 et 600 ; chiffres à chasse fixe.
- Le total centré, à la plus grande taille d'afficheur qui tient sur une ligne, roule chiffre par chiffre.
- Formes : pilules (navigation, pastille, boutons), cartes bordées aux coins de 16 dp, rond noir de saisie.
- Séries de données en niveaux de gris : noir, gris moyen, gris clair.

## Colors

Une encre presque noire sur un blanc pur, des gris pour le secondaire, les filets et les séries ; aucune teinte hormis le rouge des négatifs. Chaque valeur claire a son équivalent sombre, aux mêmes rôles Material 3.

### Primary
- **Encre** (ink) / **Papier** (paper, sombre) : rôles `primary`, `primaryContainer` et `onSurface`. Tout le texte principal, le total, les icônes, le bouton de saisie, le bouton plein, la piste de l'interrupteur actif, et la série « comptes courants ».

### Neutral
- **Blanc** (white) / **Noir** (black, sombre) : fond de l'écran, de la barre du haut, des cartes et de la pastille d'information ; tout est posé sur le même fond.
- **Gris secondaire** (grey-secondary, 5,3:1 sur blanc) / **Gris secondaire sombre** (grey-secondary-dark) : rôle `onSurfaceVariant`. Type d'un compte, explications, pourcentages, texte de la pastille, réglages à venir, afficheur vide, masque du mode discret.
- **Filet** (rule) / **Filet sombre** (rule-dark) : rôle `outlineVariant`. Bordures de 1 dp des cartes et de la pastille, filets entre lignes, filet sous la barre du haut au défilement, liseré de la pilule en mode sombre.
- **Gris de sélection** (grey-selection) / **Gris de sélection sombre** (grey-selection-dark) : rôles `secondaryContainer` et `surfaceContainerHigh`. Rail de la barre de répartition.
- **Pilule nocturne** (night-pill) : rôle `surfaceBright` en sombre ; fond de la pilule de navigation, un cran au-dessus du noir. En clair, la pilule est blanche.
- **Contour** (outline / outline-dark) : rôle `outline`, réservé aux contours de champs Material.

### Séries de données
- **Gris moyen** (series-mid / series-mid-dark) : rôle `secondary`, série « livrets ».
- **Gris clair** (series-light) / **Gris bas** (series-low-dark, sombre) : rôle `tertiary`, série « compte-titres ».

### Négatif
- **Rouge négatif** (negative / negative-dark) : rôle `error`. Montant d'un compte inférieur à zéro, dont le signe « − » reste écrit.

### Named Rules
**The Ink Only Rule.** Aucune couleur d'accent. Un état sélectionné, un lien ou une action se distinguent par l'encre, la graisse, le soulignement ou une pastille grise, jamais par une teinte.

**The Signed Red Rule.** Le rouge n'apparaît que sur un montant négatif, et jamais seul : le signe « − » porte l'information, le rouge la confirme.

**The Fixed Palette Rule.** Pas de couleurs dynamiques Material You ; `surfaceTint` est transparent pour qu'aucune élévation ne teinte une surface.

**The Greyscale Series Rule.** Les types de compte s'identifient par trois niveaux de gris (encre, gris moyen, gris clair), en point de 10 dp et en segment de barre, toujours accompagnés de leur libellé.

## Typography

**Display Font:** Hanken Grotesk (repli sans-serif), police variable embarquée (`res/font/hanken_grotesk.ttf`)
**Body Font:** Hanken Grotesk
**Label/Mono Font:** Hanken Grotesk ; aucune police mono.

**Character:** une grotesque unique, nette et neutre. Le demi-gras très serré du total lui donne la présence d'un solde imprimé ; le texte courant reste sans interlettrage. Les chiffres ont tous la même chasse, ce qui aligne les montants et laisse rouler un chiffre sans décaler ses voisins.

### Hierarchy
- **Display** (600, 57 / 45 / 36 sp, interlettrage -0,035 à -0,025 em) : réservé au total. La taille est choisie à l'exécution : la plus grande de displayLarge, displayMedium, displaySmall, headlineLarge, headlineMedium qui tient sur une ligne, en tenant compte de l'échelle de police du système.
- **Headline** (600, 32 / 28 sp) : replis du total. **headlineSmall** (600, 24 sp, -0,01 em) : titres de section (« Comptes », « Répartition », « Confidentialité »).
- **Title** (500, 16 sp) : titleMedium pour le titre centré de la barre du haut, le nom et le montant d'un compte, le titre d'un état vide et d'un réglage.
- **Body** (400, 16 / 14 sp, interlettrage 0) : bodyLarge pour la légende de répartition ; bodyMedium pour le type d'un compte et les explications.
- **Label** (500, 14 sp, interlettrage 0) : labelLarge pour la pastille d'information, le lien souligné et le texte des boutons.

### Named Rules
**The One Loud Number Rule.** Seul le total utilise l'échelle display ; aucun autre chiffre ni titre ne dépasse headlineSmall.

**The Tabular Digits Rule.** Tous les montants restent en Hanken Grotesk et passent par le composant de montant ; pas de police à chiffres proportionnels pour un montant.

## Layout

Une colonne unique, pleine largeur, à marges latérales de 20 dp sur tous les écrans. Le contenu commence 16 dp sous la barre du haut. L'accueil se lit de haut en bas : le total centré, la pastille 16 dp dessous, 28 dp, la section « Comptes » (titre de 48 dp minimum, 8 dp, carte), 24 dp, la section « Répartition » (titre, 4 dp, barre de 10 dp, 12 dp, légende). Les réglages espacent leurs sections de 32 dp.

Dans une carte, une ligne mesure au moins 56 dp, avec 20 dp de marge horizontale et 10 dp verticale : nom (deux lignes au plus) et type empilés à 2 dp, 16 dp minimum, montant calé à droite. Les lignes sont séparées par un filet en retrait de 20 dp de chaque côté. Une ligne de légende mesure au moins 40 dp : point de 10 dp, 12 dp, libellé, pourcentage en gris, 16 dp, montant.

Le contenu défile sous la pilule flottante. Il réserve en bas 96 dp pour la pilule (64 dp, plus 12 dp sous elle et 20 dp de marge), plus 72 dp quand le bouton de saisie est affiché (56 dp et 16 dp d'écart), afin que la fin d'une liste puisse toujours remonter au-dessus d'eux.

## Elevation & Depth

Le système est plat par défaut. Les cartes, la pastille et la barre du haut n'ont aucune ombre ni teinte : elles se détachent par une bordure de 1 dp en `outlineVariant`. Un filet de 1 dp apparaît sous la barre du haut seulement quand le contenu défile dessous. Le bouton de saisie a une élévation nulle dans tous ses états. Une seule surface flotte : la pilule de navigation.

### Shadow Vocabulary
- **Ombre de la pilule** (élévation Material 10 dp, ombre douce diffuse) : pilule de navigation en mode clair uniquement. En mode sombre, où l'ombre ne se voit pas, elle est remplacée par un liseré de 1 dp en `outlineVariant`.

### Named Rules
**The One Float Rule.** Seule la pilule de navigation porte une ombre ; tout le reste se sépare par une bordure de 1 dp ou par l'espace.

## Shapes

Deux familles. Les pilules et les ronds pour ce qui se touche ou marque : pilule de navigation et ses onglets (48 × 52 dp au repos, capsule légendée quand actif), pastille d'information, bouton plein, bouton de saisie rond de 56 dp, points de série de 10 dp, barre de répartition à bouts arrondis (segments séparés de 3 dp sur un rail gris). Les cartes, seules surfaces rectangulaires, ont des coins de 16 dp et une bordure de 1 dp. Tout le reste est rectiligne : filets, bords d'écran.

### Named Rules
**The Pill or Card Rule.** Une forme interactive est une pilule ou un rond ; un groupe de lignes est une carte de 16 dp. Pas de coins intermédiaires.

## Components

### Buttons
- **Bouton de saisie :** rond noir de 56 dp (papier en mode sombre), « + » Material arrondi en blanc, élévation nulle. Au-dessus de la pilule, aligné sur la marge de droite, sur Accueil et Comptes seulement ; apparaît par fondu et zoom depuis 80 % (150 ms), disparaît en 100 ms.
- **Bouton plein :** bouton Material pilule en encre, texte labelLarge blanc, 24 × 12 dp de marge interne. Seule action d'un état vide.
- **Lien de section :** texte labelLarge souligné en encre, à droite du titre de section, cible tactile de 48 dp de haut.
- **Action « œil » :** bouton icône à droite de la barre du haut ; l'œil ouvert passe à l'œil barré par un fondu enchaîné.

### Chips
- **Pastille d'information :** pilule bordée de 1 dp sur le fond, texte labelLarge gris secondaire, 16 × 8 dp de marge. Non cliquable, donc sans chevron. Sert à la date de calcul sous le total.

### Cards / Containers
- **Corner Style :** 16 dp.
- **Background :** fond de l'écran (blanc / noir).
- **Shadow Strategy :** aucune (voir Elevation & Depth).
- **Border :** 1 dp en `outlineVariant`.
- **Internal Padding :** 20 dp horizontal ; les lignes portent leur propre marge verticale (10 à 16 dp).

### Inputs / Fields
- **Interrupteur :** Switch Material 3, piste en encre et pouce blanc quand il est actif. La ligne entière de la carte est la cible tactile.

### Navigation
- **Barre du haut :** sur le fond de l'écran, titre centré en titleMedium, actions à droite. Un filet de 1 dp apparaît dessous dès que le contenu défile ; la couleur de la barre ne change jamais.
- **Pilule de navigation :** capsule flottante centrée en bas, 12 dp au-dessus de la barre système, 6 dp de marge interne et 8 dp d'écart entre onglets. Quatre onglets (Accueil, Comptes, Catégories, Réglages), annoncés par leur nom à TalkBack. Icônes maison du jeu `LineIcons` (`ui/icons`) : trait de 1,6 sur grille de 24, extrémités arrondies, aucun aplat (maison, portefeuille, grille, réglettes). Au repos, icône seule en gris secondaire, cible de 48 × 52 dp. Un indicateur unique, capsule pleine à l'encre (papier en sombre), glisse sous les onglets et épouse la largeur de l'onglet actif, qui s'élargit pour afficher son nom en labelLarge couleur du fond ; le nom n'apparaît qu'au-delà de la moitié du trajet, jamais tronqué ; chaque icône prend la couleur du fond selon la part que l'indicateur recouvre. Tout découle de la position continue du pager (page + fraction), lue en mise en page et en dessin : la capsule suit le doigt pendant un glissement. Changement d'onglet : les quatre écrans sont côte à côte dans un pager horizontal ; on glisse de gauche à droite, ou on touche un onglet (défilement sur ressort sans rebond, raideur moyenne-basse) ; retour haptique léger à chaque page atteinte ; le retour système ramène à l'Accueil.

### Total (signature)
Le montant centré à la plus grande taille d'afficheur qui tient sur une ligne, puis la pastille « Calculé au … ». Quand la valeur change, chaque caractère qui change roule verticalement (320 ms, vers le haut si le chiffre augmente) ; les positions se repèrent depuis la droite pour que les centimes restent en place. Si l'échelle d'animation du système vaut 0, le montant change d'un coup. Sans compte, l'afficheur montre « – – –,– – € » en gris secondaire et la pastille dit « Rien à calculer pour l'instant » : jamais un chiffre inventé.

### Répartition
Barre proportionnelle de 10 dp sur une rampe fixe (toujours 100 % de la largeur), segments arrondis séparés de 3 dp sur un rail gris de sélection, puis une ligne de légende par type : point de série, libellé, part en gris, sous-total. TalkBack lit les parts, jamais les montants en mode discret.

### Montants et mode discret
Tout montant passe par un composant unique qui le formate en euros français. En mode discret, il est remplacé, par un fondu enchaîné de 180 ms, par cinq points ronds dessinés et le symbole €, en gris secondaire, sans révéler ni le signe ni l'ordre de grandeur.

### État vide
Dans une carte bordée : titre en titleMedium, explication en bodyMedium gris secondaire à 6 dp, puis éventuellement un bouton plein 12 dp plus bas ; 20 dp de marge interne. Il dit ce qui manque, pourquoi, et comment y remédier.

## Do's and Don'ts

### Do:
- **Do** écrire en encre (ink / paper) sur le fond, et réserver le gris secondaire aux informations de second rang.
- **Do** regrouper des lignes liées dans une carte bordée de 1 dp aux coins de 16 dp, séparées par des filets en retrait de 20 dp.
- **Do** ouvrir une section par un titre headlineSmall en demi-gras à gauche et, s'il y a une destination, un lien souligné à droite.
- **Do** faire passer chaque montant par le composant de montant, pour le format français, le roulement et le mode discret.
- **Do** dimensionner le total à la plus grande taille de l'échelle display qui tient sur une ligne, en respectant l'échelle de police du système.
- **Do** couper net toute animation de chiffre quand l'échelle d'animation du système vaut 0.
- **Do** laisser au contenu la place de la pilule (96 dp) et du bouton de saisie (72 dp) en bas.

### Don't:
- **Don't** introduire une couleur d'accent, un dégradé ou une carte colorée en tête ; le seul rouge est celui des montants négatifs.
- **Don't** utiliser les couleurs dynamiques Material You ni teinter une surface par l'élévation.
- **Don't** poser une ombre ailleurs que sous la pilule de navigation, ni donner une élévation au bouton de saisie.
- **Don't** remplacer la pilule flottante par une barre de navigation Material pleine largeur, ni ajouter des libellés aux onglets inactifs, ni revenir aux icônes Material pleines ou contour.
- **Don't** décorer une ligne par une tuile d'icône ; un type de compte s'identifie par son libellé et, dans la répartition, par un point gris.
- **Don't** afficher un chiffre inventé dans un état vide ; montrer l'afficheur « – – –,– – € ».
