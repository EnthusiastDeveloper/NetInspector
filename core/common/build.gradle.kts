plugins {
    alias(libs.plugins.netinspector.jvm.library)
}

// Dispatchers, Result types, IP/subnet math, hex/checksum utils (design §2.1) - no
// android.* imports, fully unit-testable on the JVM.

dependencies {
    // core:model's own rule ("depends on nothing") isn't symmetric - core:common leaning on
    // its plain data types (e.g. PingTier) is fine; only core:model itself must stay a leaf.
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
}
