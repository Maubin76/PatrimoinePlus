// Fichier de build racine : déclare les plugins communs, appliqués dans les modules.
plugins {
	alias(libs.plugins.android.application) apply false
	alias(libs.plugins.kotlin.jvm) apply false
	alias(libs.plugins.kotlin.multiplatform) apply false
	alias(libs.plugins.android.kmp.library) apply false
	alias(libs.plugins.compose.multiplatform) apply false
	alias(libs.plugins.kotlin.compose) apply false
	alias(libs.plugins.kotlin.serialization) apply false
	alias(libs.plugins.ksp) apply false
	alias(libs.plugins.hilt) apply false
	alias(libs.plugins.room) apply false
	alias(libs.plugins.roborazzi) apply false
}
