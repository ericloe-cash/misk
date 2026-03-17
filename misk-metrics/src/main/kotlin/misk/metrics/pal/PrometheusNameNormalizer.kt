package misk.metrics.pal

import misk.annotation.ExperimentalMiskApi

/**
 * Canonical place for Prometheus-specific metric name normalization rules.
 *
 * In bridge mode, this runs after the caller's `MetricNameMapper` and before the metric is written
 * to OTel, ensuring that metric names follow Prometheus conventions even when recorded via OTel.
 *
 * Current rules:
 * - Strips `_total` suffix (Prometheus auto-appends this to counters, so registering
 *   `my_counter_total` would produce `my_counter_total_total` without this normalization).
 *
 * Future rules should be added here to keep normalization centralized.
 */
@ExperimentalMiskApi
object PrometheusNameNormalizer {
  fun normalize(name: String): String {
    var result = name
    if (result.endsWith("_total")) {
      result = result.removeSuffix("_total")
    }
    // Future normalization rules go here.
    return result
  }
}
