package misk.metrics.pal

import misk.annotation.ExperimentalMiskApi

/** A monotonically increasing counter metric. */
@ExperimentalMiskApi
interface PalCounter {
  fun labels(vararg labelValues: String): Child

  interface Child {
    fun inc(amount: Double = 1.0)
  }
}
