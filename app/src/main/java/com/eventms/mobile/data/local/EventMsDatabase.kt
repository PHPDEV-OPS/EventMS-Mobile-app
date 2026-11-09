package com.eventms.mobile.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedEventEntity::class,
        CachedTicketEntity::class,
        RosterEntryEntity::class,
        PendingCheckInEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class EventMsDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun ticketDao(): TicketDao
    abstract fun rosterDao(): RosterDao
    abstract fun pendingCheckInDao(): PendingCheckInDao

    companion object {
        const val NAME = "eventms.db"
    }
}
