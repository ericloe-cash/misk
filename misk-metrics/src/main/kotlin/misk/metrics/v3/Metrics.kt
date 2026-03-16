package misk.metrics.v3

import misk.metrics.v3.backend.MetricsBackend

/**
 * Backend-agnostic interface for creating metrics. Unlike [misk.metrics.v2.Metrics], this does not
 * expose any Prometheus-specific types, allowing pluggable backends (Prometheus, OTel, or both).
 *
 * Services should inject this interface to create metrics. The backing implementation is determined
 * by which module is installed:
 * - `MetricsModule` / `PrometheusMetricsServiceModule` — Prometheus-only (today's default)
 * - `BridgeMetricsModule` — OTel engine with legacy Prometheus dual-write
 * - `OtelMetricsModule` — OTel only
 */
interface Metrics {
  fun counter(name: String, help: String, labelNames: List<String> = listOf()): MiskCounter

  fun gauge(name: String, help: String = "", labelNames: List<String> = listOf()): MiskGauge

  fun peakGauge(name: String, help: String = "", labelNames: List<String> = listOf()): MiskPeakGauge

  fun providedGauge(
    name: String,
    help: String = "",
    labelNames: List<String> = listOf(),
  ): MiskProvidedGauge

  fun histogram(
    name: String,
    help: String = "",
    labelNames: List<String> = listOf(),
    buckets: List<Double> = defaultBuckets,
  ): MiskHistogram

  companion object {
    fun factory(backend: MetricsBackend): Metrics = DefaultMetrics(backend)
  }
}

internal class DefaultMetrics(private val backend: MetricsBackend) : Metrics {
  override fun counter(name: String, help: String, labelNames: List<String>): MiskCounter =
    backend.createCounter(name, help, labelNames)

  override fun gauge(name: String, help: String, labelNames: List<String>): MiskGauge =
    backend.createGauge(name, help, labelNames)

  override fun peakGauge(name: String, help: String, labelNames: List<String>): MiskPeakGauge =
    backend.createPeakGauge(name, help, labelNames)

  override fun providedGauge(
    name: String,
    help: String,
    labelNames: List<String>,
  ): MiskProvidedGauge = backend.createProvidedGauge(name, help, labelNames)

  override fun histogram(
    name: String,
    help: String,
    labelNames: List<String>,
    buckets: List<Double>,
  ): MiskHistogram = backend.createHistogram(name, help, labelNames, buckets)
}
