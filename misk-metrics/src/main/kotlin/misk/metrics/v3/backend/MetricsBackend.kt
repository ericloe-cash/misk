package misk.metrics.v3.backend

import misk.metrics.v3.MiskCounter
import misk.metrics.v3.MiskGauge
import misk.metrics.v3.MiskHistogram
import misk.metrics.v3.MiskPeakGauge
import misk.metrics.v3.MiskProvidedGauge

/**
 * SPI for metrics backend implementations. Each backend (Prometheus, OTel, Bridge) provides its
 * own implementation of this interface.
 */
interface MetricsBackend {
  fun createCounter(name: String, help: String, labelNames: List<String>): MiskCounter
  fun createGauge(name: String, help: String, labelNames: List<String>): MiskGauge
  fun createPeakGauge(name: String, help: String, labelNames: List<String>): MiskPeakGauge
  fun createProvidedGauge(name: String, help: String, labelNames: List<String>): MiskProvidedGauge
  fun createHistogram(
    name: String,
    help: String,
    labelNames: List<String>,
    buckets: List<Double>,
  ): MiskHistogram
}
