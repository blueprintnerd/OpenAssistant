package com.blueprintnerd.openassistant.data

interface AssistantDataStore {
    val useFahrenheit: Boolean
    val defaultCity: String
    val searchEngine: SearchEngine
    val homeAssistantUrl: String
    val homeAssistantToken: String

    fun getShoppingList(): List<String>
    fun setShoppingList(items: List<String>)
    fun addShoppingItem(item: String): List<String>
    fun removeShoppingItem(item: String): List<String>
    fun getReminders(): List<ReminderItem>
    fun getRoutines(): List<Routine>
}
