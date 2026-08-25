# ADR-0010: Hand-rolled wall-clock benchmark harness, not JMH or kotlinx-benchmark

Status: Accepted

## Context

improvement-ideas.md #32 asks for a benchmark/perf suite for the LAN sweep pipeline's pure
logic (parsing, scoring, precedence) and its timing-sensitive scheduling of parallel host
discovery (`HostSweeper`'s three-pass, concurrency-limited fan-out, design §8.2 Stage B).
The requirement names JMH "or a lightweight Kotlin equivalent" and leaves the choice open.

JMH is the standard JVM microbenchmarking tool: JVM forking per benchmark, dead-code-
elimination blackholing, warmup/measurement iteration control, and real statistical rigor.
`kotlinx-benchmark` is JetBrains's Kotlin-first wrapper around the same idea, usable for
Kotlin-multiplatform targets. Both are built for **hot-loop microbenchmarks** - a single pure
function called millions of times - and both need real Gradle-plugin and annotation-processor
wiring (`me.champeau.jmh` or `org.jetbrains.kotlinx.benchmark`, plus `kotlin-allopen` since
JMH-generated subclasses can't extend a final class).

Neither tool is available in `gradle/libs.versions.toml` today, so either choice adds a new
external Gradle plugin and its transitive dependencies. More importantly, the benchmark this
project actually needs most - `HostSweeper.sweep`'s pass-by-pass scheduling - is a suspend
function whose behaviour is defined by coroutine dispatch (`Dispatchers.IO.limitedParallelism`,
structured concurrency, `awaitAll`) across hundreds of addresses, not a hot loop. Getting JMH
or kotlinx-benchmark to measure a `suspend fun` meaningfully needs extra bridging (blocking
adapters, or JMH's less-common async support), fighting the tool rather than using it for the
case that matters most here.

## Decision

Use a small hand-rolled harness (`Benchmark.kt`, duplicated verbatim in each of
`:core:common`, `:core:model`, and `:data:lan` - see the KDoc on each copy for why it isn't a
single shared dependency): warm up, time N repetitions with `System.nanoTime()`, report
min/median/p95/max, and append one CSV row per scenario. Benchmarks are plain classes named
`*Benchmark.kt` living in each module's existing `src/test`, so they reuse the module's real
test doubles (`HostSweeperBenchmark` mocks `IcmpSweepProbe`/`TcpSweepProbe` with mockk exactly
the way `LanDiscoveryRepositoryTest` already does) with zero new dependencies. A `build-logic`
convention (`configureBenchmarks` in `Benchmarks.kt`) splits `*Benchmark.class` out of the
module's normal `test`/`testDebugUnitTest` task into a separate `benchmark` task, so a
benchmark run never affects the blocking build gate's pass/fail outcome or its timing.

## Consequences

Easier: zero new build-tooling dependencies or versions to track; a benchmark for a suspend
function is exactly as easy to write as one for a pure function, since the harness is just
"time this block" with no annotation-processing model to satisfy; the whole harness is ~100
lines any contributor can read in one sitting, versus JMH's substantial surface area.

Harder: no JVM forking, so a benchmark can be skewed by whatever else the JIT is doing in the
same process, and no dead-code-elimination blackholing, so a benchmark result the JIT can
prove is unused could theoretically be optimized away (in practice, `Benchmark.run` returns
and prints the block's result, and none of this suite's benchmarked calls are provably
side-effect-free, so this risk is mostly theoretical here). This harness is explicitly not
trying to match JMH's statistical rigor - see `benchmarks/baseline.csv` and
`scripts/compare-benchmarks.sh`'s generous default 50% regression threshold, sized for "catch
an accidental O(n²) or a 10x slowdown," not "detect a 5% regression reliably." That bar is
what improvement-ideas.md #32 actually asks for: a regression-catching net for dev-facing
tooling, not a publishable performance study. If the project later needs finer-grained,
statistically rigorous microbenchmarks, revisit JMH/kotlinx-benchmark then rather than paying
their setup cost now for a need this harness already covers.
