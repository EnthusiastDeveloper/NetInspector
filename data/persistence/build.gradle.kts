plugins {
    alias(libs.plugins.netinspector.android.library)
    alias(libs.plugins.netinspector.android.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "dev.enthusiastdev.netinspector.data.persistence"
}

room {
    // Schema JSON is committed for future migrations (design §10, Phase 0 task list). The
    // directory fills in once the first phase that needs persistence (OUI table, saved
    // hosts, scan history - see design §10) adds a real @Database.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
}
