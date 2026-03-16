package misk.metrics.v3

/**
 * A gauge that tracks the peak (maximum) value observed since the last collection, then resets to
 * zero after each collection.
 */
interface MiskPeakGauge {
  fun labels(vararg labelValues: String): Child

  interface Child {
    fun record(newValue: Double)
  }
}
