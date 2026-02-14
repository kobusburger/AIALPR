package com.example.aialpr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.provider.OpenableColumns
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.rememberAsyncImagePainter
import com.example.aialpr.api.PlateRecognizerResponse
import com.example.aialpr.api.PlateRecognizerService
import com.example.aialpr.db.AppDatabase
import com.example.aialpr.db.PlateInfo
import com.example.aialpr.db.RecognitionResult
import com.example.aialpr.ui.theme.AIALPRTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIALPRTheme {
                CameraScreen()
            }
        }
    }
}

@Parcelize
private sealed class ScreenState : Parcelable {
    @Parcelize data object Camera : ScreenState()
    @Parcelize data object Loading : ScreenState()
    @Parcelize data class Result(
        val photoFile: File,
        val photoUri: Uri,
        val response: PlateRecognizerResponse
    ) : ScreenState()
    @Parcelize data class Error(val message: String) : ScreenState()
    @Parcelize data object History : ScreenState()
}

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    var screenState by rememberSaveable { mutableStateOf<ScreenState>(ScreenState.Camera) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            Toast.makeText(context, "Camera permission is required", Toast.LENGTH_LONG).show()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                exportData(context, it)
                Toast.makeText(context, "Data exported successfully", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        if (!hasCameraPermission) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Camera permission needed", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        when (val state = screenState) {
            is ScreenState.Camera -> CameraPreview(
                modifier = Modifier.padding(innerPadding),
                lifecycleOwner = lifecycleOwner,
                onPhotoTaken = { file, uri ->
                    screenState = ScreenState.Loading
                    PlateRecognizerService.recognizePlate(
                        imageFile = file,
                        onSuccess = { response ->
                            coroutineScope.launch {
                                screenState = ScreenState.Result(photoFile = file, photoUri = uri, response = response)
                            }
                        },
                        onError = { msg ->
                            coroutineScope.launch { screenState = ScreenState.Error(msg) }
                        }
                    )
                },
                onOpenHistory = { screenState = ScreenState.History }
            )
            is ScreenState.Loading -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Submitting to PlateRecognizer…")
                }
            }
            is ScreenState.Result -> {
                LaunchedEffect(state.photoUri) {
                    if (state.response.results.orEmpty().isNotEmpty()) {
                        saveToDatabase(context, state)
                        Toast.makeText(context, "Saved to Local Database", Toast.LENGTH_LONG).show()
                    }
                }
                ResultScreen(
                    modifier = Modifier.padding(innerPadding),
                    photoUri = state.photoUri,
                    response = state.response,
                    onTakeAnother = { screenState = ScreenState.Camera }
                )
            }
            is ScreenState.Error -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Error: ${state.message}", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { screenState = ScreenState.Camera }) { Text("Back to camera") }
                }
            }
            is ScreenState.History -> HistoryScreen(
                modifier = Modifier.padding(innerPadding),
                onBack = { screenState = ScreenState.Camera },
                onExport = {
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    exportLauncher.launch("AIALPR_Export_$timestamp.zip")
                }
            )
        }
    }
}

private suspend fun saveToDatabase(context: Context, state: ScreenState.Result) {
    withContext(Dispatchers.IO) {
        val results = state.response.results.orEmpty()
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
        val photoBytes = state.photoFile.readBytes()
        val db = AppDatabase.getDatabase(context)

        results.forEach { r ->
            db.recognitionDao().insert(
                RecognitionResult(
                    timestamp = timestamp,
                    plate = r.plate ?: "-",
                    region = r.region?.code ?: "-",
                    score = r.score ?: 0.0,
                    photoBytes = photoBytes
                )
            )
        }
    }
}

