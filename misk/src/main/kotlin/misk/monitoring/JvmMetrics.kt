@file:OptIn(ExperimentalMiskApi::class)

package misk.monitoring

import misk.annotation.ExperimentalMiskApi
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.lang.management.RuntimeMXBean
import misk.metrics.pal.PalMetrics

/** Exposes additional JVM metrics. */
@Singleton
class JvmMetrics @Inject constructor(private val runtimeMxBean: RuntimeMXBean, metrics: PalMetrics) {
  /**
   * Exposes the JVM uptime in milliseconds as a provided gauge that retrieves the current time
   * when the gauge is read.
   *
   * Uptime is useful for a few things:
   * - Allows for easy correlation of other metrics with process startup (e.g. latencies might be slower early in a
   *   fresh VM without warm caches, pools, or full JIT)
   * - Allows for the correlation of elapsed time against the resulting time-series. This can be a useful operational
   *   tool to help reason about artifacts from time and space aggregation in a metrics pipeline (e.g. we know that
   *   1000ms _should_ be the observed rate of time elapsed per second).
   */
  init {
    metrics
      .providedGauge("jvm_uptime_ms", "JVM uptime in milliseconds")
      .labels()
      .registerProvider(runtimeMxBean) { uptime.toDouble() }
  }
}
