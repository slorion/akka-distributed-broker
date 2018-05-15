package com.tsf.commons.messaging.brokers.akka.endpoints.undertow

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import com.tsf.commons.io.serialization.TextSerializer
import com.tsf.commons.messaging.brokers.akka.Broker
import com.tsf.commons.messaging.brokers.akka.endpoints.HttpEndpoint
import com.tsf.commons.web.undertow.HttpHandlerUtil
import io.undertow.server.HttpHandler
import io.undertow.util.Headers
import io.undertow.websockets.WebSocketConnectionCallback
import io.undertow.websockets.core._

class UndertowEndpoint(broker: Broker) extends HttpEndpoint(broker) {
	def postCommand(): HttpHandler = HttpHandlerUtil.safeDispatch {
		exchange => {
			// if content-type is not specified, use application/octet-stream
			// per https://www.w3.org/Protocols/rfc2616/rfc2616-sec7.html#sec7.2.1
			val contentType = Option(exchange.getRequestHeaders.get(Headers.CONTENT_TYPE).poll()).getOrElse("application/octet-stream")

			broker.getEnvelopeSerializer(contentType) match {
				case Some(_: TextSerializer) =>
					exchange.getRequestReceiver.receiveFullString(
						(ex, body) => processRequest(contentType, body) {
							(statusCode, output) =>
								ex.getResponseHeaders.put(Headers.CONTENT_TYPE, contentType)
								ex.setStatusCode(statusCode)
								ex.getResponseSender.send(output.toString)
						}, StandardCharsets.UTF_8)

				// following the logic of https://www.w3.org/Protocols/rfc2616/rfc2616-sec7.html#sec7.2.1 for default content-type,
				// consider an unhandled content-type as binary data
				case _ =>
					exchange.getRequestReceiver.receiveFullBytes(
						(ex, body) => processRequest(contentType, ByteBuffer.wrap(body)) {
							(statusCode, output) =>
								ex.getResponseHeaders.put(Headers.CONTENT_TYPE, contentType)
								ex.setStatusCode(statusCode)
								ex.getResponseSender.send(output)
						})
			}
		}
	}

	def onWebSocketConnection(): WebSocketConnectionCallback =
		(exchange, channel) => {
			// if content-type (obtained via sub-protocol) is not specified, assume
			//  - application/json for text messages
			//  - application/octet-stream for binary messages

			channel.getReceiveSetter.set(
				new AbstractReceiveListener {
					override protected def onFullTextMessage(channel: WebSocketChannel, message: BufferedTextMessage): Unit =
						processRequest(contentType = Option(channel.getSubProtocol).getOrElse("application/json"), body = message.getData) {
							(statusCode, output) => WebSockets.sendText(output.toString, channel, null)
						}

					override protected def onFullBinaryMessage(channel: WebSocketChannel, message: BufferedBinaryMessage): Unit = {
						val data = message.getData
						try {
							processRequest(contentType = Option(channel.getSubProtocol).getOrElse("application/octet-stream"), body = WebSockets.mergeBuffers(data.getResource: _*)) {
								(statusCode, output) => WebSockets.sendBinary(output, channel, null)
							}
						} finally {
							data.free()
						}
					}
				})

			channel.resumeReceives()
		}
}