package com.example.afinal.ui.screen

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object AppColors {
    val Background = Color(0xFF121212)
    val Card = Color.White
    val Primary = Color(0xFF2196F3) // Xanh dương
    val TextMain = Color.Black
    val TextSub = Color.Gray
    val TabBar = Color(0xFF1E1E1E)
}

data class AudioRecord(
    val id: String = "",
    val final_text: String = "",
    val original_text: String = "",
    val tags: List<String> = emptyList(),
    val audio_url: String = "",
    val input_type: String = "",
    val description: String = "",
    val is_finished: Boolean = false,
    val created_at: Date? = null
)

object DataRepository {
    suspend fun uploadData(
        context: Context,
        ngrokUrl: String,
        description: String,
        tags: String,
        textInput: String?,
        audioUri: Uri?
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                var finalUrl = ngrokUrl.trim()
                if (finalUrl.endsWith("/")) finalUrl = finalUrl.dropLast(1)
                if (!finalUrl.endsWith("/api/process")) finalUrl += "/api/process"

                // Config Timeout 10 phút
                val client = OkHttpClient.Builder()
                    .connectTimeout(2, TimeUnit.MINUTES)
                    .writeTimeout(10, TimeUnit.MINUTES)
                    .readTimeout(10, TimeUnit.MINUTES)
                    .build()

                // Tạo Multipart Request
                val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
                multipart.addFormDataPart("list_tags", tags)
                multipart.addFormDataPart("description", description)

                if (textInput != null) {
                    if (textInput.isBlank()) return@withContext "Chưa nhập nội dung Text"
                    multipart.addFormDataPart("text_input", textInput)
                } else if (audioUri != null) {
                    val file = uriToFile(context, audioUri) ?: return@withContext "Lỗi đọc file Audio"
                    multipart.addFormDataPart("audio_file", file.name, file.asRequestBody("audio/mpeg".toMediaTypeOrNull()))
                } else {
                    return@withContext "Chưa chọn dữ liệu đầu vào"
                }
                val request = Request.Builder().url(finalUrl).post(multipart.build()).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) "" else "Lỗi Server: ${response.code}"

            } catch (e: Exception) {
                "Lỗi kết nối: ${e.message}"
            }
        }
    }

    // 2.2 Hàm chuyển đổi Uri sang File thực
    private fun uriToFile(context: Context, uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(context.cacheDir, "temp_audio_${System.currentTimeMillis()}.mp3")
            val outputStream = FileOutputStream(tempFile)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            tempFile
        } catch (e: Exception) { e.printStackTrace(); null }
    }

    fun listenToRecords(onDataChanged: (List<AudioRecord>) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("records")
            .orderBy("created_at", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener

                val list = snapshot.documents.mapNotNull { doc ->
                    try {
                        AudioRecord(
                            id = doc.id,
                            final_text = doc.getString("final_text") ?: "",
                            tags = doc.get("tags") as? List<String> ?: emptyList(),
                            audio_url = doc.getString("audio_url") ?: "",
                            input_type = doc.getString("input_type") ?: "",
                            description = doc.getString("description") ?: "",
                            is_finished = doc.getBoolean("is_finished") ?: false,
                            created_at = doc.getTimestamp("created_at")?.toDate()
                        )
                    } catch (e: Exception) { null }
                }
                // Lọc chỉ lấy bản ghi đã hoàn thành
                onDataChanged(list.filter { it.is_finished })
            }
    }
}
@Composable
fun UserScreen(mainNavController: NavController) {
    val context = LocalContext.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // Quản lý Media Player
    val mediaPlayer = remember { MediaPlayer() }
    var currentPlayingUrl by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) { onDispose { mediaPlayer.release() } }

    Surface(modifier = Modifier.fillMaxSize(), color = AppColors.Background) {
        Column(modifier = Modifier.fillMaxSize()) {

            // 3.1 Tab Bar
            CustomTabBar(selectedTabIndex) { selectedTabIndex = it }

            // 3.2 Nội dung chính
            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                if (selectedTabIndex == 0) {
                    InputScreen(context)
                } else {
                    ListScreen(
                        mediaPlayer = mediaPlayer,
                        currentPlayingUrl = currentPlayingUrl,
                        onUpdatePlayingUrl = { currentPlayingUrl = it }
                    )
                }
            }
        }
    }
}

// --- Component: Tab Bar ---
@Composable
fun CustomTabBar(selectedIndex: Int, onTabSelected: (Int) -> Unit) {
    val tabs = listOf("Nhập liệu", "Danh sách (Đã xong)")
    TabRow(
        selectedTabIndex = selectedIndex,
        containerColor = AppColors.TabBar,
        contentColor = Color.White,
        indicator = { tabPositions ->
            TabRowDefaults.Indicator(
                Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                color = AppColors.Primary
            )
        }
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onTabSelected(index) },
                text = {
                    Text(title, fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Normal)
                },
                selectedContentColor = AppColors.Primary,
                unselectedContentColor = AppColors.TextSub
            )
        }
    }
}

