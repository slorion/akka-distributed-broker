package com.tsf.commons.core

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config

import scala.concurrent.duration.{Duration, FiniteDuration}

private[commons] object Helpers {
	final implicit class ConfigOps(val config: Config) extends AnyVal {
		def getFiniteDuration(path: String, unit: TimeUnit): FiniteDuration =
			Duration(config.getDuration(path, unit), unit)
	}
}