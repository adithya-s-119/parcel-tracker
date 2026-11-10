package com.parceltracker.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/**
 * Rule-based parser for order and delivery emails.
 *
 * It deliberately favours precision over recall: a missed date can be added by hand,
 * but a wrong date on the calendar is much more annoying.
 */
object OrderEmailParser {

    data class ParsedOrder(
        val merchant: String,
        val itemName: String?,
        val orderNumber: String?,
        val trackingNumber: String?,
        val carrier: String?,
        val promisedDate: LocalDate?,
        val status: OrderStatus,
        val emailSubject: String,
        val snippet: String,
        val emailDate: LocalDate,
    )

    private const val MONTH =
        "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"

    private val monthFirst = Regex(
        "(?i)\\b$MONTH\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?(?!\\d)(?:,?\\s+(\\d{4}))?",
    )
    private val dayFirst = Regex(
        "(?i)\\b(\\d{1,2})(?:st|nd|rd|th)?(?!\\d)\\s+$MONTH\\.?(?:,?\\s+(\\d{4}))?",
    )
    private val isoDate = Regex("(?i)\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b")
    private val numericDatePattern = Regex("(?i)\\b(\\d{1,2})[/.\\-](\\d{1,2})(?:[/.\\-](\\d{2,4}))?\\b")

    private val fullMonthRange = Regex(
        "(?i)\\b$MONTH\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s+(\\d{4}))?\\s*(?:-|–|—|to|through|until)\\s*$MONTH\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s+(\\d{4}))?",
    )
    private val sameMonthRange = Regex(
        "(?i)\\b$MONTH\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?\\s*(?:-|–|—|to|through|until)\\s*(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s+(\\d{4}))?",
    )
    private val dayRange = Regex(
        "(?i)\\b(\\d{1,2})(?:st|nd|rd|th)?\\s*(?:-|–|—|to|through|until)\\s*(\\d{1,2})(?:st|nd|rd|th)?\\s+$MONTH\\.?(?:,?\\s+(\\d{4}))?",
    )
    private val dayFirstRange = Regex(
        "(?i)\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+$MONTH\\.?(?:,?\\s+(\\d{4}))?\\s*(?:-|–|—|to|through|until)\\s*(\\d{1,2})(?:st|nd|rd|th)?\\s+$MONTH\\.?(?:,?\\s+(\\d{4}))?",
    )

    private val weekdayDate = Regex(
        "(?i)\\b(?:arriv(?:e|es|ing)|deliver(?:y|ed|s)?|get it|expected|scheduled)\\b[^.\\n]{0,60}?\\b(?:on\\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b",
    )
    private val relativeDay = Regex(
        "(?i)\\b(?:arriv(?:e|es|ing)|deliver(?:y|ed|s)?|get it|expected|scheduled)\\b[^.\\n]{0,50}?\\b(today|tomorrow)\\b",
    )
    private val withinDays = Regex(
        "(?i)\\b(?:deliver(?:y|ed|s)?|arriv(?:e|es|ing)|get it)\\b[^.\\n]{0,50}?\\b(?:within|in)\\s+(\\d{1,2})(?:\\s*(?:-|–|to)\\s*(\\d{1,2}))?\\s*(?:business\\s+)?days?\\b",
    )

    private val deliveryContext = Regex(
        "(?i)\\b(arriv(?:e|es|ing)|deliver(?:y|ed|s|ing)?|dispatch(?:ed|ing)?|ship(?:ped|ping|s)?|expected|estimated|guaranteed|scheduled|get it|package|parcel|on its way|out for delivery)\\b",
    )
    private val strongDeliveryContext = Regex(
        "(?i)(arriv\\w*|delivery (?:by|on|date|estimate)|deliver(?:ed)? (?:by|on)|expected|estimated|guaranteed|get it by|will be delivered|scheduled to arrive|out for delivery|on its way)",
    )
    private val exclusionContext = Regex(
        "(?i)\\b(return|refund|exchange|order(?:ed)? (?:on|date)|placed on|purchase date|billing|invoice|statement|expires?|valid (?:until|through)|offer ends|coupon|promotion|payment|deliver to|shipping address|review by|warranty|subscription (?:renews|ends)|due date)\\b",
    )
    private val marketingContext = Regex(
        "(?i)\\b(return|refund|exchange|warranty|review|unsubscribe|privacy policy|terms|sale|offer|coupon|discount)\\b",
    )

