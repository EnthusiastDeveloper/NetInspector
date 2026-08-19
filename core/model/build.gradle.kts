plugins {
    alias(libs.plugins.netinspector.jvm.library)
}

// Depends on nothing (design §2.1). No android.* imports - fully unit-testable on the JVM.
