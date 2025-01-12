package com.muditsahni.documentstore.exception

// Custom exception
class OpenAIException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
