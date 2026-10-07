---
name: PatrimoinePlus
description: Patrimoine et dépenses personnels, présentés comme le résultat d'une addition sur une calculatrice Braun ET66.
colors:
  aluminium: "#EEECE7"
  aluminium-lowest: "#F7F6F2"
  aluminium-low: "#E8E6E0"
  aluminium-mid: "#E3E0DA"
  aluminium-high: "#DCD9D2"
  aluminium-highest: "#D5D2CA"
  anthracite: "#2A2724"
  warm-grey: "#5E5A54"
  rule-light: "#CFCBC3"
  outline-light: "#8C877F"
  equals-yellow: "#E2A72E"
  olive: "#5F7F4A"
  ochre: "#A9742A"
  ochre-container: "#F1DDB8"
  error-red: "#B0392B"
  error-container: "#F5D9D3"
  error-ink: "#5C1A12"
  body: "#1C1A18"
  body-lowest: "#171513"
  body-low: "#22201E"
  body-mid: "#282624"
  body-high: "#2E2B29"
  body-highest: "#363330"
  key-light: "#ECE8E0"
  key-grey: "#B5AFA6"
  rule-dark: "#45413D"
  outline-dark: "#8A847B"
  olive-light: "#9DB884"
  ochre-light: "#D9A85A"
  ochre-container-dark: "#4A3816"
  error-salmon: "#F0A597"
typography:
  display-large:
    fontFamily: "Hanken Grotesk, sans-serif"
    fontSize: "57sp"
    fontWeight: 500
    lineHeight: "64sp"
    letterSpacing: "-0.03em"
  display-medium:
    fontFamily: "Hanken Grotesk, sans-serif"
    fontSize: "45sp"
    fontWeight: 500
    lineHeight: "52sp"
    letterSpacing: "-0.025em"
  display-small:
    fontFamily: "Hanken Grotesk, sans-serif"
    fontSize: "36sp"
    fontWeight: 500
    lineHeight: "44sp"
    letterSpacing: "-0.02em"
  headline-large:
    fontFamily: "Hanken Grotesk, sans-serif"
    fontSize: "32sp"
    fontWeight: 500
    lineHeight: "40sp"
    letterSpacing: "-0.015em"
  headline-medium:
    fontFamily: "Hanken Grotesk, sans-serif"
    fontSize: "28sp"
    fontWeight: 500
    lineHeight: "36sp"
    letterSpacing: "-0.01em"
  title-large:
    fontFamily: "Hanken Grotesk, sans-serif"
    fontSize: "22sp"
    fontWeight: 500
    lineHeight: "28sp"
    letterSpacing: "0"
  title-medium:
    fontFamily: "Hanken Grotesk, sans-serif"
    fontSize: "16sp"
    fontWeight: 500
    lineHeight: "24sp"
    letterSpacing: "0.15sp"
  title-small:
    fontFamily: "Hanken Grotesk, sans-serif"
    fontSize: "14sp"
    fontWeight: 500
    lineHeight: "20sp"
    letterSpacing: "0.1sp"
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
    letterSpacing: "0.1sp"
rounded:
  full: "9999dp"
  bar: "4dp"
spacing:
  hairline: "1dp"
  segment-gap: "2dp"
  stack: "6dp"
  row-gap: "8dp"
  marker-gap: "14dp"
  gutter: "16dp"
  section: "24dp"
  fab-clearance: "96dp"
components:
  entry-fab:
    backgroundColor: "{colors.equals-yellow}"
    textColor: "{colors.anthracite}"
    rounded: "{rounded.full}"
    size: "56dp"
  equals-key:
    backgroundColor: "{colors.equals-yellow}"
    textColor: "{colors.anthracite}"
    rounded: "{rounded.full}"
    size: "36dp"
  series-dot:
    rounded: "{rounded.full}"
    size: "10dp"
  proportion-bar:
    backgroundColor: "{colors.aluminium-highest}"
    rounded: "{rounded.bar}"
    height: "8dp"
    width: "100%"
  total-display:
    textColor: "{colors.anthracite}"
    typography: "{typography.display-large}"
  account-row:
    textColor: "{colors.anthracite}"
    typography: "{typography.title-medium}"
    height: "52dp"
  subtotal-row:
    textColor: "{colors.warm-grey}"
    typography: "{typography.label-large}"
    height: "32dp"
    padding: "0 0 0 24dp"
  button-outlined:
    textColor: "{colors.anthracite}"
    typography: "{typography.label-large}"
    rounded: "{rounded.full}"
    height: "40dp"
  top-bar:
    backgroundColor: "{colors.aluminium}"
    textColor: "{colors.anthracite}"
    typography: "{typography.title-large}"
    height: "64dp"
  nav-bar:
    backgroundColor: "{colors.aluminium}"
    textColor: "{colors.warm-grey}"
    height: "80dp"
  nav-item-active:
    backgroundColor: "{colors.aluminium-highest}"
    textColor: "{colors.anthracite}"
    rounded: "{rounded.full}"
  settings-section-title:
    textColor: "{colors.warm-grey}"
    typography: "{typography.title-small}"
    padding: "20dp 16dp 4dp 16dp"
