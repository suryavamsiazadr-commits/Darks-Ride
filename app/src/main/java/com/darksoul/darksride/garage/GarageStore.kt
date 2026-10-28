package com.darksoul.darksride.garage

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Bike(
    val name: String = "Himalayan 450",
    val tankLitres: Double = 17.0,
    val odometer: Int = 0
)

data class FuelFill(
    val id: Long,
    /** Day, as UTC-midnight millis (see [Days]). */
    val date: Long,
    val odometer: Int,
    val litres: Double,
    val cost: Double,
    val fullTank: Boolean
)

data class Expense(
    val id: Long,
    val date: Long,
    val category: String,
    val amount: Double,
    val note: String
)

data class Reminder(
    val id: Long,
    val title: String,
    val dueDate: Long?,
    val dueKm: Int?
)

data class Garage(
    val bike: Bike = Bike(),
    val fuel: List<FuelFill> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val reminders: List<Reminder> = emptyList()
)

val EXPENSE_CATEGORIES = listOf("Service", "Parts", "Gear", "Insurance", "Tolls", "Other")

/** Calendar days stored as UTC-midnight millis, so a date never shifts by time zone. */
object Days {
    private val FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

    fun today(): Long = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun toDate(day: Long): LocalDate = Instant.ofEpochMilli(day).atZone(ZoneOffset.UTC).toLocalDate()

    fun format(day: Long): String = toDate(day).format(FORMAT)

    fun daysFromToday(day: Long): Long = toDate(day).toEpochDay() - LocalDate.now().toEpochDay()
}

/**
 * The garage, saved as one small JSON file on this phone only.
 * Everything is in memory as a StateFlow; each change is written straight back.
 */
object GarageStore {
    private const val FILE = "garage.json"

    private val _state = MutableStateFlow(Garage())
    val state: StateFlow<Garage> = _state.asStateFlow()
    private var loaded = false

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val f = File(c.filesDir, FILE)
        if (f.exists()) runCatching { _state.value = fromJson(JSONObject(f.readText())) }
    }

    private fun save(c: Context) {
        val f = File(c.filesDir, FILE)
        val tmp = File(c.filesDir, "$FILE.tmp")
        tmp.writeText(toJson(_state.value).toString())
        tmp.renameTo(f)
    }

    private fun update(c: Context, change: (Garage) -> Garage) {
        load(c)
        _state.value = change(_state.value)
        save(c)
    }

    private fun newId() = System.currentTimeMillis()

    // ---------- Changes ----------

    fun setBike(c: Context, name: String, tankLitres: Double) =
        update(c) { it.copy(bike = it.bike.copy(name = name, tankLitres = tankLitres)) }

    fun setOdometer(c: Context, km: Int) =
        update(c) { it.copy(bike = it.bike.copy(odometer = km)) }

    fun addFuel(c: Context, date: Long, odometer: Int, litres: Double, cost: Double, fullTank: Boolean) =
        update(c) { g ->
            val fill = FuelFill(newId(), date, odometer, litres, cost, fullTank)
            g.copy(
                fuel = (g.fuel + fill).sortedBy { it.odometer },
                bike = if (odometer > g.bike.odometer) g.bike.copy(odometer = odometer) else g.bike
            )
        }

    fun deleteFuel(c: Context, id: Long) = update(c) { g -> g.copy(fuel = g.fuel.filterNot { it.id == id }) }

    fun addExpense(c: Context, date: Long, category: String, amount: Double, note: String) =
        update(c) { g -> g.copy(expenses = (g.expenses + Expense(newId(), date, category, amount, note)).sortedBy { it.date }) }

    fun deleteExpense(c: Context, id: Long) =
        update(c) { g -> g.copy(expenses = g.expenses.filterNot { it.id == id }) }

    fun addReminder(c: Context, title: String, dueDate: Long?, dueKm: Int?) =
        update(c) { g -> g.copy(reminders = g.reminders + Reminder(newId(), title, dueDate, dueKm)) }

    fun deleteReminder(c: Context, id: Long) =
        update(c) { g -> g.copy(reminders = g.reminders.filterNot { it.id == id }) }

    // ---------- Backup ----------

    fun exportJson(c: Context): String {
        load(c)
        return toJson(_state.value).toString()
    }

    fun importJson(c: Context, json: String) {
        loaded = true
        _state.value = fromJson(JSONObject(json))
        save(c)
    }

    // ---------- JSON ----------

    private fun toJson(g: Garage) = JSONObject().apply {
        put("bike", JSONObject().put("name", g.bike.name).put("tank", g.bike.tankLitres).put("odo", g.bike.odometer))
        put("fuel", JSONArray(g.fuel.map {
            JSONObject().put("id", it.id).put("date", it.date).put("odo", it.odometer)
                .put("litres", it.litres).put("cost", it.cost).put("full", it.fullTank)
        }))
        put("expenses", JSONArray(g.expenses.map {
            JSONObject().put("id", it.id).put("date", it.date).put("cat", it.category)
                .put("amount", it.amount).put("note", it.note)
        }))
        put("reminders", JSONArray(g.reminders.map {
            JSONObject().put("id", it.id).put("title", it.title)
                .put("date", it.dueDate ?: JSONObject.NULL).put("km", it.dueKm ?: JSONObject.NULL)
        }))
    }

    private fun fromJson(o: JSONObject): Garage {
        val b = o.optJSONObject("bike")
        fun arr(key: String) = o.optJSONArray(key) ?: JSONArray()
        fun <T> JSONArray.mapObj(f: (JSONObject) -> T) = (0 until length()).map { f(getJSONObject(it)) }
        return Garage(
            bike = Bike(
                name = b?.optString("name")?.ifEmpty { null } ?: "Himalayan 450",
                tankLitres = b?.optDouble("tank", 17.0) ?: 17.0,
                odometer = b?.optInt("odo", 0) ?: 0
            ),
            fuel = arr("fuel").mapObj {
                FuelFill(it.getLong("id"), it.getLong("date"), it.getInt("odo"),
                    it.getDouble("litres"), it.getDouble("cost"), it.optBoolean("full", true))
            },
            expenses = arr("expenses").mapObj {
                Expense(it.getLong("id"), it.getLong("date"), it.optString("cat", "Other"),
                    it.getDouble("amount"), it.optString("note"))
            },
            reminders = arr("reminders").mapObj {
                Reminder(it.getLong("id"), it.optString("title"),
                    if (it.isNull("date")) null else it.getLong("date"),
                    if (it.isNull("km")) null else it.getInt("km"))
            }
        )
    }
}

