package misk.metrics.pal

import misk.annotation.ExperimentalMiskApi

/**
 * A gauge whose value is supplied by a callback function rather than being explicitly set. The
 * callback is invoked each time the metric is collected (scraped). Equivalent to OTel's
 * asynchronous gauge (`ObservableDoubleGauge`).
 *
 * The provider is held via a weak reference to the `reference` object. If the reference is garbage
 * collected, the gauge automatically returns 0. This is useful for monitoring external objects
 * (e.g., connection pool stats) without preventing their garbage collection.
 *
 * Example:
 * ```kotlin
 * val poolGauge = palMetrics.providedGauge("pool_active", "Active connections", listOf("name"))
 * poolGauge.labels("main").registerProvider(connectionPool) { activeCount }
 * ```
 *
 * @see PalMetrics for how this fits into the Prometheus → OTel migration.
 */
@ExperimentalMiskApi
interface PalProvidedGauge {
  fun labels(vararg labelValues: String): Child

  interface Child {
    fun <T : Any> registerProvider(reference: T, provider: T.() -> Number)
  }
}
