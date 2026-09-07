plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.gdo.pagecurl"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    testImplementation("junit:junit:4.13.2")
}

tasks.register("verifyPortableModule") {
    group = "verification"
    description = "Fails when the reusable module contains demo-only references or assets."

    doLast {
        val sourceRoot = layout.projectDirectory.dir("src/main").asFile
        val forbidden = listOf(
            "com.gdo.pagecurldemo",
            "page_1.jpg",
            "page_2.jpg",
            "page_3.jpg",
        )
        val textViolations = sourceRoot.walkTopDown()
            .filter(File::isFile)
            .flatMap { file ->
                val text = file.readText()
                forbidden.filter { token -> token in text }.map { token -> "$token in ${file.relativeTo(projectDir)}" }
            }
            .toList()
        val assetRoot = sourceRoot.resolve("assets")
        val assetViolations = assetRoot.takeIf(File::exists)
            ?.walkTopDown()
            ?.filter(File::isFile)
            ?.map { it.relativeTo(projectDir).path }
            ?.toList()
            .orEmpty()

        check(textViolations.isEmpty() && assetViolations.isEmpty()) {
            "Portable module boundary violations:\n" + (textViolations + assetViolations).joinToString("\n")
        }
    }
}
