package com.tsf.commons.messaging.brokers.akka.serialization

import com.tsf.commons.io.serialization.Serializer
import com.tsf.commons.messaging.brokers.akka.{RequestEnvelope, ResponseEnvelope}

import scala.util.Try

trait EnvelopeSerializer {
	this: Serializer =>

	def serializeResponse(response: ResponseEnvelope[_]): this.RawType =
		this.concreteToRaw(response)

	def deserializeRequest(raw: this.RawType, contentType: String): Try[RequestEnvelope[this.IntermediateType]] =
		deserializeRequest(raw).map(_.copy(contentType = contentType))

	def deserializeRequest(raw: this.RawType): Try[RequestEnvelope[this.IntermediateType]] =
		this.rawToConcrete[RequestEnvelope[this.IntermediateType]](raw)
}