    private val urlPattern = Regex("(?i)https?://\\S+|www\\.\\S+")
    private val whitespace = Regex("[\\t\\r]+")
    private val multiSpace = Regex(" {2,}")

    private val orderPatterns = listOf(
        Regex("(?i)\\border\\s*(?:id|no\\.?|number|#)\\s*[:#\\-]?\\s*([A-Z0-9][A-Z0-9\\-]{5,35})"),
        Regex("(?i)\\b(?:order|reference)\\s*[:#\\-]\\s*([A-Z0-9][A-Z0-9\\-]{5,35})"),
        Regex("\\b\\d{3}-\\d{7}-\\d{7}\\b"),
        Regex("(?i)\\bOD\\d{10,}\\b"),
    )

    private val trackingPattern = Regex(
        "(?i)\\b(?:tracking|awb|consignment)\\s*(?:id|no\\.?|number|#)?\\s*[:#\\-]?\\s*([A-Z0-9]{8,30})\\b",
    )

    private val itemPatterns = listOf(
        Regex("(?i)\\border of\\s+[\"“']?(.{3,100}?)[\"”']?\\s+(?:has|is|was|will)\\b"),
        Regex("(?i)\\byou(?:'ve| have)? ordered\\s*[:\\-]?\\s*[\"“']?(.{3,100})"),
        Regex("(?i)\\bitem(?: name)?\\s*[:\\-]\\s*(.{3,100})"),
        Regex("(?i)\\bproduct(?: name)?\\s*[:\\-]\\s*(.{3,100})"),
        Regex("(?i)\\bpackage contains\\s*[:\\-]?\\s*(.{3,100})"),
        Regex("(?i)\\bqty\\s*[:\\-]?\\s*\\d+\\s*[x×]\\s*(.{3,100})"),
        Regex("(?i)\\b(?:1|one) (?:item|product)\\s*[:\\-]\\s*(.{3,100})"),
    )

    private val knownMerchants = listOf(
        "amazon" to "Amazon",
        "flipkart" to "Flipkart",
        "myntra" to "Myntra",
        "ajio" to "AJIO",
        "meesho" to "Meesho",
        "nykaa" to "Nykaa",
        "snapdeal" to "Snapdeal",
        "tatacliq" to "Tata CLiQ",
        "croma" to "Croma",
        "reliancedigital" to "Reliance Digital",
        "jiomart" to "JioMart",
        "bigbasket" to "BigBasket",
        "zepto" to "Zepto",
        "blinkit" to "Blinkit",
        "swiggy" to "Swiggy",
        "zomato" to "Zomato",
        "decathlon" to "Decathlon",
        "ikea" to "IKEA",
        "ebay" to "eBay",
        "walmart" to "Walmart",
        "target" to "Target",
        "bestbuy" to "Best Buy",
        "etsy" to "Etsy",
        "shopify" to "Shopify",
        "apple" to "Apple",
        "samsung" to "Samsung",
        "oneplus" to "OnePlus",
        "boat-lifestyle" to "boAt",
        "delhivery" to "Delhivery",
        "bluedart" to "Blue Dart",
        "fedex" to "FedEx",
        "usps" to "USPS",
        "dhl" to "DHL",
    )

    private val carriers = listOf(
        "amazon logistics" to "Amazon Logistics",
        "amzl" to "Amazon Logistics",
        "delhivery" to "Delhivery",
        "blue dart" to "Blue Dart",
        "bluedart" to "Blue Dart",
        "ekart" to "Ekart",
        "dtdc" to "DTDC",
        "india post" to "India Post",
        "speed post" to "India Post",
        "fedex" to "FedEx",
        "ups" to "UPS",
        "usps" to "USPS",
        "dhl" to "DHL",
        "royal mail" to "Royal Mail",
        "evri" to "Evri",
        "australia post" to "Australia Post",
    )

