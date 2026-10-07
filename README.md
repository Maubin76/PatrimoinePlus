# PatrimoinePlus

Application Android personnelle de suivi du patrimoine et des dépenses :
comptes courants, livrets d'épargne, compte-titres (PEA). Données uniquement
en base locale sur le téléphone, sans synchronisation ni service distant.

La spécification complète (besoins, modèle de données, plan des étapes) est
dans [`doc/SPEC.md`](doc/SPEC.md) ; la couverture des tests dans
[`doc/TESTS.md`](doc/TESTS.md).

## Construire

```sh
./gradlew :app:assembleDebug        # APK : app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # tests JVM
```

## Socle technique

| Élément          | Choix                                                                    |
|------------------|--------------------------------------------------------------------------|
| Langage          | Kotlin (intégré à AGP 9), JVM 17                                         |
| Interface        | Jetpack Compose + Material 3, monochrome noir sur blanc, police Hanken Grotesk |
| Navigation       | `navigation-compose`, routes typées (`kotlinx.serialization`)             |
| Injection        | Hilt (KSP)                                                               |
| Asynchronisme    | Coroutines et Flow                                                       |
| Réglages         | DataStore Preferences (mode discret)                                     |
| Captures         | Roborazzi + Robolectric (rendu JVM, sans émulateur)                      |
| Base de données  | Room (à partir de l'étape 2)                                             |
| SDK              | minSdk 30, targetSdk 35, compileSdk 37                                   |
| Versions         | catalogue `gradle/libs.versions.toml`                                    |

`compileSdk` est à 37 parce que les bibliothèques AndroidX actuelles
(Compose 1.12, core-ktx 1.19) l'exigent ; cela ne change ni les appareils
compatibles (`minSdk`) ni le comportement à l'exécution (`targetSdk`).

## Architecture

Package racine : `fr.maubin.patrimoineplus`.

```
domain/   modèles, règles de saisie, calculs — Kotlin pur, sans Android, testé en JVM
data/     entités et DAO Room, dépôts (étape 2)
di/       modules Hilt (horloge, base, dépôts)
ui/       écrans Compose, ViewModels, thème, navigation, composants
data/settings/  réglages DataStore
```

Règles :

- aucun solde ni total stocké : tout est recalculé par `domain` à partir des
  opérations ;
- montants en centimes (`Long`), jamais en virgule flottante ;
- la date courante vient d'une `Clock` injectée, jamais de `LocalDate.now()`
  appelé directement dans la logique.

## Interface

Direction visuelle monochrome, noir sur blanc (blanc sur noir en mode sombre),
inspirée de deux maquettes de référence fournies le 2026-10-07.

- Palette fixe (`ui/theme/Color.kt`) : blanc, encre presque noire, gris
  secondaire, filets gris très clair ; aucun accent. Rouge discret réservé aux
  montants négatifs. Les couleurs dynamiques Material You sont écartées.
- Police unique Hanken Grotesk (OFL, `res/font`, licence dans
  `doc/licenses/`) ; titres et total en demi-gras ; chiffres à chasse fixe.
- Accueil : total centré en très grand, pastille « Calculé au … », section
  « Comptes » dans une carte bordée, section « Répartition » en niveaux de gris.
- Navigation : pilule flottante à icônes seules (`ui/shell/AppFrame.kt`),
  bouton rond noir de saisie au-dessus, sur Accueil et Comptes.
- Composants : `AmountText` (masqué en mode discret, chiffres qui roulent),
  `InfoPill`, `SectionHeader`, `BorderedCard`, `ProportionBar`, `EmptyState`,
  `ScreenScaffold`.
- Apparence : Système (suit le téléphone), Clair ou Sombre, au choix dans
  Réglages ; mémorisée en DataStore, les barres système suivent.
- Mode discret : l'œil de la barre du haut ou le réglage masque tous les
  montants ; mémorisé en DataStore.
- Contexte de conception : `PRODUCT.md`, `DESIGN.md` et `.impeccable/`.

Captures de relecture : `./gradlew :app:recordRoborazziDebug` écrit les PNG
dans `app/build/outputs/roborazzi/`.

## Modèle de données

Voir [`doc/SPEC.md` § 2](doc/SPEC.md#2-modèle-de-données). Implémenté à l'étape 2.

## Sauvegarde

La sauvegarde cloud Android est désactivée (`allowBackup=false`, et toutes les
données sont exclues de `cloud-backup` pour Android 12 et plus). Le transfert
direct entre téléphones reste possible. L'export et l'import JSON arrivent à
l'étape 7.

## Conventions de code

- Indentation par tabulations (largeur 4).
- Constantes préfixées `k`, variables locales `v`, paramètres `in`.
- Un commentaire par classe et par fonction ; commentaires et textes en français.
- Alignement des `=` et des commentaires de fin de ligne sur les déclarations
  consécutives.
- Commits en français, style Conventional Commits.

Conséquence connue : un paramètre de méthode redéfinie (par exemple
`onCreate(inSavedInstanceState)`) porte un autre nom que dans la classe
parente, ce qui produit une alerte du compilateur Kotlin sans effet.
