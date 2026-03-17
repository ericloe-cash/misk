package misk.redis.lettuce.metrics

import com.google.inject.Module
import io.lettuce.core.RedisClient
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.support.AsyncConnectionPoolSupport
import io.lettuce.core.support.BoundedPoolConfig
import io.prometheus.client.CollectorRegistry
import jakarta.inject.Inject
import kotlin.test.DefaultAsserter.assertEquals
import kotlin.test.Test
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.metrics.get
import misk.redis.lettuce.metrics.RedisClientMetrics.Companion.ACTIVE_CONNECTIONS
import misk.redis.lettuce.metrics.RedisClientMetrics.Companion.IDLE_CONNECTIONS
import misk.redis.lettuce.metrics.RedisClientMetrics.Companion.MAX_IDLE_CONNECTIONS
import misk.redis.lettuce.metrics.RedisClientMetrics.Companion.MAX_TOTAL_CONNECTIONS
import misk.redis.lettuce.metrics.RedisClientMetrics.Companion.MIN_IDLE_CONNECTIONS
import misk.redis.lettuce.metrics.RedisClientMetrics.Companion.NAME_LABEL
import misk.redis.lettuce.metrics.RedisClientMetrics.Companion.REPLICATION_GROUP_ID_LABEL
import misk.redis.lettuce.redisPort
import misk.redis.lettuce.redisUri
import misk.redis.lettuce.standalone.PooledStatefulRedisConnectionProvider
import misk.redis.lettuce.standalone.redisClient
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import wisp.deployment.TESTING

@MiskTest(startService = true)
@DisplayName("Verify that the RedisClientMetricsCommandLatencyRecorder records command latencies")
internal class ConnectionPoolMetricsTest {

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
  private val redisUri = redisUri {
    withHost("localhost")
    withPort(redisPort)
    withPassword("".toCharArray())
  }
  private val poolConfig = BoundedPoolConfig.create()

  private lateinit var redisClient: RedisClient
  private lateinit var connectionProvider: PooledStatefulRedisConnectionProvider<String, String>
  private val name = "test"
  private val replicationGroupId = "test-group"

  @BeforeEach
  fun setUp() {
    redisClient = redisClient()
    connectionProvider =
      PooledStatefulRedisConnectionProvider(
        AsyncConnectionPoolSupport.createBoundedObjectPoolAsync(
            { redisClient.connectAsync(StringCodec.UTF8, redisUri) },
            BoundedPoolConfig.create(),
            false, // this is handled directly in the connection provider
          )
          .thenApply {
            clientMetrics.registerConnectionPoolMetrics(name, replicationGroupId, it)
            it
          }
          .toCompletableFuture(),
        replicationGroupId,
      )
  }

  private fun gaugeValue(metricName: String): Double? =
    registry.get(metricName, NAME_LABEL to name, REPLICATION_GROUP_ID_LABEL to replicationGroupId)

  @Test
  fun `test connection pool metrics in RedisClientMetrics`() {
    connectionProvider.acquireBlocking(exclusive = true).use {
      assertEquals(
        "max total connections is ${poolConfig.maxTotal}",
        poolConfig.maxTotal.toDouble(),
        gaugeValue(MAX_TOTAL_CONNECTIONS),
      )
      assertEquals(
        "max idle connections is ${poolConfig.maxIdle}",
        poolConfig.maxIdle.toDouble(),
        gaugeValue(MAX_IDLE_CONNECTIONS),
      )
      assertEquals(
        "min idle connections is ${poolConfig.minIdle}",
        poolConfig.minIdle.toDouble(),
        gaugeValue(MIN_IDLE_CONNECTIONS),
      )
      assertEquals(
        "active connections is 1 after acquiring a connection",
        1.0,
        gaugeValue(ACTIVE_CONNECTIONS),
      )
      assertEquals(
        "idle connections is 0 after acquiring a connection",
        0.0,
        gaugeValue(IDLE_CONNECTIONS),
      )
    }
    assertEquals(
      "active connections is 0 after closing the connection",
      0.0,
      gaugeValue(ACTIVE_CONNECTIONS),
    )
    assertEquals(
      "idle connections is 1 after closing the  connection",
      1.0,
      gaugeValue(IDLE_CONNECTIONS),
    )
  }
}
