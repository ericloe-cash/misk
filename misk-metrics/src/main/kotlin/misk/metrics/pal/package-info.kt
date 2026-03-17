/**
 * # misk.metrics.pal — Platform Abstraction Layer for Metrics
 *
 * **This package is internal to misk. Application code should NOT use these types.**
 *
 * All types in this package are marked with `@ExperimentalMiskApi` which causes a compile error
 * if used without an explicit `@OptIn`. This is intentional — these are misk's internal plumbing
 * for decoupling metric instrumentation from Prometheus, not a public API for applications.
 *
 * ## For application code
 *
 * Continue using `misk.metrics.v2.Metrics` for Prometheus metrics. When you're ready to migrate
 * to OTel, use the OTel SDK directly. The PAL exists so that misk's own interceptors and framework
 * code can be backend-agnostic without requiring apps to change.
 *
 * ## For misk contributors
 *
 * Use `PalMetrics` in misk-internal code (interceptors, framework modules). Add `@OptIn(ExperimentalMiskApi::class)`
 * to your file. See `MetricsInterceptor` for an example.
 */
package misk.metrics.pal
