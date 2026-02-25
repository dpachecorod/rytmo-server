package com.rytmo.server.metrics

import io.micrometer.cloudwatch2.CloudWatchConfig
import io.micrometer.cloudwatch2.CloudWatchMeterRegistry
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient

@ApplicationScoped
class CloudWatchSetup {

    @Inject lateinit var meterRegistry: MeterRegistry

    @ConfigProperty(name = "metrics.cloudwatch.enabled", defaultValue = "false")
    var enabled: Boolean = false

    @ConfigProperty(name = "metrics.cloudwatch.namespace", defaultValue = "rytmo/api")
    lateinit var namespace: String

    @Inject lateinit var log: Logger

    fun onStart(@Observes event: StartupEvent) {
        if (!enabled) {
            log.info("CloudWatch metrics disabled")
            return
        }

        val cw = CloudWatchAsyncClient.create()
        val config =
            object : CloudWatchConfig {
                override fun namespace(): String = namespace

                override fun get(key: String): String? = null
            }
        (meterRegistry as CompositeMeterRegistry).add(CloudWatchMeterRegistry(config, Clock.SYSTEM, cw))
        log.infof("CloudWatch metrics enabled — namespace: %s", namespace)
    }
}
