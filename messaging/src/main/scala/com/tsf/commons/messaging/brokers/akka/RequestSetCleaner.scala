package com.tsf.commons.messaging.brokers.akka

import akka.actor.{Actor, ActorLogging}
import akka.cluster.Cluster
import akka.cluster.ddata.ORSet
import akka.cluster.ddata.Replicator.{Update, WriteMajority}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

protected[messaging] final class RequestSetCleaner(broker: Broker)  extends Actor with ActorLogging {
	context.system.scheduler.schedule(0.second, broker.settings.requestSet.trimmingInterval) {
		// when debugging and waiting on a breakpoint, a NullPointerException is sometimes thrown when accessing context.system, so protect against it
		if (context != null) {
			implicit val node = Cluster(context.system)

			broker.dataReplicator ! Update(broker.requestSetKey, ORSet.empty[RequestSetKey], WriteMajority(broker.settings.requestSet.writeTimeout)) {
				set =>
					val maxTimestamp = System.currentTimeMillis() - broker.settings.requestSet.maxAge.toMillis

					var filtered = set

					set.elements
						.filter(el => el.timestamp <= maxTimestamp)
						.foreach(el => filtered = filtered - el)

					filtered
			}
		}
	}

	override def receive: Receive = {
		case _ => ()
	}
}