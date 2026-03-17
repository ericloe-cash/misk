@file:OptIn(misk.annotation.ExperimentalMiskApi::class)

package misk.metrics.otel

import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import io.prometheus.client.CollectorRegistry
import jakarta.inject.Inject
import misk.inject.KAbstractModule
import misk.metrics.pal.PalMetrics
import misk.metrics.v2.Metrics
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Example showing how a caller sets up bridge mode and tests both OTel and legacy Prometheus
 * metrics.
 *
 * In bridge mode:
 * - Misk's internal metrics (via PalMetrics) go to OTel, with the caller's MetricNameMapper
 *   applied for non-canonical metrics
 * - Canonical metric mappings (via Guice multibinding) additionally write the OTel standard name
 * - App metrics using v2.Metrics go to Prometheus as they always have
 */
@MiskTest(startService = false)
class BridgeMetricsCallerExampleTest {

  @MiskTestModule
  val module = TestModule()

  /** Misk-internal metrics go through PalMetrics -> OTel. */
  @Inject lateinit var palMetrics: PalMetrics

  /** App metrics still use v2.Metrics -> Prometheus. */
  @Inject lateinit var v2Metrics: Metrics

  /** OTel reader for asserting on OTel metrics. */
  @Inject lateinit var otelReader: InMemoryMetricReader

  /** Prometheus registry for asserting on legacy app metrics. */
  @Inject lateinit var registry: CollectorRegistry

  @Test
  fun `misk metric with canonical mapping writes both original and canonical to OTel`() {
    // Simulate what MetricsInterceptor does internally
    val histogram = palMetrics.histogram(
      name = "histo_http_request_latency_ms",
      help = "count and duration in ms of incoming web requests",
      labelNames = listOf("action", "caller", "code"),
    )

    histogram.labels("MyAction", "my-peer", "200").observe(42.0)

    val otelMetrics = otelReader.collectAllMetrics()

    // Original name written to OTel (with caller's prefix via MetricNameMapper)
    assertThat(otelMetrics.find { it.name == "cash_histo_http_request_latency_ms" })
      .describedAs("Original metric name should be written to OTel with caller prefix")
      .isNotNull

    // Canonical OTel name also written (not affected by MetricNameMapper)
    assertThat(otelMetrics.find { it.name == "http.server.request.duration" })
      .describedAs("Canonical OTel metric should also be written")
      .isNotNull
  }

  @Test
  fun `misk metric without canonical mapping goes through caller mapper only`() {
    val gauge = palMetrics.gauge(
      name = "jetty_thread_pool_size",
      help = "Total threads in pool",
    )

    gauge.labels().set(50.0)

    val otelMetrics = otelReader.collectAllMetrics()

    // No canonical mapping for this metric — only the mapper-transformed name
    assertThat(otelMetrics.find { it.name == "cash_jetty_thread_pool_size" })
      .describedAs("Non-canonical metric should be written with caller prefix")
      .isNotNull
  }

  @Test
  fun `app metric using v2 Metrics goes to Prometheus unchanged`() {
    // Apps continue using v2.Metrics directly — this is NOT affected by bridge mode
    val appCounter = v2Metrics.counter(
      name = "my_app_requests_total",
      help = "App-level request counter",
      labelNames = listOf("endpoint"),
    )

    appCounter.labels("checkout").inc()
    appCounter.labels("checkout").inc()

    // Assert via Prometheus CollectorRegistry (the way apps test today)
    val sample = registry.metricFamilySamples().asSequence()
      .find { it.name == "my_app_requests" } // Prometheus strips _total
    assertThat(sample)
      .describedAs("App metric should be in Prometheus registry")
      .isNotNull

    // App metrics do NOT appear in OTel
    val otelMetrics = otelReader.collectAllMetrics()
    assertThat(otelMetrics.find { it.name == "my_app_requests_total" })
      .describedAs("App metric should NOT be in OTel")
      .isNull()
  }

  /**
   * Example test module showing how a caller configures bridge mode.
   *
   * Note: This does NOT use MiskTestingServiceModule because it transitively installs
   * MetricsModule which would conflict with BridgeMetricsModule's PalMetrics binding.
   * In a real service, you would replace the MetricsModule/PrometheusMetricsServiceModule
   * installation with BridgeMetricsModule.
   */
  class TestModule : KAbstractModule() {
    private val otelReader = InMemoryMetricReader.create()
    private val meterProvider = SdkMeterProvider.builder()
      .registerMetricReader(otelReader)
      .build()

    override fun configure() {
      // Install bridge mode with a name mapper that adds "cash_" prefix.
      // This installs PrometheusLegacyMetricsModule for v2.Metrics/CollectorRegistry.
      install(BridgeMetricsModule(nameMapper = MetricNameMapper { "cash_$it" }))

      // Install canonical mappings for misk's standard HTTP metrics
      install(MiskStandardMetricMappingsModule())

      // Provide the OTel Meter binding
      bind<Meter>().toInstance(meterProvider.get("my-service"))

      // Expose the reader for test assertions
      bind<InMemoryMetricReader>().toInstance(otelReader)
    }
  }
}
