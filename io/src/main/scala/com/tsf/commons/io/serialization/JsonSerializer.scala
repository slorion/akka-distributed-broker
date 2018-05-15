package com.tsf.commons.io.serialization

import java.math.BigInteger

import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import com.fasterxml.jackson.databind.{DeserializationFeature, SerializationFeature}
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.json4s.JsonAST.JString
import org.json4s.jackson.JsonMethods
import org.json4s.{CustomSerializer, DefaultFormats, Extraction, Formats, JValue, MappingException}

import scala.reflect.ClassTag
import scala.reflect.runtime.{universe => ru}
import scala.util.{Failure, Try}

object JsonSerializer {
	val instance = new Object with JsonSerializer
}

trait JsonSerializer extends TextSerializer {
	override type IntermediateType = JValue
	override def intermediateTag: ru.TypeTag[JValue] = ru.typeTag[JValue]

	private object BigIntSerializer extends CustomSerializer[BigInt](
		_ => ( {
			case JString(s) => BigInt(s)
		}, {
			case i: BigInt => JString(i.toString())
		}))

	private object ThrowableSerializer extends CustomSerializer[Throwable](
		_ => (
			PartialFunction.empty, {
			case e: Throwable => {
				// json4s default serialization hides all error details, which we may want to see when testing.
				// Also, some Scala errors simply override toString and have no message,
				// so if error message is not present, try get it by calling e.toString.
				// Throwable.fillInStackTrace is overridden to avoid duplicating the stack trace in the output.
				val exception =
				if (e.getMessage == null || e.getMessage.isEmpty)
					new RuntimeException(e.toString, e) {
						override def fillInStackTrace(): Throwable = this
					}
				else e

				// Use Jackson directly to get proper serialization
				// and then parse the result to output a JSON object instead of a string containing the JSON object.
				JsonMethods.parse(JsonMethods.mapper.writeValueAsString(exception))
			}
		}))

	private[this] implicit val _jsonFormats: Formats =
		DefaultFormats + BigIntSerializer + ThrowableSerializer

	{
		JsonMethods.mapper
			.registerModule((new SimpleModule)
				//TODO: serializers seems to be ignored ?!
				.addSerializer(classOf[BigInt], ToStringSerializer.instance)
				.addSerializer(classOf[BigInteger], ToStringSerializer.instance))
			.registerModule(new Jdk8Module())
			.registerModule(new JavaTimeModule())
			//TODO: add to application.conf
			.configure(SerializationFeature.INDENT_OUTPUT, false)
			.configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, true)
			.configure(DeserializationFeature.READ_ENUMS_USING_TO_STRING, true)
			.configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, false)
			.configure(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS, false)
	}

	override val defaultContentType = "application/json"

	override def rawToIntermediate(raw: CharSequence): Try[JValue] =
		Try(JsonMethods.parse(raw.toString))
			.recoverWith { case ex: MappingException => Failure(new SerializationException(cause = ex)) }

	override def intermediateToRaw(intermediate: JValue): String = JsonMethods.compact(JsonMethods.render(intermediate))

	override def intermediateToConcrete[T: ru.TypeTag](intermediate: JValue): Try[T] = extractValue[T](intermediate)

	override def concreteToIntermediate(value: Any): JValue = Extraction.decompose(value)

	override def extractValue[T: ru.TypeTag](root: JValue, path: String): Try[T] =
		if (path.isEmpty || path == "/") doExtractValue[T](root)
		else doExtractValue(root \\ path)

	private[this] def doExtractValue[T: ru.TypeTag](root: JValue): Try[T] =
		Try(Extraction.extract[T](root)(_jsonFormats, toManifest[T]()))

	// adapted from https://stackoverflow.com/questions/23383814/is-it-possible-to-convert-a-typetag-to-a-manifest
	// to be discarded when json4s supports TypeTag
	def toManifest[T: ru.TypeTag](): Manifest[T] = {
		val typeTag = ru.typeTag[T]
		val mirror = typeTag.mirror

		def toManifestRecursive(t: ru.Type): Manifest[_] = {
			import reflect.ManifestFactory

			val clazz = ClassTag[T](mirror.runtimeClass(t)).runtimeClass

			if (t.typeArgs.length == 1) {
				val arg = toManifestRecursive(t.typeArgs.head)
				ManifestFactory.classType(clazz, arg)
			} else if (t.typeArgs.length > 1) {
				val args = t.typeArgs.map(x => toManifestRecursive(x))
				ManifestFactory.classType(clazz, args.head, args.tail: _*)
			} else {
				ManifestFactory.classType(clazz)
			}
		}

		toManifestRecursive(typeTag.tpe).asInstanceOf[Manifest[T]]
	}
}