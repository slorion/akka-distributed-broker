package com.tsf.commons.io.serialization

import scala.reflect.runtime.{universe => ru}
import scala.util.Try

trait Serializer {
	type RawType
	type IntermediateType

	implicit def rawTag: ru.TypeTag[RawType]
	implicit def intermediateTag: ru.TypeTag[IntermediateType]

	val defaultContentType: String

	def rawToIntermediate(raw: RawType): Try[IntermediateType]
	def intermediateToRaw(intermediate: IntermediateType): RawType

	def intermediateToConcrete[T: ru.TypeTag](intermediate: IntermediateType): Try[T]
	def concreteToIntermediate(value: Any): IntermediateType

	// some serializers may want to optimize these
	def rawToConcrete[T: ru.TypeTag](raw: RawType): Try[T] = rawToIntermediate(raw).flatMap(intermediateToConcrete[T])
	def concreteToRaw(value: Any): RawType = intermediateToRaw(concreteToIntermediate(value))

	final def extractValue[T: ru.TypeTag](root: IntermediateType): Try[T] = extractValue[T](root, "/")
	def extractValue[T: ru.TypeTag](root: IntermediateType, path: String): Try[T]
}