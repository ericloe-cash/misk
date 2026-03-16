package misk.metrics.pal

/** A gauge metric that can go up and down. */
interface PalGauge {
  fun labels(vararg labelValues: String): Child

  interface Child {
    fun set(value: Double)
    fun inc(amount: Double = 1.0)
    fun dec(amount: Double = 1.0)
  }
}
