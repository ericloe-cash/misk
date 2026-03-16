package misk.metrics.pal

/**
 * Canonical place for Prometheus-specific metric name normalization rules. Applied to metric names
 * before they are registered with Prometheus.
 */
object PrometheusNameNormalizer {
  fun normalize(name: String): String {
    var result = name
    // Prometheus appends _total to counter names, so strip it if already present to avoid
    // double-suffixing.
    if (result.endsWith("_total")) {
      result = result.removeSuffix("_total")
    }
    // Future normalization rules go here.
    return result
  }
}
