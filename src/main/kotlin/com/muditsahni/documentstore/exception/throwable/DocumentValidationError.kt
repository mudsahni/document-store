package com.muditsahni.documentstore.exception.throwable

import com.muditsahni.documentstore.exception.ErrorCode
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(HttpStatus.NOT_FOUND)
class DocumentValidationError(code: ErrorCode, message: String) : RuntimeException(message)