---

# Design System: PatrimoinePlus

## Overview

**Creative North Star: "La Calculatrice Braun"**

Le patrimoine est le résultat d'une addition, et l'interface le montre comme une calculatrice Braun ET66 dessinée par Dieter Rams : un boîtier aluminium gris chaud (ou brun-noir en sombre), des chiffres anthracite à chasse fixe, et une seule touche jaune, la touche « = », qui précède le résultat et porte l'action. Les comptes s'alignent comme les opérandes d'une addition (point de série, nom, montant calé à droite), un filet de 1 dp les sépare du total, et le total roule chiffre par chiffre comme un afficheur quand sa valeur change.

Le système est plat, dense sans être serré, et sobre en graisses : la hiérarchie vient de la taille seule. Il n'y a ni carte, ni tuile teintée, ni ombre ; les surfaces se séparent par des filets. La palette est fixe : Material You est volontairement écarté pour que le jaune de la touche égale reste identique quel que soit le fond d'écran. Le mode sombre suit le système et reprend exactement les mêmes rôles.

Rejet confirmé dans le contrat de direction : l'arrangement par défaut des applis bancaires (carte colorée en tête, tuiles de comptes ombrées, icônes de tirelire).

**Key Characteristics:**
- Palette fixe aluminium / anthracite, un seul jaune « touche égale » réservé à l'action et au résultat.
- Une grotesque unique, Hanken Grotesk, en 400 et 500, chiffres tabulaires natifs.
- Formes rondes pour les touches et les repères (FAB, touche « = », points de série) ; filets de 1 dp au lieu d'ombres.
- Le total s'affiche à la plus grande taille d'afficheur qui tient sur une ligne.
- Mouvement d'afficheur : les chiffres roulent ; coupure nette quand les animations système sont désactivées.

## Colors

Un boîtier d'aluminium gris chaud et d'encre anthracite, un jaune unique de touche égale, et deux couleurs de série tirées de la terre (olive, ocre). Chaque valeur claire a son équivalent sombre ; les rôles Material 3 sont identiques dans les deux palettes.

### Primary
- **Jaune touche égale** (equals-yellow) : rôle `primaryContainer`, identique en clair et en sombre. Fond du bouton de saisie (FAB) et de la touche « = » qui précède le total, et rien d'autre. Ses traits et son « + » sont en anthracite (clair) ou corps brun-noir (sombre).
- **Anthracite** (anthracite) / **Gris clair chaud** (key-light, sombre) : rôle `primary` et `onSurface`. Encre principale : titres, montants, libellés actifs de la barre de navigation. Sert aussi de couleur de série pour les comptes courants.

### Secondary
- **Olive** (olive) / **Olive clair** (olive-light, sombre) : rôle `secondary`. Série de données des livrets : point de série et segment de la barre de répartition.

### Tertiary
- **Ocre** (ochre) / **Ocre clair** (ochre-light, sombre) : rôle `tertiary`. Série de données du compte-titres. Les conteneurs ocre (ochre-container, ochre-container-dark) sont déclarés dans le schéma mais pas encore employés à l'écran.

