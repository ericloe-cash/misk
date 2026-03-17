package misk.redis.lettuce.metrics

import com.google.inject.Module
import io.lettuce.core.RedisClient
import io.prometheus.client.CollectorRegistry
import jakarta.inject.Inject
import kotlin.test.DefaultAsserter.assertEquals
import kotlin.test.Test
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.metrics.getSample
import misk.redis.lettuce.metrics.RedisClientMetrics.Companion.FIRST_RESPONSE_TIME
import misk.redis.lettuce.metrics.RedisClientMetrics.Companion.OPERATION_TIME
import misk.redis.lettuce.redisPort
import misk.redis.lettuce.redisUri
import misk.redis.lettuce.standalone.clientResources
import misk.redis.lettuce.standalone.redisClient
import misk.redis.lettuce.standalone.withConnectionBlocking
import misk.redis2.metrics.RedisClientMetricsCommandLatencyRecorder
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import wisp.deployment.TESTING

@MiskTest(startService = true)
@DisplayName("Verify that the RedisClientMetricsCommandLatencyRecorder records command latencies")
internal class CommandLatencyRecorderTest {

  @MiskTestModule
  private val module: Module =
    object : KAbstractModule() {
      override fun configure() {
        install(MiskTestingServiceModule())
        install(DeploymentModule(TESTING))
      }
    }

  @Inject internal lateinit var clientMetrics: RedisClientMetrics
  @Inject internal lateinit var registry: CollectorRegistry
  private lateinit var redisClient: RedisClient

  @BeforeEach
  fun setUp() {
    redisClient =
      redisClient(
        redisURI =
          redisUri {
            withHost("localhost")
            withPort(redisPort)
            withPassword("".toCharArray())
          },
        clientResources =
          clientResources {
            commandLatencyRecorder(
              RedisClientMetricsCommandLatencyRecorder(
                replicationGroupId = "test_replication_group_001",
                clientMetrics = clientMetrics,
              )
            )
          },
      )
  }

  @Test
  fun `test ping command has first response time latencies registered`() {
    redisClient.withConnectionBlocking {
      assertEquals("result is PONG", "PONG", sync().ping())
      val sample = registry.getSample(
        FIRST_RESPONSE_TIME,
        arrayOf("replication_group_id" to "test_replication_group_001", "command" to "PING"),
        sampleName = "${FIRST_RESPONSE_TIME}_count",
      )
      assert(sample != null) { "Expected first response time metric for PING" }
      assert(sample!!.value > 0.0) { "Expected at least one observation" }
    }
  }

  @Test
  fun `test ping command has operation time latencies registered`() {
    redisClient.withConnectionBlocking {
      assertEquals("result is PONG", "PONG", sync().ping())
      val sample = registry.getSample(
        OPERATION_TIME,
        arrayOf("replication_group_id" to "test_replication_group_001", "command" to "PING"),
        sampleName = "${OPERATION_TIME}_count",
      )
      assert(sample != null) { "Expected operation time metric for PING" }
      assert(sample!!.value > 0.0) { "Expected at least one observation" }
    }
  }
}
