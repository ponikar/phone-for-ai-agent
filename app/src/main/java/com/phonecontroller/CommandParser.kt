package com.phonecontroller

import com.phonecontroller.models.CommandWrapper
import kotlinx.serialization.json.Json

object CommandParser {

    private val json = Json { ignoreUnknownKeys = true }

    private val VALID_TYPES = setOf(
        "tap", "swipe", "back", "home", "get_ui_tree", "click_text", "click_description",
        "type", "wait", "long_press", "keyevent", "get_state"
    )

    fun parse(raw: String): Result<CommandWrapper> = runCatching {
        val cmd = json.decodeFromString<CommandWrapper>(raw)
        require(cmd.type in VALID_TYPES) { "unknown_command_type: ${cmd.type}" }
        require(cmd.id.isNotBlank()) { "missing command id" }
        validateParams(cmd)
        cmd
    }

    private fun validateParams(cmd: CommandWrapper) {
        when (cmd.type) {
            "tap" -> {
                require(cmd.x != null) { "tap requires x" }
                require(cmd.y != null) { "tap requires y" }
            }
            "swipe" -> {
                require(cmd.x1 != null) { "swipe requires x1" }
                require(cmd.y1 != null) { "swipe requires y1" }
                require(cmd.x2 != null) { "swipe requires x2" }
                require(cmd.y2 != null) { "swipe requires y2" }
            }
            "click_text" -> require(!cmd.text.isNullOrBlank()) { "click_text requires text" }
            "click_description" -> require(!cmd.description.isNullOrBlank()) { "click_description requires description" }
            "type" -> require(!cmd.text.isNullOrBlank()) { "type requires text" }
            "wait" -> require(cmd.ms != null && cmd.ms > 0) { "wait requires positive ms" }
            "long_press" -> {
                require(cmd.x != null) { "long_press requires x" }
                require(cmd.y != null) { "long_press requires y" }
            }
            "keyevent" -> require(cmd.key != null) { "keyevent requires key" }
            else -> { }
        }
    }
}
