package com.rytmo.library.exceptions

class DeframeException(message: String, val statusCode: Int) : RuntimeException(message)
