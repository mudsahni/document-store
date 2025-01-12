package com.muditsahni.documentstore.controller

import com.google.firebase.auth.FirebaseAuth
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/dev")
class DevController(private val firebaseAuth: FirebaseAuth) {
    @GetMapping("/token")
    fun getTestToken(): String {
        // IMPORTANT: Only enable this endpoint in development
        return firebaseAuth.createCustomToken("nuotTDRyK0V3t9qR7dgFVHeVc393")
    }
}