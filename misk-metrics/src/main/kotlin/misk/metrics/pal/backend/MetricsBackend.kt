package misk.metrics.pal.backend

import misk.metrics.pal.PalCounter
import misk.metrics.pal.PalGauge
import misk.metrics.pal.PalHistogram
import misk.metrics.pal.PalPeakGauge
import misk.metrics.pal.PalProvidedGauge

/**
 * SPI for metrics backend implementations. Each backend (Prometheus, OTel, Bridge) provides its
 * own implementation of this interface.
 */
interface MetricsBackend {
  fun createCounter(name: String, help: String, labelNames: List<String>): PalCounter
  fun createGauge(name: String, help: String, labelNames: List<String>): PalGauge
  fun createPeakGauge(name: String, help: String, labelNames: List<String>): PalPeakGauge
  fun createProvidedGauge(name: String, help: String, labelNames: List<String>): PalProvidedGauge
  fun createHistogram(
    name: String,
    help: String,
    labelNames: List<String>,
    buckets: List<Double>,
  ): PalHistogram
}
