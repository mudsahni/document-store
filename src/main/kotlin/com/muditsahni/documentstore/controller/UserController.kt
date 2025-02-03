package com.muditsahni.documentstore.controller

import com.muditsahni.documentstore.model.dto.response.GetUserDetailsResponse
import com.muditsahni.documentstore.model.entity.toGetUserDetailsResponse
import com.muditsahni.documentstore.security.FirebaseUserDetails
import com.muditsahni.documentstore.service.impl.DefaultUserService
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@CrossOrigin // Add this annotation
@RequestMapping("/api/v1/tenants/{tenantId}/users")
@Tag(name = "User Information", description = "Endpoints to fetch user details")
@SecurityRequirement(name = "firebase")
class UserController(
    val userService: DefaultUserService
) {

    companion object {
        private val logger = KotlinLogging.logger {
            DocumentController::class.java.name
        }
    }

    @GetMapping("/{userId}")
    suspend fun get(
        @PathVariable tenantId: String,
        @PathVariable userId: String,
        @AuthenticationPrincipal userDetails: FirebaseUserDetails
    ): ResponseEntity<GetUserDetailsResponse> {
        logger.info { "Get user details call received" }

        // Get collection
        val user = userService.getUser(userDetails.tenant, userId)

        logger.info { "User fetched successfully" }
        // Return collection
        return ResponseEntity.ok(user.toGetUserDetailsResponse())
    }
}