    private data class DateCandidate(
        val date: LocalDate,
        val index: Int,
        val score: Int,
    )

    fun parse(message: GmailMessage): ParsedOrder? {
        val body = if (HtmlText.looksLikeHtml(message.bodyText)) {
            HtmlText.toPlain(message.bodyText)
        } else {
            message.bodyText
        }

        val combined = "${message.subject}\n$body"
            .replace('\u00A0', ' ')
            .let { urlPattern.replace(it, " ") }
            .replace(whitespace, " ")
            .replace(multiSpace, " ")
            .take(30_000)

        val status = detectStatus(message.subject, body)
        val orderNumber = extractOrderNumber(combined)
        val promisedDate = extractDeliveryDate(combined, message.receivedDate, status)
        val merchant = merchantFor(message.from)
        val trackingNumber = extractTrackingNumber(combined)
        val carrier = detectCarrier(combined)
        val itemName = extractItemName(message.subject, body)

        val orderEvidence = Regex(
            "(?i)\\b(order confirmation|order confirmed|thank you for your order|your order|your package|your shipment|order (?:#|no\\.?|number)|order id|has (?:been )?shipped|has (?:been )?delivered|was delivered|out for delivery|will be delivered|arriving|scheduled to arrive|on its way|dispatched)\\b",
        ).containsMatchIn("${message.subject}\n${body.take(6_000)}")

        val hasOrderCue = orderNumber != null
        val hasDate = promisedDate != null
        val isOrderEmail = hasOrderCue ||
            (orderEvidence && (hasDate || status != OrderStatus.UPCOMING))

        if (!isOrderEmail) return null

        // A shipping update with no date and no order number cannot add anything useful.
        if (promisedDate == null && orderNumber == null &&
            status != OrderStatus.DELIVERED && status != OrderStatus.CANCELLED
        ) {
            return null
        }

        return ParsedOrder(
            merchant = merchant,
            itemName = itemName,
            orderNumber = orderNumber,
            trackingNumber = trackingNumber,
            carrier = carrier,
            promisedDate = promisedDate,
            status = status,
            emailSubject = message.subject,
            snippet = HtmlText.normalizeSpace(body).take(220),
            emailDate = message.receivedDate,
        )
    }

