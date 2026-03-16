package com.rytmo.library.exceptions

class PrivyWalletException(message: String, val statusCode: Int) : RuntimeException(message)
