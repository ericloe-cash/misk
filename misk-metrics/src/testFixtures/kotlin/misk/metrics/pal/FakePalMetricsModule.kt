package misk.metrics.pal

import misk.inject.KAbstractModule

/**
 * Test module that binds [PalMetrics] to [FakePalMetrics] for in-memory metric testing without any
 * backend dependency.
 */
class FakePalMetricsModule : KAbstractModule() {
  override fun configure() {
    bind<PalMetrics>().to<FakePalMetrics>()
    bind<FakePalMetrics>().asEagerSingleton()
  }
}
