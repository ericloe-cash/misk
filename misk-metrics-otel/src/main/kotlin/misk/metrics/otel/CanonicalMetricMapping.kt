@file:OptIn(ExperimentalMiskApi::class)

package misk.metrics.otel

import misk.annotation.ExperimentalMiskApi

/**
 * Defines a mapping from a legacy metric name to its OTel canonical equivalent. Register these via
 * Guice multibinding so that mappings live close to the code that defines the metric.
 *
 * In bridge mode, when a metric with [legacyName] is observed, the bridge will additionally write
 * to OTel using [canonicalName] with labels remapped according to [labelMapping]. The caller
 * cannot intercept or transform canonical metrics.
 *
 * Example:
 * ```kotlin
 * multibind<CanonicalMetricMapping>().toInstance(
 *   CanonicalMetricMapping(
 *     legacyName = "histo_http_request_latency_ms",
 *     canonicalName = "http.server.request.duration",
 *     labelMapping = mapOf("action" to "http.route", "caller" to "server.address", "code" to "http.response.status_code"),
 *   )
 * )
 * ```
 */
@ExperimentalMiskApi
data class CanonicalMetricMapping(
  /** The legacy metric name as used in misk call sites (e.g., `histo_http_request_latency_ms`). */
  val legacyName: String,
  /** The OTel semantic convention name (e.g., `http.server.request.duration`). */
  val canonicalName: String,
  /** Maps legacy label names to OTel attribute names. */
  val labelMapping: Map<String, String> = emptyMap(),
)
