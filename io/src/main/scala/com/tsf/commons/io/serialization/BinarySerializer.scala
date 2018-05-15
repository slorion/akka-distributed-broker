package com.tsf.commons.io.serialization

import java.nio.ByteBuffer

import scala.reflect.runtime.{universe => ru}

trait BinarySerializer extends Serializer {
	override type RawType = ByteBuffer
	override def rawTag: ru.TypeTag[ByteBuffer] = ru.typeTag[ByteBuffer]
}