package com.rytmo.library.exceptions

class EmailAlreadyExistsException(email: String) : RuntimeException("A customer with email '$email' already exists")
