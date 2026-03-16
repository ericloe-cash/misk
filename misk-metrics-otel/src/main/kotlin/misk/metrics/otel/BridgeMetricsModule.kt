@file:OptIn(ExperimentalMiskApi::class)

package misk.metrics.otel

import com.google.inject.Provides
import io.opentelemetry.api.metrics.Meter
import jakarta.inject.Singleton
import misk.annotation.ExperimentalMiskApi
import misk.inject.KAbstractModule
import misk.metrics.MetricsModule
import misk.metrics.pal.PalMetrics

/**
 * Bridge metrics module (Mode 2). Misk's internal metrics are written to OTel. App metrics using
 * `v2.Metrics` continue to go to Prometheus unchanged.
 *
 * The metric pipeline in bridge mode:
 * 1. Every misk metric goes through the caller's [MetricNameMapper] — can transform or drop
 * 2. [misk.metrics.pal.PrometheusNameNormalizer] runs on the output
 * 3. Written to OTel (unless dropped)
 * 4. If the metric has a [CanonicalMetricMapping] (via multibinding), the canonical OTel version
 *    is additionally written with remapped labels. Canonical metrics cannot be intercepted.
 *
 * The caller must provide:
 * - A binding for [Meter]
 *
 * The caller may optionally:
 * - Provide a [MetricNameMapper] constructor param for pipeline-specific naming
 * - Install [MiskStandardMetricMappingsModule] for canonical OTel names
 * - Multibind additional [CanonicalMetricMapping]s for custom canonical mappings
 *
 * This module also installs [MetricsModule] so `v2.Metrics`, `v1.Metrics`, and
 * `CollectorRegistry` remain available for app code.
 *
 * **Open question**: Can OTel metrics with mapped names be equivalent enough to Prometheus metrics
 * they replace? Differences in histogram bucket semantics, counter reset behavior, and label
 * handling may cause dashboard/alert divergence even with correct name mapping.
 */
@ExperimentalMiskApi
class BridgeMetricsModule(
  private val nameMapper: MetricNameMapper = MetricNameMapper.IDENTITY,
) : KAbstractModule() {
  override fun configure() {
    requireBinding<Meter>()
    install(MetricsModule())
    // Ensure the multibinding exists even if no canonical mappings are installed.
    newMultibinder<CanonicalMetricMapping>()
  }

  @Provides @Singleton
  fun providePalMetrics(
    meter: Meter,
    canonicalMappings: Set<CanonicalMetricMapping>,
  ): PalMetrics {
    val otelBackend = OtelMetricsBackend(meter)
    return PalMetrics.factory(BridgeMetricsBackend(otelBackend, canonicalMappings, nameMapper))
  }
}
