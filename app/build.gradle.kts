import java.util.Properties

// Module application : Kotlin (intégré à AGP 9), Compose, Hilt, KSP.
plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.ksp)
	alias(libs.plugins.hilt)
	alias(libs.plugins.room)
	alias(libs.plugins.roborazzi)
}

// Clé de signature des versions publiées : lue dans le gradle.properties de
// l'utilisateur ou dans local.properties (jamais versionnés). Absente (CI,
// autre poste) : la version release est signée avec la clé de debug.
val kLocal      = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use { load(it) } }	// local.properties
val kSigning    = { inName: String -> (findProperty("patrimoineplus.$inName") ?: kLocal.getProperty("patrimoineplus.$inName"))?.toString() }	// réglage de signature
val kKeystore   = kSigning("keystore")?.let { file(it) }?.takeIf { it.exists() }		// fichier de la clé

android {
	namespace  = "fr.maubin.patrimoineplus"
	compileSdk = 37

	defaultConfig {
		applicationId = "fr.maubin.patrimoineplus"
		minSdk        = 30
		targetSdk     = 35
		versionCode   = property("patrimoineplus.versionCode").toString().toInt()
		versionName   = property("patrimoineplus.version").toString()

		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}

	signingConfigs {
		if (kKeystore != null)
		{
			create("publication") {
				storeFile     = kKeystore
				storePassword = kSigning("keyPassword")
				keyAlias      = kSigning("keyAlias")
				keyPassword   = kSigning("keyPassword")
			}
		}
	}

	buildTypes {
		// Version optimisée (R8), signée avec la clé de publication : les mises
		// à jour s'installent par-dessus sans perdre les données. Sans clé,
		// signée avec la clé de debug pour l'installer quand même.
		release {
			isMinifyEnabled   = true
			isShrinkResources = true
			signingConfig     = signingConfigs.findByName("publication") ?: signingConfigs.getByName("debug")
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	buildFeatures {
		compose     = true
		buildConfig = true
	}

	// Ressources Android disponibles dans les tests JVM (captures Robolectric).
	testOptions {
		unitTests.isIncludeAndroidResources = true
	}
}

dependencies {
	implementation(project(":core"))
	implementation(project(":ui"))
	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.core.splashscreen)
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.lifecycle.runtime.compose)
	implementation(libs.androidx.lifecycle.viewmodel.compose)
	implementation(libs.androidx.navigation.compose)
	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.compose.ui)
	implementation(libs.androidx.compose.ui.graphics)
	implementation(libs.androidx.compose.ui.tooling.preview)
	implementation(libs.androidx.compose.material3)
	implementation(libs.androidx.compose.material.icons)
	implementation(libs.hilt.android)
	implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
	implementation(libs.kotlinx.coroutines.android)
	implementation(libs.kotlinx.serialization.json)
	implementation(libs.androidx.datastore.preferences)
	ksp(libs.hilt.compiler)
	implementation(libs.androidx.room.runtime)
	implementation(libs.androidx.biometric)
	implementation(libs.androidx.fragment.ktx)
	implementation(libs.androidx.lifecycle.process)
	implementation(libs.androidx.work.runtime)
	implementation(libs.zxing.core)													// lecture du QR code d'appairage du PC
	implementation(libs.androidx.camera.camera2)									// appareil photo (scan du QR code)
	implementation(libs.androidx.camera.lifecycle)
	implementation(libs.androidx.camera.view)
	implementation(libs.androidx.room.ktx)
	ksp(libs.androidx.room.compiler)
	debugImplementation(libs.androidx.compose.ui.tooling)

	testImplementation(testFixtures(project(":core")))
	testImplementation(libs.junit)
	testImplementation(libs.kotlinx.coroutines.test)
	testImplementation(libs.robolectric)
	testImplementation(libs.androidx.room.testing)
	testImplementation(libs.roborazzi)
	testImplementation(libs.roborazzi.compose)
	testImplementation(libs.androidx.junit)
	testImplementation(platform(libs.androidx.compose.bom))
	testImplementation(libs.androidx.compose.ui.test.junit4)
	debugImplementation(libs.androidx.compose.ui.test.manifest)
	androidTestImplementation(libs.androidx.junit)
	androidTestImplementation(libs.androidx.espresso.core)
}

// Schéma Room exporté et versionné : toute évolution passe par une migration.
room {
	schemaDirectory("$projectDir/schemas")
}
