package com.granaflow.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Bg = Color(0xFFF4F7F3)
private val Panel = Color(0xFFFFFFFF)
private val Green = Color(0xFF638B73)
private val Muted = Color(0xFF78877D)
private val White = Color(0xFF26352C)
private val Red = Color(0xFFC87979)
private val categories = listOf("Alimentação", "Moradia", "Transporte", "Saúde", "Lazer", "Compras", "Educação", "Contas", "Salário", "Outros")

data class Entry(val id: Long, val title: String, val amount: Double, val category: String, val income: Boolean, val date: Long, val note: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GranaFlowApp() }
    }
}

@Composable
fun GranaFlowApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("granaflow", 0) }
    val entries = remember {
        mutableStateListOf<Entry>().apply {
            val raw = prefs.getString("entries", "[]") ?: "[]"
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(Entry(o.optLong("id"), o.optString("title"), o.optDouble("amount"), o.optString("category"), o.optBoolean("income"), o.optLong("date"), o.optString("note")))
                }
            } catch (_: Exception) { }
        }
    }
    var budgets by remember { mutableStateOf(prefs.getString("budgets", "") ?: "") }
    var monthlyLimit by remember { mutableStateOf(prefs.getString("monthlyLimit", "") ?: "") }
    var showMonthlyLimit by remember { mutableStateOf(false) }
    var goalName by remember { mutableStateOf(prefs.getString("goalName", "Reserva de emergência") ?: "Reserva de emergência") }
    var goalTarget by remember { mutableStateOf(prefs.getString("goalTarget", "1000") ?: "1000") }
    var goalSaved by remember { mutableStateOf(prefs.getString("goalSaved", "0") ?: "0") }
    var tab by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Entry?>(null) }
    var filter by remember { mutableStateOf("Todos") }
    var search by remember { mutableStateOf("") }
    var showBudget by remember { mutableStateOf(false) }
    var showGoal by remember { mutableStateOf(false) }

    fun persist() {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().put("id", e.id).put("title", e.title).put("amount", e.amount).put("category", e.category).put("income", e.income).put("date", e.date).put("note", e.note))
        }
        prefs.edit().putString("entries", arr.toString()).putString("budgets", budgets)
            .putString("monthlyLimit", monthlyLimit).putString("goalName", goalName).putString("goalTarget", goalTarget).putString("goalSaved", goalSaved).apply()
    }

    val now = System.currentTimeMillis()
    val monthEntries = entries.filter { val d = Date(it.date); val n = Date(now); val f = SimpleDateFormat("yyyyMM", Locale.US); f.format(d) == f.format(n) }
    val income = monthEntries.filter { it.income }.sumOf { it.amount }
    val expenses = monthEntries.filter { !it.income }.sumOf { it.amount }
    val balance = entries.sumOf { if (it.income) it.amount else -it.amount }
    val money = { v: Double -> NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(v) }

    MaterialTheme(colorScheme = lightColorScheme(primary = Green, background = Bg, surface = Panel, onBackground = White, onSurface = White, error = Red)) {
        Scaffold(containerColor = Bg, bottomBar = {
            NavigationBar(containerColor = Panel, contentColor = Muted) {
                listOf("Início", "Lançamentos", "Orçamentos", "Metas").forEachIndexed { i, label ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = {
                        Icon(when (i) { 0 -> Icons.Default.Home; 1 -> Icons.Default.ReceiptLong; 2 -> Icons.Default.PieChart; else -> Icons.Default.Flag }, contentDescription = label)
                    }, label = { Text(label, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Green, selectedTextColor = Green, indicatorColor = Green.copy(alpha = .13f), unselectedIconColor = Muted, unselectedTextColor = Muted))
                }
            }
        }, floatingActionButton = {
            if (tab == 0 || tab == 1) FloatingActionButton(onClick = { editing = null; showAdd = true }, containerColor = Green, contentColor = Bg) { Icon(Icons.Default.Add, "Adicionar lançamento") }
        }) { padding ->
            when (tab) {
                0 -> Dashboard(Modifier.padding(padding), balance, income, expenses, monthlyLimit.toDoubleOrNull() ?: 0.0, money, monthEntries, onAdd = { showAdd = true }, onSeeAll = { tab = 1 }, onBudget = { tab = 2 }, onMonthlyLimit = { showMonthlyLimit = true })
                1 -> Transactions(Modifier.padding(padding), entries, filter, { filter = it }, search, { search = it }, money, onEdit = { editing = it; showAdd = true }, onDelete = { entries.remove(it); persist() })
                2 -> Budgets(Modifier.padding(padding), entries, budgets, money, onConfigure = { showBudget = true })
                else -> Goals(Modifier.padding(padding), goalName, goalTarget, goalSaved, onConfigure = { showGoal = true })
            }
        }
    }

    if (showMonthlyLimit) MonthlyLimitDialog(monthlyLimit, onDismiss = { showMonthlyLimit = false }, onSave = { monthlyLimit = it; persist(); showMonthlyLimit = false })
    if (showAdd) EntryDialog(editing, onDismiss = { showAdd = false; editing = null }, onSave = { e ->
        if (editing != null) { val index = entries.indexOfFirst { it.id == editing!!.id }; if (index >= 0) entries[index] = e }
        else entries.add(0, e)
        persist(); showAdd = false; editing = null
    })
    if (showBudget) BudgetDialog(budgets, onDismiss = { showBudget = false }, onSave = { budgets = it; persist(); showBudget = false })
    if (showGoal) GoalDialog(goalName, goalTarget, goalSaved, onDismiss = { showGoal = false }, onSave = { n, t, s -> goalName = n; goalTarget = t; goalSaved = s; persist(); showGoal = false })

    LaunchedEffect(tab) { }
}

