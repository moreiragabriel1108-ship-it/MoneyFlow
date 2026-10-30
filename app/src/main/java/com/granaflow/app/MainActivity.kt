package com.granaflow.app

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

private val BG = Color(0xFF080B09)
private val CARD = Color(0xFF141B16)
private val GREEN = Color(0xFF39E58C)
private val TEXT = Color(0xFFF0F5F1)
private val MUTED = Color(0xFF9AA79F)
private val RED = Color(0xFFFF6B6B)
data class Entry(val id: Long, val title: String, val value: Double, val category: String, val type: String, val date: String)

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { App() } }
}

@Composable
fun App() {
 val ctx = LocalContext.current
 val prefs = remember { ctx.getSharedPreferences("granaflow", 0) }
 val entries = remember {
  mutableStateListOf<Entry>().apply {
   try {
    val a = JSONArray(prefs.getString("entries", "[]"))
    for (i in 0 until a.length()) { val o=a.getJSONObject(i); add(Entry(o.getLong("id"),o.getString("title"),o.getDouble("value"),o.getString("category"),o.getString("type"),o.getString("date"))) }
   } catch (_: Exception) {}
  }
 }
 var page by remember { mutableIntStateOf(0) }
 var dialog by remember { mutableStateOf(false) }
 var selected by remember { mutableStateOf<Entry?>(null) }
 var budget by remember { mutableDoubleStateOf(prefs.getString("budget","1500")?.toDoubleOrNull() ?: 1500.0) }
 var budgetDialog by remember { mutableStateOf(false) }
 var goalName by remember { mutableStateOf(prefs.getString("goalName","Reserva de emergência") ?: "Reserva de emergência") }
 var goalTarget by remember { mutableDoubleStateOf(prefs.getString("goalTarget","3000")?.toDoubleOrNull() ?: 3000.0) }
 var goalSaved by remember { mutableDoubleStateOf(prefs.getString("goalSaved","0")?.toDoubleOrNull() ?: 0.0) }
 var goalDialog by remember { mutableStateOf(false) }
 fun persist() {
  val a=JSONArray()
  entries.forEach { e -> a.put(JSONObject().put("id",e.id).put("title",e.title).put("value",e.value).put("category",e.category).put("type",e.type).put("date",e.date)) }
  prefs.edit().putString("entries",a.toString()).apply()
 }
 val income=entries.filter { it.type=="Receita" }.sumOf { it.value }
 val expense=entries.filter { it.type=="Despesa" }.sumOf { it.value }
 val balance=income-expense
 val money: (Double)->String = { NumberFormat.getCurrencyInstance(Locale("pt","BR")).format(it) }
 MaterialTheme(colorScheme=darkColorScheme(primary=GREEN,background=BG,surface=CARD,onBackground=TEXT,onSurface=TEXT,onPrimary=BG)) {
  Scaffold(containerColor=BG, bottomBar={
   NavigationBar(containerColor=CARD) {
    val names=listOf("Início","Movimentos","Orçamento","Metas","Relatórios")
    val icons=listOf(Icons.Filled.Home,Icons.Filled.ReceiptLong,Icons.Filled.AccountBalanceWallet,Icons.Filled.Savings,Icons.Filled.BarChart)
    names.forEachIndexed { i,n -> NavigationBarItem(selected=page==i,onClick={page=i},icon={Icon(icons[i],n)},label={Text(n,fontSize=10.sp)},colors=NavigationBarItemDefaults.colors(selectedIconColor=GREEN,selectedTextColor=GREEN,indicatorColor=GREEN.copy(alpha=.13f),unselectedIconColor=MUTED,unselectedTextColor=MUTED)) }
   }
  }, floatingActionButton={
   if(page<2) FloatingActionButton(onClick={selected=null;dialog=true},containerColor=GREEN,contentColor=BG){Icon(Icons.Filled.Add,"Adicionar")}
  }) { pad ->
   Column(Modifier.fillMaxSize().padding(pad).padding(horizontal=17.dp)) {
    Row(Modifier.fillMaxWidth().padding(vertical=18.dp),verticalAlignment=Alignment.CenterVertically) {
     Column(Modifier.weight(1f)) {
      Text("GRANAFLOW",color=GREEN,fontWeight=FontWeight.Bold,fontSize=12.sp,letterSpacing=2.sp)
      Text(listOf("Seu resumo financeiro","Todas as movimentações","Planeje seus limites","Seus objetivos","Visão dos seus gastos")[page],color=TEXT,fontSize=20.sp,fontWeight=FontWeight.Bold)
     }
     Icon(Icons.Filled.AccountBalance,contentDescription="GranaFlow",tint=GREEN)
    }
    when(page) {
     0 -> Dashboard(balance,income,expense,entries,money){selected=it;dialog=true}
     1 -> Movements(entries,money,{selected=it;dialog=true},{entries.remove(it);persist()})
     2 -> BudgetPage(budget,expense,money){budgetDialog=true}
     3 -> GoalPage(goalName,goalTarget,goalSaved,money){goalDialog=true}
     else -> Reports(entries,money)
    }
   }
  }
 }
 if(dialog) EntryDialog(selected,{dialog=false}) { title,value,category,type ->
  val date=SimpleDateFormat("dd/MM/yyyy",Locale("pt","BR")).format(Date())
  if(selected==null) entries.add(0,Entry(System.currentTimeMillis(),title,value,category,type,date))
  else { val idx=entries.indexOfFirst{it.id==selected!!.id}; if(idx>=0) entries[idx]=selected!!.copy(title=title,value=value,category=category,type=type) }
  persist();dialog=false
 }
 if(budgetDialog) ValueDialog("Orçamento mensal",budget.toString(),{v->v.toDoubleOrNull()?.takeIf{it>0}?.let{budget=it;prefs.edit().putString("budget",it.toString()).apply()};budgetDialog=false},{budgetDialog=false})
 if(goalDialog) GoalEdit(goalName,goalTarget,goalSaved,{goalDialog=false}) { n,t,s ->
  goalName=n;goalTarget=t;goalSaved=s
  prefs.edit().putString("goalName",n).putString("goalTarget",t.toString()).putString("goalSaved",s.toString()).apply();goalDialog=false
 }
}

