@file:OptIn(ExperimentalMiskApi::class)

package misk.metrics.otel

import com.google.inject.Provides
import io.opentelemetry.api.metrics.Meter
import jakarta.inject.Singleton
import misk.annotation.ExperimentalMiskApi
import misk.inject.KAbstractModule
import misk.metrics.MetricsModule
import misk.metrics.pal.MetricNameTransformer
import misk.metrics.pal.PalMetrics
import misk.metrics.pal.backend.PrometheusMetricsBackend

/**
 * Bridge metrics module (Mode 2). OTel is the primary engine; legacy Prometheus metrics are also
 * emitted via dual-write. Both `PalMetrics` (backed by the bridge) and `misk.metrics.v2.Metrics`
 * (backed by Prometheus) are available.
 *
 * The caller must provide a binding for [Meter]. Optionally bind [MetricNameTransformer] to
 * customize legacy metric names (e.g., adding a prefix). Defaults to identity.
 *
 * This module also installs [MetricsModule] to provide v2.Metrics, v1.Metrics, and
 * CollectorRegistry for legacy consumers.
 */
@ExperimentalMiskApi
class BridgeMetricsModule(
  private val nameTransformer: MetricNameTransformer = MetricNameTransformer.IDENTITY,
) : KAbstractModule() {
  override fun configure() {
    requireBinding<Meter>()
    install(MetricsModule())
  }

  @Provides @Singleton
  fun providePalMetrics(
    meter: Meter,
    v2Metrics: misk.metrics.v2.Metrics,
  ): PalMetrics {
    val otelBackend = OtelMetricsBackend(meter)
    val prometheusBackend = PrometheusMetricsBackend(v2Metrics)
    return PalMetrics.factory(BridgeMetricsBackend(otelBackend, prometheusBackend, nameTransformer))
  }
}
