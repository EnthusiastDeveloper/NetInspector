plugins {
    alias(libs.plugins.netinspector.android.library)
    alias(libs.plugins.netinspector.android.compose)
}

android {
    namespace = "dev.enthusiastdev.netinspector.core.designsystem"
}

dependencies {
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.window)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)
}
