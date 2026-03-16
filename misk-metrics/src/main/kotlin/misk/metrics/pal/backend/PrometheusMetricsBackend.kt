package misk.metrics.pal.backend

import misk.annotation.ExperimentalMiskApi
import misk.metrics.v2.Metrics
import misk.metrics.v2.PeakGauge
import misk.metrics.v2.ProvidedGauge
import misk.metrics.pal.PalCounter
import misk.metrics.pal.PalGauge
import misk.metrics.pal.PalHistogram
import misk.metrics.pal.PalPeakGauge
import misk.metrics.pal.PalProvidedGauge

/**
 * [MetricsBackend] that delegates to [misk.metrics.v2.Metrics] for Prometheus-based metric
 * creation.
 */
@ExperimentalMiskApi
class PrometheusMetricsBackend(private val v2: Metrics) : MetricsBackend {

  override fun createCounter(name: String, help: String, labelNames: List<String>): PalCounter {
    val counter = v2.counter(name, help, labelNames)
    return object : PalCounter {
      override fun labels(vararg labelValues: String) = object : PalCounter.Child {
        override fun inc(amount: Double) = counter.labels(*labelValues).inc(amount)
      }
    }
  }

  override fun createGauge(name: String, help: String, labelNames: List<String>): PalGauge {
    val gauge = v2.gauge(name, help, labelNames)
    return object : PalGauge {
      override fun labels(vararg labelValues: String) = object : PalGauge.Child {
        override fun set(value: Double) = gauge.labels(*labelValues).set(value)
        override fun inc(amount: Double) = gauge.labels(*labelValues).inc(amount)
        override fun dec(amount: Double) = gauge.labels(*labelValues).dec(amount)
      }
    }
  }

  override fun createPeakGauge(
    name: String,
    help: String,
    labelNames: List<String>,
  ): PalPeakGauge {
    val peakGauge = v2.peakGauge(name, help, labelNames)
    return object : PalPeakGauge {
      override fun labels(vararg labelValues: String) = object : PalPeakGauge.Child {
        override fun record(newValue: Double) = peakGauge.labels(*labelValues).record(newValue)
      }
    }
  }

  override fun createProvidedGauge(
    name: String,
    help: String,
    labelNames: List<String>,
  ): PalProvidedGauge {
    val providedGauge = v2.providedGauge(name, help, labelNames)
    return object : PalProvidedGauge {
      override fun labels(vararg labelValues: String) = object : PalProvidedGauge.Child {
        override fun <T : Any> registerProvider(reference: T, provider: T.() -> Number) =
          providedGauge.labels(*labelValues).registerProvider(reference, provider)
      }
    }
  }

  override fun createHistogram(
    name: String,
    help: String,
    labelNames: List<String>,
    buckets: List<Double>,
  ): PalHistogram {
    val histogram = v2.histogram(name, help, labelNames, buckets)
    return object : PalHistogram {
      override fun labels(vararg labelValues: String) = object : PalHistogram.Child {
        override fun observe(value: Double) = histogram.labels(*labelValues).observe(value)
      }
    }
  }
}
