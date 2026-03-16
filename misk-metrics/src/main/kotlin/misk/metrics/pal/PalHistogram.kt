package misk.metrics.pal

import misk.annotation.ExperimentalMiskApi

/** A histogram metric that tracks the distribution of observed values into configurable buckets. */
@ExperimentalMiskApi
interface PalHistogram {
  fun labels(vararg labelValues: String): Child

  interface Child {
    fun observe(value: Double)
  }
}
