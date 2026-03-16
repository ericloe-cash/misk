@file:OptIn(misk.annotation.ExperimentalMiskApi::class)

package misk.metrics.otel

import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import io.prometheus.client.CollectorRegistry
import misk.metrics.pal.MetricNameTransformer
import misk.metrics.pal.backend.PrometheusMetricsBackend
import misk.metrics.v2.Metrics
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BridgeMetricsBackendTest {
  private lateinit var metricReader: InMemoryMetricReader
  private lateinit var registry: CollectorRegistry
  private lateinit var bridge: BridgeMetricsBackend

  @BeforeEach
  fun setUp() {
    metricReader = InMemoryMetricReader.create()
    val meterProvider = SdkMeterProvider.builder()
      .registerMetricReader(metricReader)
      .build()
    val meter = meterProvider.get("test")

    registry = CollectorRegistry()
    val v2Metrics = Metrics.factory(registry)

    bridge = BridgeMetricsBackend(
      otel = OtelMetricsBackend(meter),
      prometheus = PrometheusMetricsBackend(v2Metrics),
      nameTransformer = MetricNameTransformer { "cash_$it" },
    )
  }

  @Test
  fun counterDualWrites() {
    val counter = bridge.createCounter("my_counter", "test", listOf("action"))
    counter.labels("foo").inc()
    counter.labels("foo").inc(3.0)

    // Verify OTel side
    val otelMetrics = metricReader.collectAllMetrics()
    assertThat(otelMetrics.find { it.name == "my_counter" }).isNotNull

    // Verify Prometheus side with transformed name
    val promSample = registry.metricFamilySamples().asSequence()
      .find { it.name == "cash_my_counter" }
    assertThat(promSample).isNotNull
  }

  @Test
  fun histogramDualWrites() {
    val histogram = bridge.createHistogram(
      "my_histo", "test", listOf("action"), listOf(10.0, 50.0, 100.0),
    )
    histogram.labels("bar").observe(25.0)

    // Verify OTel side
    val otelMetrics = metricReader.collectAllMetrics()
    assertThat(otelMetrics.find { it.name == "my_histo" }).isNotNull

    // Verify Prometheus side with transformed name
    val promSample = registry.metricFamilySamples().asSequence()
      .find { it.name == "cash_my_histo" }
    assertThat(promSample).isNotNull
  }

  @Test
  fun nameTransformerApplied() {
    bridge.createCounter("request_total", "test", listOf())
      .labels().inc()

    // _total should be stripped by PrometheusNameNormalizer, then prefixed by transformer
    val promSample = registry.metricFamilySamples().asSequence()
      .find { it.name == "cash_request" }
    assertThat(promSample).isNotNull
  }
}