### Neutral
- **Aluminium** (aluminium) / **Corps brun-noir** (body, sombre) : fond de l'écran, de la barre du haut et de la barre de navigation ; tout est posé sur ce même fond.
- **Échelle des conteneurs** (aluminium-lowest à aluminium-highest ; body-lowest à body-highest) : rôles `surfaceContainer*`. En pratique, le plus haut sert de rail à la barre de répartition, d'indicateur de l'onglet actif (`secondaryContainer`) ; les autres restent disponibles pour les feuilles et menus Material.
- **Gris tiède** (warm-grey) / **Gris clé** (key-grey, sombre) : rôle `onSurfaceVariant`, texte secondaire (6:1 sur l'aluminium) : date de calcul, sous-totaux, titres de groupes de réglages, afficheur remis à zéro, réglages à venir.
- **Filet clair** (rule-light) / **Filet sombre** (rule-dark) : rôle `outlineVariant`, tous les filets de 1 dp.
- **Contour** (outline-light / outline-dark) : rôle `outline`, contours de champs et du bouton contour.
- **Rouge erreur** (error-red / error-salmon, sombre) : rôle `error`. Montants négatifs d'un compte et erreurs ; conteneurs d'erreur (error-container, error-ink) réservés aux messages d'erreur.

### Named Rules
**The One Yellow Rule.** Le jaune touche égale n'apparaît que sur la touche d'action (FAB de saisie) et sur la touche « = » du résultat. Jamais sur un lien, une puce, un surlignage, un graphique ou un état sélectionné.

**The Fixed Palette Rule.** Pas de couleurs dynamiques Material You ; `surfaceTint` est transparent pour qu'aucune élévation ne teinte une surface.

**The Series Rule.** Anthracite, olive et ocre identifient les types de compte (courant, livrets, compte-titres) et n'existent qu'en point de série et en segment de barre ; ce ne sont pas des couleurs décoratives.

## Typography

**Display Font:** Hanken Grotesk (repli sans-serif), police variable embarquée (`res/font/hanken_grotesk.ttf`, licence OFL)
**Body Font:** Hanken Grotesk
**Label/Mono Font:** Hanken Grotesk ; aucune police mono.

**Character:** une grotesque unique, neutre et légèrement géométrique, comme la sérigraphie d'un appareil Braun. Ses chiffres ont tous la même chasse par défaut, ce qui aligne les colonnes de montants sans réglage.

### Hierarchy
- **Display** (500, 57 / 45 / 36 sp, interlettrage -0,03 à -0,02 em) : réservé au total, dans l'afficheur. La taille est choisie à l'exécution : la plus grande de displayLarge, displayMedium, displaySmall, headlineLarge, headlineMedium qui tient sur une ligne, en tenant compte de l'échelle de police du système.
- **Headline** (500, 32 / 28 sp, interlettrage -0,015 / -0,01 em) : replis de l'afficheur du total.
- **Title** (500, 22 / 16 / 14 sp) : titleLarge pour le titre de la barre du haut ; titleMedium pour le nom et le montant d'un compte et le titre d'un état vide ; titleSmall pour les titres de groupes de réglages.
- **Body** (400, 16 / 14 sp, interlettrage 0) : bodyMedium pour la date de calcul et les explications d'état vide ; bodyLarge dans les listes de réglages.
- **Label** (500, 14 sp) : labelLarge pour les sous-totaux de groupe, les boutons et la barre de navigation (labelMedium).

### Named Rules
**The Scale-Only Rule.** La hiérarchie vient de la taille. Les graisses se limitent à 400 (corps) et 500 (tout le reste) ; la graisse 600 est chargée mais n'a aucun rôle.

**The Tabular Digits Rule.** Tous les montants restent en Hanken Grotesk, dont les dix chiffres ont la même chasse : les montants s'alignent à droite et un chiffre qui roule ne décale pas ses voisins. Ne pas introduire une police à chiffres proportionnels pour un montant.

## Layout

Une colonne unique, pleine largeur, à marges latérales de 16 dp (gutter) sur tous les écrans principaux. Le contenu commence 8 dp sous la barre du haut. L'accueil se lit de haut en bas comme une addition posée : bloc du total (touche « = » de 36 dp, 14 dp d'écart, montant ; puis la date à 6 dp ; puis 14 dp et la barre de répartition pleine largeur), un filet avec 24 dp au-dessus et 12 dp en dessous, puis les lignes de comptes.

Une ligne de compte mesure au moins 52 dp : point de série de 10 dp, 14 dp, nom (deux lignes au plus, coupé par ellipse), 16 dp minimum, montant calé à droite. Les groupes de comptes sont séparés par 8 dp. Un groupe de plusieurs comptes se termine par un sous-total en retrait de 24 dp, de 32 dp de haut minimum ; un groupe d'un seul compte n'en a pas. La liste réserve 96 dp en bas pour que le FAB ne masque jamais le dernier montant.

Les écrans de réglages utilisent des éléments de liste Material posés sur le fond, groupés sous un titre (20 dp au-dessus, 4 dp en dessous) et séparés par un filet en retrait de 16 dp de chaque côté.

## Elevation & Depth

Le système est entièrement plat. Aucune ombre : le FAB a une élévation nulle dans tous ses états, la barre de navigation a une élévation tonale nulle, et `surfaceTint` est transparent. La profondeur se lit par des filets de 1 dp en `outlineVariant` : un filet permanent au-dessus de la barre de navigation, un filet sous la barre du haut qui n'apparaît que lorsque le contenu défile dessous, un filet entre les opérandes et le résultat.

### Named Rules
**The Rule-Not-Shadow Rule.** Toute séparation est un filet de 1 dp ou un espace ; jamais une ombre, une carte ou une teinte de conteneur.

## Shapes

