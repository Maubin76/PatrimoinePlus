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
	implementation(libs.jna.platform)												// barre de titre aux couleurs du thème (Windows 11, DWM)

	testImplementation(testFixtures(project(":core")))
	testImplementation(libs.junit)
	testImplementation(libs.kotlinx.coroutines.test)
	testImplementation(libs.compose.desktop.ui.test.junit4)
}

// Version commune aux deux applis ; le paquet Windows veut trois nombres (1.0 → 1.0.0).
val kVersion = property("patrimoineplus.version").toString()						// version affichée
val kPackage = (kVersion.split('.') + listOf("0", "0", "0")).take(3).joinToString(".")	// version du paquet

// Version lue par l'appli au lancement (mises à jour) : ressource version.properties générée.
val generateVersion = tasks.register("generateVersion") {
	val vOut = layout.buildDirectory.dir("generated/version")						// dossier généré
	inputs.property("version", kVersion)
	outputs.dir(vOut)
	doLast {
		vOut.get().file("version.properties").asFile.apply { parentFile.mkdirs(); writeText("version=$kVersion\n") }
	}
}
sourceSets.main { resources.srcDir(generateVersion) }

compose.desktop {
	application {
		mainClass = "fr.maubin.patrimoineplus.desktop.MainKt"

		nativeDistributions {
			targetFormats(TargetFormat.Msi)
			packageName    = "PatrimoinePlus"
			packageVersion = kPackage
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
	archiveFileName.set("PatrimoinePlus-$kVersion-windows.zip")
	destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}

// Tests sans écran, comme sur la CI (Linux) : une fenêtre AWT y serait refusée ;
// les visites guidées ne s'ouvrent pas d'elles-mêmes (elles bloqueraient les clics
// des tests) ; le test des visites les réactive pour lui.
tasks.withType<Test>().configureEach {
	systemProperty("java.awt.headless", "true")
	systemProperty("patrimoineplus.tours", "off")
}
