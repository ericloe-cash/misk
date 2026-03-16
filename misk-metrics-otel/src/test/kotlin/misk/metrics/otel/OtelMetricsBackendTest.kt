package misk.metrics.otel

import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OtelMetricsBackendTest {
  private lateinit var metricReader: InMemoryMetricReader
  private lateinit var backend: OtelMetricsBackend

  @BeforeEach
  fun setUp() {
    metricReader = InMemoryMetricReader.create()
    val meterProvider = SdkMeterProvider.builder()
      .registerMetricReader(metricReader)
      .build()
    val meter = meterProvider.get("test")
    backend = OtelMetricsBackend(meter)
  }

  @Test
  fun counter() {
    val counter = backend.createCounter("test_counter", "A test counter", listOf("action"))
    counter.labels("foo").inc()
    counter.labels("foo").inc(5.0)
    counter.labels("bar").inc()

    val metrics = metricReader.collectAllMetrics()
    val counterData = metrics.find { it.name == "test_counter" }
    assertThat(counterData).isNotNull
  }

  @Test
  fun histogram() {
    val histogram = backend.createHistogram(
      "test_histogram", "A test histogram", listOf("action"),
      listOf(10.0, 50.0, 100.0),
    )
    histogram.labels("foo").observe(25.0)
    histogram.labels("foo").observe(75.0)
    histogram.labels("bar").observe(5.0)

    val metrics = metricReader.collectAllMetrics()
    val histogramData = metrics.find { it.name == "test_histogram" }
    assertThat(histogramData).isNotNull
  }

  @Test
  fun gauge() {
    val gauge = backend.createGauge("test_gauge", "A test gauge", listOf("pool"))
    gauge.labels("main").inc(10.0)
    gauge.labels("main").dec(3.0)

    val metrics = metricReader.collectAllMetrics()
    val gaugeData = metrics.find { it.name == "test_gauge" }
    assertThat(gaugeData).isNotNull
  }

  @Test
  fun peakGauge() {
    val peakGauge = backend.createPeakGauge("test_peak", "A test peak gauge", listOf())
    peakGauge.labels().record(10.0)
    peakGauge.labels().record(50.0)
    peakGauge.labels().record(25.0) // lower than peak, should be ignored

    val metrics = metricReader.collectAllMetrics()
    val peakData = metrics.find { it.name == "test_peak" }
    assertThat(peakData).isNotNull
    // After collection, peak should reset
    val metricsAfterReset = metricReader.collectAllMetrics()
    val peakAfterReset = metricsAfterReset.find { it.name == "test_peak" }
    assertThat(peakAfterReset).isNotNull
  }

  @Test
  fun providedGauge() {
    val ref = object { var value = 42.0 }
    val providedGauge = backend.createProvidedGauge("test_provided", "A test provided gauge", listOf())
    providedGauge.labels().registerProvider(ref) { value }

    val metrics = metricReader.collectAllMetrics()
    val providedData = metrics.find { it.name == "test_provided" }
    assertThat(providedData).isNotNull
  }
}