Deux familles de formes. Les touches et repères sont des cercles parfaits : le FAB de saisie, la touche « = » (rond jaune portant deux traits à bouts ronds), les points de série de 10 dp. Les barres sont des rubans à bouts arrondis : la barre de répartition de 8 dp de haut, dont chaque segment a des extrémités arrondies (4 dp) et 2 dp d'écart avec le suivant, sur un rail de la couleur du conteneur le plus haut. Les composants Material conservés (bouton contour, interrupteur, indicateur de l'onglet actif) gardent leur forme pilule d'origine. Tout le reste est rectiligne : filets, lignes, bords d'écran.

### Named Rules
**The Round Key Rule.** Ce qui se presse comme une touche ou marque une série est un cercle ; aucune forme carrée ou à coins arrondis pour ces rôles.

## Components

### Buttons
- **Bouton de saisie (FAB) :** touche ronde jaune, signe « + » arrondi en anthracite, élévation nulle au repos, au survol, au focus et à l'appui. Présent sur Accueil et Comptes seulement, en bas à droite au-dessus de la barre de navigation.
- **Bouton contour :** bouton Material OutlinedButton, pilule, contour `outline`, texte labelLarge en encre principale. Seule action d'un état vide (« Créer mes comptes »).
- **Action « œil » :** bouton icône de la barre du haut ; l'icône passe de l'œil ouvert à l'œil barré par un fondu enchaîné.

### Cards / Containers
- Aucune carte. Le contenu est posé directement sur le fond.

### Inputs / Fields
- **Interrupteur :** Switch Material 3 aux couleurs du schéma (piste en encre principale, pouce sur le fond quand il est actif). L'élément de liste entier est la cible tactile.

### Navigation
- **Barre du haut :** sur le fond de l'écran, titre en titleLarge à gauche, actions à droite. Un filet apparaît dessous quand le contenu défile ; sa couleur ne change jamais.
- **Barre de navigation :** quatre onglets (Accueil, Comptes, Catégories, Réglages), icônes Material « outlined », posée sur le fond et séparée du contenu par un filet. L'onglet actif porte un indicateur pilule en `secondaryContainer`, icône et libellé en encre principale ; les autres sont en gris tiède. Changement d'onglet : fondu sortant de 90 ms puis fondu entrant de 220 ms.

### Afficheur du total (signature)
La touche « = » de 36 dp, puis le montant à la plus grande taille d'afficheur qui tient sur une ligne, puis « Calculé au … » en bodyMedium gris tiède, puis la barre de répartition. Quand la valeur change, chaque caractère qui change roule verticalement (320 ms, vers le haut si le chiffre augmente) ; les positions se repèrent depuis la droite pour que les centimes restent en place. Si l'échelle d'animation du système vaut 0, le montant change d'un coup. Sans compte, l'afficheur est remis à zéro : « – – –,– – € » en gris tiède, jamais un chiffre inventé.

### Lignes de comptes
Opérandes de l'addition : point de série, nom en titleMedium, montant titleMedium calé à droite, en rouge erreur s'il est négatif. Le sous-total d'un groupe est en retrait, plus petit (labelLarge) et en gris tiède.

### Montants et mode discret
Tout montant passe par un composant unique qui le formate en euros français et le remplace par un masque en mode discret, avec un fondu enchaîné de 180 ms. En mode discret, la description lue par TalkBack ne cite aucun montant.

### État vide
Titre en titleMedium, explication en bodyMedium gris tiède à 6 dp, puis éventuellement un bouton contour 10 dp plus bas. Il dit ce qui manque, pourquoi, et comment y remédier.

## Do's and Don'ts

### Do:
- **Do** réserver le jaune touche égale (equals-yellow) au FAB de saisie et à la touche « = » du résultat.
- **Do** séparer les zones par un filet de 1 dp en `outlineVariant` ou par l'espace, et laisser chaque surface sur le fond de l'écran.
- **Do** caler les montants à droite en Hanken Grotesk et les faire passer par le composant de montant, pour le format français et le mode discret.
- **Do** dimensionner un chiffre clé à la plus grande taille de l'échelle qui tient sur une ligne, en respectant l'échelle de police du système.
- **Do** identifier un type de compte par un point de série de 10 dp et le même ton dans la barre de répartition (anthracite, olive, ocre).
- **Do** couper net toute animation de chiffre quand l'échelle d'animation du système vaut 0.

### Don't:
- **Don't** utiliser les couleurs dynamiques Material You ni teinter une surface par l'élévation.
- **Don't** ajouter d'ombre, de carte, de tuile teintée ou d'élévation au FAB.
- **Don't** mettre le jaune sur un lien, un état sélectionné, un graphique ou un surlignage.
- **Don't** créer de hiérarchie par la graisse : rien au-delà de 500.
- **Don't** afficher un chiffre inventé dans un état vide ; montrer l'afficheur remis à zéro (« – – –,– – € »).
- **Don't** employer olive ou ocre comme couleurs d'accent hors des séries de données.
