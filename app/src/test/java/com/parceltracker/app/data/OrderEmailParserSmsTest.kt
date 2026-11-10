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
    fun `ignores an otp text`() {
        val parsed = OrderEmailParser.parseSms(
            address = "VK-BANK",
            body = "Your OTP is 123456. Do not share it with anyone.",
            receivedDate = LocalDate.of(2026, 11, 10),
        )

        assertNull(parsed)
    }
}
