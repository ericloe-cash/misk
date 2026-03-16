package misk.metrics.pal

/** A monotonically increasing counter metric. */
interface PalCounter {
  fun labels(vararg labelValues: String): Child

  interface Child {
    fun inc(amount: Double = 1.0)
  }
}
