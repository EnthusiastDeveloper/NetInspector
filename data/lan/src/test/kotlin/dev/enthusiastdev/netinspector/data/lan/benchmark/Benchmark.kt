package dev.enthusiastdev.netinspector.data.lan.benchmark

import java.io.File

/**
 * improvement-ideas.md #32 - a minimal wall-clock benchmark harness for the LAN sweep
 * pipeline's pure logic. Deliberately hand-rolled rather than JMH or kotlinx-benchmark: see
 * docs/adr/0010-hand-rolled-benchmark-harness.md for why. This is intentionally duplicated
 * (not shared via a project dependency) in every module with a `*Benchmark.kt` file -
 * `:core:model` cannot depend on any other project at all (design §2.1), so a shared
 * `testFixtures` artifact isn't an option for the one module that most needs this, and three
 * near-identical ~50-line copies is simpler than the module-graph gymnastics a shared
 * dependency would need to reach all of them.
 *
 * Not statistically rigorous the way JMH is (no JVM forking, no dead-code-elimination
 * blackholing) - catching a pipeline regression via `benchmarks/baseline.csv` is this
 * suite's only job, and that bar doesn't need JMH's.
 */
object Benchmark {
    data class Result(
        val name: String,
        val iterations: Int,
        val minMs: Double,
        val medianMs: Double,
        val p95Ms: Double,
        val maxMs: Double,
    )

    /**
     * Warms up [warmupIterations] times (unmeasured, lets the JIT compile hot paths so the
     * measured runs reflect steady-state performance, not interpreter/compilation overhead),
     * then times [iterations] repetitions of [block]. Prints and records the result as one
     * CSV row via [recordResult].
     */
    fun run(
        name: String,
        warmupIterations: Int = 5,
        iterations: Int = 20,
        block: () -> Unit,
    ): Result {
        repeat(warmupIterations) { block() }

        val timingsMs =
            DoubleArray(iterations) {
                val startNanos = System.nanoTime()
                block()
                (System.nanoTime() - startNanos) / NANOS_PER_MILLI
            }
        timingsMs.sort()

        val result =
            Result(
                name = name,
                iterations = iterations,
                minMs = timingsMs.first(),
                medianMs = percentile(timingsMs, MEDIAN_PERCENTILE),
                p95Ms = percentile(timingsMs, P95_PERCENTILE),
                maxMs = timingsMs.last(),
            )
        println(
            "BENCHMARK ${result.name}: min=${result.minMs.format()}ms " +
                "median=${result.medianMs.format()}ms p95=${result.p95Ms.format()}ms " +
                "max=${result.maxMs.format()}ms (n=${result.iterations})",
        )
        recordResult(result)
        return result
    }

    private fun percentile(
        sortedMs: DoubleArray,
        fraction: Double,
    ): Double {
        val index = (fraction * (sortedMs.size - 1)).toInt().coerceIn(0, sortedMs.size - 1)
        return sortedMs[index]
    }

    private fun Double.format(): String = "%.4f".format(this)

    /** Fixed-point, never scientific notation - `benchmarks/compare-benchmarks.sh` parses
     * this column with `awk`, which reads scientific notation fine, but a human diffing
     * `benchmarks/baseline.csv` shouldn't have to. */
    private fun Double.toCsv(): String = "%.6f".format(this)

    /**
     * Appends one CSV row per call to the file named by the `netinspector.benchmark.resultsFile`
     * system property (set per-module by `build-logic`'s `configureBenchmarks`), defaulting to
     * a path under the working directory when run outside Gradle. The Gradle `benchmark` task
     * runs every module's benchmarks sequentially within one JVM (no parallel forks), so a
     * plain synchronized append is enough - no file locking needed across processes.
     */
    @Synchronized
    private fun recordResult(result: Result) {
        val module = System.getProperty("netinspector.benchmark.module") ?: "unknown"
        val file = File(System.getProperty("netinspector.benchmark.resultsFile") ?: "build/benchmark-results.csv")
        file.parentFile?.mkdirs()
        val isNew = !file.exists()
        file.appendText(
            buildString {
                if (isNew) appendLine("module,benchmark,iterations,min_ms,median_ms,p95_ms,max_ms")
                append(module).append(',')
                append(result.name).append(',')
                append(result.iterations).append(',')
                append(result.minMs.toCsv()).append(',')
                append(result.medianMs.toCsv()).append(',')
                append(result.p95Ms.toCsv()).append(',')
                appendLine(result.maxMs.toCsv())
            },
        )
    }

    private const val NANOS_PER_MILLI = 1_000_000.0
    private const val MEDIAN_PERCENTILE = 0.50
    private const val P95_PERCENTILE = 0.95
}
