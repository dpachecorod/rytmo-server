package com.rytmo.library.exceptions

class CardAccountException(override val message: String, cause: Throwable? = null) : RuntimeException(message, cause)
