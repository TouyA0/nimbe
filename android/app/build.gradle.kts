plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Version de Nimbe, donnée par le tag en CI (-Pnimbe.version=1.2.0 ou 1.2.0-beta.1).
val nimbeVersion = providers.gradleProperty("nimbe.version").getOrElse("0.0.0")

/**
 * versionCode toujours croissant, bêtas comprises : 1.2.3-beta.4 donne 1020304 et 1.2.3 donne 1020399.
 * Une bêta passe donc après la version précédente et avant la stable du même numéro.
 */
fun versionCodeOf(version: String): Int {
    val match = Regex("""(\d+)\.(\d+)\.(\d+)(?:-beta\.(\d+))?""").matchEntire(version)
        ?: error("Version « $version » invalide : attendu 1.2.3 ou 1.2.3-beta.4")
    val (major, minor, patch, beta) = match.destructured
    require(minor.toInt() < 100 && patch.toInt() < 100) { "Version « $version » : mineure et correctif sous 100" }
    val betaPart = if (beta.isEmpty()) 99 else beta.toInt().also { require(it in 1..98) { "Bêta entre 1 et 98" } }
    return ((major.toInt() * 100 + minor.toInt()) * 100 + patch.toInt()) * 100 + betaPart
}

android {
    namespace = "io.github.touya0.nimbe"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.touya0.nimbe"
        minSdk = 28
        targetSdk = 37
        versionCode = versionCodeOf(nimbeVersion)
        versionName = nimbeVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    // Clé des versions publiées : elle n'existe qu'en CI, dans l'environnement GitHub « release ».
    // Sans elle (en local), l'APK release sort non signé et ne peut pas remplacer une vraie version.
    val keystore = providers.environmentVariable("NIMBE_KEYSTORE").orNull
    if (keystore != null) {
        signingConfigs.create("release") {
            storeFile = file(keystore)
            storeType = "pkcs12"
            storePassword = providers.environmentVariable("NIMBE_KEYSTORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("NIMBE_KEY_ALIAS").get()
            // PKCS12 : la clé a le même mot de passe que le fichier.
            keyPassword = storePassword
        }
    }

    buildTypes {
        // La version de développement s'installe à côté de la version publiée, sans la remplacer.
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

// Schémas de la base versionnés dans le dépôt : ils servent à écrire et tester les migrations.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.work.runtime)
    // Affiches et fonds Jellyfin : cache mémoire et disque, images à la taille demandée.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    testImplementation(libs.junit)
    // org.json est fourni par Android ; pour les tests sur le PC, on prend la vraie bibliothèque.
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}