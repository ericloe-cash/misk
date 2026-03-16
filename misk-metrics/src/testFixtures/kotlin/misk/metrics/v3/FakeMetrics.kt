package misk.metrics.v3

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.metrics.v3.backend.MetricsBackend
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory [Metrics] implementation for testing. Provides assertion helpers to inspect recorded
 * metric values without any Prometheus or OTel dependency.
 */
@Singleton
class FakeMetrics @Inject constructor() : Metrics {
  private val backend = FakeMetricsBackend()

  override fun counter(name: String, help: String, labelNames: List<String>): MiskCounter =
    backend.createCounter(name, help, labelNames)

  override fun gauge(name: String, help: String, labelNames: List<String>): MiskGauge =
    backend.createGauge(name, help, labelNames)

  override fun peakGauge(name: String, help: String, labelNames: List<String>): MiskPeakGauge =
    backend.createPeakGauge(name, help, labelNames)

  override fun providedGauge(
    name: String,
    help: String,
    labelNames: List<String>,
  ): MiskProvidedGauge = backend.createProvidedGauge(name, help, labelNames)

  override fun histogram(
    name: String,
    help: String,
    labelNames: List<String>,
    buckets: List<Double>,
  ): MiskHistogram = backend.createHistogram(name, help, labelNames, buckets)

  /** Returns the current counter value, or null if not found. */
  fun getCounter(name: String, vararg labelValues: String): Double? =
    backend.counters[MetricKey(name, labelValues.toList())]?.get()

  /** Returns the current gauge value, or null if not found. */
  fun getGauge(name: String, vararg labelValues: String): Double? =
    backend.gauges[MetricKey(name, labelValues.toList())]?.get()

  /** Returns the current peak gauge value, or null if not found. */
  fun getPeakGauge(name: String, vararg labelValues: String): Double? =
    backend.peakGauges[MetricKey(name, labelValues.toList())]?.get()

  /** Returns the count of observations in a histogram, or null if not found. */
  fun getHistogramCount(name: String, vararg labelValues: String): Long? =
    backend.histograms[MetricKey(name, labelValues.toList())]?.count

  /** Returns the sum of all observations in a histogram, or null if not found. */
  fun getHistogramSum(name: String, vararg labelValues: String): Double? =
    backend.histograms[MetricKey(name, labelValues.toList())]?.sum

  /** Resets all recorded metrics. */
  fun reset() {
    backend.counters.clear()
    backend.gauges.clear()
    backend.peakGauges.clear()
    backend.providedGauges.clear()
    backend.histograms.clear()
  }
}

internal data class MetricKey(val name: String, val labelValues: List<String>)

internal class FakeCounterValue {
  @Volatile
  private var value = 0.0

  @Synchronized
  fun inc(amount: Double) {
    value += amount
  }

  fun get() = value
}

internal class FakeGaugeValue {
  @Volatile
  private var value = 0.0

  @Synchronized
  fun set(v: Double) {
    value = v
  }

  @Synchronized
  fun inc(amount: Double) {
    value += amount
  }

  @Synchronized
  fun dec(amount: Double) {
    value -= amount
  }

  fun get() = value
}

internal class FakePeakGaugeValue {
  @Volatile
  private var value = 0.0

  @Synchronized
  fun record(newValue: Double) {
    if (newValue > value) value = newValue
  }

  fun get() = value
}

internal class FakeProvidedGaugeValue {
  var reference: WeakReference<Any> = WeakReference(null)
    private set
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
  var count = 0L
    private set
  var sum = 0.0
    private set

  @Synchronized
  fun observe(value: Double) {
    count++
    sum += value
  }
}

internal class FakeMetricsBackend : MetricsBackend {
  val counters = ConcurrentHashMap<MetricKey, FakeCounterValue>()
  val gauges = ConcurrentHashMap<MetricKey, FakeGaugeValue>()
  val peakGauges = ConcurrentHashMap<MetricKey, FakePeakGaugeValue>()
  val providedGauges = ConcurrentHashMap<MetricKey, FakeProvidedGaugeValue>()
  val histograms = ConcurrentHashMap<MetricKey, FakeHistogramValue>()

  override fun createCounter(name: String, help: String, labelNames: List<String>): MiskCounter =
    FakeMiskCounter(name, this)

  override fun createGauge(name: String, help: String, labelNames: List<String>): MiskGauge =
    FakeMiskGauge(name, this)

  override fun createPeakGauge(
    name: String,
    help: String,
    labelNames: List<String>,
  ): MiskPeakGauge = FakeMiskPeakGauge(name, this)

  override fun createProvidedGauge(
    name: String,
    help: String,
    labelNames: List<String>,
  ): MiskProvidedGauge = FakeMiskProvidedGauge(name, this)

  override fun createHistogram(
    name: String,
    help: String,
    labelNames: List<String>,
    buckets: List<Double>,
  ): MiskHistogram = FakeMiskHistogram(name, this)
}

private class FakeMiskCounter(
  private val name: String,
  private val backend: FakeMetricsBackend,
) : MiskCounter {
  override fun labels(vararg labelValues: String): MiskCounter.Child {
    val key = MetricKey(name, labelValues.toList())
    val value = backend.counters.getOrPut(key) { FakeCounterValue() }
    return object : MiskCounter.Child {
      override fun inc(amount: Double) = value.inc(amount)
    }
  }
}

private class FakeMiskGauge(
  private val name: String,
  private val backend: FakeMetricsBackend,
) : MiskGauge {
  override fun labels(vararg labelValues: String): MiskGauge.Child {
    val key = MetricKey(name, labelValues.toList())
    val value = backend.gauges.getOrPut(key) { FakeGaugeValue() }
    return object : MiskGauge.Child {
      override fun set(value: Double) = backend.gauges[key]!!.set(value)
      override fun inc(amount: Double) = backend.gauges[key]!!.inc(amount)
      override fun dec(amount: Double) = backend.gauges[key]!!.dec(amount)
    }
  }
}

private class FakeMiskPeakGauge(
  private val name: String,
  private val backend: FakeMetricsBackend,
) : MiskPeakGauge {
  override fun labels(vararg labelValues: String): MiskPeakGauge.Child {
    val key = MetricKey(name, labelValues.toList())
    val value = backend.peakGauges.getOrPut(key) { FakePeakGaugeValue() }
    return object : MiskPeakGauge.Child {
      override fun record(newValue: Double) = value.record(newValue)
    }
  }
}

private class FakeMiskProvidedGauge(
  private val name: String,
  private val backend: FakeMetricsBackend,
) : MiskProvidedGauge {
  override fun labels(vararg labelValues: String): MiskProvidedGauge.Child {
    val key = MetricKey(name, labelValues.toList())
    val value = backend.providedGauges.getOrPut(key) { FakeProvidedGaugeValue() }
    return object : MiskProvidedGauge.Child {
      override fun <T : Any> registerProvider(reference: T, provider: T.() -> Number) =
        value.registerProvider(reference, provider)
    }
  }
}

private class FakeMiskHistogram(
  private val name: String,
  private val backend: FakeMetricsBackend,
) : MiskHistogram {
  override fun labels(vararg labelValues: String): MiskHistogram.Child {
    val key = MetricKey(name, labelValues.toList())
    val value = backend.histograms.getOrPut(key) { FakeHistogramValue() }
    return object : MiskHistogram.Child {
      override fun observe(value: Double) =
        backend.histograms[key]!!.observe(value)
    }
  }
}
