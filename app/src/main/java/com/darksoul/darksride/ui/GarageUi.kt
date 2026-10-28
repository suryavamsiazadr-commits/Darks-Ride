package com.darksoul.darksride.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darksoul.darksride.docs.DocVault
import com.darksoul.darksride.garage.Days
import com.darksoul.darksride.garage.EXPENSE_CATEGORIES
import com.darksoul.darksride.garage.Garage
import com.darksoul.darksride.garage.GarageMath
import com.darksoul.darksride.garage.GarageStore
import com.darksoul.darksride.ui.theme.Go
import com.darksoul.darksride.ui.theme.Stop
import java.text.NumberFormat
import java.util.Locale

private val rupees: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
    maximumFractionDigits = 0
}

private fun money(v: Double) = rupees.format(v)
private fun km(v: Int) = "%,d km".format(v)

/** Garage: odometer, mileage, spend, distance to empty, fuel log, expenses and reminders. */
@Composable
fun GarageScreen(insets: WindowInsets = WindowInsets.safeDrawing) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { GarageStore.load(context) }
    val g by GarageStore.state.collectAsState()

    RideColumn(insets = insets) {
        Title("Garage")
        SummaryCard(g)
        DueSoonCard(g)
        FuelCard(g)
        ExpensesCard(g)
        RemindersCard(g)
        BikeCard(g)
    }
}

// ---------- Summary ----------

