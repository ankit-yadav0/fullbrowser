package com.example.model

data class Bookmark(
    val id: String,
    val url: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)
