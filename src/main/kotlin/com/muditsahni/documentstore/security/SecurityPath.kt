package com.muditsahni.documentstore.security

enum class SecurityPath(val pattern: String) {
    API("/api/**"),
    PROCESS_CALLBACK("/api/v1/tenants/*/collections/*/documents/*/process"),
    SSE("/api/v1/tenants/*/collections/*/sse"),
    HEALTH("/health"),
    DEV_TOKEN("/dev/token"),
    SWAGGER_UI("/swagger-ui/**"),
    API_DOCS("/api-docs/**");

    companion object {
        fun publicPaths() = listOf(
            PROCESS_CALLBACK,
            SSE,
            HEALTH,
            DEV_TOKEN,
            SWAGGER_UI,
            API_DOCS
        ).map { it.pattern }

        fun jwtAuthPaths() = listOf(
            PROCESS_CALLBACK,
            SSE

        ).map { it.pattern }

        private fun String.toRegexPattern(): Regex {
            return this.replace("/", "\\/")
                .replace("*", "[^/]+")
                .toRegex()
        }

        fun firebaseBypassPaths() = setOf(
            PROCESS_CALLBACK,
            SSE
        ).map { it.pattern.toRegexPattern() }
    }
}