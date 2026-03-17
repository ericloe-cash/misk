@file:OptIn(ExperimentalMiskApi::class)

package misk.metrics.otel

import misk.annotation.ExperimentalMiskApi
import misk.metrics.pal.PalCounter
import misk.metrics.pal.PalGauge
import misk.metrics.pal.PalHistogram
import misk.metrics.pal.PalPeakGauge
import misk.metrics.pal.PalProvidedGauge
import misk.metrics.pal.PrometheusNameNormalizer
import misk.metrics.pal.backend.MetricsBackend

/**
 * Bridge [MetricsBackend] that writes all misk metrics to OTel (Mode 2 of the Prometheus-to-OTel
 * migration). This backend is the core of bridge mode: it takes each metric creation call and
 * produces up to two OTel instruments -- the mapped metric and optionally a canonical metric.
 *
 * ## Pipeline for each metric
 *
 * 1. The caller's [MetricNameMapper] runs on the original name. It can transform the name (e.g.,
 *    add a `cash_` prefix) or return `null` to drop the metric from OTel entirely.
 * 2. [PrometheusNameNormalizer] runs on the mapper output (e.g., strips `_total` suffix so OTel
 *    counter naming conventions are followed).
 * 3. The metric is written to OTel with the resulting name (unless dropped by step 1).
 * 4. **Independently** of steps 1-3, if the original metric name matches a [CanonicalMetricMapping]
 *    (registered via Guice multibinding), a second OTel instrument is created with the canonical
 *    OTel name and remapped label names. The canonical instrument is always created when a mapping
 *    exists -- the [MetricNameMapper] cannot drop or rename it.
 *
 * At runtime, every observation (e.g., `counter.labels("a").inc()`) fans out to both the mapped
 * child and the canonical child (if they exist). Either or both may be null if the metric was
 * dropped or has no canonical mapping.
 *
 * ## What is NOT affected
 *
 * App metrics created via `v2.Metrics` or `v1.Metrics` are not routed through this backend. Those
 * APIs are backed by [misk.metrics.PrometheusLegacyMetricsModule] and continue writing directly to
 * the Prometheus `CollectorRegistry`. Only metrics created through [PalMetrics] flow through here.
 *
 * ## Example
 *
 * Given a metric `histo_http_request_latency_ms` with a canonical mapping to
 * `http.server.request.duration`, bridge mode produces:
 * - An OTel histogram named according to the mapper + normalizer (e.g., `histo_http_request_latency_ms`)
 * - A second OTel histogram named `http.server.request.duration` with OTel semantic convention labels
 */
@ExperimentalMiskApi
class BridgeMetricsBackend(
  private val otel: MetricsBackend,
  private val canonicalMappings: Map<String, CanonicalMetricMapping>,
  private val nameMapper: MetricNameMapper = MetricNameMapper.IDENTITY,
) : MetricsBackend {

  constructor(
    otel: MetricsBackend,
    canonicalMappings: Set<CanonicalMetricMapping>,
    nameMapper: MetricNameMapper = MetricNameMapper.IDENTITY,
  ) : this(otel, canonicalMappings.associateBy { it.legacyName }, nameMapper)

  private fun mapName(name: String): String? {
    val mapped = nameMapper.map(name) ?: return null
    return PrometheusNameNormalizer.normalize(mapped)
  }

  override fun createCounter(name: String, help: String, labelNames: List<String>): PalCounter {
    val mappedName = mapName(name)
    val otelCounter = mappedName?.let { otel.createCounter(it, help, labelNames) }
    val canonical = canonicalMappings[name]
    val canonicalCounter = canonical?.let {
      otel.createCounter(it.canonicalName, help, it.remapLabelNames(labelNames))
    }
    return BridgeCounter(otelCounter, canonicalCounter, canonical, labelNames)
  }

  override fun createGauge(name: String, help: String, labelNames: List<String>): PalGauge {
    val mappedName = mapName(name)
    val otelGauge = mappedName?.let { otel.createGauge(it, help, labelNames) }
    val canonical = canonicalMappings[name]
    val canonicalGauge = canonical?.let {
      otel.createGauge(it.canonicalName, help, it.remapLabelNames(labelNames))
    }
    return BridgeGauge(otelGauge, canonicalGauge, canonical, labelNames)
  }

  override fun createPeakGauge(name: String, help: String, labelNames: List<String>): PalPeakGauge {
    val mappedName = mapName(name)
    val otelPeakGauge = mappedName?.let { otel.createPeakGauge(it, help, labelNames) }
    val canonical = canonicalMappings[name]
    val canonicalPeakGauge = canonical?.let {
      otel.createPeakGauge(it.canonicalName, help, it.remapLabelNames(labelNames))
    }
    return BridgePeakGauge(otelPeakGauge, canonicalPeakGauge, canonical, labelNames)
  }

  override fun createProvidedGauge(name: String, help: String, labelNames: List<String>): PalProvidedGauge {
    val mappedName = mapName(name)
    val otelProvidedGauge = mappedName?.let { otel.createProvidedGauge(it, help, labelNames) }
    val canonical = canonicalMappings[name]
    val canonicalProvidedGauge = canonical?.let {
      otel.createProvidedGauge(it.canonicalName, help, it.remapLabelNames(labelNames))
    }
    return BridgeProvidedGauge(otelProvidedGauge, canonicalProvidedGauge, canonical, labelNames)
  }

  override fun createHistogram(
    name: String,
    help: String,
    labelNames: List<String>,
    buckets: List<Double>,
  ): PalHistogram {
    val mappedName = mapName(name)
    val otelHistogram = mappedName?.let { otel.createHistogram(it, help, labelNames, buckets) }
    val canonical = canonicalMappings[name]
    val canonicalHistogram = canonical?.let {
      otel.createHistogram(it.canonicalName, help, it.remapLabelNames(labelNames), buckets)
    }
    return BridgeHistogram(otelHistogram, canonicalHistogram, canonical, labelNames)
  }
}