@Composable
private fun Dashboard(modifier: Modifier, balance: Double, income: Double, expenses: Double, monthlyLimit: Double, money: (Double) -> String, recent: List<Entry>, onAdd: () -> Unit, onSeeAll: () -> Unit, onBudget: () -> Unit, onMonthlyLimit: () -> Unit) {
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("GRANAFLOW", color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp); Text("Seu dinheiro sob controle", color = Muted, fontSize = 13.sp) }
                Icon(Icons.Default.AccountBalanceWallet, null, tint = Green, modifier = Modifier.size(28.dp))
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("SALDO TOTAL", color = Muted, fontSize = 12.sp, letterSpacing = 1.sp)
                    Text(money(balance), color = White, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryMini("Receitas do mês", money(income), Green, Modifier.weight(1f), Icons.Default.TrendingUp)
                        SummaryMini("Despesas do mês", money(expenses), Red, Modifier.weight(1f), Icons.Default.TrendingDown)
                    }
                }
            }
        }
        item {
            val remaining = monthlyLimit - expenses
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F0E9)), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("DINHEIRO DO MÊS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = onMonthlyLimit) { Text(if (monthlyLimit > 0) "Editar valor" else "Definir valor", color = Green) }
                    }
                    Text(if (monthlyLimit > 0) money(monthlyLimit) else "Toque em definir valor", color = White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    if (monthlyLimit > 0) {
                        Text("Gasto: ${money(expenses)}  •  ${if (remaining >= 0) "Restam ${money(remaining)}" else "Passou ${money(-remaining)}"}", color = if (remaining >= 0) Green else Red, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        LinearProgressIndicator(progress = { (expenses / monthlyLimit).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), color = if (remaining >= 0) Green else Red, trackColor = Color(0xFFD5E1D7))
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile("Novo lançamento", Icons.Default.AddCircle, Modifier.weight(1f), onAdd)
                ActionTile("Orçamentos", Icons.Default.PieChart, Modifier.weight(1f), onBudget)
            }
        }
        item { SectionHeader("Últimos lançamentos", "Ver todos", onSeeAll) }
        if (recent.isEmpty()) item { EmptyState("Nenhum lançamento ainda", "Toque no botão + para registrar sua primeira movimentação.") }
        items(recent.take(5), key = { it.id }) { EntryRow(it, money, {}, null) }
    }
}

@Composable
private fun SummaryMini(label: String, value: String, color: Color, modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(modifier.background(Bg, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Text(label, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}
@Composable
private fun ActionTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Green); Spacer(Modifier.width(8.dp)); Text(label, color = White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
    }
}
@Composable
private fun SectionHeader(title: String, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { Text(title, color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(action, color = Green, fontSize = 12.sp, modifier = Modifier.clickable { onClick() }) }
}
@Composable
private fun EmptyState(title: String, subtitle: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Inbox, null, tint = Green, modifier = Modifier.size(32.dp)); Text(title, color = White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)); Text(subtitle, color = Muted, fontSize = 12.sp) }
    }
}

