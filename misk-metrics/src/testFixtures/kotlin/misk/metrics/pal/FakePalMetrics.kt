@file:OptIn(ExperimentalMiskApi::class)

package misk.metrics.pal

import misk.annotation.ExperimentalMiskApi
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory [PalMetrics] implementation for testing. Provides assertion helpers to inspect recorded
 * metric values without any backend dependency.
 */
@Singleton
class FakePalMetrics @Inject constructor() : PalMetrics {
  private val counters = ConcurrentHashMap<MetricKey, FakeCounterValue>()
  private val gauges = ConcurrentHashMap<MetricKey, FakeGaugeValue>()
  private val peakGauges = ConcurrentHashMap<MetricKey, FakePeakGaugeValue>()
  private val providedGauges = ConcurrentHashMap<MetricKey, FakeProvidedGaugeValue>()
  private val histograms = ConcurrentHashMap<MetricKey, FakeHistogramValue>()

  override fun counter(name: String, help: String, labelNames: List<String>): PalCounter =
    object : PalCounter {
      override fun labels(vararg labelValues: String): PalCounter.Child {
        val value = counters.getOrPut(MetricKey(name, labelValues.toList())) { FakeCounterValue() }
        return object : PalCounter.Child {
          override fun inc(amount: Double) = value.inc(amount)
        }
      }
    }

  override fun gauge(name: String, help: String, labelNames: List<String>): PalGauge =
    object : PalGauge {
      override fun labels(vararg labelValues: String): PalGauge.Child {
        val value = gauges.getOrPut(MetricKey(name, labelValues.toList())) { FakeGaugeValue() }
        return object : PalGauge.Child {
          override fun set(value: Double) = gauges[MetricKey(name, labelValues.toList())]!!.set(value)
          override fun inc(amount: Double) = gauges[MetricKey(name, labelValues.toList())]!!.inc(amount)
          override fun dec(amount: Double) = gauges[MetricKey(name, labelValues.toList())]!!.dec(amount)
        }
      }
    }

  override fun peakGauge(name: String, help: String, labelNames: List<String>): PalPeakGauge =
    object : PalPeakGauge {
      override fun labels(vararg labelValues: String): PalPeakGauge.Child {
        val value = peakGauges.getOrPut(MetricKey(name, labelValues.toList())) { FakePeakGaugeValue() }
        return object : PalPeakGauge.Child {
          override fun record(newValue: Double) = value.record(newValue)
        }
      }
    }

  override fun providedGauge(
    name: String,
    help: String,
    labelNames: List<String>,
  ): PalProvidedGauge =
    object : PalProvidedGauge {
      override fun labels(vararg labelValues: String): PalProvidedGauge.Child {
        val value = providedGauges.getOrPut(MetricKey(name, labelValues.toList())) { FakeProvidedGaugeValue() }
        return object : PalProvidedGauge.Child {
          override fun <T : Any> registerProvider(reference: T, provider: T.() -> Number) =
            value.registerProvider(reference, provider)
        }
      }
    }

  override fun histogram(
    name: String,
    help: String,
    labelNames: List<String>,
    buckets: List<Double>,
  ): PalHistogram =
    object : PalHistogram {
      override fun labels(vararg labelValues: String): PalHistogram.Child {
        val value = histograms.getOrPut(MetricKey(name, labelValues.toList())) { FakeHistogramValue() }
        return object : PalHistogram.Child {
          override fun observe(value: Double) =
            histograms[MetricKey(name, labelValues.toList())]!!.observe(value)
        }
      }
    }

  /** Returns the current counter value, or null if not found. */
  fun getCounter(name: String, vararg labelValues: String): Double? =
    counters[MetricKey(name, labelValues.toList())]?.get()

  /** Returns the current gauge value, or null if not found. */
  fun getGauge(name: String, vararg labelValues: String): Double? =
    gauges[MetricKey(name, labelValues.toList())]?.get()

  /** Returns the current peak gauge value, or null if not found. */
  fun getPeakGauge(name: String, vararg labelValues: String): Double? =
    peakGauges[MetricKey(name, labelValues.toList())]?.get()

  /** Returns the count of observations in a histogram, or null if not found. */
  fun getHistogramCount(name: String, vararg labelValues: String): Long? =
    histograms[MetricKey(name, labelValues.toList())]?.count

  /** Returns the sum of all observations in a histogram, or null if not found. */
  fun getHistogramSum(name: String, vararg labelValues: String): Double? =
    histograms[MetricKey(name, labelValues.toList())]?.sum

  /** Resets all recorded metrics. */
  fun reset() {
    counters.clear()
    gauges.clear()
    peakGauges.clear()
    providedGauges.clear()
    histograms.clear()
  }
}

internal data class MetricKey(val name: String, val labelValues: List<String>)

internal class FakeCounterValue {
  @Volatile private var value = 0.0
  @Synchronized fun inc(amount: Double) { value += amount }
  fun get() = value
}

internal class FakeGaugeValue {
  @Volatile private var value = 0.0
  @Synchronized fun set(v: Double) { value = v }
  @Synchronized fun inc(amount: Double) { value += amount }
  @Synchronized fun dec(amount: Double) { value -= amount }
  fun get() = value
}

internal class FakePeakGaugeValue {
  @Volatile private var value = 0.0
  @Synchronized fun record(newValue: Double) { if (newValue > value) value = newValue }
  fun get() = value
}

internal class FakeProvidedGaugeValue {
  private var reference: WeakReference<Any> = WeakReference(null)
  private var provider: () -> Number = { 0 }
  fun <T : Any> registerProvider(reference: T, provider: T.() -> Number) {
    this.reference = WeakReference(reference)
    this.provider = {
      @Suppress("UNCHECKED_CAST")
      (this.reference.get() as? T)?.provider() ?: 0
    }
  }
  fun get(): Double = provider().toDouble()
}

internal class FakeHistogramValue {
  var count = 0L; private set
  var sum = 0.0; private set
  @Synchronized fun observe(value: Double) { count++; sum += value }
}
