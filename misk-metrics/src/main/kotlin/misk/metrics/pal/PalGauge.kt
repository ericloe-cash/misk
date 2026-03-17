package misk.metrics.pal

import misk.annotation.ExperimentalMiskApi

/**
 * A gauge metric that can go up and down. Equivalent to Prometheus `Gauge` or OTel
 * `DoubleUpDownCounter`.
 *
 * Use for values that fluctuate (queue depth, active connections, temperature). For values that
 * only go up, use [PalCounter]. For values provided by a callback, use [PalProvidedGauge].
 *
 * **OTel limitation**: OTel's `UpDownCounter` does not have a `set()` operation — only `add()`.
 * In OTel-backed modes, `set()` is approximated. The Prometheus backend supports `set()` natively.
 *
 * @see PalMetrics for how this fits into the Prometheus → OTel migration.
 */
@ExperimentalMiskApi
interface PalGauge {
  fun labels(vararg labelValues: String): Child

  interface Child {
    fun set(value: Double)
    fun inc(amount: Double = 1.0)
    fun dec(amount: Double = 1.0)
  }
}
