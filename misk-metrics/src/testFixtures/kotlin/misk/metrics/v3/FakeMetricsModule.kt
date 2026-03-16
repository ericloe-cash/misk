package misk.metrics.v3

import misk.inject.KAbstractModule

/**
 * Test module that binds [Metrics] to [FakeMetrics] for in-memory metric testing without any
 * backend dependency.
 */
class FakeMetricsModule : KAbstractModule() {
  override fun configure() {
    bind<Metrics>().to<FakeMetrics>()
    bind<FakeMetrics>().asEagerSingleton()
  }
}
