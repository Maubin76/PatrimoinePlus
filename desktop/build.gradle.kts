// Appli PC (Windows) : Compose Multiplatform pour la JVM. Lit et, plus tard,
// modifie les mêmes données que l'appli Android, par le fichier d'échange du
// dossier synchronisé ; mode démonstration sans synchronisation.
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.compose.multiplatform)
}

java {
	sourceCompatibility = JavaVersion.VERSION_17
	targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
	compilerOptions {
		jvmTarget.set(JvmTarget.JVM_17)
	}
}

dependencies {
	implementation(project(":core"))
	implementation(project(":ui"))
	implementation(compose.desktop.currentOs)
	implementation(libs.compose.desktop.material3)
	implementation(libs.kotlinx.coroutines.swing)

	testImplementation(testFixtures(project(":core")))
	testImplementation(libs.junit)
	testImplementation(libs.kotlinx.coroutines.test)
	testImplementation(libs.compose.desktop.ui.test.junit4)
}

compose.desktop {
	application {
		mainClass = "fr.maubin.patrimoineplus.desktop.MainKt"

		nativeDistributions {
			targetFormats(TargetFormat.Msi)
			packageName    = "PatrimoinePlus"
			packageVersion = "1.0.0"
			description    = "Suivi du patrimoine et des dépenses"
			vendor         = "Mathieu Aubin"
			// Modules de la JVM embarquée : suggestRuntimeModules, plus java.xml (lecture des classeurs)
			// et jdk.localedata (mois et jours en français ; sans lui : « 12 Oct 2026 »).
			modules("java.instrument", "jdk.unsupported", "java.xml", "jdk.localedata")

			windows {
				iconFile.set(project.file("icons/patrimoineplus.ico"))
				menuGroup = "PatrimoinePlus"
			}
		}
	}
}

// Dossier portable zippé : PatrimoinePlus.exe et sa JVM, à dézipper n'importe où, sans
// installation ni droits administrateur (pas de WiX nécessaire, contrairement au .msi).
tasks.register<Zip>("packageZip") {
	group       = "distribution"
	description = "Construit le dossier portable de l'appli PC et le zippe."
	dependsOn("createDistributable")
	from(layout.buildDirectory.dir("compose/binaries/main/app"))
	archiveFileName.set("PatrimoinePlus-1.0.0-windows.zip")
	destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}
