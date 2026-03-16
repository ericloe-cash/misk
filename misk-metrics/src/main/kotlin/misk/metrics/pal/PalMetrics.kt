package misk.metrics.pal

import misk.annotation.ExperimentalMiskApi
import misk.metrics.pal.backend.MetricsBackend

/**
 * Platform abstraction layer for metrics. Decouples misk's internal metric instrumentation from
 * any specific backend (Prometheus, OTel, or both).
 *
 * The backing implementation is determined by which module is installed:
 * - `MetricsModule` / `PrometheusMetricsServiceModule` — Prometheus-only (today's default)
 * - `BridgeMetricsModule` — OTel engine with legacy Prometheus dual-write
 * - `OtelMetricsModule` — OTel only
 */
@ExperimentalMiskApi
interface PalMetrics {
  fun counter(name: String, help: String, labelNames: List<String> = listOf()): PalCounter

  fun gauge(name: String, help: String = "", labelNames: List<String> = listOf()): PalGauge

  fun peakGauge(name: String, help: String = "", labelNames: List<String> = listOf()): PalPeakGauge

  fun providedGauge(
    name: String,
    help: String = "",
    labelNames: List<String> = listOf(),
  ): PalProvidedGauge

  fun histogram(
    name: String,
    help: String = "",
    labelNames: List<String> = listOf(),
    buckets: List<Double> = defaultBuckets,
  ): PalHistogram

  companion object {
    fun factory(backend: MetricsBackend): PalMetrics = DefaultPalMetrics(backend)
  }
}

@OptIn(ExperimentalMiskApi::class)
internal class DefaultPalMetrics(private val backend: MetricsBackend) : PalMetrics {
  override fun counter(name: String, help: String, labelNames: List<String>): PalCounter =
    backend.createCounter(name, help, labelNames)

  override fun gauge(name: String, help: String, labelNames: List<String>): PalGauge =
    backend.createGauge(name, help, labelNames)

  override fun peakGauge(name: String, help: String, labelNames: List<String>): PalPeakGauge =
    backend.createPeakGauge(name, help, labelNames)

  override fun providedGauge(
    name: String,
    help: String,
    labelNames: List<String>,
  ): PalProvidedGauge = backend.createProvidedGauge(name, help, labelNames)

  override fun histogram(
    name: String,
    help: String,
    labelNames: List<String>,
    buckets: List<Double>,
  ): PalHistogram = backend.createHistogram(name, help, labelNames, buckets)
}
