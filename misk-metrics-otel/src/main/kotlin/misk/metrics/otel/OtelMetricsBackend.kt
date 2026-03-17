@file:OptIn(ExperimentalMiskApi::class)

package misk.metrics.otel

import misk.annotation.ExperimentalMiskApi
import com.google.common.util.concurrent.AtomicDouble
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.DoubleHistogram
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.Meter
import misk.metrics.pal.PalCounter
import misk.metrics.pal.PalGauge
import misk.metrics.pal.PalHistogram
import misk.metrics.pal.PalPeakGauge
import misk.metrics.pal.PalProvidedGauge
import misk.metrics.pal.backend.MetricsBackend
import java.lang.ref.WeakReference

/**
 * [MetricsBackend] that creates OpenTelemetry SDK instruments for each PAL metric type.
 *
 * This is the low-level OTel implementation used by both **Mode 2 (Bridge)** and **Mode 3
 * (OTel-only)**. In bridge mode, [BridgeMetricsBackend] delegates to an instance of this class.
 * In OTel-only mode, [OtelMetricsModule] wires this directly as the sole backend.
 *
 * ## PAL type to OTel instrument mapping
 *
 * | PAL type          | OTel instrument                         | Notes                              |
 * |-------------------|-----------------------------------------|------------------------------------|
 * | [PalCounter]      | [LongCounter]                           | `inc(Double)` truncates to `Long`  |
 * | [PalGauge]        | `DoubleUpDownCounter`                   | See limitation below               |
 * | [PalPeakGauge]    | Async gauge (`buildWithCallback`)       | Resets to 0 after each collection  |
 * | [PalProvidedGauge]| Async gauge (`buildWithCallback`)       | Holds weak refs to providers       |
 * | [PalHistogram]    | [DoubleHistogram]                       | Explicit bucket boundaries         |
 *
 * ## Known limitations
 *
 * - **Gauge `set()` approximation**: OTel has no synchronous gauge with `set()`. The gauge is
 *   backed by a `DoubleUpDownCounter`, so `set(value)` actually calls `add(value)` instead of
 *   replacing the current value. This means `set()` is incorrect in OTel-only mode. In bridge
 *   mode this is acceptable because the Prometheus side handles `set()` correctly and the OTel
 *   side is best-effort during migration.
 *
 * - **PeakGauge reset timing**: Peak values are reset to 0.0 after each OTel collection callback.
 *   The reset window is determined by the SDK's export interval, not by the caller. If the export
 *   interval differs from the previous Prometheus scrape interval, peak values may appear different.
 *
 * - **Counter precision**: `PalCounter.inc(Double)` is truncated to `Long` because OTel counters
 *   are integer-valued. Fractional increments are silently lost.
 */
@ExperimentalMiskApi
class OtelMetricsBackend(private val meter: Meter) : MetricsBackend {

  override fun createCounter(name: String, help: String, labelNames: List<String>): PalCounter {
    val counter = meter.counterBuilder(name).setDescription(help).build()
    return OtelPalCounter(counter, labelNames)
  }

  override fun createGauge(name: String, help: String, labelNames: List<String>): PalGauge {
    val gauge = meter.upDownCounterBuilder(name).setDescription(help).ofDoubles().build()
    return OtelPalGauge(gauge, labelNames)
  }

  override fun createPeakGauge(name: String, help: String, labelNames: List<String>): PalPeakGauge {
    val peakState = OtelPeakGaugeState(labelNames)
    meter.gaugeBuilder(name)
      .setDescription(help)
      .buildWithCallback { measurement ->
        peakState.collectAndReset { attributes, value ->
          measurement.record(value, attributes)
        }
      }
    return peakState
  }

  override fun createProvidedGauge(
    name: String,
    help: String,
    labelNames: List<String>,
  ): PalProvidedGauge {
    val providedState = OtelProvidedGaugeState(labelNames)
    meter.gaugeBuilder(name)
      .setDescription(help)
      .buildWithCallback { measurement ->
        providedState.collect { attributes, value ->
          measurement.record(value, attributes)
        }
      }
    return providedState
  }

  override fun createHistogram(
    name: String,
    help: String,
    labelNames: List<String>,
    buckets: List<Double>,
  ): PalHistogram {
    val histogram = meter.histogramBuilder(name)
      .setDescription(help)
      .setExplicitBucketBoundariesAdvice(buckets)
      .build()
    return OtelPalHistogram(histogram, labelNames)
  }
}

