package com.rytmo.library.services

import com.rytmo.models.GreetingInput

class GreetingService {
    fun greet(input: GreetingInput) = input.name
}
