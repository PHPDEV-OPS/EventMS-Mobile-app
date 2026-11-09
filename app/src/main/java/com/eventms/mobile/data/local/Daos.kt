package com.eventms.mobile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Query("SELECT * FROM cached_events WHERE ownedByOrganizer = 0 ORDER BY startDate ASC")
    fun observePublicEvents(): Flow<List<CachedEventEntity>>

    @Query("SELECT * FROM cached_events WHERE ownedByOrganizer = 1 ORDER BY startDate DESC")
    fun observeOrganizerEvents(): Flow<List<CachedEventEntity>>

    @Query("SELECT * FROM cached_events WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): CachedEventEntity?

    /** Other events by the same organizer — powers the "past events" section. */
    @Query(
        "SELECT * FROM cached_events WHERE organizerId = :organizerId AND id != :excludeEventId " +
            "ORDER BY startDate DESC"
    )
    suspend fun findByOrganizer(organizerId: Long, excludeEventId: Long): List<CachedEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(events: List<CachedEventEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: CachedEventEntity)

    @Query("DELETE FROM cached_events WHERE ownedByOrganizer = :ownedByOrganizer")
    suspend fun clear(ownedByOrganizer: Boolean)

    @Query("DELETE FROM cached_events WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface TicketDao {

    @Query("SELECT * FROM cached_tickets ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CachedTicketEntity>>

    @Query("SELECT * FROM cached_tickets WHERE bookingId = :bookingId LIMIT 1")
    fun observeOne(bookingId: String): Flow<CachedTicketEntity?>

    @Query("SELECT * FROM cached_tickets WHERE bookingId = :bookingId LIMIT 1")
    suspend fun findById(bookingId: String): CachedTicketEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tickets: List<CachedTicketEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(ticket: CachedTicketEntity)

    @Query("DELETE FROM cached_tickets")
    suspend fun clear()
}

@Dao
interface RosterDao {

    @Query("SELECT * FROM roster_entries WHERE eventId = :eventId ORDER BY email ASC")
    fun observeForEvent(eventId: Long): Flow<List<RosterEntryEntity>>

    /** Ticket-number lookup used by the scanner while offline. */
    @Query("SELECT * FROM roster_entries WHERE ticketNumber = :ticketNumber LIMIT 1")
    suspend fun findByTicketNumber(ticketNumber: String): RosterEntryEntity?

    @Query("SELECT * FROM roster_entries WHERE bookingId = :bookingId LIMIT 1")
    suspend fun findByBookingId(bookingId: String): RosterEntryEntity?

    @Query("SELECT COUNT(*) FROM roster_entries WHERE eventId = :eventId AND checkedInAt IS NOT NULL")
    fun observeCheckedInCount(eventId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM roster_entries WHERE eventId = :eventId")
    fun observeTotalCount(eventId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<RosterEntryEntity>)

    @Query("UPDATE roster_entries SET checkedInAt = :checkedInAt WHERE bookingId = :bookingId")
    suspend fun markCheckedIn(bookingId: String, checkedInAt: String)

    @Query("DELETE FROM roster_entries WHERE eventId = :eventId")
    suspend fun clearEvent(eventId: Long)
}

@Dao
interface PendingCheckInDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(entry: PendingCheckInEntity): Long

    @Query("SELECT * FROM pending_check_ins WHERE synced = 0 ORDER BY scannedAt ASC")
    suspend fun unsynced(): List<PendingCheckInEntity>

    @Query("SELECT COUNT(*) FROM pending_check_ins WHERE synced = 0")
    fun observeUnsyncedCount(): Flow<Int>

    @Query("UPDATE pending_check_ins SET synced = 1, lastError = NULL WHERE id = :id")
    suspend fun markSynced(id: Long)

    @Query("UPDATE pending_check_ins SET lastError = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String)

    @Query("DELETE FROM pending_check_ins WHERE synced = 1")
    suspend fun clearSynced()
}
