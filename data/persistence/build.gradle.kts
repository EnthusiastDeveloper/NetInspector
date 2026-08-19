plugins {
    alias(libs.plugins.netinspector.android.library)
    alias(libs.plugins.netinspector.android.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.protobuf)
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

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") { option("lite") }
                create("kotlin") { option("lite") }
            }
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.core)
    implementation(libs.protobuf.kotlin.lite)
}
