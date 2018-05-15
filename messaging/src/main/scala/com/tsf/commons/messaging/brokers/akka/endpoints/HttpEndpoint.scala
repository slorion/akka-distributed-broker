package com.tsf.commons.messaging.brokers.akka.endpoints

import java.nio.ByteBuffer

import akka.pattern._
import akka.util.Timeout
import com.tsf.commons.io.serialization.{BinarySerializer, SerializationException, Serializer, TextSerializer}
import com.tsf.commons.messaging.brokers.akka.{Broker, RequestEnvelope, ResponseEnvelope}
import com.tsf.commons.messaging.brokers.akka.serialization.EnvelopeSerializer
import com.tsf.commons.net.http.HttpStatus

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

abstract class HttpEndpoint(broker: Broker) {
	def processRequest(contentType: String, body: CharSequence)(writeResponse: (Int, CharSequence) => Unit): Unit = {
		broker.getEnvelopeSerializer(contentType) match {
			case Some(serializer: EnvelopeSerializer with TextSerializer) =>
				processRequest(serializer.deserializeRequest(body, contentType), serializer)(writeResponse)

			case Some(_: EnvelopeSerializer with BinarySerializer) =>
				writeError(
					HttpStatus.UNSUPPORTED_MEDIA_TYPE.value,
					new RuntimeException(s"'$contentType' content-type requires binary data."),
					broker.fallbackSerializer
				)((statusCode, output) => writeResponse(statusCode, output.toString))

			case Some(_) | None =>
				writeError(
					HttpStatus.UNSUPPORTED_MEDIA_TYPE.value,
					new RuntimeException(s"'$contentType' content-type is not supported."),
					broker.fallbackSerializer
				)((statusCode, output) => writeResponse(statusCode, output.toString))
		}
	}

	def processRequest(contentType: String, body: ByteBuffer)(writeResponse: (Int, ByteBuffer) => Unit): Unit = {
		broker.getEnvelopeSerializer(contentType) match {
			case Some(serializer: EnvelopeSerializer with BinarySerializer) =>
				processRequest(serializer.deserializeRequest(body, contentType), serializer)(writeResponse)

			case Some(_: EnvelopeSerializer with TextSerializer) =>
				writeError(
					HttpStatus.UNSUPPORTED_MEDIA_TYPE.value,
					new RuntimeException(s"'$contentType' content-type requires text data."),
					broker.fallbackSerializer
				)((statusCode, output) => writeResponse(statusCode, ByteBuffer.wrap(output.toString.getBytes("UTF-8"))))

			case Some(_) | None =>
				writeError(
					HttpStatus.UNSUPPORTED_MEDIA_TYPE.value,
					new RuntimeException(s"'$contentType' content-type is not supported."),
					broker.fallbackSerializer
				)((statusCode, output) => writeResponse(statusCode, ByteBuffer.wrap(output.toString.getBytes("UTF-8"))))
		}
	}

	private[this] def processRequest(request: Try[RequestEnvelope[_]], serializer: EnvelopeSerializer with Serializer)(writeResponse: (Int, serializer.RawType) => Unit): Unit =
		request match {
			case Success(jobRequest) => {
				implicit val timeout = Timeout(broker.settings.messageTimeout)

				broker.master ? jobRequest map {
					case jobResponse: ResponseEnvelope[_] =>
						writeResponse(jobResponse.statusCode, serializer.serializeResponse(jobResponse))
				} recover {
					case error =>
						writeError(jobRequest, HttpStatus.INTERNAL_SERVER_ERROR.value, error, serializer)(writeResponse)
				}

				()
			}

			case failure@Failure(error) => {
				val statusCode =
					if (error.isInstanceOf[SerializationException]) {
						HttpStatus.BAD_REQUEST.value
					} else {
						HttpStatus.INTERNAL_SERVER_ERROR.value
					}

				writeError(statusCode, failure, serializer)(writeResponse)
			}
		}

	private[this] def writeError(jobRequest: RequestEnvelope[_], statusCode: Int, error: Throwable, serializer: EnvelopeSerializer with Serializer)(writeResponse: (Int, serializer.RawType) => Unit): Unit =
		writeError(jobRequest, statusCode, Failure(error), serializer)(writeResponse)

	private[this] def writeError(jobRequest: RequestEnvelope[_], statusCode: Int, error: Failure[_], serializer: EnvelopeSerializer with Serializer)(writeResponse: (Int, serializer.RawType) => Unit): Unit =
		writeResponse(statusCode, serializer.serializeResponse(ResponseEnvelope.fromRequest(jobRequest, error, statusCode)))

	private[this] def writeError(statusCode: Int, error: Throwable, serializer: EnvelopeSerializer with Serializer)(writeResponse: (Int, serializer.RawType) => Unit): Unit =
		writeError(statusCode, Failure(error), serializer)(writeResponse)

	private[this] def writeError(statusCode: Int, error: Failure[_], serializer: EnvelopeSerializer with Serializer)(writeResponse: (Int, serializer.RawType) => Unit): Unit = {
		writeResponse(statusCode, serializer.serializeResponse(ResponseEnvelope(null, "", serializer.defaultContentType, error, statusCode)))
	}
}