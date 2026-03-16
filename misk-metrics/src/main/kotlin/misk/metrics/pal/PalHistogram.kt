package misk.metrics.pal

/** A histogram metric that tracks the distribution of observed values into configurable buckets. */
interface PalHistogram {
  fun labels(vararg labelValues: String): Child

  interface Child {
    fun observe(value: Double)
  }
}
