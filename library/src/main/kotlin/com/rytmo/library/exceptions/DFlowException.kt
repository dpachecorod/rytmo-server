package com.rytmo.library.exceptions

class DFlowException(override val message: String, val statusCode: Int, cause: Throwable? = null) : RuntimeException(message, cause)
