package com.tsf.commons.messaging.brokers.akka

object RoutingInformation {
	val default = RoutingInformation(Set.empty[String])

	def apply(): RoutingInformation = default
	def apply(roles: String*): RoutingInformation = RoutingInformation(roles.toSet)
}

case class RoutingInformation(roles: Set[String])