package com.tsf.commons.messaging.brokers.akka

import akka.actor.{Actor, ActorLogging}
import com.tsf.commons.net.http.HttpStatus

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

protected[messaging] class WorkExecutor(broker: Broker) extends Actor with ActorLogging {
	override def receive: Receive = {
		case request: RequestEnvelope[_] =>
			val replyTo = sender()

			broker.requestHandlers.map(_._2).find(_.isDefinedAt(request)) match {
				case None =>
					Failure(new RuntimeException(s"No handler was able to process the request $request."))

				case Some(f) =>
					Try {
						val serializer = broker.getEnvelopeSerializer(request.contentType).getOrElse(throw new RuntimeException(s"Cannot find serializer for content-type ${request.contentType}."))

						f.apply(request)(serializer) onComplete {
							case Success(response) =>
								replyTo ! response

							case Failure(error) =>
								replyTo ! ResponseEnvelope.fromRequest(request, Failure(error), HttpStatus.INTERNAL_SERVER_ERROR.value)
						}
					} recover {
						case error =>
							replyTo ! ResponseEnvelope.fromRequest(request, Failure(error), HttpStatus.INTERNAL_SERVER_ERROR.value)
					}
			}

			context.stop(self)
	}
}