private suspend fun exportData(context: Context, uri: Uri) {
    withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val allResults = db.recognitionDao().getAll()
        val usedNames = mutableSetOf<String>()
        
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            ZipOutputStream(outputStream).use { zipOut ->
                val csvContent = StringBuilder("ID,Timestamp,Plate,Region,Score,PhotoFile\n")
                allResults.forEach { res ->
                    val date = try {
                        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(res.timestamp)
                    } catch (_: Exception) { null }
                    
                    val baseName = if (date != null) SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(date) else "photo_${res.id}"
                    var photoFileName = "$baseName.jpg"
                    var counter = 1
                    while (usedNames.contains(photoFileName)) {
                        photoFileName = "${baseName}_$counter.jpg"
                        counter++
                    }
                    usedNames.add(photoFileName)

                    csvContent.append("${res.id},${res.timestamp},${res.plate},${res.region},${res.score},$photoFileName\n")
                    
                    zipOut.putNextEntry(ZipEntry("photos/$photoFileName"))
                    zipOut.write(res.photoBytes)
                    zipOut.closeEntry()
                }
                
                zipOut.putNextEntry(ZipEntry("data.csv"))
                zipOut.write(csvContent.toString().toByteArray())
                zipOut.closeEntry()
            }
        }
    }
}

@Composable
private fun CameraPreview(
    modifier: Modifier,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onPhotoTaken: (File, Uri) -> Unit,
    onOpenHistory: () -> Unit
) {
    val context = LocalContext.current
    Box(modifier = modifier.fillMaxSize()) {
        var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
        var cameraBound by remember { mutableStateOf(false) }

        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                if (!cameraBound) {
                    cameraBound = true
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = view.surfaceProvider
                        }
                        val resolutionSelector = ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(1920, 1080),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER
                                )
                            )
                            .build()
                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .setResolutionSelector(resolutionSelector)
                            .build()
                        imageCapture = capture
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                capture
                            )
                        } catch (_: Exception) {}
                    }, ContextCompat.getMainExecutor(context))
                }
            }
        )

        FloatingActionButton(
            onClick = {
                val capture = imageCapture ?: return@FloatingActionButton
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val photoFile = File(context.cacheDir, "$timestamp.jpg")
                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                capture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            onPhotoTaken(photoFile, Uri.fromFile(photoFile))
                        }
                        override fun onError(exception: ImageCaptureException) {
                            Toast.makeText(context, "Capture error: ${exception.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .size(72.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            shape = MaterialTheme.shapes.medium,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Text("📷", style = MaterialTheme.typography.headlineMedium)
        }

        FloatingActionButton(
            onClick = onOpenHistory,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Icon(Icons.Default.History, contentDescription = "History")
        }
    }
}

