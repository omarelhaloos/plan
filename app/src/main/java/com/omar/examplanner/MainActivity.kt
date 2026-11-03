package com.omar.examplanner

import android.app.*
import android.content.*
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val Green = Color(0xFF159957)
private val Pale = Color(0xFFEAF7EF)
private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private val dateDisplay = SimpleDateFormat("dd/MM/yyyy", Locale("ar"))

data class Subject(
    val name: String, val examDate: String,
    val tasks: List<String> = emptyList(), val done: Set<Int> = emptySet()
)
data class StudySession(
    val id: Int, val title: String, val date: String, val start: String, val end: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 300)
        }
        setContent { ExamPlannerApp(this) }
    }
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("study_reminders", "تذكيرات المذاكرة",
                NotificationManager.IMPORTANCE_HIGH).apply {
                description = "تنبيهات بداية جلسات المذاكرة"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

class StudyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "موعد المذاكرة"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(context, "study_reminders")
        else Notification.Builder(context)
        val n = notification.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("حان وقت المذاكرة 📚")
            .setContentText(title)
            .setAutoCancel(true).build()
        manager.notify(intent.getIntExtra("id", 1), n)
    }
}

private fun scheduleReminder(context: Context, session: StudySession) {
    try {
        val parsed = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            .parse("${session.date} ${session.start}") ?: return
        if (parsed.time <= System.currentTimeMillis()) return
        val intent = Intent(context, StudyReminderReceiver::class.java)
            .putExtra("title", session.title).putExtra("id", session.id)
        val pending = PendingIntent.getBroadcast(context, session.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, parsed.time, pending)
    } catch (_: Exception) { }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamPlannerApp(activity: ComponentActivity) {
    val prefs = activity.getSharedPreferences("exam_planner", 0)
    fun loadSubjects(): List<Subject> = try {
        val a = JSONArray(prefs.getString("subjects", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i); val t = o.optJSONArray("tasks") ?: JSONArray()
            val d = o.optJSONArray("done") ?: JSONArray()
            Subject(o.optString("name"), o.optString("date"),
                (0 until t.length()).map { t.getString(it) },
                (0 until d.length()).map { d.getInt(it) }.toSet())
        }
    } catch (_: Exception) { emptyList() }
    fun loadSessions(): List<StudySession> = try {
        val a = JSONArray(prefs.getString("sessions", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            StudySession(o.optInt("id"), o.optString("title"), o.optString("date"),
                o.optString("start"), o.optString("end"))
        }
    } catch (_: Exception) { emptyList() }

    var subjects by remember { mutableStateOf(loadSubjects()) }
    var sessions by remember { mutableStateOf(loadSessions()) }
    var tab by remember { mutableIntStateOf(0) }
    var addSubject by remember { mutableStateOf(false) }
    var addSession by remember { mutableStateOf(false) }
    var taskIndex by remember { mutableIntStateOf(-1) }

    fun saveSubjects(list: List<Subject>) {
        val a = JSONArray()
        list.forEach { s ->
            val o = JSONObject().put("name", s.name).put("date", s.examDate)
            val t = JSONArray(); s.tasks.forEach { t.put(it) }
            val d = JSONArray(); s.done.sorted().forEach { d.put(it) }
            o.put("tasks", t).put("done", d); a.put(o)
        }
        prefs.edit().putString("subjects", a.toString()).apply(); subjects = list
    }
    fun saveSessions(list: List<StudySession>) {
        val a = JSONArray()
        list.forEach { s -> a.put(JSONObject().put("id", s.id).put("title", s.title)
            .put("date", s.date).put("start", s.start).put("end", s.end)) }
        prefs.edit().putString("sessions", a.toString()).apply(); sessions = list
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = Green, secondary = Green,
        background = Color(0xFFF7FAF8), surface = Color.White)) {
        Scaffold(
            topBar = { TopAppBar(title = {
                Column {
                    Text("Exam Planner", fontWeight = FontWeight.Bold)
                    Text("خطّط لمذاكرتك وسيطر على وقتك", fontSize = 12.sp, color = Color.Gray)
                }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)) },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    NavigationBarItem(tab == 0, { tab = 0 }, icon = { Icon(Icons.Default.Event, null) },
                        label = { Text("الرئيسية") })
                    NavigationBarItem(tab == 1, { tab = 1 }, icon = { Icon(Icons.Default.CalendarMonth, null) },
                        label = { Text("الجدول") })
                    NavigationBarItem(tab == 2, { tab = 2 }, icon = { Icon(Icons.Default.MenuBook, null) },
                        label = { Text("المواد") })
                }
            },
            floatingActionButton = {
                FloatingActionButton(onClick = {
                    if (tab == 1) addSession = true else addSubject = true
                }, containerColor = Green, contentColor = Color.White) { Icon(Icons.Default.Add, "إضافة") }
            },
            containerColor = Color(0xFFF7FAF8)
        ) { padding ->
            when (tab) {
                0 -> {
                    val total = subjects.sumOf { it.tasks.size }
                    val done = subjects.sumOf { it.done.size }
                    val next = subjects.mapNotNull { s ->
                        parseExamDate(s.examDate)?.let { s to it }
                    }.filter { it.second.time >= dayStart() }.minByOrNull { it.second.time }
                    LazyColumn(Modifier.padding(padding).fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = Pale),
                                shape = RoundedCornerShape(20.dp)) {
                                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                                    Text("أهلاً بيك 👋", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    Text("كل جلسة مذاكرة بتقرّبك من هدفك", color = Color.DarkGray)
                                    Spacer(Modifier.height(14.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Stat("المواد", subjects.size.toString())
                                        Stat("جلسات المذاكرة", sessions.size.toString())
                                        Stat("المهام المنجزة", "$done/$total")
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat()/total },
                                        modifier = Modifier.fillMaxWidth().height(8.dp), color = Green,
                                        trackColor = Color.White)
                                    Text(if (total == 0) "أضف مهامك لمتابعة التقدم"
                                        else "نسبة الإنجاز ${done * 100 / total}%", fontSize = 12.sp)
                                }
                            }
                        }
                        item { Text("الامتحان القادم", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                        item {
                            if (next == null) EmptyCard("أضف المواد وتواريخ امتحاناتها لعرض العد التنازلي")
                            else {
                                val days = ((next.second.time - dayStart()) / 86400000L).toInt()
                                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                        Text(next.first.name, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                        Text("التاريخ: ${next.first.examDate}")
                                        Text(if (days == 0) "الامتحان اليوم!" else "متبقي $days يوم",
                                            color = Green, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    }
                                }
                            }
                        }
                        item { Text("جلسات المذاكرة القادمة", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                        if (sessions.isEmpty()) item { EmptyCard("لم تضف جلسات بعد. افتح تبويب الجدول واضغط +") }
                        items(sessions.sortedBy { "${it.date} ${it.start}" }.take(5)) { s ->
                            SessionCard(s, onDelete = { saveSessions(sessions.filterNot { it.id == s.id }) })
                        }
                    }
                }
                1 -> LazyColumn(Modifier.padding(padding).fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Text("جدول المذاكرة", fontWeight = FontWeight.Bold, fontSize = 20.sp) }
                    item { Text("أضف جلسة بتاريخ ووقت بداية ونهاية. سيصلك تنبيه عند بدايتها.",
                        color = Color.Gray, fontSize = 13.sp) }
                    if (sessions.isEmpty()) item { EmptyCard("لا توجد جلسات مذاكرة. اضغط + لإضافة أول جلسة.") }
                    items(sessions.sortedBy { "${it.date} ${it.start}" }) { s ->
                        SessionCard(s, onDelete = { saveSessions(sessions.filterNot { it.id == s.id }) })
                    }
                }
                else -> LazyColumn(Modifier.padding(padding).fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Text("المواد والامتحانات", fontWeight = FontWeight.Bold, fontSize = 20.sp) }
                    if (subjects.isEmpty()) item { EmptyCard("أضف المادة وتاريخ امتحانها من زر +") }
                    items(subjects.sortedBy { parseExamDate(it.examDate)?.time ?: Long.MAX_VALUE }) { s ->
                        SubjectCard(s, onTasks = { taskIndex = subjects.indexOf(s) },
                            onDelete = { saveSubjects(subjects.filterNot { it.name == s.name && it.examDate == s.examDate }) })
                    }
                }
            }
        }
    }

    if (addSubject) AddSubjectDialog({ addSubject = false }) { n, d ->
        saveSubjects(subjects + Subject(n, d)); addSubject = false
    }
    if (addSession) AddSessionDialog(activity, { addSession = false }) { title, date, start, end ->
        val session = StudySession((System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            title, date, start, end)
        saveSessions(sessions + session)
        scheduleReminder(activity, session)
        addSession = false
    }
    if (taskIndex in subjects.indices) {
        val i = taskIndex
        TaskDialog(subjects[i], { taskIndex = -1 },
            { t -> saveSubjects(subjects.toMutableList().also { it[i] = it[i].copy(tasks = it[i].tasks + t) }) },
            { ix, checked -> saveSubjects(subjects.toMutableList().also {
                val s = it[i]; it[i] = s.copy(done = if (checked) s.done + ix else s.done - ix)
            }) },
            { ix -> saveSubjects(subjects.toMutableList().also {
                val s = it[i]; it[i] = s.copy(tasks = s.tasks.filterIndexed { j, _ -> j != ix },
                    done = s.done.filter { it < ix }.toSet() + s.done.filter { it > ix }.map { it - 1 })
            }) })
    }
}

