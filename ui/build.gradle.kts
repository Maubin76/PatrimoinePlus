// Interface commune aux applis Android et PC : thème, composants graphiques
// (montants, courbes, barres, marques), icônes et logo. Compose
// Multiplatform, compilé pour Android et pour la JVM du PC ; s'appuie sur
// les calculs et formats du module :core.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.android.kmp.library)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.compose.multiplatform)
}

kotlin {
	android {
		namespace  = "fr.maubin.patrimoineplus.ui"
		compileSdk = 37
		minSdk     = 30
		androidResources {
			enable = true															// ressources Compose (police) empaquetées dans l'APK
		}
		compilerOptions {
			jvmTarget.set(JvmTarget.JVM_17)
		}
	}
	jvm("desktop") {
		compilerOptions {
			jvmTarget.set(JvmTarget.JVM_17)
		}
	}

	sourceSets {
		commonMain.dependencies {
			api(project(":core"))
			api(compose.runtime)
			api(compose.foundation)
			api(compose.ui)
			api(libs.compose.desktop.material3)
			implementation(compose.components.resources)
		}
	}
}

compose.resources {
	publicResClass = true
	packageOfResClass = "fr.maubin.patrimoineplus.ui.resources"
}
