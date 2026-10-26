package com.smarttransparentmess.erp.data.erp

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ExtendedErpDtosTest {
    @Test
    fun adminNotificationBatchHistoryIsMapped() {
        val dto = Json.decodeFromString<ExtendedErpSnapshotDto>(
            """
            {
              "notification_batches": [
                {
                  "id": "batch-1",
                  "audience": "MEMBER",
                  "target_member_id": "member-1",
                  "target_member_name": "Member One",
                  "actor_name": "Main Admin",
                  "title": "Meal update",
                  "body": "Lunch starts at 2 PM.",
                  "recipient_count": 1,
                  "created_at": "2026-10-25T10:00:00Z"
                }
              ]
            }
            """.trimIndent(),
        )

        val batch = dto.toDomain("2026-10-25T10:01:00Z").notificationBatches.single()
        assertEquals("batch-1", batch.id)
        assertEquals("MEMBER", batch.text("audience"))
        assertEquals("Member One", batch.text("target_member_name"))
        assertEquals(1L, batch.long("recipient_count"))
    }
}