private fun dayStart(): Long {
    val c = Calendar.getInstance(); c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0); return c.timeInMillis
}
private fun parseExamDate(value: String): Date? {
    val formats = listOf("yyyy-MM-dd", "dd/MM/yyyy", "d/M/yyyy", "dd/MM", "d/M")
    for (f in formats) try {
        val parser = SimpleDateFormat(f, Locale.US); parser.isLenient = false
        val d = parser.parse(value) ?: continue
        if (f == "dd/MM" || f == "d/M") {
            val c = Calendar.getInstance(); val p = Calendar.getInstance().apply { time = d }
            c.set(Calendar.MONTH, p.get(Calendar.MONTH)); c.set(Calendar.DAY_OF_MONTH, p.get(Calendar.DAY_OF_MONTH))
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0)
            return c.time
        }
        return d
    } catch (_: Exception) { }
    return null
}

@Composable fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Green)
        Text(label, fontSize = 11.sp, color = Color.DarkGray)
    }
}
@Composable fun EmptyCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Text(message, Modifier.fillMaxWidth().padding(20.dp), color = Color.Gray)
    }
}
@Composable fun SubjectCard(s: Subject, onTasks: () -> Unit, onDelete: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("الامتحان: ${s.examDate}", color = Color.Gray, fontSize = 12.sp)
                    parseExamDate(s.examDate)?.let {
                        val days = ((it.time - dayStart()) / 86400000L).toInt()
                        Text(if (days < 0) "انتهى موعد الامتحان" else if (days == 0) "الامتحان اليوم" else "متبقي $days يوم",
                            color = Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "حذف", tint = Color.Gray) }
            }
            val count = s.tasks.size; val done = s.done.size
            LinearProgressIndicator(progress = { if (count == 0) 0f else done.toFloat()/count },
                modifier = Modifier.fillMaxWidth().height(6.dp), color = Green)
            Text("$done من $count مهام مكتملة", fontSize = 11.sp, color = Color.Gray)
            TextButton(onClick = onTasks) { Text("إدارة المحاضرات والمهام") }
        }
    }
}
@Composable fun SessionCard(s: StudySession, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.title, fontWeight = FontWeight.Bold)
                Text("${s.date}  •  ${s.start} – ${s.end}", color = Color.Gray, fontSize = 12.sp)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "حذف", tint = Color.Gray) }
        }
    }
}
@Composable fun AddSubjectDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }; var date by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("إضافة مادة") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("اسم المادة") }, singleLine = true)
            OutlinedTextField(date, { date = it }, label = { Text("تاريخ الامتحان: YYYY-MM-DD") }, singleLine = true)
        } },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
            onAdd(name.trim(), date.ifBlank { "غير محدد" })
        }) { Text("إضافة") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}
