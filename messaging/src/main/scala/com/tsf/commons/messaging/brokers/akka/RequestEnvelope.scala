package com.tsf.commons.messaging.brokers.akka

object RequestEnvelope {
	val defaultContentType = "application/octet-stream"
}

case class RequestEnvelope[+T](
	requestId: Any = IdGenerator.generateId(),
	requestName: String,
	payload: T,
	contentType: String = RequestEnvelope.defaultContentType,
	tags: Set[String] = Set.empty[String]
) {
	def withContentType(contentType: String): RequestEnvelope[T] = this.copy(contentType = contentType)
	def withTags(tags: Set[String]): RequestEnvelope[T] = this.copy(tags = tags)
}