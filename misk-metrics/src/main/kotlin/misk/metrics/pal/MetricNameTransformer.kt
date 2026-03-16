package misk.metrics.pal

import misk.annotation.ExperimentalMiskApi

/**
 * Transforms metric names for pipeline-specific conventions (e.g., adding an app prefix like
 * "cash."). Applied uniformly to all legacy Prometheus metric names during bridge mode.
 */
@ExperimentalMiskApi
fun interface MetricNameTransformer {
  fun transform(name: String): String

  companion object {
    val IDENTITY = MetricNameTransformer { it }
  }
}
