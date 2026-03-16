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
 * Bridge [MetricsBackend] that writes all misk metrics to OTel. The pipeline for each metric:
 *
 * 1. The caller's [MetricNameMapper] runs — can transform the name or return null to drop it.
 * 2. [PrometheusNameNormalizer] runs on the mapper output (e.g., `_total` stripping).
 * 3. The metric is written to OTel with the resulting name (unless dropped).
 * 4. If the metric has a [CanonicalMetricMapping], the canonical OTel version is additionally
 *    written with remapped labels. The caller cannot intercept canonical metrics.
 *
 * App metrics (using `v2.Metrics` directly) are unaffected — they still go to Prometheus.
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

/** Remap label names according to the canonical mapping. Unmapped labels pass through as-is. */
private fun CanonicalMetricMapping.remapLabelNames(labelNames: List<String>): List<String> =
  labelNames.map { labelMapping[it] ?: it }


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

private fun CanonicalMetricMapping?.remapValues(
  labelNames: List<String>,
  labelValues: Array<out String>,
): Array<out String> = labelValues // Values are positional, names are remapped at creation time