    private fun extractDeliveryDate(
        text: String,
        received: LocalDate,
        status: OrderStatus,
    ): LocalDate? {
        val candidates = mutableListOf<DateCandidate>()

        fun add(date: LocalDate?, index: Int, bonus: Int = 0) {
            if (date == null) return
            candidates += DateCandidate(date, index, scoreAt(text, index, date, received, status) + bonus)
        }

        // Date ranges: use the last promised day.
        fullMonthRange.findAll(text).forEach { match ->
            val month1 = monthNumber(match.groupValues[1]) ?: return@forEach
            val month2 = monthNumber(match.groupValues[4]) ?: month1
            val year = yearOrNull(match.groupValues[6])
                ?: yearOrNull(match.groupValues[3])
                ?: received.year
            val end = resolveDate(year, month2, match.groupValues[5].toIntOrNull(), received)
            add(end, match.range.first, bonus = 4)
        }

        sameMonthRange.findAll(text).forEach { match ->
            val month = monthNumber(match.groupValues[1]) ?: return@forEach
            val year = yearOrNull(match.groupValues[4]) ?: received.year
            val end = resolveDate(year, month, match.groupValues[3].toIntOrNull(), received)
            add(end, match.range.first, bonus = 4)
        }

        dayRange.findAll(text).forEach { match ->
            val month = monthNumber(match.groupValues[3]) ?: return@forEach
            val year = yearOrNull(match.groupValues[4]) ?: received.year
            val end = resolveDate(year, month, match.groupValues[2].toIntOrNull(), received)
            add(end, match.range.first, bonus = 4)
        }

        dayFirstRange.findAll(text).forEach { match ->
            val month = monthNumber(match.groupValues[5])
                ?: monthNumber(match.groupValues[2])
                ?: return@forEach
            val year = yearOrNull(match.groupValues[6])
                ?: yearOrNull(match.groupValues[3])
                ?: received.year
            val end = resolveDate(year, month, match.groupValues[4].toIntOrNull(), received)
            add(end, match.range.first, bonus = 4)
        }

        isoDate.findAll(text).forEach { match ->
            val date = resolveDate(
                match.groupValues[1].toIntOrNull(),
                match.groupValues[2].toIntOrNull(),
                match.groupValues[3].toIntOrNull(),
                received,
            )
            add(date, match.range.first, bonus = 2)
        }

        monthFirst.findAll(text).forEach { match ->
            val month = monthNumber(match.groupValues[1]) ?: return@forEach
            val year = yearOrNull(match.groupValues[3])
            val date = resolveDate(year, month, match.groupValues[2].toIntOrNull(), received)
            add(date, match.range.first, bonus = 2)
        }

        dayFirst.findAll(text).forEach { match ->
            val month = monthNumber(match.groupValues[2]) ?: return@forEach
            val year = yearOrNull(match.groupValues[3])
            val date = resolveDate(year, month, match.groupValues[1].toIntOrNull(), received)
            add(date, match.range.first, bonus = 2)
        }

        numericDatePattern.findAll(text).forEach { match ->
            val first = match.groupValues[1].toIntOrNull() ?: return@forEach
            val second = match.groupValues[2].toIntOrNull() ?: return@forEach
            val year = yearOrNull(match.groupValues[3])
            val date = resolveNumericDate(first, second, year, received) ?: return@forEach
            add(date, match.range.first, bonus = 1)
        }

        weekdayDate.findAll(text).forEach { match ->
            val target = runCatching {
                DayOfWeek.valueOf(match.groupValues[1].uppercase(Locale.ENGLISH))
            }.getOrNull() ?: return@forEach

            val date = if (status == OrderStatus.DELIVERED) {
                val back = (received.dayOfWeek.value - target.value + 7) % 7
                received.minusDays(back.toLong())
            } else {
                val ahead = (target.value - received.dayOfWeek.value + 7) % 7
                received.plusDays(ahead.toLong())
            }
            add(date, match.range.first, bonus = 1)
        }

        relativeDay.findAll(text).forEach { match ->
            val date = when (match.groupValues[1].lowercase(Locale.ENGLISH)) {
                "today" -> received
                "tomorrow" -> received.plusDays(1)
                else -> null
            }
            add(date, match.range.first, bonus = 5)
        }

        withinDays.findAll(text).forEach { match ->
            val days = match.groupValues[2].toIntOrNull() ?: match.groupValues[1].toIntOrNull()
            if (days != null && days in 0..60) {
                add(received.plusDays(days.toLong()), match.range.first, bonus = 3)
            }
        }

        val minScore = if (status == OrderStatus.DELIVERED) 1 else 2
        val best = candidates
            .filter { it.score >= minScore }
            .sortedWith(
                compareByDescending<DateCandidate> { it.score }
                    .thenByDescending { if (it.date.isBefore(received)) 0 else 1 }
                    .thenBy { abs(ChronoUnit.DAYS.between(received, it.date)) }
                    .thenBy { it.index },
            )
            .firstOrNull()

        return best?.date
    }

    private fun scoreAt(
        text: String,
        index: Int,
        date: LocalDate,
        received: LocalDate,
        status: OrderStatus,
    ): Int {
        val start = (index - 110).coerceAtLeast(0)
        val before = text.substring(start, index).lowercase(Locale.ENGLISH)
        val after = text.substring(index, (index + 60).coerceAtMost(text.length))
            .lowercase(Locale.ENGLISH)
        val window = "$before $after"

        var score = 0
        if (deliveryContext.containsMatchIn(before.takeLast(100))) score += 4
        if (deliveryContext.containsMatchIn(after.take(50))) score += 1
        if (strongDeliveryContext.containsMatchIn(before.takeLast(90))) score += 2
        if (exclusionContext.containsMatchIn(before.takeLast(90))) score -= 9
        if (marketingContext.containsMatchIn(window)) score -= 3

        if (!date.isBefore(received)) {
            score += 2
        } else if (date.isBefore(received.minusDays(7))) {
            score -= 4
        }

        if (date.isAfter(received.plusDays(365))) score -= 4
        if (index < 400) score += 1
        if (status == OrderStatus.DELIVERED && Regex("(?i)\\bdelivered\\b").containsMatchIn(window)) {
            score += 3
        }

        return score
    }

