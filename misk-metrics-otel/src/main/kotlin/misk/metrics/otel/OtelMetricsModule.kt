package misk.metrics.otel

import com.google.inject.Provides
import io.opentelemetry.api.metrics.Meter
import jakarta.inject.Singleton
import misk.inject.KAbstractModule
import misk.metrics.pal.PalMetrics

/**
 * OTel-only metrics module (Mode 3). Binds [PalMetrics] backed by [OtelMetricsBackend].
 * Injecting `misk.metrics.v2.Metrics` or `CollectorRegistry` will fail at provision time.
 *
 * The caller must provide a binding for [Meter].
 */
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
