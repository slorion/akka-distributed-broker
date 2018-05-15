package com.tsf.commons.io.serialization

import scala.reflect.runtime.{universe => ru}

trait TextSerializer extends Serializer {
	override type RawType = CharSequence
	override def rawTag: ru.TypeTag[CharSequence] = ru.typeTag[CharSequence]
}