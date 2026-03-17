package misk.metrics.pal

import misk.annotation.ExperimentalMiskApi
import misk.metrics.pal.backend.MetricsBackend
import misk.metrics.v2.defaultBuckets

/**
 * Platform Abstraction Layer (PAL) for metrics.
 *
 * This is the internal metrics interface used by misk's own instrumentation (interceptors, jetty,
 * etc.). It decouples misk from any specific metrics backend, enabling a migration path from
 * Prometheus to OpenTelemetry without changing misk's metric call sites.
 *
 * ## How it fits into the OTel migration
 *
 * Misk is migrating from Prometheus-native metrics to OpenTelemetry. The migration has three modes,
 * each backed by a different [MetricsBackend]:
 *
 * 1. **Prometheus only** (`MetricsModule`) — Today's default. PalMetrics delegates to
 *    [misk.metrics.v2.Metrics] via [PrometheusMetricsBackend][misk.metrics.pal.backend.PrometheusMetricsBackend].
 *    No OTel involvement. No behavior change.
 *
 * 2. **Bridge** (`BridgeMetricsModule`) — Misk's internal metrics are written to OTel. Metrics with
 *    canonical OTel names (registered via `CanonicalMetricMapping` multibindings) are additionally
 *    written under their OTel semantic convention name. A caller-provided `MetricNameMapper` can
 *    transform or drop non-canonical metric names. App code using `v2.Metrics` continues to write
 *    to Prometheus unchanged.
 *
 * 3. **OTel only** (`OtelMetricsModule`) — All misk metrics go to OTel. Injecting `v2.Metrics`
 *    fails at startup, forcing all consumers to use PalMetrics or the OTel SDK directly.
 *
 * ## Who should use this
 *
 * **Misk internal code** (interceptors, framework modules) should inject `PalMetrics`.
 * **Application code** should continue using `misk.metrics.v2.Metrics` until they are ready to
 * migrate to OTel directly. PalMetrics is not a public versioned API — it is misk's internal
 * plumbing, marked with [@ExperimentalMiskApi].
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
