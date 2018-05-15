package com.tsf.commons.messaging.brokers.akka

import scala.util.Try

object ResponseEnvelope {
	def fromRequest[TRequest, TResponse](request: RequestEnvelope[TRequest], payload: Try[TResponse], statusCode: Int) =
		ResponseEnvelope(request.requestId, request.requestName, request.contentType, payload, statusCode)
}

final case class ResponseEnvelope[+T](requestId: Any, requestName: String, contentType: String, payload: Try[T], statusCode: Int)