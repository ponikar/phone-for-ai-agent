package com.phonecontroller.models

import kotlinx.serialization.Serializable

@Serializable
data class UiNode(
    val text: String? = null,
    val description: String? = null,
    val viewId: String? = null,
    val className: String? = null,
    val packageName: String? = null,
    val clickable: Boolean = false,
    val enabled: Boolean = false,
    val focused: Boolean = false,
    val bounds: List<Int>? = null
)
