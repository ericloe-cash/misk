package misk.metrics.v3.backend

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import io.prometheus.client.Histogram
import misk.metrics.v2.PeakGauge
import misk.metrics.v2.ProvidedGauge
import misk.metrics.v3.MiskCounter
import misk.metrics.v3.MiskGauge
import misk.metrics.v3.MiskHistogram
import misk.metrics.v3.MiskPeakGauge
import misk.metrics.v3.MiskProvidedGauge

/**
 * [MetricsBackend] backed by Prometheus client library types, registered against a
 * [CollectorRegistry].
 */
class PrometheusMetricsBackend(private val registry: CollectorRegistry) : MetricsBackend {

  override fun createCounter(name: String, help: String, labelNames: List<String>): MiskCounter {
    val counter = Counter.build(name, help)
      .labelNames(*labelNames.toTypedArray())
      .register(registry)
    return PrometheusMiskCounter(counter)
  }

  override fun createGauge(name: String, help: String, labelNames: List<String>): MiskGauge {
    val gauge = Gauge.build(name, help)
      .labelNames(*labelNames.toTypedArray())
      .register(registry)
    return PrometheusMiskGauge(gauge)
  }

  override fun createPeakGauge(
    name: String,
    help: String,
    labelNames: List<String>,
  ): MiskPeakGauge {
    val peakGauge = PeakGauge.builder(name, help)
      .labelNames(*labelNames.toTypedArray())
      .register(registry)
    return PrometheusMiskPeakGauge(peakGauge)
  }

  override fun createProvidedGauge(
    name: String,
    help: String,
    labelNames: List<String>,
  ): MiskProvidedGauge {
    val providedGauge = ProvidedGauge.builder(name, help)
      .labelNames(*labelNames.toTypedArray())
      .register(registry)
    return PrometheusMiskProvidedGauge(providedGauge)
  }

  override fun createHistogram(
    name: String,
    help: String,
    labelNames: List<String>,
    buckets: List<Double>,
  ): MiskHistogram {
    val histogram = Histogram.build(name, help)
      .labelNames(*labelNames.toTypedArray())
      .buckets(*buckets.toDoubleArray())
      .register(registry)
    return PrometheusMiskHistogram(histogram)
  }
}

private class PrometheusMiskCounter(private val counter: Counter) : MiskCounter {
  override fun labels(vararg labelValues: String): MiskCounter.Child =
    PrometheusMiskCounterChild(counter.labels(*labelValues))

  private class PrometheusMiskCounterChild(private val child: Counter.Child) : MiskCounter.Child {
    override fun inc(amount: Double) = child.inc(amount)
  }
}

private class PrometheusMiskGauge(private val gauge: Gauge) : MiskGauge {
  override fun labels(vararg labelValues: String): MiskGauge.Child =
    PrometheusMiskGaugeChild(gauge.labels(*labelValues))

  private class PrometheusMiskGaugeChild(private val child: Gauge.Child) : MiskGauge.Child {
    override fun set(value: Double) = child.set(value)
    override fun inc(amount: Double) = child.inc(amount)
    override fun dec(amount: Double) = child.dec(amount)
  }
}

private class PrometheusMiskPeakGauge(private val peakGauge: PeakGauge) : MiskPeakGauge {
  override fun labels(vararg labelValues: String): MiskPeakGauge.Child =
    PrometheusMiskPeakGaugeChild(peakGauge.labels(*labelValues))

  private class PrometheusMiskPeakGaugeChild(
    private val child: PeakGauge.Child,
  ) : MiskPeakGauge.Child {
    override fun record(newValue: Double) = child.record(newValue)
  }
}

private class PrometheusMiskProvidedGauge(
  private val providedGauge: ProvidedGauge,
) : MiskProvidedGauge {
  override fun labels(vararg labelValues: String): MiskProvidedGauge.Child =
    PrometheusMiskProvidedGaugeChild(providedGauge.labels(*labelValues))

  private class PrometheusMiskProvidedGaugeChild(
    private val child: ProvidedGauge.Child,
  ) : MiskProvidedGauge.Child {
    override fun <T : Any> registerProvider(reference: T, provider: T.() -> Number) =
      child.registerProvider(reference, provider)
  }
}

private class PrometheusMiskHistogram(private val histogram: Histogram) : MiskHistogram {
  override fun labels(vararg labelValues: String): MiskHistogram.Child =
    PrometheusMiskHistogramChild(histogram.labels(*labelValues))

  private class PrometheusMiskHistogramChild(
    private val child: Histogram.Child,
  ) : MiskHistogram.Child {
    override fun observe(value: Double) = child.observe(value)
  }
}
