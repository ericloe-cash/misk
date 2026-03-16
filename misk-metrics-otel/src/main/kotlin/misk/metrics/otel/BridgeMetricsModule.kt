package misk.metrics.otel

import com.google.inject.Provides
import io.opentelemetry.api.metrics.Meter
import io.prometheus.client.CollectorRegistry
import jakarta.inject.Singleton
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.metrics.MetricsModule
import misk.metrics.pal.MetricNameTransformer
import misk.metrics.pal.PalMetrics
import misk.metrics.pal.backend.PrometheusMetricsBackend

/**
 * Bridge metrics module (Mode 2). OTel is the primary engine; legacy Prometheus metrics are also
 * emitted via dual-write. Both `PalMetrics` (backed by the bridge) and `misk.metrics.v2.Metrics`
 * (backed by Prometheus) are available.
 *
 * The caller must provide bindings for:
 * - [Meter] — the OTel meter
 * - [MetricNameTransformer] — optional, defaults to identity. Bind to customize legacy metric
 *   names (e.g., adding a `cash.` prefix).
 *
 * This module also installs [MetricsModule] to provide v2.Metrics, v1.Metrics, and
 * CollectorRegistry for legacy consumers.
 */
class BridgeMetricsModule : KAbstractModule() {
  override fun configure() {
    requireBinding<Meter>()
    install(MetricsModule())

    // Provide a default identity transformer if the caller doesn't bind one.
    bind<MetricNameTransformer>().toInstance(MetricNameTransformer.IDENTITY)
  }

  @Provides @Singleton
  fun providePalMetrics(
    meter: Meter,
    v2Metrics: misk.metrics.v2.Metrics,
    nameTransformer: MetricNameTransformer,
  ): PalMetrics {
    val otelBackend = OtelMetricsBackend(meter)
    val prometheusBackend = PrometheusMetricsBackend(v2Metrics)
    return PalMetrics.factory(BridgeMetricsBackend(otelBackend, prometheusBackend, nameTransformer))
  }
}