/** Mileage and money, worked out from the fuel log. */
object GarageMath {

    /** km/l for each full-tank fill (km since the previous full fill ÷ litres put in since). */
    fun mileagePerFill(fills: List<FuelFill>): Map<Long, Double> {
        val sorted = fills.sortedBy { it.odometer }
        val result = mutableMapOf<Long, Double>()
        var lastFull = -1
        sorted.forEachIndexed { i, fill ->
            if (fill.fullTank) {
                if (lastFull >= 0) {
                    val km = fill.odometer - sorted[lastFull].odometer
                    val litres = sorted.subList(lastFull + 1, i + 1).sumOf { it.litres }
                    if (km > 0 && litres > 0) result[fill.id] = km / litres
                }
                lastFull = i
            }
        }
        return result
    }

    /** Overall km/l across every full-to-full stretch. */
    fun averageKmpl(fills: List<FuelFill>): Double? {
        val sorted = fills.sortedBy { it.odometer }
        var km = 0.0
        var litres = 0.0
        var lastFull = -1
        sorted.forEachIndexed { i, fill ->
            if (fill.fullTank) {
                if (lastFull >= 0) {
                    km += fill.odometer - sorted[lastFull].odometer
                    litres += sorted.subList(lastFull + 1, i + 1).sumOf { it.litres }
                }
                lastFull = i
            }
        }
        return if (km > 0 && litres > 0) km / litres else null
    }

    /** Fuel money per km ridden, between the first and last fill. */
    fun fuelCostPerKm(fills: List<FuelFill>): Double? {
        val sorted = fills.sortedBy { it.odometer }
        if (sorted.size < 2) return null
        val km = sorted.last().odometer - sorted.first().odometer
        val cost = sorted.drop(1).sumOf { it.cost }
        return if (km > 0) cost / km else null
    }

    /** Estimated km left in the tank: since the last full fill, at your average km/l. */
    fun distanceToEmpty(g: Garage): Int? {
        val avg = averageKmpl(g.fuel) ?: return null
        val sorted = g.fuel.sortedBy { it.odometer }
        val lastFullIndex = sorted.indexOfLast { it.fullTank }
        if (lastFullIndex < 0) return null
        val lastFull = sorted[lastFullIndex]
        // Top-ups after the last full tank add range; a tank can never hold more than full.
        val topUps = sorted.drop(lastFullIndex + 1).sumOf { it.litres }
        val ridden = (g.bike.odometer - lastFull.odometer).coerceAtLeast(0)
        val range = (g.bike.tankLitres + topUps) * avg - ridden
        return range.coerceIn(0.0, g.bike.tankLitres * avg).toInt()
    }

    /** Fuel + other expenses this calendar month. */
    fun spendThisMonth(g: Garage): Double {
        val now = LocalDate.now()
        fun thisMonth(day: Long) = Days.toDate(day).let { it.year == now.year && it.month == now.month }
        return g.fuel.filter { thisMonth(it.date) }.sumOf { it.cost } +
            g.expenses.filter { thisMonth(it.date) }.sumOf { it.amount }
    }
}
