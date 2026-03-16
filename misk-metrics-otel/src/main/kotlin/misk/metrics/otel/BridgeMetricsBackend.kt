package misk.metrics.otel

import misk.metrics.pal.MetricNameTransformer
import misk.metrics.pal.PalCounter
import misk.metrics.pal.PalGauge
import misk.metrics.pal.PalHistogram
import misk.metrics.pal.PalPeakGauge
import misk.metrics.pal.PalProvidedGauge
import misk.metrics.pal.PrometheusNameNormalizer
import misk.metrics.pal.backend.MetricsBackend

/**
 * Bridge [MetricsBackend] that dual-writes to both OTel (primary) and Prometheus (legacy).
 * Every recording operation writes to both backends. The [nameTransformer] is applied to metric
 * names on the Prometheus side for pipeline-specific conventions (e.g., adding a prefix).
 *
 * Standard metric name mappings (misk-owned metrics like `histo_http_request_latency_ms`) are
 * handled by the caller via [MiskStandardMetricMappings] which produces the legacy name.
 */
class BridgeMetricsBackend(
  private val otel: MetricsBackend,
  private val prometheus: MetricsBackend,
  private val nameTransformer: MetricNameTransformer = MetricNameTransformer.IDENTITY,
) : MetricsBackend {

  private fun legacyName(name: String): String =
    nameTransformer.transform(PrometheusNameNormalizer.normalize(name))

  override fun createCounter(name: String, help: String, labelNames: List<String>): PalCounter {
    val otelCounter = otel.createCounter(name, help, labelNames)
    val promCounter = prometheus.createCounter(legacyName(name), help, labelNames)
    return DualWriteCounter(otelCounter, promCounter)
  }

  override fun createGauge(name: String, help: String, labelNames: List<String>): PalGauge {
    val otelGauge = otel.createGauge(name, help, labelNames)
    val promGauge = prometheus.createGauge(legacyName(name), help, labelNames)
    return DualWriteGauge(otelGauge, promGauge)
  }

  override fun createPeakGauge(
    name: String,
    help: String,
    labelNames: List<String>,
  ): PalPeakGauge {
    val otelPeakGauge = otel.createPeakGauge(name, help, labelNames)
    val promPeakGauge = prometheus.createPeakGauge(legacyName(name), help, labelNames)
    return DualWritePeakGauge(otelPeakGauge, promPeakGauge)
  }

  override fun createProvidedGauge(
    name: String,
    help: String,
    labelNames: List<String>,
  ): PalProvidedGauge {
    val otelProvidedGauge = otel.createProvidedGauge(name, help, labelNames)
    val promProvidedGauge = prometheus.createProvidedGauge(legacyName(name), help, labelNames)
    return DualWriteProvidedGauge(otelProvidedGauge, promProvidedGauge)
  }

  override fun createHistogram(
    name: String,
    help: String,
    labelNames: List<String>,
    buckets: List<Double>,
  ): PalHistogram {
    val otelHistogram = otel.createHistogram(name, help, labelNames, buckets)
    val promHistogram = prometheus.createHistogram(legacyName(name), help, labelNames, buckets)
    return DualWriteHistogram(otelHistogram, promHistogram)
  }
}

private class DualWriteCounter(
  private val otel: PalCounter,
  private val prom: PalCounter,
) : PalCounter {
  override fun labels(vararg labelValues: String) = object : PalCounter.Child {
    private val otelChild = otel.labels(*labelValues)
    private val promChild = prom.labels(*labelValues)
    override fun inc(amount: Double) {
      otelChild.inc(amount)
      promChild.inc(amount)
    }
  }
}

private class DualWriteGauge(
  private val otel: PalGauge,
  private val prom: PalGauge,
) : PalGauge {
  override fun labels(vararg labelValues: String) = object : PalGauge.Child {
    private val otelChild = otel.labels(*labelValues)
    private val promChild = prom.labels(*labelValues)
    override fun set(value: Double) { otelChild.set(value); promChild.set(value) }
    override fun inc(amount: Double) { otelChild.inc(amount); promChild.inc(amount) }
    override fun dec(amount: Double) { otelChild.dec(amount); promChild.dec(amount) }
  }
}

private class DualWritePeakGauge(
  private val otel: PalPeakGauge,
  private val prom: PalPeakGauge,
) : PalPeakGauge {
  override fun labels(vararg labelValues: String) = object : PalPeakGauge.Child {
    private val otelChild = otel.labels(*labelValues)
    private val promChild = prom.labels(*labelValues)
    override fun record(newValue: Double) { otelChild.record(newValue); promChild.record(newValue) }
  }
}

private class DualWriteProvidedGauge(
  private val otel: PalProvidedGauge,
  private val prom: PalProvidedGauge,
) : PalProvidedGauge {
  override fun labels(vararg labelValues: String) = object : PalProvidedGauge.Child {
    private val otelChild = otel.labels(*labelValues)
    private val promChild = prom.labels(*labelValues)
    override fun <T : Any> registerProvider(reference: T, provider: T.() -> Number) {
      otelChild.registerProvider(reference, provider)
      promChild.registerProvider(reference, provider)
    }
  }
}

private class DualWriteHistogram(
  private val otel: PalHistogram,
  private val prom: PalHistogram,
) : PalHistogram {
  override fun labels(vararg labelValues: String) = object : PalHistogram.Child {
    private val otelChild = otel.labels(*labelValues)
    private val promChild = prom.labels(*labelValues)
    override fun observe(value: Double) { otelChild.observe(value); promChild.observe(value) }
  }
}
