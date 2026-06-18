package com.phonecontroller

object AccessibilityController {

    @Volatile
    var service: PhoneAccessibilityService? = null
        private set

    fun isEnabled(): Boolean = service != null

    fun register(service: PhoneAccessibilityService) {
        this.service = service
    }

    fun unregister() {
        this.service = null
    }
}
