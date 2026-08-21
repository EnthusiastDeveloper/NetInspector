plugins {
    alias(libs.plugins.netinspector.android.library)
    alias(libs.plugins.netinspector.android.hilt)
}

android {
    namespace = "dev.enthusiastdev.netinspector.data.wifi"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.mockk)
}
