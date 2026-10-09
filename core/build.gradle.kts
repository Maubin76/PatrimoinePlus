// Module commun : modèle, calculs, règles de saisie, format de sauvegarde (classeur
// Excel, ancien JSON), données de démonstration, cours publics. Kotlin pur sur la JVM,
// sans Android ni interface : partagé par l'appli Android et l'appli PC.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.kotlin.serialization)
	`java-test-fixtures`
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
	api(libs.kotlinx.serialization.json)
	api(libs.kotlinx.coroutines.core)

	testImplementation(libs.junit)
	testImplementation(libs.kotlinx.coroutines.test)
}
