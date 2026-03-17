package misk.metrics.pal

import misk.annotation.ExperimentalMiskApi

/**
 * A gauge that tracks the peak (maximum) value observed since the last collection, then resets to
 * zero after each collection. This is a misk-specific metric type — neither Prometheus nor OTel
 * have a direct equivalent.
 *
 * Use for tracking maximum observed values over a scrape interval (e.g., peak thread count, peak
 * queue depth). The value resets on each collection, so it reflects the peak within the most
 * recent interval.
 *
 * **Prometheus implementation**: Uses [misk.metrics.v2.PeakGauge], which resets on `collect()`.
 * **OTel implementation**: Uses an async gauge with internal `AtomicDouble` that resets when the
 * OTel export callback fires. The reset timing depends on the OTel export interval, which may
 * differ from the Prometheus scrape interval.
 *
 * @see PalMetrics for how this fits into the Prometheus → OTel migration.
 */
@ExperimentalMiskApi
interface PalPeakGauge {
  fun labels(vararg labelValues: String): Child

  interface Child {
    fun record(newValue: Double)
  }
}