@Composable
private fun SummaryCard(g: Garage) {
    val context = LocalContext.current
    var editOdo by remember { mutableStateOf(false) }
    val avg = GarageMath.averageKmpl(g.fuel)
    val perKm = GarageMath.fuelCostPerKm(g.fuel)
    val dte = GarageMath.distanceToEmpty(g)

    Card {
        Text(g.bike.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Stat("Odometer", km(g.bike.odometer), Modifier.weight(1f))
            TextButton(onClick = { editOdo = true }) { Text("Update") }
        }
        Row {
            Stat("Mileage", avg?.let { "%.1f km/l".format(it) } ?: "—", Modifier.weight(1f))
            Stat("Distance to empty", dte?.let { km(it) } ?: "—", Modifier.weight(1f))
        }
        Row {
            Stat("Fuel cost", perKm?.let { "₹%.2f / km".format(it) } ?: "—", Modifier.weight(1f))
            Stat("This month", money(GarageMath.spendThisMonth(g)), Modifier.weight(1f))
        }
        if (avg == null) {
            Body("Log two full-tank fills to see your real mileage and distance to empty.", dim = true)
        }
    }

    if (editOdo) {
        var value by remember { mutableStateOf(g.bike.odometer.toString()) }
        AlertDialog(
            onDismissRequest = { editOdo = false },
            title = { Text("Odometer") },
            text = { NumberInput("Reading on the dash (km)", value, { value = it }, decimal = false) },
            confirmButton = {
                TextButton(onClick = {
                    value.toIntOrNullSafe()?.let { GarageStore.setOdometer(context, it) }
                    editOdo = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editOdo = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

// ---------- Due soon (reminders + document expiry dates) ----------

private data class DueItem(val title: String, val detail: String, val overdue: Boolean)

@Composable
private fun DueSoonCard(g: Garage) {
    val context = LocalContext.current
    val docs = remember(g) { DocVault.expiries(context) }
    val items = buildList {
        g.reminders.forEach { r ->
            val daysLeft = r.dueDate?.let { Days.daysFromToday(it) }
            val kmLeft = r.dueKm?.let { it - g.bike.odometer }
            val soon = (daysLeft != null && daysLeft <= 30) || (kmLeft != null && kmLeft <= 500)
            if (soon) {
                val overdue = (daysLeft != null && daysLeft < 0) || (kmLeft != null && kmLeft < 0)
                add(DueItem(r.title, dueText(daysLeft, kmLeft), overdue))
            }
        }
        docs.forEach { d ->
            val daysLeft = Days.daysFromToday(d.expiry)
            if (daysLeft <= 30) add(DueItem("${d.title} expires", dueText(daysLeft, null), daysLeft < 0))
        }
    }
    if (items.isEmpty()) return
    CollapsibleCard(
        title = "Due soon",
        stateKey = "due_soon",
        titleColor = if (items.any { it.overdue }) Stop else MaterialTheme.colorScheme.primary,
        summary = items.joinToString(" · ") { it.title }
    ) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f))
                Text(item.detail, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = if (item.overdue) Stop else MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun dueText(daysLeft: Long?, kmLeft: Int?): String = listOfNotNull(
    daysLeft?.let {
        when {
            it < 0 -> "${-it} days overdue"
            it == 0L -> "today"
            else -> "in $it days"
        }
    },
    kmLeft?.let { if (it < 0) "${-it} km over" else "in ${km(it)}" }
).joinToString(" · ")

// ---------- Fuel log ----------

@Composable
private fun FuelCard(g: Garage) {
    val context = LocalContext.current
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Long?>(null) }
    val perFill = remember(g.fuel) { GarageMath.mileagePerFill(g.fuel) }
    val recent = g.fuel.sortedByDescending { it.odometer }.take(10)

    CollapsibleCard(
        title = "Fuel log",
        stateKey = "fuel",
        summary = recent.firstOrNull()?.let { "Last: ${Days.format(it.date)} · ${"%.1f".format(it.litres)} L" }
            ?: "No fills yet",
        trailing = { TextButton(onClick = { adding = true }) { Text("+ Fill") } }
    ) {
        if (recent.isEmpty()) Body("Tap + Fill at the pump. Mark full tanks to get your real mileage.", dim = true)
        recent.forEachIndexed { i, f ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            Row(
                modifier = Modifier.fillMaxWidth().clickable { deleting = f.id }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${Days.format(f.date)} · ${km(f.odometer)}", fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "${"%.2f".format(f.litres)} L · ${money(f.cost)}" + if (f.fullTank) " · full" else "",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                perFill[f.id]?.let {
                    Text("%.1f km/l".format(it), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Go)
                }
            }
        }
    }

    if (adding) FuelDialog(g, onDone = { adding = false })
    deleting?.let { id ->
        ConfirmDelete("Delete this fill?", onYes = { GarageStore.deleteFuel(context, id); deleting = null },
            onNo = { deleting = null })
    }
}

@Composable
private fun FuelDialog(g: Garage, onDone: () -> Unit) {
    val context = LocalContext.current
    var day by remember { mutableStateOf(Days.today()) }
    var odo by remember { mutableStateOf(if (g.bike.odometer > 0) g.bike.odometer.toString() else "") }
    var litres by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var full by remember { mutableStateOf(true) }
    val valid = odo.toIntOrNullSafe() != null && (litres.toDoubleOrNullSafe() ?: 0.0) > 0 &&
        cost.toDoubleOrNullSafe() != null

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Fuel fill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateInput("Date", day, { it?.let { d -> day = d } })
                NumberInput("Odometer (km)", odo, { odo = it }, decimal = false)
                NumberInput("Litres", litres, { litres = it })
                NumberInput("Amount paid (₹)", cost, { cost = it })
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { full = !full }) {
                    Checkbox(checked = full, onCheckedChange = { full = it })
                    Text("Filled to full tank")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                GarageStore.addFuel(context, day, odo.toInt(), litres.toDouble(), cost.toDouble(), full)
                onDone()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } }
    )
}

// ---------- Expenses ----------

@Composable
private fun ExpensesCard(g: Garage) {
    val context = LocalContext.current
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Long?>(null) }
    val recent = g.expenses.sortedByDescending { it.date }.take(10)
    val total = g.expenses.sumOf { it.amount } + g.fuel.sumOf { it.cost }

    CollapsibleCard(
        title = "Expenses",
        stateKey = "expenses",
        defaultOpen = false,
        summary = "All time: ${money(total)} · this month: ${money(GarageMath.spendThisMonth(g))}",
        trailing = { TextButton(onClick = { adding = true }) { Text("+ Add") } }
    ) {
        val byCategory = (g.expenses.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } } +
            ("Fuel" to g.fuel.sumOf { it.cost })).filterValues { it > 0 }
        if (byCategory.isNotEmpty()) {
            byCategory.entries.sortedByDescending { it.value }.forEach { (cat, sum) ->
                Row {
                    Text(cat, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Text(money(sum), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
        }
        if (recent.isEmpty()) Body("Service, parts, gear, insurance, tolls… Fuel is added from the fuel log.", dim = true)
        recent.forEach { e ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { deleting = e.id }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(e.note.ifEmpty { e.category }, fontSize = 15.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                    Text("${Days.format(e.date)} · ${e.category}", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                Text(money(e.amount), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }

    if (adding) ExpenseDialog(onDone = { adding = false })
    deleting?.let { id ->
        ConfirmDelete("Delete this expense?", onYes = { GarageStore.deleteExpense(context, id); deleting = null },
            onNo = { deleting = null })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpenseDialog(onDone: () -> Unit) {
    val context = LocalContext.current
    var day by remember { mutableStateOf(Days.today()) }
    var category by remember { mutableStateOf(EXPENSE_CATEGORIES.first()) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EXPENSE_CATEGORIES.forEach { cat ->
                        FilterChip(selected = cat == category, onClick = { category = cat }, label = { Text(cat) })
                    }
                }
                NumberInput("Amount (₹)", amount, { amount = it })
                TextInput("What was it? (optional)", note, { note = it })
                DateInput("Date", day, { it?.let { d -> day = d } })
            }
        },
        confirmButton = {
            TextButton(enabled = (amount.toDoubleOrNullSafe() ?: 0.0) > 0, onClick = {
                GarageStore.addExpense(context, day, category, amount.toDouble(), note.trim())
                onDone()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } }
    )
}

// ---------- Reminders ----------

@Composable
private fun RemindersCard(g: Garage) {
    val context = LocalContext.current
    var adding by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf<Long?>(null) }

    CollapsibleCard(
        title = "Reminders",
        stateKey = "reminders",
        defaultOpen = false,
        summary = if (g.reminders.isEmpty()) "Service, chain, oil, tyres…" else "${g.reminders.size} set",
        trailing = { TextButton(onClick = { adding = true }) { Text("+ Add") } }
    ) {
        if (g.reminders.isEmpty()) {
            Body("Add reminders by date, by odometer, or both (e.g. oil change at 10,000 km). " +
                "Insurance and PUC dates come from Documents.", dim = true)
        }
        g.reminders.forEach { r ->
            val daysLeft = r.dueDate?.let { Days.daysFromToday(it) }
            val kmLeft = r.dueKm?.let { it - g.bike.odometer }
            val overdue = (daysLeft != null && daysLeft < 0) || (kmLeft != null && kmLeft < 0)
            Row(
                modifier = Modifier.fillMaxWidth().clickable { finishing = r.id }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(r.title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        listOfNotNull(r.dueDate?.let { Days.format(it) }, r.dueKm?.let { "at ${km(it)}" })
                            .joinToString(" · "),
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Text(dueText(daysLeft, kmLeft), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = if (overdue) Stop else MaterialTheme.colorScheme.primary)
            }
        }
    }

    if (adding) ReminderDialog(onDone = { adding = false })
    finishing?.let { id ->
        AlertDialog(
            onDismissRequest = { finishing = null },
            title = { Text("Done?") },
            text = { Text("Mark this reminder as done and remove it?") },
            confirmButton = {
                TextButton(onClick = { GarageStore.deleteReminder(context, id); finishing = null }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { finishing = null }) { Text("Keep") } }
        )
    }
}

@Composable
private fun ReminderDialog(onDone: () -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var day by remember { mutableStateOf<Long?>(null) }
    var atKm by remember { mutableStateOf("") }
    val valid = title.isNotBlank() && (day != null || atKm.toIntOrNullSafe() != null)

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput("What? (e.g. Oil change)", title, { title = it })
                DateInput("Due date", day, { day = it }, clearable = true)
                NumberInput("Due at odometer (km)", atKm, { atKm = it }, decimal = false)
                Body("Set a date, an odometer reading, or both.", dim = true)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                GarageStore.addReminder(context, title.trim(), day, atKm.toIntOrNullSafe())
                onDone()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } }
    )
}

// ---------- Bike ----------

@Composable
private fun BikeCard(g: Garage) {
    val context = LocalContext.current
    var name by remember(g.bike.name) { mutableStateOf(g.bike.name) }
    var tank by remember(g.bike.tankLitres) { mutableStateOf(g.bike.tankLitres.toString()) }
    CollapsibleCard(
        title = "Bike",
        stateKey = "bike",
        defaultOpen = false,
        summary = "${g.bike.name} · ${g.bike.tankLitres} L tank"
    ) {
        TextInput("Name", name, { name = it })
        NumberInput("Tank capacity (litres)", tank, { tank = it })
        BigButton("Save", secondary = true, onClick = {
            GarageStore.setBike(context, name.trim().ifEmpty { "My bike" }, tank.toDoubleOrNullSafe() ?: 17.0)
        })
    }
}

@Composable
internal fun ConfirmDelete(question: String, onYes: () -> Unit, onNo: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNo,
        title = { Text(question) },
        confirmButton = { TextButton(onClick = onYes) { Text("Delete", color = Stop) } },
        dismissButton = { TextButton(onClick = onNo) { Text("Cancel") } }
    )
}
