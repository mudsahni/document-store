package com.muditsahni.documentstore.exception.throwable

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(HttpStatus.NOT_FOUND)
class CollectionNotFoundException(code: String, message: String) : ResourceNotFoundException(code, message)