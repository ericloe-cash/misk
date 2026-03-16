@file:OptIn(ExperimentalMiskApi::class)

package misk.metrics.otel

import misk.annotation.ExperimentalMiskApi

/**
 * Caller-provided mapper for transforming metric names in bridge mode. Applied to every metric
 * that does NOT have a canonical OTel mapping. The mapper can:
 * - Transform the name (e.g., add a prefix like `cash_`)
 * - Return null to signal the metric should be dropped from OTel
 *
 * After the mapper runs, [misk.metrics.pal.PrometheusNameNormalizer] is applied (e.g., `_total`
 * stripping).
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