@Composable fun AddSessionDialog(activity: ComponentActivity, onDismiss: () -> Unit,
                                onAdd: (String, String, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(dateFmt.format(Date())) }
    var start by remember { mutableStateOf("08:00") }
    var end by remember { mutableStateOf("10:00") }
    val ctx = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss, title = { Text("إضافة جلسة مذاكرة") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("المادة / المهمة") }, singleLine = true)
            OutlinedButton(onClick = {
                val c = Calendar.getInstance()
                DatePickerDialog(ctx, { _, y, m, d ->
                    date = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
            }, modifier = Modifier.fillMaxWidth()) { Text("التاريخ: $date") }
            OutlinedButton(onClick = {
                val c = Calendar.getInstance()
                TimePickerDialog(ctx, { _, h, m -> start = String.format(Locale.US, "%02d:%02d", h, m) },
                    c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
            }, modifier = Modifier.fillMaxWidth()) { Text("وقت البداية: $start") }
            OutlinedButton(onClick = {
                val c = Calendar.getInstance()
                TimePickerDialog(ctx, { _, h, m -> end = String.format(Locale.US, "%02d:%02d", h, m) },
                    c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
            }, modifier = Modifier.fillMaxWidth()) { Text("وقت النهاية: $end") }
            Text("سيتم ضبط تنبيه عند بداية الجلسة. اسمح بالإشعارات من إعدادات الهاتف.",
                fontSize = 11.sp, color = Color.Gray)
        } },
        confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onAdd(title, date, start, end) }) {
            Text("حفظ الجلسة")
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}
@Composable fun TaskDialog(subject: Subject, onDismiss: () -> Unit, onAdd: (String) -> Unit,
                           onToggle: (Int, Boolean) -> Unit, onDelete: (Int) -> Unit) {
    var task by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("مهام: ${subject.name}") },
        text = { Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(task, { task = it }, Modifier.weight(1f),
                    label = { Text("محاضرة / جزء مطلوب") }, singleLine = true)
                IconButton(onClick = { if (task.isNotBlank()) { onAdd(task.trim()); task = "" } }) {
                    Icon(Icons.Default.Add, "إضافة", tint = Green)
                }
            }
            subject.tasks.forEachIndexed { i, item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(i in subject.done, { onToggle(i, it) })
                    Text(item, Modifier.weight(1f))
                    IconButton(onClick = { onDelete(i) }) { Icon(Icons.Default.Delete, null, tint = Color.Gray) }
                }
            }
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("تم") } })
}
