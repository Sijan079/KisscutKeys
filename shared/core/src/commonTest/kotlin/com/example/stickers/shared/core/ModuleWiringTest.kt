package com.example.stickers.shared.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleWiringTest {
    @Test
    fun coreSeesTheSingleDatabaseFileFromDb() {
        assertEquals("stickers.db", CoreInfo.databaseFileName)
    }
}
