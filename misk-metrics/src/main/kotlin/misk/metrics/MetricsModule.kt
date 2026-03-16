@file:OptIn(ExperimentalMiskApi::class)

package misk.metrics

import com.google.inject.Provider
import misk.annotation.ExperimentalMiskApi
import io.prometheus.client.CollectorRegistry
import jakarta.inject.Inject
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.metrics.pal.PalMetrics
import misk.metrics.pal.backend.PrometheusMetricsBackend

class MetricsModule : KAbstractModule() {
  override fun configure() {
    bind<CollectorRegistry>().toProvider(CollectorRegistryProvider::class.java).asSingleton()
    bind<Metrics>().toProvider(MetricsProvider::class.java).asSingleton()
    bind<misk.metrics.v2.Metrics>().toProvider(V2MetricsProvider::class.java).asSingleton()
    bind<PalMetrics>().toProvider(PalMetricsProvider::class.java).asSingleton()
  }

  /**
   * In order to make it possible to install this module multiple times, we make this binding not dependent on the
   * instance of [PrometheusMetricsClientModule] that created it.
   */
  internal class CollectorRegistryProvider @Inject constructor() : Provider<CollectorRegistry> {
    override fun get(): CollectorRegistry {
      return CollectorRegistry()
    }
  }

  internal class MetricsProvider @Inject constructor(private val v2Metrics: misk.metrics.v2.Metrics) :
    Provider<Metrics> {
    override fun get(): Metrics {
      return Metrics.factory(v2Metrics)
    }
  }

  internal class V2MetricsProvider @Inject constructor(private val registry: CollectorRegistry) :
    Provider<misk.metrics.v2.Metrics> {
    override fun get(): misk.metrics.v2.Metrics {
      return misk.metrics.v2.Metrics.factory(registry)
    }
  }

  internal class PalMetricsProvider @Inject constructor(private val v2Metrics: misk.metrics.v2.Metrics) :
    Provider<PalMetrics> {
    override fun get(): PalMetrics {
      return PalMetrics.factory(PrometheusMetricsBackend(v2Metrics))
    }
  }
}
