@file:OptIn(ExperimentalMiskApi::class)

package misk.metrics.otel

import misk.annotation.ExperimentalMiskApi
import misk.inject.KAbstractModule

/**
 * Installs canonical OTel metric mappings for misk-owned standard metrics. Each mapping is
 * registered as a Guice multibinding of [CanonicalMetricMapping].
 *
 * In bridge mode, these cause the bridge to additionally write the OTel canonical metric (with
 * remapped labels) alongside the original metric name. The caller cannot intercept or transform
 * canonical metrics.
 *
 * Install this module alongside [BridgeMetricsModule] to get canonical OTel names for misk's
 * standard HTTP metrics.
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