@Composable
fun PanelCard(content:@Composable ColumnScope.()->Unit) {
 Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=CARD),shape=RoundedCornerShape(19.dp)){Column(Modifier.padding(18.dp),content=content)}
}
@Composable
fun Dashboard(balance:Double,income:Double,expense:Double,entries:List<Entry>,money:(Double)->String,onEdit:(Entry)->Unit) {
 LazyColumn(verticalArrangement=Arrangement.spacedBy(13.dp),contentPadding=PaddingValues(bottom=20.dp)) {
  item { PanelCard {
   Text("SALDO TOTAL",color=MUTED,fontSize=12.sp,letterSpacing=1.sp)
   Text(money(balance),color=if(balance>=0)GREEN else RED,fontSize=31.sp,fontWeight=FontWeight.Bold)
   Spacer(Modifier.height(14.dp))
   Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
    Column(Modifier.weight(1f).background(BG,RoundedCornerShape(12.dp)).padding(11.dp)){Text("Receitas",color=MUTED,fontSize=12.sp);Text(money(income),color=GREEN,fontWeight=FontWeight.Bold)}
    Column(Modifier.weight(1f).background(BG,RoundedCornerShape(12.dp)).padding(11.dp)){Text("Despesas",color=MUTED,fontSize=12.sp);Text(money(expense),color=RED,fontWeight=FontWeight.Bold)}
   }
  } }
  item { Text("Últimas movimentações",color=TEXT,fontSize=17.sp,fontWeight=FontWeight.Bold) }
  if(entries.isEmpty()) item { Empty("Nenhum lançamento","Toque no + para registrar sua primeira receita ou despesa.") }
  items(entries.take(7),key={it.id}){EntryRow(it,money){onEdit(it)}}
 }
}
@Composable
fun Movements(entries:List<Entry>,money:(Double)->String,onEdit:(Entry)->Unit,onDelete:(Entry)->Unit) {
 LazyColumn(verticalArrangement=Arrangement.spacedBy(9.dp),contentPadding=PaddingValues(bottom=18.dp)) {
  if(entries.isEmpty()) item{Empty("Histórico vazio","Adicione movimentações pelo botão +.")}
  items(entries,key={it.id}) { e -> Row(verticalAlignment=Alignment.CenterVertically) { Box(Modifier.weight(1f)){EntryRow(e,money){onEdit(e)}};IconButton(onClick={onDelete(e)}){Icon(Icons.Filled.DeleteOutline,"Excluir",tint=RED)} } }
 }
}
@Composable
fun EntryRow(e:Entry,money:(Double)->String,onClick:()->Unit) {
 Row(Modifier.fillMaxWidth().background(CARD,RoundedCornerShape(14.dp)).clickable{onClick()}.padding(13.dp),verticalAlignment=Alignment.CenterVertically) {
  Icon(if(e.type=="Receita")Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,null,tint=if(e.type=="Receita")GREEN else RED,modifier=Modifier.size(28.dp))
  Column(Modifier.weight(1f).padding(start=10.dp)){Text(e.title,color=TEXT,fontWeight=FontWeight.SemiBold);Text(e.category+" • "+e.date,color=MUTED,fontSize=11.sp)}
  Text((if(e.type=="Receita")"+ " else "- ")+money(e.value),color=if(e.type=="Receita")GREEN else RED,fontWeight=FontWeight.Bold,fontSize=13.sp)
 }
}
@Composable
fun BudgetPage(budget:Double,spent:Double,money:(Double)->String,onEdit:()->Unit) {
 val p=if(budget>0)(spent/budget).coerceIn(0.0,1.0).toFloat() else 0f
 Column(verticalArrangement=Arrangement.spacedBy(14.dp)){PanelCard{
  Text("LIMITE MENSAL",color=MUTED,fontSize=12.sp)
  Text(money(budget),color=TEXT,fontSize=30.sp,fontWeight=FontWeight.Bold)
  LinearProgressIndicator(progress={p},modifier=Modifier.fillMaxWidth().padding(vertical=15.dp).height(9.dp),color=if(spent>budget)RED else GREEN,trackColor=BG)
  Text("Gasto: "+money(spent),color=MUTED)
  Text(if(spent>budget)"Limite ultrapassado em "+money(spent-budget) else "Disponível: "+money(budget-spent),color=if(spent>budget)RED else GREEN,fontWeight=FontWeight.Bold)
 };Button(onClick=onEdit,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=GREEN,contentColor=BG)){Text("Editar orçamento")}}
}
@Composable
fun GoalPage(name:String,target:Double,saved:Double,money:(Double)->String,onEdit:()->Unit) {
 val p=if(target>0)(saved/target).coerceIn(0.0,1.0).toFloat() else 0f
 Column(verticalArrangement=Arrangement.spacedBy(14.dp)){PanelCard{
  Icon(Icons.Filled.Savings,null,tint=GREEN,modifier=Modifier.size(34.dp))
  Text("META DE ECONOMIA",color=MUTED,fontSize=12.sp)
  Text(name,color=TEXT,fontSize=21.sp,fontWeight=FontWeight.Bold)
  Text(money(saved)+" de "+money(target),color=GREEN,fontSize=19.sp)
  LinearProgressIndicator(progress={p},modifier=Modifier.fillMaxWidth().padding(vertical=13.dp).height(9.dp),color=GREEN,trackColor=BG)
  Text((p*100).toInt().toString()+"% concluído • faltam "+money((target-saved).coerceAtLeast(0.0)),color=MUTED)
 };Button(onClick=onEdit,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=GREEN,contentColor=BG)){Text("Editar meta")}}
}
@Composable
fun Reports(entries:List<Entry>,money:(Double)->String) {
 val expenses=entries.filter{it.type=="Despesa"};val total=expenses.sumOf{it.value}
 val groups=expenses.groupBy{it.category}.mapValues{it.value.sumOf{e->e.value}}.toList().sortedByDescending{it.second}
 LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=18.dp)){
  item{PanelCard{Text("TOTAL DE DESPESAS",color=MUTED,fontSize=12.sp);Text(money(total),color=RED,fontSize=28.sp,fontWeight=FontWeight.Bold);Text(expenses.size.toString()+" lançamentos",color=MUTED)}}
  item{Text("Gastos por categoria",color=TEXT,fontSize=17.sp,fontWeight=FontWeight.Bold)}
  if(groups.isEmpty())item{Empty("Sem dados","Registre despesas para montar seu relatório.")}
  items(groups){(cat,value)->PanelCard{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(cat,color=TEXT);Text(money(value),color=GREEN,fontWeight=FontWeight.Bold)};LinearProgressIndicator(progress={if(total>0)(value/total).toFloat() else 0f},modifier=Modifier.fillMaxWidth().padding(top=9.dp).height(7.dp),color=GREEN,trackColor=BG)}}
 }
}
@Composable
fun Empty(title:String,msg:String){Column(Modifier.fillMaxWidth().background(CARD,RoundedCornerShape(15.dp)).padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Filled.AccountBalanceWallet,null,tint=GREEN,modifier=Modifier.size(34.dp));Text(title,color=TEXT,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=8.dp));Text(msg,color=MUTED,fontSize=12.sp)}}

