package com.tsf.commons.messaging.brokers.akka

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.Cluster
import akka.cluster.ddata.ORSet
import akka.cluster.ddata.Replicator._
import akka.pattern._
import akka.util.Timeout
import com.tsf.commons.net.http.HttpStatus

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Failure

protected[messaging] class Worker(broker: Broker) extends Actor with ActorLogging {
	override def receive: Receive = {
		case request: RequestEnvelope[_] =>
			val replyTo = sender()

			implicit val timeout = Timeout(broker.settings.messageTimeout)
			implicit val node = Cluster(context.system)

			val _ = broker.dataReplicator ? Update(broker.requestSetKey, ORSet.empty[RequestSetKey], WriteMajority(broker.settings.requestSet.writeTimeout)) {
				set =>
					val key = new RequestSetKey(request.requestId, System.nanoTime())

					if (set.contains(key)) {
						throw new RequestAlreadyExistsException(request.requestId)
					} else {
						set + key
					}
			} map {
				case msg: UpdateResponse[_] =>
					msg match {
						case _: UpdateSuccess[_] =>
							context.actorOf(Props(classOf[WorkExecutor], broker), s"${classOf[WorkExecutor].getName}-${request.requestId}").tell(request, replyTo)

						case ModifyFailure(key@_, errorMessage@_, cause: RequestAlreadyExistsException, req@_) =>
							replyTo ! ResponseEnvelope.fromRequest(request, Failure(cause), HttpStatus.CONFLICT.value)

						case failure: UpdateFailure[_] =>
							replyTo ! ResponseEnvelope.fromRequest(request, Failure(new RuntimeException(s"Distributed Data update failure: $failure")), HttpStatus.INTERNAL_SERVER_ERROR.value)
					}
			}
	}
}