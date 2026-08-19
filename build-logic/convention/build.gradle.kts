plugins {
    `kotlin-dsl`
}

group = "dev.enthusiastdev.netinspector.buildlogic"

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.compose.compiler.gradlePlugin)
    implementation(libs.ksp.gradlePlugin)
    implementation(libs.hilt.gradlePlugin)
    implementation(libs.room.gradlePlugin)
    implementation(libs.detekt.gradlePlugin)
    implementation(libs.ktlint.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "netinspector.android.application"
            implementationClass = "netinspector.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "netinspector.android.library"
            implementationClass = "netinspector.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "netinspector.android.compose"
            implementationClass = "netinspector.buildlogic.AndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "netinspector.android.hilt"
            implementationClass = "netinspector.buildlogic.AndroidHiltConventionPlugin"
        }
        register("jvmLibrary") {
            id = "netinspector.jvm.library"
            implementationClass = "netinspector.buildlogic.JvmLibraryConventionPlugin"
        }
    }
}
