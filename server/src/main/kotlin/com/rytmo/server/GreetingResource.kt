package com.rytmo.server

import com.rytmo.library.services.GreetingService
import com.rytmo.models.GreetingInput
import com.rytmo.models.GreetingOutput
import com.rytmo.server.auth.PrivyProtected
import jakarta.validation.constraints.NotBlank
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType

@Path("/hello")
class GreetingResource {
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @PrivyProtected
    fun hello(@NotBlank @QueryParam("name") name: @NotBlank String): GreetingOutput = GreetingOutput(GreetingService().greet(GreetingInput(name)))
}
