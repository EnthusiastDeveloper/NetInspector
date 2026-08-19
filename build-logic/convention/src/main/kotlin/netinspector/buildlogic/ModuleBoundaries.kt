package netinspector.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

/**
 * Enforces the module graph from design doc §2.1: `:data:*` modules may depend on
 * `:core:model` and `:core:common` but never on each other, and `:core:model` depends on
 * nothing. Checked per-project after evaluation so a bad `project(":data:...")` reference
 * fails the build with a specific message instead of silently compiling.
 */
internal fun Project.enforceModuleBoundaries() {
    afterEvaluate {
        configurations.forEach { configuration ->
            configuration.dependencies
                .filterIsInstance<ProjectDependency>()
                .forEach { dependency ->
                    val from = path
                    val to = dependency.path

                    if (from == ":core:model") {
                        throw GradleException(
                            "Module boundary violation: $from must depend on nothing " +
                                "(design doc §2.1), but declares a dependency on $to.",
                        )
                    }

                    if (from.startsWith(":data:") && to.startsWith(":data:") && to != from) {
                        throw GradleException(
                            "Module boundary violation: $from must not depend on $to. " +
                                ":data modules may depend on :core:model and :core:common " +
                                "only, never on each other (design doc §2.1).",
                        )
                    }
                }
        }
    }
}