@Composable
fun EntryDialog(old:Entry?,dismiss:()->Unit,save:(String,Double,String,String)->Unit) {
 var title by remember(old){mutableStateOf(old?.title?:"")}
 var value by remember(old){mutableStateOf(old?.value?.toString()?:"")}
 var category by remember(old){mutableStateOf(old?.category?:"Alimentação")}
 var type by remember(old){mutableStateOf(old?.type?:"Despesa")}
 val cats=listOf("Alimentação","Transporte","Moradia","Contas","Saúde","Lazer","Educação","Compras","Salário","Benefício","Vendas","Outros")
 var expanded by remember{mutableStateOf(false)}
 AlertDialog(onDismissRequest=dismiss,containerColor=CARD,title={Text(if(old==null)"Novo lançamento" else "Editar lançamento",color=TEXT)},text={
  Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Despesa","Receita").forEach{FilterChip(selected=type==it,onClick={type=it},label={Text(it)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=GREEN,selectedLabelColor=BG))}}
   OutlinedTextField(title,{title=it},label={Text("Descrição")},singleLine=true)
   OutlinedTextField(value,{value=it},label={Text("Valor em R$")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
   Box{OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()){Text(category,color=TEXT)};DropdownMenu(expanded,{expanded=false}){cats.forEach{DropdownMenuItem(text={Text(it)},onClick={category=it;expanded=false})}}}
  }
 },confirmButton={TextButton(onClick={val v=value.replace(",",".").toDoubleOrNull();if(title.isNotBlank()&&v!=null&&v>0)save(title.trim(),v,category,type)}){Text("Salvar",color=GREEN)}},dismissButton={TextButton(onClick=dismiss){Text("Cancelar",color=MUTED)}})
}
@Composable
fun ValueDialog(title:String,initial:String,save:(String)->Unit,dismiss:()->Unit) {
 var v by remember{mutableStateOf(initial)}
 AlertDialog(onDismissRequest=dismiss,containerColor=CARD,title={Text(title,color=TEXT)},text={OutlinedTextField(v,{v=it},label={Text("Valor em R$")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))},confirmButton={TextButton(onClick={save(v)}){Text("Salvar",color=GREEN)}},dismissButton={TextButton(onClick=dismiss){Text("Cancelar",color=MUTED)}})
}
@Composable
fun GoalEdit(name:String,target:Double,saved:Double,dismiss:()->Unit,save:(String,Double,Double)->Unit) {
 var n by remember{mutableStateOf(name)};var t by remember{mutableStateOf(target.toString())};var s by remember{mutableStateOf(saved.toString())}
 AlertDialog(onDismissRequest=dismiss,containerColor=CARD,title={Text("Configurar meta",color=TEXT)},text={Column(verticalArrangement=Arrangement.spacedBy(7.dp)){OutlinedTextField(n,{n=it},label={Text("Nome")});OutlinedTextField(t,{t=it},label={Text("Objetivo em R$")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal));OutlinedTextField(s,{s=it},label={Text("Já guardado em R$")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))}},confirmButton={TextButton(onClick={val a=t.replace(",",".").toDoubleOrNull();val b=s.replace(",",".").toDoubleOrNull();if(n.isNotBlank()&&a!=null&&a>0&&b!=null&&b>=0)save(n,a,b)}){Text("Salvar",color=GREEN)}},dismissButton={TextButton(onClick=dismiss){Text("Cancelar",color=MUTED)}})
}
