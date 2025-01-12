package com.muditsahni.documentstore

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication
@EnableAsync(proxyTargetClass = true)
class Application

fun main(args: Array<String>) {
	runApplication<Application>(*args)
}
