package misk.web.interceptors

import io.prometheus.client.CollectorRegistry
import jakarta.inject.Inject
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.metrics.summaryCount
import misk.metrics.summarySum
import misk.security.authz.AccessControlModule
import misk.security.authz.FakeCallerAuthenticator
import misk.security.authz.FakeCallerAuthenticator.Companion.SERVICE_HEADER
import misk.security.authz.FakeCallerAuthenticator.Companion.USER_HEADER
import misk.security.authz.MiskCallerAuthenticator
import misk.security.authz.Unauthenticated
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.PathParam
import misk.web.Response
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class ExclusiveTimingInterceptorTest {
  @MiskTestModule val module = TestModule()
  val httpClient = OkHttpClient()

  @Inject private lateinit var exclusiveTimingInterceptorFactory: ExclusiveTimingInterceptor.Factory
  @Inject private lateinit var metricsInterceptorFactory: MetricsInterceptor.Factory
  @Inject private lateinit var jettyService: JettyService
  @Inject private lateinit var registry: CollectorRegistry

  private fun exclusiveLabels(code: Int, service: String = "unknown") =
    arrayOf("action" to "ExclusiveTimingInterceptorTestAction", "caller" to service, "status_code" to code.toString())

  private fun metricsLabels(code: Int, service: String = "unknown") =
    arrayOf("action" to "ExclusiveTimingInterceptorTestAction", "caller" to service, "code" to code.toString())

  @Test
  fun time() {
    // Fire off a request
    val response = invoke(200)
    assertThat(response.code).isEqualTo(200)

    // Wait for metrics to be recorded asynchronously
    await().atMost(1, TimeUnit.SECONDS).untilAsserted {
      // Figure out how long each of the latency metrics was
      val requestDurationSum = registry.summarySum("histo_http_request_latency_ms", *metricsLabels(200))!!
      val exclusiveDurationSum = registry.summarySum("histo_http_request_exclusive_latency_ms", *exclusiveLabels(200))!!
      val difference = requestDurationSum - exclusiveDurationSum

      // Verify that the sleep time was excluded
      // (but leave some room for small differences due to execution time.)
      assertThat(difference)
        .isCloseTo(
          /* expected = */ ExclusiveTimingInterceptorTestAction.SLEEP_TIME.toDouble(),
          /* offset = */ Offset.offset(ExclusiveTimingInterceptorTestAction.SLEEP_TIME.toDouble() / 2),
        )
    }
  }

  @Test
  fun responseCodes() {
    // Fire off a bunch of requests
    invoke(200)
    invoke(200)
    invoke(202)
    invoke(404)
    invoke(403)
    invoke(403)
    invoke(200, "my-peer")
    invoke(200, "my-peer")
    invoke(200, "my-peer")
    invoke(200, "my-peer")
    invoke(200, user = "some-user")

    val metricName = "histo_http_request_exclusive_latency_ms"

    // Wait for metrics to be recorded asynchronously
    await().atMost(1, TimeUnit.SECONDS).untilAsserted {
      // Make sure all the right metrics were generated
      assertThat(registry.summaryCount(metricName, *exclusiveLabels(200))?.toInt()).isEqualTo(2)
      assertThat(registry.summaryCount(metricName, *exclusiveLabels(202))?.toInt()).isEqualTo(1)
      assertThat(registry.summaryCount(metricName, *exclusiveLabels(404))?.toInt()).isEqualTo(1)
      assertThat(registry.summaryCount(metricName, *exclusiveLabels(403))?.toInt()).isEqualTo(2)
      assertThat(registry.summaryCount(metricName, *exclusiveLabels(200, "my-peer"))?.toInt()).isEqualTo(4)
      assertThat(registry.summaryCount(metricName, *exclusiveLabels(200, "<user>"))?.toInt()).isEqualTo(1)
    }
  }

  fun invoke(desiredStatusCode: Int, service: String? = null, user: String? = null): okhttp3.Response {
    val url = jettyService.httpServerUrl.newBuilder().encodedPath("/call/$desiredStatusCode").build()

    val request = okhttp3.Request.Builder().url(url).get()
    service?.let { request.addHeader(SERVICE_HEADER, it) }
    user?.let { request.addHeader(USER_HEADER, it) }
    return httpClient.newCall(request.build()).execute().also {
      // Make sure the request returned the expected code
      assertThat(it.code).isEqualTo(desiredStatusCode)
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(AccessControlModule())
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      multibind<MiskCallerAuthenticator>().to<FakeCallerAuthenticator>()
      install(WebActionModule.create<ExclusiveTimingInterceptorTestAction>())

      bind<MetricsInterceptor.Factory>()
      install(ExclusiveTimingInterceptor.Module())
    }
  }
}

internal class ExclusiveTimingInterceptorTestAction
@Inject
constructor(private val excludedTime: ThreadLocal<ExcludedTime>) : WebAction {
  @Get("/call/{desiredStatusCode}")
  @Unauthenticated
  fun call(@PathParam desiredStatusCode: Int): Response<String> {
    Thread.sleep(SLEEP_TIME)
    excludedTime.get().add(SLEEP_TIME.milliseconds)
    return Response("foo", statusCode = desiredStatusCode)
  }

  companion object {
    const val SLEEP_TIME: Long = 10
  }
}
