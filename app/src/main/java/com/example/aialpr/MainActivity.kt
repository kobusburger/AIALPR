package com.example.aialpr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.rememberAsyncImagePainter
import com.example.aialpr.api.PlateRecognizerResponse
import com.example.aialpr.api.PlateRecognizerService
import com.example.aialpr.db.AppDatabase
import com.example.aialpr.db.RecognitionResult
import com.example.aialpr.ui.theme.AIALPRTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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


private sealed class ScreenState {
    data object Camera : ScreenState()
    data object Loading : ScreenState()
    data class Result(
        val photoFile: File,
        val photoUri: Uri,
        val response: PlateRecognizerResponse
    ) : ScreenState()
    data class Error(val message: String) : ScreenState()
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
    var screenState: ScreenState by remember { mutableStateOf(ScreenState.Camera) }

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
                context = context,
                lifecycleOwner = lifecycleOwner,
                onPhotoTaken = { file, uri ->
                    screenState = ScreenState.Loading
                    val handler = android.os.Handler(android.os.Looper.getMainLooper())
                    PlateRecognizerService.recognizePlate(
                        imageFile = file,
                        onSuccess = { response ->
                            handler.post {
                                screenState = ScreenState.Result(photoFile = file, photoUri = uri, response = response)
                            }
                        },
                        onError = { msg ->
                            handler.post { screenState = ScreenState.Error(msg) }
                        }
                    )
                }
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
            is ScreenState.Result -> ResultScreen(
                modifier = Modifier.padding(innerPadding),
                photoUri = state.photoUri,
                response = state.response,
                onTakeAnother = { screenState = ScreenState.Camera },
                onSaveLocally = {
                    coroutineScope.launch {
                        saveToDatabase(context, state)
                        Toast.makeText(context, "Saved to Local Database", Toast.LENGTH_LONG).show()
                    }
                },
                onExport = {
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    exportLauncher.launch("AIALPR_Export_$timestamp.zip")
                }
            )
            is ScreenState.Error -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Error: ${state.message}", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { screenState = ScreenState.Camera }) { Text("Back to camera") }
                }
            }
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
        
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            ZipOutputStream(outputStream).use { zipOut ->
                // Write CSV entry
                val csvContent = StringBuilder("ID,Timestamp,Plate,Region,Score,PhotoFile\n")
                allResults.forEach { res ->
                    val photoFileName = "photo_${res.id}.jpg"
                    csvContent.append("${res.id},${res.timestamp},${res.plate},${res.region},${res.score},$photoFileName\n")
                    
                    // Write Photo entry
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
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onPhotoTaken: (File, Uri) -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        val previewView = remember { PreviewView(context) }
        var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
        var cameraBound by remember { mutableStateOf(false) }

        AndroidView(
            factory = { previewView },
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
                val photoFile = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
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
    }
}

@Composable
private fun ResultScreen(
    modifier: Modifier,
    photoUri: Uri,
    response: PlateRecognizerResponse,
    onTakeAnother: () -> Unit,
    onSaveLocally: () -> Unit,
    onExport: () -> Unit
) {
    val context = LocalContext.current
    var histories by remember { mutableStateOf<Map<String, List<RecognitionResult>>>(emptyMap()) }
    val detectedPlates = response.results?.mapNotNull { it.plate } ?: emptyList()

    LaunchedEffect(detectedPlates) {
        if (detectedPlates.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(context)
                val map = mutableMapOf<String, List<RecognitionResult>>()
                detectedPlates.distinct().forEach { plate ->
                    map[plate] = db.recognitionDao().getHistoryForPlate(plate)
                }
                histories = map
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
                
                val history = histories[plateName] ?: emptyList()
                if (history.isNotEmpty()) {
                    Text("History for $plateName:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                    history.take(3).forEach { item ->
                        Text("  • ${item.timestamp.replace("T", " ")} (${item.region})", style = MaterialTheme.typography.bodySmall)
                    }
                    if (history.size > 3) {
                        Text("  ... and ${history.size - 3} more", style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onTakeAnother, modifier = Modifier.weight(1f)) { Text("Take another") }
                Button(onClick = onSaveLocally, modifier = Modifier.weight(1f)) { Text("Save Locally") }
            }
            Button(
                onClick = onExport,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Text("Export Database & Photos (ZIP)")
            }
        }
    }
}
