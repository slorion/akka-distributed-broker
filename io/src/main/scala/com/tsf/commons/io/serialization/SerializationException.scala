package com.tsf.commons.io.serialization

class SerializationException(message: String = null, cause: Throwable = null) extends RuntimeException(if (message != null) message else if (cause != null) cause.getMessage else null, cause)