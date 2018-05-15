package com.tsf.commons.messaging.brokers.akka

import java.util.concurrent.ConcurrentHashMap

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.cluster.ddata.{DistributedData, ORSetKey}
import com.tsf.commons.io.serialization.{JsonSerializer, Serializer}
import com.tsf.commons.messaging.brokers.akka.serialization.EnvelopeSerializer

import scala.concurrent.Future

class Broker(val settings: BrokerSettings) {
	val system = ActorSystem(settings.clusterName, settings.config.getConfig("akka-config"))

	val master: ActorRef = system.actorOf(Props(classOf[Master], this), "master")

	protected[messaging] val dataReplicator: ActorRef = DistributedData(system).replicator

	protected[messaging] val requestSetKey: ORSetKey[RequestSetKey] = ORSetKey[RequestSetKey]("requestSet")
	protected[messaging] val requestSetCleaner: ActorRef = system.actorOf(Props(classOf[RequestSetCleaner], this), "requestSetCleaner")

	protected[messaging] var requestHandlers = Vector.empty[(Any, PartialFunction[RequestEnvelope[_], EnvelopeSerializer with Serializer => Future[ResponseEnvelope[_]]])]
	protected[messaging] var requestRouters = Vector.empty[(Any, PartialFunction[RequestEnvelope[_], RoutingInformation])]

	private[this] val _serializers = new ConcurrentHashMap[String, EnvelopeSerializer with Serializer]
	protected[messaging] val fallbackSerializer = new EnvelopeSerializer with JsonSerializer

	def registerRequestHandler(key: Any, handler: PartialFunction[RequestEnvelope[_], EnvelopeSerializer with Serializer => Future[ResponseEnvelope[_]]]): Unit =
		this.synchronized {
			requestHandlers = requestHandlers :+ ((key, handler))
		}

	def unregisterRequestHandler(key: Any): Unit =
		this.synchronized {
			requestHandlers = requestHandlers.filter(_._1 != key)
		}

	def registerRequestRouter(key: Any, router: PartialFunction[RequestEnvelope[_], RoutingInformation]): Unit =
		this.synchronized {
			requestRouters = requestRouters :+ ((key, router))
		}

	def unregisterRequestRouter(key: Any): Unit =
		this.synchronized {
			requestRouters = requestRouters.filter(_._1 != key)
		}

	def registerContentType(contentType: String, serializer: EnvelopeSerializer with Serializer): Unit = {
		_serializers.put(contentType, serializer)
		()
	}

	def unregisterContentType(contentType: String): Unit = {
		_serializers.remove(contentType)
		()
	}

	def getEnvelopeSerializer(contentType: String): Option[EnvelopeSerializer with Serializer] = Option(_serializers.get(contentType))
}