/**
 * Remaps label names according to the canonical mapping's [CanonicalMetricMapping.labelMapping].
 * Labels not present in the mapping pass through with their original names.
 */
private fun CanonicalMetricMapping.remapLabelNames(labelNames: List<String>): List<String> =
  labelNames.map { labelMapping[it] ?: it }

/**
 * Fans out counter increments to the mapped OTel counter and the canonical OTel counter (if any).
 * Either delegate may be null if the metric was dropped by the mapper or has no canonical mapping.
 */
private class BridgeCounter(
  private val otel: PalCounter?,
  private val canonical: PalCounter?,
  private val mapping: CanonicalMetricMapping?,
  private val labelNames: List<String>,
) : PalCounter {
  override fun labels(vararg labelValues: String) = object : PalCounter.Child {
    private val otelChild = otel?.labels(*labelValues)
    private val canonicalChild = canonical?.labels(*mapping.remapValues(labelNames, labelValues))
    override fun inc(amount: Double) {
      otelChild?.inc(amount)
      canonicalChild?.inc(amount)
    }
  }
}

/** Fans out gauge operations to the mapped and canonical OTel gauges. See [BridgeCounter]. */
private class BridgeGauge(
  private val otel: PalGauge?,
  private val canonical: PalGauge?,
  private val mapping: CanonicalMetricMapping?,
  private val labelNames: List<String>,
) : PalGauge {
  override fun labels(vararg labelValues: String) = object : PalGauge.Child {
    private val otelChild = otel?.labels(*labelValues)
    private val canonicalChild = canonical?.labels(*mapping.remapValues(labelNames, labelValues))
    override fun set(value: Double) { otelChild?.set(value); canonicalChild?.set(value) }
    override fun inc(amount: Double) { otelChild?.inc(amount); canonicalChild?.inc(amount) }
    override fun dec(amount: Double) { otelChild?.dec(amount); canonicalChild?.dec(amount) }
  }
}

/** Fans out peak gauge recordings to the mapped and canonical OTel peak gauges. See [BridgeCounter]. */
private class BridgePeakGauge(
  private val otel: PalPeakGauge?,
  private val canonical: PalPeakGauge?,
  private val mapping: CanonicalMetricMapping?,
  private val labelNames: List<String>,
) : PalPeakGauge {
  override fun labels(vararg labelValues: String) = object : PalPeakGauge.Child {
    private val otelChild = otel?.labels(*labelValues)
    private val canonicalChild = canonical?.labels(*mapping.remapValues(labelNames, labelValues))
    override fun record(newValue: Double) { otelChild?.record(newValue); canonicalChild?.record(newValue) }
  }
}

/** Fans out provider registrations to the mapped and canonical OTel provided gauges. See [BridgeCounter]. */
private class BridgeProvidedGauge(
  private val otel: PalProvidedGauge?,
  private val canonical: PalProvidedGauge?,
  private val mapping: CanonicalMetricMapping?,
  private val labelNames: List<String>,
) : PalProvidedGauge {
  override fun labels(vararg labelValues: String) = object : PalProvidedGauge.Child {
    private val otelChild = otel?.labels(*labelValues)
    private val canonicalChild = canonical?.labels(*mapping.remapValues(labelNames, labelValues))
    override fun <T : Any> registerProvider(reference: T, provider: T.() -> Number) {
      otelChild?.registerProvider(reference, provider)
      canonicalChild?.registerProvider(reference, provider)
    }
  }
}

/** Fans out histogram observations to the mapped and canonical OTel histograms. See [BridgeCounter]. */
private class BridgeHistogram(
  private val otel: PalHistogram?,
  private val canonical: PalHistogram?,
  private val mapping: CanonicalMetricMapping?,
  private val labelNames: List<String>,
) : PalHistogram {
  override fun labels(vararg labelValues: String) = object : PalHistogram.Child {
    private val otelChild = otel?.labels(*labelValues)
    private val canonicalChild = canonical?.labels(*mapping.remapValues(labelNames, labelValues))
    override fun observe(value: Double) { otelChild?.observe(value); canonicalChild?.observe(value) }
  }
}

/**
 * Returns label values for the canonical instrument. Values are returned as-is because label
 * remapping is positional: the canonical instrument was created with remapped label *names* (via
 * [remapLabelNames]), so the values at the same indices are already correct.
 */
private fun CanonicalMetricMapping?.remapValues(
  labelNames: List<String>,
  labelValues: Array<out String>,
): Array<out String> = labelValues
