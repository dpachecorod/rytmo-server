package com.rytmo.server.auth

import jakarta.ws.rs.NameBinding
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement

@NameBinding
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@SecurityRequirement(name = "BearerAuth")
annotation class PrivyProtected
