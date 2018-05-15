package com.tsf.commons.messaging.brokers.akka

import java.util.concurrent.TimeUnit

import com.tsf.commons.core.Helpers._
import com.typesafe.config.Config

import scala.concurrent.duration.{Duration, FiniteDuration}

final class BrokerSettings(val config: Config) {
	private val _cc = config.getConfig("com.tsf.commons.messaging.brokers.akka")

	val clusterName: String = _cc.getString("cluster-name")
	val workerRoleName: String = _cc.getString("worker-role-name")
	val messageTimeout: FiniteDuration = _cc.getFiniteDuration("message-timeout", TimeUnit.MILLISECONDS)

	require(clusterName.length > 0, "cluster-name is mandatory.")
	require(workerRoleName.length > 0, "worker-role-name is mandatory.")
	require(messageTimeout > Duration.Zero, "message-timeout > 0")

	val requestSet = new RequestSetSettings(_cc.getConfig("request-set"))

	final class RequestSetSettings(val config: Config) {
		val trimmingInterval: FiniteDuration = config.getFiniteDuration("trimming-interval", TimeUnit.MILLISECONDS)
		val writeTimeout: FiniteDuration = config.getFiniteDuration("write-timeout", TimeUnit.MILLISECONDS)
		val maxAge: FiniteDuration = config.getFiniteDuration("max-age", TimeUnit.MILLISECONDS)

		require(trimmingInterval > Duration.Zero, "request-set.trimming-interval > 0")
		require(writeTimeout > Duration.Zero, "request-set.write-timeout > 0")
		require(maxAge > Duration.Zero, "request-set.max-age > 0")
	}
}