package misk.web.interceptors

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.Action
import misk.MiskCaller
import misk.metrics.pal.PalHistogram
import misk.metrics.pal.PalMetrics
import misk.scope.ActionScoped
import misk.time.timed
import misk.web.NetworkChain
import misk.web.NetworkInterceptor

internal class MetricsInterceptor
internal constructor(
  private val actionName: String,
  private val requestDurationHistogram: PalHistogram,
  private val caller: ActionScoped<MiskCaller?>,
) : NetworkInterceptor {
  override fun intercept(chain: NetworkChain) {
    val (elapsedTime, result) = timed { chain.proceed(chain.httpCall) }

    val elapsedTimeMillis = elapsedTime.toMillis().toDouble()
    val callingPrincipal =
      when {
        caller.get()?.service != null -> caller.get()?.service!!
        caller.get()?.user != null -> "<user>"
        else -> "unknown"
      }

    val statusCode = chain.httpCall.statusCode
    requestDurationHistogram.labels(actionName, callingPrincipal, statusCode.toString()).observe(elapsedTimeMillis)
    return result
  }

  @Singleton
  class Factory
  @Inject
  constructor(
    m: PalMetrics,
    private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
  ) : NetworkInterceptor.Factory {
    internal val requestDurationHistogram =
      m.histogram(
        name = "histo_http_request_latency_ms",
        help = "count and duration in ms of incoming web requests",
        labelNames = listOf("action", "caller", "code"),
      )

    override fun create(action: Action) =
      MetricsInterceptor(action.name, requestDurationHistogram, caller)
  }
}
