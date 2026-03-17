package misk.metrics.pal

import misk.annotation.ExperimentalMiskApi

/**
 * A monotonically increasing counter metric. Equivalent to Prometheus `Counter` or OTel
 * `LongCounter`. Use for counts of events (requests served, errors, etc.).
 *
 * Call [labels] with the label values matching the `labelNames` from [PalMetrics.counter] to get
 * a [Child] that can be incremented. For metrics with no labels, call `labels()` with no arguments.
 *
 * @see PalMetrics for how this fits into the Prometheus → OTel migration.
 */
@ExperimentalMiskApi
interface PalCounter {
  fun labels(vararg labelValues: String): Child

  interface Child {
    fun inc(amount: Double = 1.0)
  }
}
