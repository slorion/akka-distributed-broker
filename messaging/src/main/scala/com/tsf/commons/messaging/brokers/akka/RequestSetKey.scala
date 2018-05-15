package com.tsf.commons.messaging.brokers.akka

class RequestSetKey(val requestId: Any, val timestamp: Long) extends Serializable {
	override def equals(obj: Any): Boolean =
		obj match {
			case key: RequestSetKey => key.requestId == this.requestId
			case _ => false
		}

	override def hashCode(): Int = requestId.hashCode
	override def toString: String = s"($requestId,$timestamp)"
}