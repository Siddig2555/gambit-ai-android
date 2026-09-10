package com.gambitai.engine

object Symbols {
    val keys = listOf("C","T","P","R","S","W","F","H","Z","L")
    val emoji = mapOf(
        "C" to "🌽", "T" to "🍅", "P" to "🌶️", "R" to "🥕", "S" to "🍤",
        "W" to "🐄", "F" to "🐟", "H" to "🐔", "Z" to "🍕", "L" to "🍇"
    )
    val opposite = mapOf("T" to "C","C" to "T","W" to "S","S" to "W",
        "P" to "R","R" to "P","L" to "Z","Z" to "L","F" to "H","H" to "F")
    val colorFamily = mapOf("T" to "P","P" to "T","C" to "R","R" to "C")
    val meat = setOf("S","W","F","H")
}
