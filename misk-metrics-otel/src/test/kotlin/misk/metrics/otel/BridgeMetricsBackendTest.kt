@file:OptIn(misk.annotation.ExperimentalMiskApi::class)

package misk.metrics.otel

import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BridgeMetricsBackendTest {
  private lateinit var metricReader: InMemoryMetricReader
  private lateinit var bridge: BridgeMetricsBackend

  @BeforeEach
  fun setUp() {
    metricReader = InMemoryMetricReader.create()
    val meterProvider = SdkMeterProvider.builder()
      .registerMetricReader(metricReader)
      .build()
    val meter = meterProvider.get("test")

    bridge = BridgeMetricsBackend(
      otel = OtelMetricsBackend(meter),
      canonicalMappings = setOf(
        CanonicalMetricMapping(
          legacyName = "histo_http_request_latency_ms",
          canonicalName = "http.server.request.duration",
          labelMapping = mapOf("action" to "http.route", "code" to "http.response.status_code"),
        ),
      ),
      nameMapper = MetricNameMapper { "cash_$it" },
    )
  }

  @Test
  fun counterWritesToOtelWithMappedName() {
    val counter = bridge.createCounter("my_counter", "test", listOf("action"))
    counter.labels("foo").inc()
    counter.labels("foo").inc(3.0)

    val otelMetrics = metricReader.collectAllMetrics()
    assertThat(otelMetrics.find { it.name == "cash_my_counter" }).isNotNull
  }

  @Test
  fun histogramWithCanonicalMapping() {
    val histogram = bridge.createHistogram(
      "histo_http_request_latency_ms", "test", listOf("action", "code"), listOf(10.0, 50.0),
    )
    histogram.labels("MyAction", "200").observe(25.0)

    val otelMetrics = metricReader.collectAllMetrics()

    // Original name goes through mapper
    assertThat(otelMetrics.find { it.name == "cash_histo_http_request_latency_ms" }).isNotNull

    // Canonical name is also written (not affected by mapper)
    assertThat(otelMetrics.find { it.name == "http.server.request.duration" }).isNotNull
  }

  @Test
  fun mapperCanDropMetric() {
    val reader = InMemoryMetricReader.create()
    val dropBridge = BridgeMetricsBackend(
      otel = OtelMetricsBackend(
        SdkMeterProvider.builder().registerMetricReader(reader).build().get("test2")
      ),
      canonicalMappings = emptySet(),
      nameMapper = MetricNameMapper { null },
    )

    val counter = dropBridge.createCounter("dropped_counter", "test", listOf())
    counter.labels().inc()

    val otelMetrics = reader.collectAllMetrics()
    assertThat(otelMetrics.find { it.name == "dropped_counter" }).isNull()
  }

  @Test
  fun canonicalMappingStillWritesWhenMapperDrops() {
    val reader = InMemoryMetricReader.create()
    val dropBridge = BridgeMetricsBackend(
      otel = OtelMetricsBackend(
        SdkMeterProvider.builder().registerMetricReader(reader).build().get("test3")
      ),
      canonicalMappings = setOf(
        CanonicalMetricMapping(
          legacyName = "histo_http_request_latency_ms",
          canonicalName = "http.server.request.duration",
          labelMapping = mapOf("action" to "http.route"),
        ),
      ),
      nameMapper = MetricNameMapper { null },
    )

    val histogram = dropBridge.createHistogram(
      "histo_http_request_latency_ms", "test", listOf("action"), listOf(10.0),
    )
    histogram.labels("MyAction").observe(25.0)

    val otelMetrics = reader.collectAllMetrics()
    assertThat(otelMetrics.find { it.name == "histo_http_request_latency_ms" }).isNull()
    assertThat(otelMetrics.find { it.name == "http.server.request.duration" }).isNotNull
  }

  @Test
  fun totalSuffixStripped() {
    val counter = bridge.createCounter("request_total", "test", listOf())
    counter.labels().inc()

    val otelMetrics = metricReader.collectAllMetrics()
    assertThat(otelMetrics.find { it.name == "cash_request" }).isNotNull
  }
}
