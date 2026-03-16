package misk.metrics.v3

/**
 * A gauge metric that can go up and down.
 */
interface MiskGauge {
  fun labels(vararg labelValues: String): Child

  interface Child {
    fun set(value: Double)
    fun inc(amount: Double = 1.0)
    fun dec(amount: Double = 1.0)
  }
}
