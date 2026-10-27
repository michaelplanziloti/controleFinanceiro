package com.controlefinanceiro.app.ui.common

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

private val BR = Locale("pt", "BR")
private val currency: NumberFormat = NumberFormat.getCurrencyInstance(BR)

object Money {
    fun format(cents: Long): String = currency.format(cents / 100.0)

    fun formatPercent(value: Double): String = String.format(BR, "%.1f%%", value * 100)

    /** Text for an amount input field: "1234,56" (empty when zero). */
    fun toInput(cents: Long): String =
        if (cents == 0L) "" else String.format(BR, "%s%d,%02d", if (cents < 0) "-" else "", abs(cents) / 100, abs(cents) % 100)

    /** Accepts "1.234,56", "1234,5", "1234.56" or "R$ 1.234,56". Returns null when it is not a number. */
    fun parse(text: String): Long? {
        var clean = text.replace("R$", "").replace(" ", "").trim()
        if (clean.isEmpty()) return 0
        val lastComma = clean.lastIndexOf(',')
        val lastDot = clean.lastIndexOf('.')
        clean = when {
            lastComma > lastDot -> clean.replace(".", "").replace(',', '.')
            lastDot > lastComma && clean.count { it == '.' } > 1 -> clean.replace(".", "")
            else -> clean.replace(",", "")
        }
        return clean.toBigDecimalOrNull()?.movePointRight(2)?.setScale(0, java.math.RoundingMode.HALF_UP)?.toLong()
    }
}

val MONTH_NAMES = listOf(
    "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
    "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro",
)

fun monthLabel(year: Int, month: Int) = "${MONTH_NAMES[month - 1]} $year"

/** First name only, so cards stay on one line even with full names. */
fun shortName(name: String): String = name.trim().substringBefore(' ').ifEmpty { name }