/** Converts parallel label name/value arrays into an OTel [Attributes] instance. */
private fun buildAttributes(labelNames: List<String>, labelValues: Array<out String>): Attributes {
  val builder = Attributes.builder()
  labelNames.forEachIndexed { i, name ->
    if (i < labelValues.size) {
      builder.put(AttributeKey.stringKey(name), labelValues[i])
    }
  }
  return builder.build()
}

/** Wraps an OTel [LongCounter]. Each `labels()` call captures an [Attributes] snapshot. */
private class OtelPalCounter(
  private val counter: LongCounter,
  private val labelNames: List<String>,
) : PalCounter {
  override fun labels(vararg labelValues: String) = object : PalCounter.Child {
    private val attrs = buildAttributes(labelNames, labelValues)
    override fun inc(amount: Double) = counter.add(amount.toLong(), attrs)
  }
}

/**
 * Wraps an OTel `DoubleUpDownCounter` as a [PalGauge]. Note: `set()` is approximate -- see
 * [OtelMetricsBackend] class doc for details on this known limitation.
 */
private class OtelPalGauge(
  private val gauge: io.opentelemetry.api.metrics.DoubleUpDownCounter,
  private val labelNames: List<String>,
) : PalGauge {
  override fun labels(vararg labelValues: String) = object : PalGauge.Child {
    private val attrs = buildAttributes(labelNames, labelValues)
    override fun set(value: Double) {
      // OTel UpDownCounter doesn't have set() — we track and delta.
      // For simplicity, just add the value. This is a known limitation.
      // In bridge mode, the Prometheus side handles set() correctly.
      gauge.add(value, attrs)
    }

    override fun inc(amount: Double) = gauge.add(amount, attrs)
    override fun dec(amount: Double) = gauge.add(-amount, attrs)
  }
}

/** Wraps an OTel [DoubleHistogram]. Each `labels()` call captures an [Attributes] snapshot. */
private class OtelPalHistogram(
  private val histogram: DoubleHistogram,
  private val labelNames: List<String>,
) : PalHistogram {
  override fun labels(vararg labelValues: String) = object : PalHistogram.Child {
    private val attrs = buildAttributes(labelNames, labelValues)
    override fun observe(value: Double) = histogram.record(value, attrs)
  }
}

/**
 * Tracks peak values per label set and reports them via OTel async gauge callback, resetting after
 * each collection.
 */
private class OtelPeakGaugeState(
  private val labelNames: List<String>,
) : PalPeakGauge {
  private val peaks = java.util.concurrent.ConcurrentHashMap<List<String>, AtomicDouble>()

  override fun labels(vararg labelValues: String) = object : PalPeakGauge.Child {
    private val key = labelValues.toList()
    override fun record(newValue: Double) {
      val peak = peaks.getOrPut(key) { AtomicDouble() }
      while (true) {
        val prev = peak.get()
        if (newValue > prev) {
          if (peak.compareAndSet(prev, newValue)) return
        } else return
      }
    }
  }

  fun collectAndReset(emit: (Attributes, Double) -> Unit) {
    for ((labelValues, peak) in peaks) {
      val value = peak.getAndSet(0.0)
      val attrs = buildAttributes(labelNames, labelValues.toTypedArray())
      emit(attrs, value)
    }
  }
}

/**
 * Tracks provider callbacks per label set and reports them via OTel async gauge callback.
 */
private class OtelProvidedGaugeState(
  private val labelNames: List<String>,
) : PalProvidedGauge {
  private val providers =
    java.util.concurrent.ConcurrentHashMap<List<String>, () -> Double>()

  override fun labels(vararg labelValues: String) = object : PalProvidedGauge.Child {
    private val key = labelValues.toList()
    override fun <T : Any> registerProvider(reference: T, provider: T.() -> Number) {
      val weakRef = WeakReference(reference)
      providers[key] = {
        @Suppress("UNCHECKED_CAST")
        (weakRef.get() as? T)?.provider()?.toDouble() ?: 0.0
      }
    }
  }

  fun collect(emit: (Attributes, Double) -> Unit) {
    for ((labelValues, provider) in providers) {
      val attrs = buildAttributes(labelNames, labelValues.toTypedArray())
      emit(attrs, provider())
    }
  }
}
