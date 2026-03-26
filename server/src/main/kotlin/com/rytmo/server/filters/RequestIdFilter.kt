package com.rytmo.server.filters

import jakarta.annotation.Priority
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.container.ContainerResponseContext
import jakarta.ws.rs.container.ContainerResponseFilter
import jakarta.ws.rs.ext.Provider
import org.slf4j.MDC
import java.util.UUID

@Provider
@Priority(Priorities.USER - 1000)
class RequestIdFilter :
    ContainerRequestFilter,
    ContainerResponseFilter {
    companion object {
        const val REQUEST_ID_HEADER = "X-Request-Id"
        const val MDC_KEY = "requestId"
    }

    override fun filter(requestContext: ContainerRequestContext) {
        val requestId =
            requestContext.getHeaderString(REQUEST_ID_HEADER) ?: UUID.randomUUID().toString()
        MDC.put(MDC_KEY, requestId)
        requestContext.headers.putSingle(REQUEST_ID_HEADER, requestId)
    }

    override fun filter(requestContext: ContainerRequestContext, responseContext: ContainerResponseContext) {
        responseContext.headers.putSingle(REQUEST_ID_HEADER, MDC.get(MDC_KEY))
        MDC.remove(MDC_KEY)
    }
}
