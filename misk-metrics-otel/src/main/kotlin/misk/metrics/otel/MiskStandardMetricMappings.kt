@file:OptIn(ExperimentalMiskApi::class)

package misk.metrics.otel

import misk.annotation.ExperimentalMiskApi
import misk.inject.KAbstractModule

/**
 * Installs canonical OTel metric mappings for misk-owned standard metrics (e.g., HTTP request
 * latency). Each mapping is registered as a Guice multibinding of [CanonicalMetricMapping].
 *
 * In bridge mode, these cause [BridgeMetricsBackend] to additionally write the OTel semantic
 * convention metric (with remapped labels) alongside the original legacy metric name. The caller's
 * [MetricNameMapper] cannot intercept or transform canonical metrics -- they are always written
 * when a mapping exists.
 *
 * ## How to add new mappings
 *
 * Mappings should live close to the code that defines each metric. For misk-owned metrics, add
 * them here. For app-specific metrics, multibind [CanonicalMetricMapping] in your own module:
 *
 * ```kotlin
 * multibind<CanonicalMetricMapping>().toInstance(
 *   CanonicalMetricMapping(
 *     legacyName = "my_app_request_count_total",
 *     canonicalName = "my_app.request.count",
 *     labelMapping = mapOf("env" to "deployment.environment"),
 *   )
 * )
 * ```
 *
 * ## Usage
 *
 * Install this module alongside [BridgeMetricsModule]:
 *
 * ```kotlin
 * install(BridgeMetricsModule(nameMapper = myMapper))
 * install(MiskStandardMetricMappingsModule())
 * ```
 */
@ExperimentalMiskApi
class MiskStandardMetricMappingsModule : KAbstractModule() {
  override fun configure() {
    // Inbound HTTP request latency
    multibind<CanonicalMetricMapping>().toInstance(
      CanonicalMetricMapping(
        legacyName = "histo_http_request_latency_ms",
        canonicalName = "http.server.request.duration",
        labelMapping = mapOf(
          "action" to "http.route",
          "caller" to "server.address",
          "code" to "http.response.status_code",
        ),
      )
    )

    // Outbound HTTP client latency
    multibind<CanonicalMetricMapping>().toInstance(
      CanonicalMetricMapping(
        legacyName = "histo_client_http_request_latency_ms",
        canonicalName = "http.client.request.duration",
        labelMapping = mapOf(
          "action" to "http.route",
          "code" to "http.response.status_code",
        ),
      )
    )
  }
}
