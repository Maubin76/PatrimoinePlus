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
		}
	}
}
