package misk.metrics.v3

/**
 * A monotonically increasing counter metric.
 */
interface MiskCounter {
  fun labels(vararg labelValues: String): Child

  interface Child {
    fun inc(amount: Double = 1.0)
  }
}
