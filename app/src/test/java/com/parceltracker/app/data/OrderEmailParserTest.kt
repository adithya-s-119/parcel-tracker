package com.parceltracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class OrderEmailParserTest {

    private fun message(
        subject: String,
        from: String,
        body: String,
        received: LocalDate,
    ) = GmailMessage(
        id = "message-1",
        threadId = "thread-1",
        subject = subject,
        from = from,
        receivedDate = received,
        receivedEpochMillis = received.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        snippet = body.take(120),
        bodyText = body,
    )

    @Test
    fun `parses an Amazon shipping email`() {
        val parsed = OrderEmailParser.parse(
            message(
                subject = "Your Amazon.in order of \"boAt Rockerz 450\" has been shipped",
                from = "Amazon.in <ship-update@amazon.in>",
                body = "Your package is arriving Thursday, November 12. " +
                    "Order # 171-1234567-1234567. Track your package for the latest updates.",
                received = LocalDate.of(2026, 11, 8),
            ),
        )

        assertNotNull(parsed)
        assertEquals("Amazon", parsed!!.merchant)
        assertEquals("boAt Rockerz 450", parsed.itemName)
        assertEquals("171-1234567-1234567", parsed.orderNumber)
        assertEquals(LocalDate.of(2026, 11, 12), parsed.promisedDate)
        assertEquals(OrderStatus.SHIPPED, parsed.status)
    }

    @Test
    fun `parses a delivered Flipkart email`() {
        val parsed = OrderEmailParser.parse(
            message(
                subject = "Your order has been delivered",
                from = "Flipkart <no-reply@flipkart.com>",
                body = "Delivered on 08 Nov 2026. Order ID: OD123456789012345000. " +
                    "We hope you enjoy your purchase.",
                received = LocalDate.of(2026, 11, 8),
            ),
        )

        assertNotNull(parsed)
        assertEquals("Flipkart", parsed!!.merchant)
        assertEquals("OD123456789012345000", parsed.orderNumber)
        assertEquals(LocalDate.of(2026, 11, 8), parsed.promisedDate)
        assertEquals(OrderStatus.DELIVERED, parsed.status)
    }

    @Test
    fun `ignores cinema and event bookings`() {
        for (from in listOf("PVR Cinemas <noreply@pvr.in>", "BookMyShow <no-reply@bookmyshow.com>")) {
            val parsed = OrderEmailParser.parse(
                message(
                    subject = "Food Purchase Confirmation",
                    from = from,
                    body = "Order ID: 500260159943419. Show on 12 November 2026. " +
                        "Enjoy your snacks at the cinema.",
                    received = LocalDate.of(2026, 11, 10),
                ),
            )

            assertNull(parsed)
        }
    }

    @Test
    fun `anchors a delivered email's date to the email day`() {
        val received = LocalDate.of(2026, 11, 10)
        val parsed = OrderEmailParser.parse(
            message(
                subject = "Your Swiggy order was successfully delivered",
                from = "Swiggy <noreply@swiggy.in>",
                body = "Order # 252426828133601. Next delivery is 21 June 2027.",
                received = received,
            ),
        )

        assertEquals(OrderStatus.DELIVERED, parsed?.status)
        assertEquals(received, parsed?.promisedDate)
    }

    @Test
    fun `detects successfully delivered in the subject`() {
        val parsed = OrderEmailParser.parse(
            message(
                subject = "Your Swiggy order was successfully delivered",
                from = "Swiggy <noreply@swiggy.in>",
                body = "Enjoy your meal. Order # 252426828133601.",
                received = LocalDate.of(2026, 11, 10),
            ),
        )

        assertEquals(OrderStatus.DELIVERED, parsed?.status)
    }

    @Test
    fun `parses an order confirmation with a numeric date`() {
        val parsed = OrderEmailParser.parse(
            message(
                subject = "Order confirmed: Nike Air Zoom",
                from = "Nike <orders@nike.com>",
                body = "Thanks for your order. Expected delivery: 25/11/2026. Order No: 1234567890.",
                received = LocalDate.of(2026, 11, 10),
            ),
        )

        assertNotNull(parsed)
        assertEquals("Nike", parsed!!.merchant)
        assertEquals(LocalDate.of(2026, 11, 25), parsed.promisedDate)
        assertEquals(OrderStatus.UPCOMING, parsed.status)
        assertEquals("1234567890", parsed.orderNumber)
    }

    @Test
    fun `uses the end of a delivery window`() {
        val parsed = OrderEmailParser.parse(
            message(
                subject = "Your Amazon order is arriving",
                from = "Amazon <order-update@amazon.com>",
                body = "Arriving Nov 12 - Nov 15. Order # 171-1234567-1234567.",
                received = LocalDate.of(2026, 11, 8),
            ),
        )

        assertEquals(LocalDate.of(2026, 11, 15), parsed?.promisedDate)
    }

    @Test
    fun `does not mistake a return window for a delivery date`() {
        val parsed = OrderEmailParser.parse(
            message(
                subject = "Your order is on its way",
                from = "Amazon <order-update@amazon.com>",
                body = "Delivery by Nov 12. Return by Nov 20. Order # 171-1234567-1234567.",
                received = LocalDate.of(2026, 11, 8),
            ),
        )

        assertEquals(LocalDate.of(2026, 11, 12), parsed?.promisedDate)
    }

    @Test
    fun `understands tomorrow`() {
        val parsed = OrderEmailParser.parse(
            message(
                subject = "Your package is arriving tomorrow",
                from = "Amazon <order-update@amazon.com>",
                body = "Arriving tomorrow by 8 PM. Order # 1234567890.",
                received = LocalDate.of(2026, 11, 10),
            ),
        )

        assertEquals(LocalDate.of(2026, 11, 11), parsed?.promisedDate)
    }

    @Test
    fun `understands a weekday delivery`() {
        val parsed = OrderEmailParser.parse(
            message(
                subject = "Delivery update for your order",
                from = "Amazon <order-update@amazon.com>",
                body = "Delivery by Tuesday. Order # 1234567890.",
                received = LocalDate.of(2026, 11, 9),
            ),
        )

        assertEquals(LocalDate.of(2026, 11, 10), parsed?.promisedDate)
    }

    @Test
    fun `delivered email without a date is still useful`() {
        val parsed = OrderEmailParser.parse(
            message(
                subject = "Delivered: Your Amazon.in order",
                from = "Amazon.in <ship-update@amazon.in>",
                body = "Your package was delivered. We hope you enjoy it.",
                received = LocalDate.of(2026, 11, 8),
            ),
        )

        assertNotNull(parsed)
        assertEquals(OrderStatus.DELIVERED, parsed!!.status)
        assertNull(parsed.promisedDate)
    }

    @Test
    fun `ignores promotional emails that mention delivery dates`() {
        val parsed = OrderEmailParser.parse(
            message(
                subject = "Amazon Great Indian Sale is live",
                from = "Amazon.in <deals@amazon.in>",
                body = "Delivery by 12 Nov on select items. Shop now and save big.",
                received = LocalDate.of(2026, 11, 8),
            ),
        )

        assertNull(parsed)
    }

    @Test
    fun `ignores unrelated email with the word order`() {
        val parsed = OrderEmailParser.parse(
            message(
                subject = "Your order of operations guide",
                from = "Newsletter <hello@example.com>",
                body = "Download the guide today and learn something new.",
                received = LocalDate.of(2026, 11, 8),
            ),
        )

        assertNull(parsed)
    }

    @Test
    fun `recognises hobby and electronics store orders`() {
        val stores = mapOf(
            "orders@robu.in" to "Robu",
            "orders@robocraze.com" to "Robocraze",
            "orders@hubtronics.in" to "Hubtronics",
            "orders@xcluma.com" to "Xcluma",
            "orders@probots.co.in" to "Probots",
            "orders@sharvielectronics.com" to "Sharvie Electronics",
            "orders@flyrobo.in" to "FlyRobo",
            "orders@robokits.co.in" to "Robokits",
            "orders@thinkrobotics.in" to "ThinkRobotics",
            "orders@rhydolabz.com" to "RhydoLABZ",
        )

        for ((from, merchant) in stores) {
            val parsed = OrderEmailParser.parse(
                message(
                    subject = "Order Confirmation #RB-100234",
                    from = "Store <$from>",
                    body = "Thank you for your order. Order ID: RB-100234. " +
                        "Expected delivery: 14 November 2026.",
                    received = LocalDate.of(2026, 11, 10),
                ),
            )

            assertEquals(merchant, parsed?.merchant)
        }
    }

    @Test
    fun `expands a truncated subject product name from the email body`() {
        val parsed = OrderEmailParser.parse(
            message(
                subject = "Delivered: “STYLEDOSE Women's Diamond...”",
                from = "Amazon <ship-update@amazon.in>",
                body = "STYLEDOSE Women's Diamond Stud Earrings for Women\n" +
                    "Quantity: 1\nOrder # 403-8725311-7564316\nDelivered today.",
                received = LocalDate.of(2026, 11, 10),
            ),
        )

        assertEquals(
            "STYLEDOSE Women's Diamond Stud Earrings for Women",
            parsed?.itemName,
        )
        assertEquals(OrderStatus.DELIVERED, parsed?.status)
    }

    @Test
    fun `does not treat a progress timeline as out for delivery`() {
        val parsed = OrderEmailParser.parse(
            message(
                subject = "Delivery update for your Amazon order",
                from = "Amazon <ship-update@amazon.in>",
                body = "Your order is on its way. Progress: Ordered, Shipped, " +
                    "Out for delivery, Delivered. Order # 171-1234567-1234567.",
                received = LocalDate.of(2026, 11, 10),
            ),
        )

        assertEquals(OrderStatus.SHIPPED, parsed?.status)
    }

    @Test
    fun `trusts an ordered subject over the email timeline`() {
        val parsed = OrderEmailParser.parse(
            message(
                subject = "Ordered: “Test Product”",
                from = "Amazon <ship-update@amazon.in>",
                body = "Arriving 12 November. Progress: Ordered, Shipped, " +
                    "Out for delivery, Delivered. Order # 171-1234567-1234567.",
                received = LocalDate.of(2026, 11, 10),
            ),
        )

        assertEquals(OrderStatus.UPCOMING, parsed?.status)
    }

    @Test
    fun `detects a failed delivery attempt`() {
        val parsed = OrderEmailParser.parse(
            message(
                subject = "Delivery attempt failed for your Amazon order",
                from = "Amazon <ship-update@amazon.in>",
                body = "We attempted delivery today but no one was available. " +
                    "Order # 171-1234567-1234567.",
                received = LocalDate.of(2026, 11, 10),
            ),
        )

        assertEquals(OrderStatus.DELIVERY_FAILED, parsed?.status)
    }

    @Test
    fun `detects a delayed delivery and its new date`() {
        val parsed = OrderEmailParser.parse(
            message(
                subject = "Your Amazon order is delayed",
                from = "Amazon <ship-update@amazon.in>",
                body = "The new delivery date is 15 November. Order # 171-1234567-1234567.",
                received = LocalDate.of(2026, 11, 10),
            ),
        )

        assertEquals(OrderStatus.DELAYED, parsed?.status)
        assertEquals(LocalDate.of(2026, 11, 15), parsed?.promisedDate)
    }

    @Test
    fun `detects ready for pickup`() {
        val parsed = OrderEmailParser.parse(
            message(
                subject = "Your package is ready for pickup",
                from = "Amazon <ship-update@amazon.in>",
                body = "Collect your package from the Amazon Hub. Order # 171-1234567-1234567.",
                received = LocalDate.of(2026, 11, 10),
            ),
        )

        assertEquals(OrderStatus.READY_FOR_PICKUP, parsed?.status)
    }

    @Test
    fun `detects returned to sender`() {
        val parsed = OrderEmailParser.parse(
            message(
                subject = "Your package was returned to sender",
                from = "Amazon <ship-update@amazon.in>",
                body = "The package could not be delivered and was returned to sender. " +
                    "Order # 171-1234567-1234567.",
                received = LocalDate.of(2026, 11, 10),
            ),
        )

        assertEquals(OrderStatus.RETURNED, parsed?.status)
    }

    @Test
    fun `extracts carrier and tracking number`() {
        val parsed = OrderEmailParser.parse(
            message(
                subject = "Your order has shipped",
                from = "Myntra <orders@myntra.com>",
                body = "Your order has shipped via Delhivery. Tracking ID: 123456789012. " +
                    "Expected delivery: 12 November 2026. Order No: 9876543210.",
                received = LocalDate.of(2026, 11, 8),
            ),
        )

        assertNotNull(parsed)
        assertEquals("Delhivery", parsed!!.carrier)
        assertEquals("123456789012", parsed.trackingNumber)
        assertEquals(LocalDate.of(2026, 11, 12), parsed.promisedDate)
        assertTrue(parsed.emailSubject.contains("shipped"))
    }
}
