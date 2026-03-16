package misk.metrics.pal

import misk.annotation.ExperimentalMiskApi

/**
 * A gauge whose value is supplied by a callback provider. The provider is held via a weak
 * reference, so the gauge automatically returns 0 if the reference is garbage collected.
 */
@ExperimentalMiskApi
interface PalProvidedGauge {
  fun labels(vararg labelValues: String): Child

  interface Child {
    fun <T : Any> registerProvider(reference: T, provider: T.() -> Number)
  }
}