@Composable
private fun Transactions(modifier: Modifier, entries: List<Entry>, filter: String, setFilter: (String) -> Unit, search: String, setSearch: (String) -> Unit, money: (Double) -> String, onEdit: (Entry) -> Unit, onDelete: (Entry) -> Unit) {
    val shown = entries.filter { (filter == "Todos" || (filter == "Receitas" && it.income) || (filter == "Despesas" && !it.income)) && (it.title.contains(search, true) || it.category.contains(search, true)) }
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Text("Lançamentos", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp, bottom = 12.dp))
        OutlinedTextField(search, setSearch, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Buscar por nome ou categoria") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, colors = fieldColors())
        Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Todos", "Receitas", "Despesas").forEach { f -> FilterChip(selected = filter == f, onClick = { setFilter(f) }, label = { Text(f) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Green.copy(alpha = .18f), selectedLabelColor = Green)) }
        }
        if (shown.isEmpty()) EmptyState("Nada encontrado", "Seus lançamentos aparecerão aqui.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(shown, key = { it.id }) { e ->
                EntryRow(e, money, { onEdit(e) }, { onDelete(e) })
            }
        }
    }
}

@Composable
private fun EntryRow(e: Entry, money: (Double) -> String, onEdit: () -> Unit, onDelete: (() -> Unit)?) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(if (e.income) Green.copy(alpha = .14f) else Red.copy(alpha = .14f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(if (e.income) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward, null, tint = if (e.income) Green else Red)
            }
            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                Text(e.title, color = White, fontWeight = FontWeight.SemiBold)
                Text("${e.category} • ${SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(e.date))}", color = Muted, fontSize = 11.sp)
                if (e.note.isNotBlank()) Text(e.note, color = Muted, fontSize = 10.sp)
            }
            Text((if (e.income) "+ " else "- ") + money(e.amount), color = if (e.income) Green else Red, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (onDelete != null) {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar", tint = Muted) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Excluir", tint = Red) }
            }
        }
    }
}

