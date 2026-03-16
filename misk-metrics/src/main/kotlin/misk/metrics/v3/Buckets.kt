package misk.metrics.v3

/**
 * Default set of buckets which assumes the value is in milliseconds (ms).
 *
 * Contains 58 buckets ranging from 1ms to 1hr. Adapted from the default M3 buckets.
 * https://github.com/m3db/m3/blob/v1.1.0/src/x/instrument/methods.go#L57-L83
 */
val defaultBuckets =
  listOf(
    1.0, 2.0, 4.0, 6.0, 8.0, 10.0, 20.0, 40.0, 60.0, 80.0,
    100.0, 200.0, 400.0, 600.0, 800.0,
    1000.0, 1500.0, 2000.0, 2500.0, 3000.0, 3500.0, 4000.0, 4500.0, 5000.0, 5500.0,
    6000.0, 6500.0, 7000.0, 7500.0, 8000.0, 8500.0, 9000.0, 9500.0,
    10000.0, 15000.0, 20000.0, 25000.0, 30000.0, 35000.0, 40000.0, 45000.0, 50000.0, 55000.0,
    60000.0, 150000.0, 300000.0, 450000.0, 600000.0, 900000.0,
    1200000.0, 1500000.0, 1800000.0, 2100000.0, 2400000.0, 2700000.0, 3000000.0, 3300000.0,
    3600000.0,
  )

/**
 * Sparse set of buckets which assumes the value is in milliseconds (ms).
 *
 * Contains 21 buckets ranging from 1ms to 8m. Adapted from the default M3 buckets.
 * https://github.com/m3db/m3/blob/v1.1.0/src/x/instrument/methods.go#L85-L147
 */
val defaultSparseBuckets =
  listOf(
    1.0, 5.0, 10.0, 25.0, 50.0, 75.0,
    100.0, 250.0, 500.0, 750.0,
    1000.0, 2500.0, 5000.0, 7500.0,
    10000.0, 25000.0, 50000.0, 75000.0,
    100000.0, 250000.0, 500000.0,
  )

/** Generate a list of upper bounds of buckets for a histogram with a linear sequence. */
fun linearBuckets(start: Double, width: Double, count: Int): List<Double> {
  return generateSequence(start) { it + width }.take(count).toList()
}

/** Generate a list of upper bounds of buckets for a histogram with an exponential sequence. */
fun exponentialBuckets(start: Double, factor: Double, count: Int): List<Double> {
  return generateSequence(start) { it * factor }.take(count).toList()
}
