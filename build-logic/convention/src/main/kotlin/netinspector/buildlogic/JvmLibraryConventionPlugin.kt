package netinspector.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * For `:core:model` and `:core:common` - pure Kotlin, no Android SDK dependency, fully
 * unit-testable on the JVM (design §2.1, §12).
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")

            extensions.configure<KotlinJvmProjectExtension> {
                jvmToolchain(17)
            }

            dependencies {
                add("testImplementation", libs.findLibrary("junit4").get())
                add("testImplementation", libs.findLibrary("truth").get())
                add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
            }

            enforceModuleBoundaries()
            configureStaticAnalysis()
        }
    }
}
