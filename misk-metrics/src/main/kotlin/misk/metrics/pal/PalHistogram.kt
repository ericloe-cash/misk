package misk.metrics.pal

import misk.annotation.ExperimentalMiskApi

/**
 * A histogram metric that tracks the distribution of observed values into configurable buckets.
 * Equivalent to Prometheus `Histogram` or OTel `DoubleHistogram`.
 *
 * Use for latency, request size, or any value where you want to understand the distribution. The
 * bucket boundaries are configured at creation time via [PalMetrics.histogram]'s `buckets`
 * parameter (defaults to [misk.metrics.v2.defaultBuckets]).
 *
 * **Note on buckets**: In Prometheus mode, these are explicit bucket boundaries. In OTel mode, the
 * same boundaries are passed as `ExplicitBucketBoundariesAdvice`. OTel also supports exponential
 * histograms which adapt automatically, but the PAL currently uses explicit buckets for
 * compatibility with existing dashboards.
 *
 * @see PalMetrics for how this fits into the Prometheus → OTel migration.
 */
@ExperimentalMiskApi
interface PalHistogram {
  fun labels(vararg labelValues: String): Child

  interface Child {
    fun observe(value: Double)
  }
}
