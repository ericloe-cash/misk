package misk.metrics.pal

import misk.annotation.ExperimentalMiskApi

/**
 * A gauge that tracks the peak (maximum) value observed since the last collection, then resets to
 * zero after each collection.
 */
@ExperimentalMiskApi
interface PalPeakGauge {
  fun labels(vararg labelValues: String): Child

  interface Child {
    fun record(newValue: Double)
  }
}
