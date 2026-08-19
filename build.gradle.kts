// Intentionally minimal: every module applies build-logic convention plugins
// (netinspector.android.*, netinspector.jvm.library), which own plugin/version
// resolution via compileOnly coordinates in build-logic/convention/build.gradle.kts.
// See docs/references/dependency-versions.md for the version rationale.

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