@Composable
private fun Budgets(modifier: Modifier, entries: List<Entry>, budgets: String, money: (Double) -> String, onConfigure: () -> Unit) {
    val limits = remember(budgets) { budgets.split("|").mapNotNull { part -> val p = part.split(":"); if (p.size == 2) p[0] to (p[1].toDoubleOrNull() ?: 0.0) else null }.toMap() }
    val now = SimpleDateFormat("yyyyMM", Locale.US).format(Date())
    val current = entries.filter { !it.income && SimpleDateFormat("yyyyMM", Locale.US).format(Date(it.date)) == now }
    Column(modifier.fillMaxSize().padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Orçamentos", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); TextButton(onClick = onConfigure) { Text("Configurar", color = Green) } }
        Text("Defina limites mensais por categoria.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 16.dp))
        if (limits.isEmpty()) EmptyState("Nenhum orçamento definido", "Configure limites para acompanhar seus gastos.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(limits.toList()) { (category, limit) ->
                val spent = current.filter { it.category == category }.sumOf { it.amount }
                val progress = if (limit > 0) (spent / limit).toFloat().coerceIn(0f, 1f) else 0f
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row { Text(category, color = White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("${money(spent)} / ${money(limit)}", color = if (spent > limit) Red else Green, fontSize = 12.sp) }
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), color = if (spent > limit) Red else Green, trackColor = Bg)
                        Text(if (spent > limit) "Limite ultrapassado em ${money(spent-limit)}" else "Restam ${money(limit-spent)}", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Goals(modifier: Modifier, name: String, targetText: String, savedText: String, onConfigure: () -> Unit) {
    val target = targetText.toDoubleOrNull() ?: 0.0
    val saved = savedText.toDoubleOrNull() ?: 0.0
    val money = { v: Double -> NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(v) }
    Column(modifier.fillMaxSize().padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Metas", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); TextButton(onClick = onConfigure) { Text("Editar meta", color = Green) } }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Column(Modifier.padding(20.dp)) {
                Icon(Icons.Default.Flag, null, tint = Green, modifier = Modifier.size(30.dp))
                Text(name, color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                Text("Valor guardado", color = Muted, fontSize = 12.sp)
                Text(money(saved), color = Green, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                Text("Objetivo: ${money(target)}", color = Muted, modifier = Modifier.padding(top = 8.dp))
                LinearProgressIndicator(progress = { if (target > 0) (saved / target).toFloat().coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp), color = Green, trackColor = Bg)
                Text(if (target > saved) "Faltam ${money(target-saved)}" else "Meta alcançada! 🎉", color = Green, modifier = Modifier.padding(top = 8.dp))
            }
        }
        Text("Dica: atualize o valor guardado sempre que separar dinheiro para essa meta.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryDialog(existing: Entry?, onDismiss: () -> Unit, onSave: (Entry) -> Unit) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var amount by remember { mutableStateOf(existing?.amount?.toString() ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: categories[0]) }
    var income by remember { mutableStateOf(existing?.income ?: false) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Panel, title = { Text(if (existing == null) "Novo lançamento" else "Editar lançamento", color = White) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !income, onClick = { income = false }, label = { Text("Despesa") })
                FilterChip(selected = income, onClick = { income = true }, label = { Text("Receita") })
            }
            OutlinedTextField(title, { title = it }, label = { Text("Descrição") }, singleLine = true, colors = fieldColors())
            OutlinedTextField(amount, { amount = it.replace(",", ".") }, label = { Text("Valor (R$)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, colors = fieldColors())
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(category, {}, readOnly = true, label = { Text("Categoria") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth(), colors = fieldColors())
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    categories.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { category = c; expanded = false }) }
                }
            }
            OutlinedTextField(note, { note = it }, label = { Text("Observação (opcional)") }, colors = fieldColors(), minLines = 1)
            if (error) Text("Informe uma descrição e um valor válido maior que zero.", color = Red, fontSize = 12.sp)
        }
    }, confirmButton = { TextButton(onClick = {
        val v = amount.toDoubleOrNull()
        if (title.isNotBlank() && v != null && v > 0) onSave(Entry(existing?.id ?: System.currentTimeMillis(), title.trim(), v, category, income, existing?.date ?: System.currentTimeMillis(), note.trim())) else error = true
    }) { Text("Salvar", color = Green) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Muted) } })
}

@Composable
private fun BudgetDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val map = remember(current) { current.split("|").mapNotNull { val p=it.split(":"); if(p.size==2) p[0] to p[1] else null }.toMap().toMutableMap() }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Panel, title = { Text("Limites mensais", color = White) }, text = {
        LazyColumn(modifier = Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories.filter { it !in listOf("Salário", "Outros") }) { c ->
                OutlinedTextField(value = map[c] ?: "", onValueChange = { map[c] = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.replace(",", ".") }, label = { Text("$c (R$)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = fieldColors(), modifier = Modifier.fillMaxWidth())
            }
        }
    }, confirmButton = { TextButton(onClick = { onSave(map.filterValues { (it.toDoubleOrNull() ?: 0.0) > 0 }.entries.joinToString("|") { "${it.key}:${it.value}" }) }) { Text("Salvar", color = Green) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Muted) } })
}

@Composable
private fun GoalDialog(name0: String, target0: String, saved0: String, onDismiss: () -> Unit, onSave: (String,String,String) -> Unit) {
    var name by remember { mutableStateOf(name0) }; var target by remember { mutableStateOf(target0) }; var saved by remember { mutableStateOf(saved0) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Panel, title = { Text("Configurar meta", color = White) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Nome da meta") }, colors = fieldColors())
            OutlinedTextField(target, { target = it.replace(",", ".") }, label = { Text("Objetivo (R$)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = fieldColors())
            OutlinedTextField(saved, { saved = it.replace(",", ".") }, label = { Text("Já guardado (R$)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = fieldColors())
        }
    }, confirmButton = { TextButton(onClick = { if (name.isNotBlank() && (target.toDoubleOrNull() ?: -1.0) >= 0 && (saved.toDoubleOrNull() ?: -1.0) >= 0) onSave(name,target,saved) }) { Text("Salvar", color = Green) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Muted) } })
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(focusedBorderColor = Green, unfocusedBorderColor = Color(0xFFB8C5BA), focusedLabelColor = Green, cursorColor = Green, focusedTextColor = White, unfocusedTextColor = White, focusedContainerColor = Panel, unfocusedContainerColor = Panel, focusedLeadingIconColor = Green, unfocusedLeadingIconColor = Muted)

@Composable
private fun MonthlyLimitDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(current) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Panel, title = { Text("Dinheiro disponível no mês", color = White) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Informe quanto você tem para usar neste mês. O GranaFlow vai descontar suas despesas registradas e mostrar quanto ainda resta.", color = Muted, fontSize = 13.sp)
            OutlinedTextField(value, { value = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.replace(",", ".") }, label = { Text("Valor total (R$)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, colors = fieldColors())
        }
    }, confirmButton = { TextButton(onClick = { if ((value.toDoubleOrNull() ?: -1.0) >= 0) onSave(value) }) { Text("Salvar", color = Green) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Muted) } })
}