    private fun resolveDate(
        year: Int?,
        month: Int?,
        day: Int?,
        received: LocalDate,
    ): LocalDate? {
        if (month == null || day == null) return null
        if (month !in 1..12 || day !in 1..31) return null

        val targetYear = year ?: received.year
        var date = runCatching { LocalDate.of(targetYear, month, day) }.getOrNull()

        if (date == null && year == null) {
            date = (1..4).firstNotNullOfOrNull { offset ->
                runCatching { LocalDate.of(targetYear + offset, month, day) }.getOrNull()
            }
        }

        if (date != null && year == null && date.isBefore(received.minusDays(30))) {
            date = runCatching { date!!.plusYears(1) }.getOrNull() ?: date
        }

        return date
    }

    private fun resolveNumericDate(
        first: Int,
        second: Int,
        year: Int?,
        received: LocalDate,
    ): LocalDate? {
        val dayFirst = Locale.getDefault().country.uppercase(Locale.ENGLISH)
            .let { it !in setOf("US", "CA", "PH") }

        val primary = if (dayFirst) {
            resolveDate(year, second, first, received)
        } else {
            resolveDate(year, first, second, received)
        }
        val secondary = if (dayFirst) {
            resolveDate(year, first, second, received)
        } else {
            resolveDate(year, second, first, received)
        }

        return when {
            primary == null -> secondary
            secondary == null -> primary
            primary.isBefore(received.minusDays(30)) &&
                !secondary.isBefore(received.minusDays(30)) -> secondary
            else -> primary
        }
    }

    private fun detectStatus(subject: String, body: String): OrderStatus {
        val subjectLower = subject.lowercase(Locale.ENGLISH).trim()
        val bodyLower = body.lowercase(Locale.ENGLISH).take(8_000)
        val all = "$subjectLower\n$bodyLower"

        val cancelled = subjectLower.startsWith("cancelled") ||
            subjectLower.startsWith("canceled") ||
            Regex(
                "(?i)\\b(order|item|shipment|delivery)\\b[^.]{0,80}\\b(?:has been|was|is|got) (?:cancelled|canceled)\\b",
            ).containsMatchIn(bodyLower) ||
            Regex("(?i)\\b(?:cancelled|canceled) (?:your|the) order\\b").containsMatchIn(bodyLower)

        if (cancelled) return OrderStatus.CANCELLED
        if (Regex("(?i)\\bout for delivery\\b").containsMatchIn(all)) {
            return OrderStatus.OUT_FOR_DELIVERY
        }

        val deliveredSubject = subjectLower.startsWith("delivered") ||
            Regex(
                "(?i)\\b(your )?(package|order|item|shipment|delivery)\\b[^.!\\n]{0,80}\\b(?:was|has been|is) delivered\\b",
            ).containsMatchIn(subjectLower)
        val deliveredBody = Regex(
            "(?i)\\b(?:has been|was|is|successfully) delivered\\b|\\bdelivered successfully\\b|\\bdelivery successful\\b|\\bdelivered on\\b",
        ).containsMatchIn(bodyLower)

        if (deliveredSubject || deliveredBody) return OrderStatus.DELIVERED

        if (Regex("(?i)\\b(shipped|dispatched|on its way|in transit|handed over to|picked up by)\\b")
                .containsMatchIn(all)
        ) {
            return OrderStatus.SHIPPED
        }

        return OrderStatus.UPCOMING
    }

