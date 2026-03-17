package misk.web.interceptors

import io.prometheus.client.CollectorRegistry
import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.metrics.getSample
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
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.awaitility.Durations.ONE_MILLISECOND
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.withPollInterval
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class MetricsInterceptorTest {
  @MiskTestModule val module = TestModule()
  val httpClient = OkHttpClient()

  @Inject private lateinit var registry: CollectorRegistry
  @Inject private lateinit var jettyService: JettyService

  private fun histoLabels(code: Int, service: String = "unknown") =
    arrayOf("action" to "MetricsInterceptorTestAction", "caller" to service, "code" to code.toString())

  @BeforeEach
  fun sendRequests() {
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
  }

  @Test
  fun responseCodes() {
    await.withPollInterval(ONE_MILLISECOND).atMost(ONE_HUNDRED_MILLISECONDS).untilAsserted {
      assertThat(histoCount(200)).isEqualTo(2)
      assertThat(histoCount(202)).isEqualTo(1)
      assertThat(histoCount(404)).isEqualTo(1)
      assertThat(histoCount(403)).isEqualTo(2)
      assertThat(histoCount(200, "my-peer")).isEqualTo(4)
      assertThat(histoCount(200, "<user>")).isEqualTo(1)
    }
  }

  private fun histoCount(code: Int, service: String = "unknown"): Int? =
    registry.getSample(
      "histo_http_request_latency_ms",
      histoLabels(code, service),
      sampleName = "histo_http_request_latency_ms_count",
    )?.value?.toInt()

  fun invoke(desiredStatusCode: Int, service: String? = null, user: String? = null): okhttp3.Response {
    val url = jettyService.httpServerUrl.newBuilder().encodedPath("/call/$desiredStatusCode").build()

    val request = okhttp3.Request.Builder().url(url).get()
    service?.let { request.addHeader(SERVICE_HEADER, it) }
    user?.let { request.addHeader(USER_HEADER, it) }
    return httpClient.newCall(request.build()).execute().also {
      assertThat(it.code).isEqualTo(desiredStatusCode)
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(AccessControlModule())
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      multibind<MiskCallerAuthenticator>().to<FakeCallerAuthenticator>()
      install(WebActionModule.create<MetricsInterceptorTestAction>())
    }
  }
}

internal class MetricsInterceptorTestAction @Inject constructor() : WebAction {
  @Get("/call/{desiredStatusCode}")
  @Unauthenticated
  fun call(@PathParam desiredStatusCode: Int): Response<String> {
    return Response("foo", statusCode = desiredStatusCode)
  }
}
