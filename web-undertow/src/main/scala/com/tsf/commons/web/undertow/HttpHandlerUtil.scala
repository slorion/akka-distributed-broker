package com.tsf.commons.web.undertow

import io.undertow.server.HttpHandler
import io.undertow.util.SameThreadExecutor

object HttpHandlerUtil {
	def safeDispatch(handler: HttpHandler): HttpHandler = {
		require(handler != null, "handler")

		exchange => {
			exchange.dispatch(SameThreadExecutor.INSTANCE, () => handler.handleRequest(exchange))
			()
		}
	}
}