    private fun extractOrderNumber(text: String): String? {
        for (pattern in orderPatterns) {
            val match = pattern.find(text) ?: continue
            val raw = match.groups[1]?.value ?: match.value
            cleanOrderNumber(raw)?.let { return it }
        }
        return null
    }

    private fun cleanOrderNumber(raw: String): String? {
        var value = raw.trim().trimEnd('.', ',', ')', ']', ';', ':', '!').uppercase(Locale.ENGLISH)
        value = value.replace(Regex("\\s+"), "")
        if (value.length !in 6..40) return null
        if (value.none { it.isDigit() }) return null
        if (Regex("^\\d{1,2}[/.-]\\d{1,2}").containsMatchIn(value)) return null
        return value
    }

    private fun extractTrackingNumber(text: String): String? {
        val match = trackingPattern.find(text) ?: return null
        return cleanOrderNumber(match.groupValues[1])
    }

    private fun detectCarrier(text: String): String? {
        val lower = text.lowercase(Locale.ENGLISH)
        return carriers.firstOrNull { lower.contains(it.first) }?.second
    }

    private fun extractItemName(subject: String, body: String): String? {
        val searchSpace = "${subject.take(250)}\n${body.take(8_000)}"
        for (pattern in itemPatterns) {
            val match = pattern.find(searchSpace) ?: continue
            cleanItem(match.groupValues[1])?.let { return it }
        }
        return null
    }

    private fun cleanItem(raw: String): String? {
        var value = raw.substringBefore('\n').substringBefore('|').trim()
        value = value.replace(
            Regex("(?i)\\s+(?:has been|was|is|will be|ships|arrives|arriving|delivered|shipped)\\b.*$"),
            "",
        )
        value = value.replace(
            Regex("(?i)\\s*(?:qty|quantity|sold by|size|colour|color)\\s*[:\\-].*$"),
            "",
        )
        value = value.trim().trim('"', '\'', '“', '”', ' ', '.', ',', ':', ';')
        value = HtmlText.normalizeSpace(value)

        if (value.length < 3) return null
        if (value.lowercase(Locale.ENGLISH) in GENERIC_ITEM_VALUES) return null
        return value.take(100)
    }

    private fun merchantFor(from: String): String {
        val lowerFrom = from.lowercase(Locale.ENGLISH)
        val email = Regex("<([^>]+)>").find(from)?.groupValues?.get(1)
            ?.trim()?.lowercase(Locale.ENGLISH)
            ?: lowerFrom
        val domain = email.substringAfter('@', email)

        knownMerchants.firstOrNull { (key, _) ->
            domain.contains(key) || lowerFrom.contains(key)
        }?.let { return it.second }

        val display = from.substringBefore('<').trim().trim('"')
            .replace(Regex("(?i)\\s+via\\s+.*$"), "")
        if (display.isNotBlank() && !display.contains("@") && display.length <= 50) {
            return display
        }

        val fallback = domain.substringBefore('.').replace("-", " ")
        return fallback.takeIf { it.isNotBlank() }
            ?.let { HtmlText.titleCase(it) }
            ?: "Unknown store"
    }

    private fun monthNumber(raw: String): Int? = when (raw.lowercase(Locale.ENGLISH).removeSuffix(".")) {
        "jan", "january" -> 1
        "feb", "february" -> 2
        "mar", "march" -> 3
        "apr", "april" -> 4
        "may" -> 5
        "jun", "june" -> 6
        "jul", "july" -> 7
        "aug", "august" -> 8
        "sep", "sept", "september" -> 9
        "oct", "october" -> 10
        "nov", "november" -> 11
        "dec", "december" -> 12
        else -> null
    }

    private fun yearOrNull(raw: String): Int? {
        val value = raw.toIntOrNull() ?: return null
        return when {
            value in 100..999 -> value
            value in 2000..2100 -> value
            value in 0..99 -> 2000 + value
            else -> null
        }
    }

    private val GENERIC_ITEM_VALUES = setOf(
        "your order",
        "order",
        "item",
        "items",
        "details",
        "confirmation",
        "your package",
        "your shipment",
    )
}
