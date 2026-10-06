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
| Interface        | Jetpack Compose + Material 3 (couleurs dynamiques), icônes Material      |
| Navigation       | `navigation-compose`, routes typées (`kotlinx.serialization`)             |
| Injection        | Hilt (KSP)                                                               |
| Asynchronisme    | Coroutines et Flow                                                       |
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
ui/       écrans Compose, ViewModels, thème, navigation
```

Règles :

- aucun solde ni total stocké : tout est recalculé par `domain` à partir des
  opérations ;
- montants en centimes (`Long`), jamais en virgule flottante ;
- la date courante vient d'une `Clock` injectée, jamais de `LocalDate.now()`
  appelé directement dans la logique.

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