// --- Component: Màn hình Nhập liệu (Tab 1) ---
@Composable
fun InputScreen(context: Context) {
    var ngrokUrl by remember { mutableStateOf("https://emergently-basipetal-marge.ngrok-free.dev") }
    var description by remember { mutableStateOf("Gửi từ Android") }
    var tagsInput by remember {
        mutableStateOf("Romance, Pet, Mysteries, Facilities information, " +
            "Health, Food and drink, Social and communities, Personal experience, Warning, Study Hacks, " +
            "Library Vibes, Confessions, Motivation, Gaming, Burnout, Emotional support")
    }
    var isTextMode by remember { mutableStateOf(true) }
    var textInput by remember { mutableStateOf("") }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { selectedFileUri = it }

    fun handleSend() {
        isLoading = true
        scope.launch {
            val errorMsg = DataRepository.uploadData(
                context, ngrokUrl, description, tagsInput,
                if (isTextMode) textInput else null,
                if (!isTextMode) selectedFileUri else null
            )

            isLoading = false
            if (errorMsg.isEmpty()) {
                Toast.makeText(context, "✅ Đã gửi! Vui lòng chờ xử lý.", Toast.LENGTH_LONG).show()
                textInput = ""
                selectedFileUri = null
            } else {
                Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Card(
            colors = CardDefaults.cardColors(containerColor = AppColors.Card),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Input URL
                OutlinedTextField(
                    value = ngrokUrl, onValueChange = { ngrokUrl = it }, label = { Text("Ngrok URL") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = AppColors.TextMain, unfocusedTextColor = AppColors.TextMain)
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Toggle Mode
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { isTextMode = true },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isTextMode) AppColors.Primary else Color.LightGray),
                        modifier = Modifier.weight(1f).padding(end = 4.dp)
                    ) { Text("Nhập Text") }
                    Button(
                        onClick = { isTextMode = false },
                        colors = ButtonDefaults.buttonColors(containerColor = if (!isTextMode) AppColors.Primary else Color.LightGray),
                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                    ) { Text("Gửi Audio") }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Input Content
                if (isTextMode) {
                    OutlinedTextField(
                        value = textInput, onValueChange = { textInput = it }, label = { Text("Nội dung Text") },
                        modifier = Modifier.fillMaxWidth(), minLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = AppColors.TextMain, unfocusedTextColor = AppColors.TextMain)
                    )
                } else {
                    Button(
                        onClick = { filePicker.launch("audio/*") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                    ) { Text(if (selectedFileUri == null) "📂 Chọn File Audio" else "✅ Đã chọn file") }
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Other Inputs
                OutlinedTextField(
                    value = tagsInput, onValueChange = { tagsInput = it }, label = { Text("Tags gợi ý") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = AppColors.TextMain, unfocusedTextColor = AppColors.TextMain)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = description, onValueChange = { description = it }, label = { Text("Mô tả") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = AppColors.TextMain, unfocusedTextColor = AppColors.TextMain)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { handleSend() }, enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
        ) {
            if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            else Text("GỬI DỮ LIỆU", fontWeight = FontWeight.Bold)
        }
    }
}

// --- Component: Màn hình Danh sách (Tab 2) ---
@Composable
fun ListScreen(
    mediaPlayer: MediaPlayer,
    currentPlayingUrl: String?,
    onUpdatePlayingUrl: (String?) -> Unit
) {
    var records by remember { mutableStateOf<List<AudioRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current

    fun toggleAudio(url: String) {
        if (currentPlayingUrl == url) {
            // Stop
            try { if (mediaPlayer.isPlaying) mediaPlayer.stop(); mediaPlayer.reset() } catch (e: Exception) {}
            onUpdatePlayingUrl(null)
        } else {
            // Play New
            try {
                mediaPlayer.reset()
                mediaPlayer.setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                mediaPlayer.setDataSource(url)
                mediaPlayer.prepareAsync()
                mediaPlayer.setOnPreparedListener { it.start(); onUpdatePlayingUrl(url) }
                mediaPlayer.setOnCompletionListener { onUpdatePlayingUrl(null) }
            } catch (e: Exception) {
                Toast.makeText(context, "Lỗi Audio", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        DataRepository.listenToRecords { data ->
            records = data
            isLoading = false
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AppColors.Primary)
        }
    } else if (records.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Chưa có dữ liệu hoàn tất.", color = AppColors.TextSub)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(records) { record ->
                RecordItem(record = record, isPlaying = currentPlayingUrl == record.audio_url) {
                    if (record.audio_url.isNotEmpty()) toggleAudio(record.audio_url)
                }
            }
        }
    }
}

// --- Component: Item trong Danh sách ---
@Composable
fun RecordItem(record: AudioRecord, isPlaying: Boolean, onPlayClick: () -> Unit) {
    val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play Button
            IconButton(
                onClick = onPlayClick,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isPlaying) Color(0xFFFFEBEE) else Color(0xFFE3F2FD))
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Refresh else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = if (isPlaying) Color.Red else AppColors.Primary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val badgeColor = if (record.input_type == "AUDIO") AppColors.Primary else Color(0xFF9C27B0)
                    Text(
                        text = if (record.input_type == "AUDIO") "MIC" else "TXT",
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        modifier = Modifier.background(badgeColor, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (record.created_at != null) dateFormat.format(record.created_at) else "",
                        fontSize = 12.sp, color = AppColors.TextSub
                    )
                }

                // Description
                if (record.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = record.description, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.TextMain)
                }

                // Main Text
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = record.final_text,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                // Tags
                if (record.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "#${record.tags.joinToString(" #")}",
                        fontSize = 11.sp, color = AppColors.Primary,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        }
    }
}