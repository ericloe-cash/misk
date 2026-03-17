@file:OptIn(ExperimentalMiskApi::class)

package misk.metrics.otel

import com.google.inject.Provides
import io.opentelemetry.api.metrics.Meter
import jakarta.inject.Singleton
import misk.annotation.ExperimentalMiskApi
import misk.inject.KAbstractModule
import misk.metrics.pal.PalMetrics

/**
 * OTel-only metrics module -- **Mode 3** of the Prometheus-to-OTel migration.
 *
 * Use this module when all downstream consumers (dashboards, alerts, SLOs) have been migrated to
 * read from OTel and there is no longer a need to emit Prometheus metrics. This is the end-state
 * target of the migration.
 *
 * ## What it does
 *
 * - Binds [PalMetrics] backed by [OtelMetricsBackend], so all metrics created through the PAL
 *   abstraction are written directly to OpenTelemetry.
 * - Does **not** bind `misk.metrics.v2.Metrics`, `misk.metrics.v1.Metrics`, or Prometheus
 *   `CollectorRegistry`. Injecting any of those will fail at provision time, which surfaces
 *   leftover Prometheus dependencies as compile/startup errors rather than silent behavior changes.
 *
 * ## Prerequisites
 *
 * - The caller must provide a Guice binding for [Meter] (typically from an OTel SDK setup module).
 * - All metric consumers must be reading from OTel. If any dashboard or alert still reads
 *   Prometheus, use [BridgeMetricsModule] (Mode 2) instead.
 *
 * ## Known limitations
 *
 * See [OtelMetricsBackend] for limitations of the OTel instrument mappings, particularly the
 * gauge `set()` approximation which is more impactful in this mode since there is no Prometheus
 * fallback.
 */
@ExperimentalMiskApi
class OtelMetricsModule : KAbstractModule() {
  override fun configure() {
    requireBinding<Meter>()

    // v2.Metrics and v1.Metrics are intentionally not bound — injecting them is an error.
    // This forces consumers to use PalMetrics or OTel SDK directly.
  }

  @Provides @Singleton
  fun providePalMetrics(meter: Meter): PalMetrics =
    PalMetrics.factory(OtelMetricsBackend(meter))
}
