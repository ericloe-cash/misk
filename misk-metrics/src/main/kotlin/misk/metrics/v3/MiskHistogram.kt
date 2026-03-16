package misk.metrics.v3

/**
 * A histogram metric that tracks the distribution of observed values into configurable buckets.
 */
interface MiskHistogram {
  fun labels(vararg labelValues: String): Child

  interface Child {
    fun observe(value: Double)
  }
}
