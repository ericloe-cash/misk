package misk.metrics.otel

/**
 * Defines the legacy Prometheus name mappings for misk-owned standard metrics. These are used in
 * bridge mode to dual-write the OTel canonical metric alongside the legacy Prometheus name.
 *
 * When misk interceptors create metrics via PalMetrics, they use the legacy names directly
 * (e.g., `histo_http_request_latency_ms`). In a future migration to OTel-canonical names, this
 * mapping would be used by the bridge to produce both versions from a single observation.
 */
object MiskStandardMetricMappings {
  /**
   * Map of OTel semantic convention metric name to its legacy Prometheus equivalent.
   * Currently a reference for future use — the interceptors still use legacy names directly.
   */
  val mappings = mapOf(
    // Inbound HTTP
    "http.server.request.duration" to LegacyMetricMapping(
      legacyName = "histo_http_request_latency_ms",
      labelMapping = mapOf(
        "http.route" to "action",
        "server.address" to "caller",
        "http.response.status_code" to "code",
      ),
    ),
    // Outbound HTTP client
    "http.client.request.duration" to LegacyMetricMapping(
      legacyName = "histo_client_http_request_latency_ms",
      labelMapping = mapOf(
        "http.route" to "action",
        "http.response.status_code" to "code",
      ),
    ),
  )
}

data class LegacyMetricMapping(
  val legacyName: String,
  val labelMapping: Map<String, String> = emptyMap(),
)
