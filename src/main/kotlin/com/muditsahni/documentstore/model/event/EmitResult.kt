package com.muditsahni.documentstore.model.event

sealed class EmitResult {
    object Success : EmitResult()
    object Closed : EmitResult()
    object NoSubscribers : EmitResult()
    object Overflow : EmitResult()
    object Unknown : EmitResult()
}
