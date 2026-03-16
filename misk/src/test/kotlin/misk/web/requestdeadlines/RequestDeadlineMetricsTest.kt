package misk.web.requestdeadlines

import io.prometheus.client.CollectorRegistry
import jakarta.inject.Inject
import java.time.Duration
import kotlin.reflect.typeOf
import misk.Action
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.metrics.summaryCount
import misk.metrics.summarySum
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.DispatchMechanism
import misk.web.Get
import misk.web.actions.WebAction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = false)
class RequestDeadlineMetricsTest {
  @MiskTestModule val module = TestModule()

  @Inject private lateinit var metrics: RequestDeadlineMetrics
  @Inject private lateinit var registry: CollectorRegistry

  @Test
  fun `recordDeadlinePropagated skips health check actions - case insensitive`() {
    val upperCaseAction = createActionWithName("LIVENESSCHECKACTION")
    val mixedCaseAction = createActionWithName("ReadinessCheckAction")

    metrics.recordDeadlinePropagated(upperCaseAction, Duration.ofSeconds(5), "test_source")
    metrics.recordDeadlinePropagated(mixedCaseAction, Duration.ofSeconds(3), "test_source")

    // Verify histogram is not updated for either case
    assertThat(
        registry.summaryCount("deadline_duration_ms", "action" to "LIVENESSCHECKACTION", "source" to "test_source", "protocol" to "http")
      )
      .isNull()
    assertThat(
        registry.summaryCount("deadline_duration_ms", "action" to "ReadinessCheckAction", "source" to "test_source", "protocol" to "http")
      )
      .isNull()
  }

  @Test
  fun `recordDeadlinePropagated processes normal actions`() {
    val normalAction = createActionWithName("normalaction")
    val timeout = Duration.ofSeconds(10)

    metrics.recordDeadlinePropagated(normalAction, timeout, "test_source")

    // Verify histogram is updated
    assertThat(
        registry.summaryCount("deadline_duration_ms", "action" to "normalaction", "source" to "test_source", "protocol" to "http")
      )
      .isEqualTo(1.0)
    assertThat(
        registry.summarySum("deadline_duration_ms", "action" to "normalaction", "source" to "test_source", "protocol" to "http")
      )
      .isEqualTo(10000.0)
  }

  @Test
  fun `recordDeadlinePropagated ignores partial matches`() {
    val partialMatchAction = createActionWithName("mylivenesscheckactionextended")
    val timeout = Duration.ofSeconds(2)

    metrics.recordDeadlinePropagated(partialMatchAction, timeout, "test_source")

    // Should process this action since it's not an exact match
    assertThat(
        registry.summaryCount("deadline_duration_ms", "action" to "mylivenesscheckactionextended", "source" to "test_source", "protocol" to "http")
      )
      .isEqualTo(1.0)
  }

  @Test
  fun `recordDeadlineExceeded skips health check actions`() {
    val livenessAction = createActionWithName("livenesscheckaction")
    val normalAction = createActionWithName("normalaction")

    // Health check actions should be skipped, normal actions should be recorded
    metrics.recordDeadlineExceeded(livenessAction, "inbound", true, 1000L)
    metrics.recordDeadlineExceeded(normalAction, "inbound", false, 500L)

    // Health check action should not be recorded, normal action should be
    assertThat(
        registry.summaryCount("deadline_exceeded_time_ms", "action" to "livenesscheckaction", "direction" to "inbound", "enforced" to "true", "protocol" to "http")
      )
      .isNull()
    assertThat(
        registry.summaryCount("deadline_exceeded_time_ms", "action" to "normalaction", "direction" to "inbound", "enforced" to "false", "protocol" to "http")
      )
      .isEqualTo(1.0)
  }

  @Test
  fun `recordDeadlineExceeded skips health check actions - case insensitive`() {
    val readinessAction = createActionWithName("ReadinessCheckAction")
    val normalAction = createActionWithName("testaction")

    metrics.recordDeadlineExceeded(readinessAction, "outbound", false, 750L)
    metrics.recordDeadlineExceeded(normalAction, "outbound", true, 250L)

    // Health check action should not be recorded due to case-insensitive matching
    assertThat(
        registry.summaryCount("deadline_exceeded_time_ms", "action" to "ReadinessCheckAction", "direction" to "outbound", "enforced" to "false", "protocol" to "http")
      )
      .isNull()
    assertThat(
        registry.summaryCount("deadline_exceeded_time_ms", "action" to "testaction", "direction" to "outbound", "enforced" to "true", "protocol" to "http")
      )
      .isEqualTo(1.0)
  }

  private fun createActionWithName(name: String): Action {
    return Action(
      name = name,
      function = TestAction::call,
      acceptedMediaRanges = emptyList(),
      responseContentType = null,
      parameters = emptyList(),
      returnType = typeOf<String>(),
      dispatchMechanism = DispatchMechanism.GET,
    )
  }

  internal class TestAction @Inject constructor() : WebAction {
    @Get("/test") fun call(): String = "test"
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
    }
  }
}
