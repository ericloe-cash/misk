package misk.metrics.pal.backend

import misk.annotation.ExperimentalMiskApi
import misk.metrics.pal.PalCounter
import misk.metrics.pal.PalGauge
import misk.metrics.pal.PalHistogram
import misk.metrics.pal.PalPeakGauge
import misk.metrics.pal.PalProvidedGauge

/**
 * Service provider interface (SPI) for metrics backend implementations. Each backend creates
 * metric instruments backed by a specific telemetry system.
 *
 * Implementations:
 * - [PrometheusMetricsBackend] — Delegates to [misk.metrics.v2.Metrics] for Prometheus.
 * - `OtelMetricsBackend` (in `misk-metrics-otel`) — Creates OTel SDK instruments.
 * - `BridgeMetricsBackend` (in `misk-metrics-otel`) — Writes to OTel with caller-provided name
 *   mapping and optional canonical OTel name generation.
 *
 * This is internal to misk's metrics plumbing. Application code should not implement or interact
 * with this interface directly.
 *
 * @see misk.metrics.pal.PalMetrics for the consumer-facing interface.
 */
@ExperimentalMiskApi
interface MetricsBackend {
  fun createCounter(name: String, help: String, labelNames: List<String>): PalCounter
  fun createGauge(name: String, help: String, labelNames: List<String>): PalGauge
  fun createPeakGauge(name: String, help: String, labelNames: List<String>): PalPeakGauge
  fun createProvidedGauge(name: String, help: String, labelNames: List<String>): PalProvidedGauge
  fun createHistogram(
    name: String,
    help: String,
    labelNames: List<String>,
    buckets: List<Double>,
  ): PalHistogram
}
