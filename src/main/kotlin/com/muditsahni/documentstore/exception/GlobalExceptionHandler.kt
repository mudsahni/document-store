package com.muditsahni.documentstore.exception

import com.muditsahni.documentstore.exception.throwable.CollectionCreationError
import com.muditsahni.documentstore.exception.throwable.CollectionNotFoundException
import com.muditsahni.documentstore.exception.throwable.ResourceNotFoundException
import com.muditsahni.documentstore.respository.DocumentNotFoundException
import okio.FileNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ServerWebInputException

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(IllegalStateException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleIllegalState(e: IllegalStateException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(
                code = MajorErrorCode.INV_MAJ_DOC_001.code,
                message = MajorErrorCode.INV_MAJ_DOC_001.message
            ))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                code = MajorErrorCode.GEN_MAJ_REQ_001.code,
                message = MajorErrorCode.GEN_MAJ_REQ_001.message
            ))
    }

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleGeneral(e: Exception): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(
                code = MajorErrorCode.GEN_MAJ_UNK_001.code,
                message = MajorErrorCode.GEN_MAJ_UNK_001.message
            ))
    }

    @ExceptionHandler(ResourceNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleResourceNotFoundException(ex: ResourceNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(
                code = MajorErrorCode.GEN_MAJ_RES_001.code,
                message = MajorErrorCode.GEN_MAJ_RES_001.message
            ))
    }

    @ExceptionHandler(CollectionNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleCollectionNotFoundException(ex: CollectionNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(
                code = MajorErrorCode.GEN_MAJ_COL_003.code,
                message = MajorErrorCode.GEN_MAJ_COL_003.message
            ))
    }

    @ExceptionHandler(DocumentNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleDocumentNotFoundException(ex: DocumentNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(
                code = MajorErrorCode.GEN_MAJ_DOC_003.code,
                message = MajorErrorCode.GEN_MAJ_DOC_003.message
            ))
    }


    @ExceptionHandler(FileNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleFileNotFoundException(ex: FileNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(
                code = MajorErrorCode.GEN_MAJ_RES_001.code,
                message = MajorErrorCode.GEN_MAJ_RES_001.message
            ))
    }

    @ExceptionHandler(ServerWebInputException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleServerWebInputException(e: ServerWebInputException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                code = MajorErrorCode.GEN_MAJ_REQ_001.code,
                message = MajorErrorCode.GEN_MAJ_REQ_001.message
            ))
    }

    @ExceptionHandler(CollectionCreationError::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleCollectionCreationError(e: CollectionCreationError): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(
                code = MajorErrorCode.GEN_MAJ_COL_001.code,
                message = MajorErrorCode.GEN_MAJ_COL_001.message
            ))
    }

}