package com.parceltracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class OrderEmailParserSmsTest {

    @Test
    fun `parses an out for delivery text`() {
        val today = LocalDate.of(2026, 11, 10)
        val parsed = OrderEmailParser.parseSms(
            address = "AD-AMAZON",
            body = "Your Amazon package is out for delivery. Arriving today.",
            receivedDate = today,
        )

        assertNotNull(parsed)
        assertEquals("Amazon", parsed!!.merchant)
        assertEquals(OrderStatus.OUT_FOR_DELIVERY, parsed.status)
        assertEquals(today, parsed.promisedDate)
    }

    @Test
    fun `parses a delivered text`() {
        val today = LocalDate.of(2026, 11, 10)
        val parsed = OrderEmailParser.parseSms(
            address = "VK-FLIPKART",
            body = "Your order has been delivered. Delivered today. Order ID: OD123456789012345000",
            receivedDate = today,
        )

        assertNotNull(parsed)
        assertEquals("Flipkart", parsed!!.merchant)
        assertEquals(OrderStatus.DELIVERED, parsed.status)
        assertEquals(today, parsed.promisedDate)
        assertEquals("OD123456789012345000", parsed.orderNumber)
    }

    @Test
    fun `parses a tomorrow delivery text`() {
        val today = LocalDate.of(2026, 11, 10)
        val parsed = OrderEmailParser.parseSms(
            address = "DELHIVERY",
            body = "Your package will be delivered tomorrow. Order 1234567890.",
            receivedDate = today,
        )

        assertNotNull(parsed)
        assertEquals(today.plusDays(1), parsed!!.promisedDate)
    }

    @Test
    fun `extracts a courier article number from an out for delivery text`() {
        val parsed = OrderEmailParser.parseSms(
            address = "AD-INPOST-S",
            body = "Item: CL657613435IN is out for delivery. Delivery will be attempted " +
                "by - Aiswarya S V (BEAT_07) - on 2026-11-06 - IndiaPost",
            receivedDate = LocalDate.of(2026, 11, 6),
        )

        assertNotNull(parsed)
        assertEquals("CL657613435IN", parsed!!.orderNumber)
        assertEquals(OrderStatus.OUT_FOR_DELIVERY, parsed.status)
    }

    @Test
    fun `extracts a courier article number from a delivered text`() {
        val parsed = OrderEmailParser.parseSms(
            address = "VM-INPOST-S",
            body = "Article JD591470963IN delivered on 2026-11-06 to Adithya. " +
                "Please share delivery feedback.",
            receivedDate = LocalDate.of(2026, 11, 6),
        )

        assertNotNull(parsed)
        assertEquals("JD591470963IN", parsed!!.orderNumber)
        assertEquals(OrderStatus.DELIVERED, parsed.status)
    }

    @Test
    fun `extracts an article number after a parcel code`() {
        val parsed = OrderEmailParser.parseSms(
            address = "VM-INPOST-S",
            body = "Delivery attempt for SP_INLAND_PARCEL No: JD591470963IN is Successful " +
                "on 06-11-2026 15:02:42 - IndiaPost",
            receivedDate = LocalDate.of(2026, 11, 6),
        )

        assertNotNull(parsed)
        assertEquals("JD591470963IN", parsed!!.orderNumber)
        assertEquals(OrderStatus.DELIVERED, parsed.status)
    }

    @Test
    fun `ignores an otp text`() {
        val parsed = OrderEmailParser.parseSms(
            address = "VK-BANK",
            body = "Your OTP is 123456. Do not share it with anyone.",
            receivedDate = LocalDate.of(2026, 11, 10),
        )

        assertNull(parsed)
    }
}