@Composable
private fun ResultScreen(
    modifier: Modifier,
    photoUri: Uri,
    response: PlateRecognizerResponse,
    onTakeAnother: () -> Unit
) {
    val context = LocalContext.current
    var plateInfos by remember { mutableStateOf<Map<String, PlateInfo>>(emptyMap()) }
    val detectedPlates = response.results?.mapNotNull { it.plate } ?: emptyList()

    LaunchedEffect(detectedPlates) {
        if (detectedPlates.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(context)
                val map = mutableMapOf<String, PlateInfo>()
                detectedPlates.distinct().forEach { plate ->
                    db.plateInfoDao().getInfoForPlate(plate)?.let {
                        map[plate] = it
                    }
                }
                plateInfos = map
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Result", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Image(
            painter = rememberAsyncImagePainter(photoUri),
            contentDescription = "Captured photo",
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(16.dp))
        val results = response.results.orEmpty()
        if (results.isEmpty()) {
            Text("No plates detected", style = MaterialTheme.typography.bodyLarge)
        } else {
            Text("Plates found: ${results.size}", style = MaterialTheme.typography.titleMedium)
            results.forEach { r ->
                val plateName = r.plate ?: "-"
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween)
                {
                    Text("Plate: $plateName", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text("Score: ${String.format(Locale.US, "%.2f", r.score ?: 0.0)}", style = MaterialTheme.typography.bodySmall)
                }
                Text("Region: ${r.region?.code ?: "-"}", style = MaterialTheme.typography.bodySmall)
                
                plateInfos[plateName]?.let { info ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Additional Info:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = info.extraData,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onTakeAnother, modifier = Modifier.fillMaxWidth()) { Text("Take another") }
    }
}

@Composable
private fun HistoryScreen(
    modifier: Modifier,
    onBack: () -> Unit,
    onExport: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var allResults by remember { mutableStateOf<List<RecognitionResult>>(emptyList()) }
    var filterText by rememberSaveable { mutableStateOf("") }
    var selectedResult by rememberSaveable { mutableStateOf<RecognitionResult?>(null) }
    var selectedPlateInfo by remember { mutableStateOf<PlateInfo?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val fileName = getFileName(context, it)
            if (fileName?.lowercase()?.endsWith(".csv") == true) {
                coroutineScope.launch {
                    importCsv(context, it)
                    Toast.makeText(context, "CSV imported successfully", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(context, "Please select a .csv file", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            allResults = db.recognitionDao().getAll()
        }
    }

    LaunchedEffect(selectedResult) {
        selectedResult?.let { res ->
            withContext(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(context)
                selectedPlateInfo = db.plateInfoDao().getInfoForPlate(res.plate)
            }
        } ?: run {
            selectedPlateInfo = null
        }
    }

    val filteredResults = if (filterText.isEmpty()) {
        allResults
    } else {
        allResults.filter { it.plate.contains(filterText, ignoreCase = true) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Recognition History",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
            IconButton(onClick = { 
                importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values")) 
            }) {
                Icon(Icons.Default.FileUpload, contentDescription = "Import CSV")
            }
            IconButton(onClick = onExport) {
                Icon(Icons.Default.FileDownload, contentDescription = "Export")
            }
        }

        OutlinedTextField(
            value = filterText,
            onValueChange = { filterText = it },
            label = { Text("Filter by Plate") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            singleLine = true
        )

        if (filteredResults.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No records found")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Plate", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("Timestamp", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold)
                        Text("Region", modifier = Modifier.weight(0.8f), fontWeight = FontWeight.Bold)
                        Text("Score", modifier = Modifier.weight(0.7f), fontWeight = FontWeight.Bold)
                    }
                }

                items(filteredResults) { res ->
                    Column(modifier = Modifier.clickable { selectedResult = res }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(res.plate, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                res.timestamp.replace("T", " ").substringBeforeLast(":"),
                                modifier = Modifier.weight(1.5f),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(res.region, modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.bodySmall)
                            Text(
                                String.format(Locale.US, "%.2f", res.score),
                                modifier = Modifier.weight(0.7f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
                    }
                }
            }
        }
    }

    selectedResult?.let { res ->
        Dialog(onDismissRequest = { selectedResult = null }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Plate: ${res.plate}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = res.timestamp.replace("T", " "),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    Image(
                        painter = rememberAsyncImagePainter(res.photoBytes),
                        contentDescription = "Recognition Photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .background(Color.Black),
                        contentScale = ContentScale.Fit
                    )

                    selectedPlateInfo?.let { info ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Additional Info:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Text(
                            text = info.extraData,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.Start).padding(top = 4.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { selectedResult = null },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

private fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) result = cursor.getString(index)
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}

private suspend fun importCsv(context: Context, uri: Uri) {
    withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val plateInfos = mutableListOf<PlateInfo>()
        
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                val header = reader.readLine() // Read header
                if (header != null) {
                    val columns = header.split(",")
                    val plateIndex = columns.indexOfFirst { it.contains("plate", ignoreCase = true) }
                    
                    if (plateIndex != -1) {
                        var line = reader.readLine()
                        while (line != null) {
                            val values = line.split(",")
                            if (values.size > plateIndex) {
                                val plate = values[plateIndex].trim()
                                val extraBuilder = StringBuilder()
                                for (i in values.indices) {
                                    if (i != plateIndex && i < columns.size) {
                                        if (extraBuilder.isNotEmpty()) extraBuilder.append("\n")
                                        extraBuilder.append("${columns[i].trim()}: ${values[i].trim()}")
                                    }
                                }
                                plateInfos.add(PlateInfo(plate, extraBuilder.toString()))
                            }
                            line = reader.readLine()
                        }
                    }
                }
            }
        }
        
        if (plateInfos.isNotEmpty()) {
            db.plateInfoDao().replaceAll(plateInfos)
        }
    }
}
