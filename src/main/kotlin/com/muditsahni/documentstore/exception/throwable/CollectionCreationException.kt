package com.muditsahni.documentstore.exception.throwable

import com.muditsahni.documentstore.exception.ErrorCode
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
class CollectionCreationError(code: ErrorCode, message: String) : RuntimeException(message)