plugins {
    alias(libs.plugins.netinspector.jvm.library)
}

// Dispatchers, Result types, IP/subnet math, hex/checksum utils (design §2.1) - no
// android.* imports, fully unit-testable on the JVM.

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}
