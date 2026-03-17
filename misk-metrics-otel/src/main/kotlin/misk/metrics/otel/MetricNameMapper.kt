@file:OptIn(ExperimentalMiskApi::class)

package misk.metrics.otel

import misk.annotation.ExperimentalMiskApi

/**
 * Caller-provided mapper for transforming metric names in bridge mode. Applied to every metric
 * that flows through [BridgeMetricsBackend], regardless of whether it has a canonical mapping.
 * The mapper controls the "mapped" OTel instrument name; it does **not** affect canonical metrics
 * (those are always written if a [CanonicalMetricMapping] exists).
 *
 * The mapper can:
 * - Transform the name (e.g., add a prefix like `cash_`)
 * - Return `null` to drop the mapped OTel instrument (the canonical instrument, if any, is still
 *   written)
 *
 * After the mapper runs, [misk.metrics.pal.PrometheusNameNormalizer] is applied to the result
 * (e.g., `_total` suffix stripping so OTel counter naming conventions are followed).
 *
 * Pass an instance to [BridgeMetricsModule] to customize naming for your service's pipeline.
 */
@ExperimentalMiskApi
fun interface MetricNameMapper {
  /**
   * Transform the metric name, or return null to drop it.
   */
  fun map(name: String): String?

  companion object {
    /** Identity mapper — passes names through unchanged, never drops. */
    val IDENTITY = MetricNameMapper { it }
  }
}
