package netinspector.buildlogic

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.register

/**
 * ideas.md #32 - a benchmark suite for the LAN sweep pipeline's pure logic
 * (parsing, scoring, precedence, and the timing-sensitive scheduling of parallel host
 * discovery). Benchmarks live as ordinary classes in `src/test`, named `*Benchmark.kt`, so
 * they share a module's existing test classpath and doubles (mockk, fakes) rather than
 * needing a parallel dependency set - but they measure wall-clock time rather than assert
 * behaviour, and a wall-clock number is meaningless if the JVM that produced it also spent
 * time on unrelated `@Test` assertions, so they must never run as part of [baseTestTaskName]
 * (the build gate stays fast and deterministic - see docs/adr for why the benchmark job in
 * CI is wired in separately, and non-blocking).
 *
 * Splits `*Benchmark.class` out of [baseTestTaskName] into a new `benchmark` task that reuses
 * the same compiled test classes and classpath. A module with no `*Benchmark.kt` files still
 * gets a `benchmark` task; it just runs zero tests.
 *
 * [otherTestTaskNamesToExclude] covers every other `Test` task that would otherwise also pick
 * up `*Benchmark.class` from the same source set - an Android library module's release build
 * type variant (`testReleaseUnitTest`) compiles and runs the identical test classes as its
 * debug variant, so excluding only `testDebugUnitTest` still leaves the release variant
 * running benchmarks as part of the root `test` aggregate task (verified by an initial version
 * of this suite: `test` ran `HostSweeperBenchmark` under `testReleaseUnitTest` even though
 * `testDebugUnitTest` correctly skipped it).
 */
internal fun Project.configureBenchmarks(
    baseTestTaskName: String,
    otherTestTaskNamesToExclude: List<String> = emptyList(),
) {
    val projectPath = path
    afterEvaluate {
        val baseTask = tasks.getByName<Test>(baseTestTaskName)
        baseTask.exclude("**/*Benchmark.class")
        otherTestTaskNamesToExclude.forEach { taskName ->
            tasks.getByName<Test>(taskName).exclude("**/*Benchmark.class")
        }

        val resultsFile = layout.buildDirectory.file("benchmark-results.csv").get().asFile

        tasks.register<Test>("benchmark") {
            group = "verification"
            description = "Runs *Benchmark.kt scenarios (excluded from '$baseTestTaskName')."
            testClassesDirs = baseTask.testClassesDirs
            classpath = baseTask.classpath
            include("**/*Benchmark.class")
            // Wall-clock results are never reproducible byte-for-byte, so this must always
            // re-run rather than being skipped as UP-TO-DATE.
            outputs.upToDateWhen { false }
            // The harness (Benchmark.kt in src/test) only ever appends a row per call - clear
            // the file first, or re-running this task piles up duplicate rows from every past
            // run instead of reporting just the latest one.
            doFirst { resultsFile.delete() }
            systemProperty("netinspector.benchmark.resultsFile", resultsFile.absolutePath)
            systemProperty("netinspector.benchmark.module", projectPath)
            testLogging {
                showStandardStreams = true
            }
        }
    }
}
