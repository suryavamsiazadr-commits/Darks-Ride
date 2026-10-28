package com.darksoul.darksride.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.darksoul.darksride.docs.Backup
import com.darksoul.darksride.docs.DocFile
import com.darksoul.darksride.docs.DocItem
import com.darksoul.darksride.docs.DocType
import com.darksoul.darksride.docs.DocVault
import com.darksoul.darksride.garage.Days
import com.darksoul.darksride.ui.theme.Stop
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Locked again whenever the app is left (unless it's just the scanner or file picker). */
private object DocLock {
    var unlocked by mutableStateOf(false)
    var picking = false
}

internal fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

// ---------- Screen ----------

@Composable
fun DocumentsScreen(insets: WindowInsets = WindowInsets.safeDrawing) {
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (!DocLock.picking) {
            DocLock.unlocked = false
            DocVault.forget()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (!DocLock.picking) {
                DocLock.unlocked = false
                DocVault.forget()
            }
        }
    }
    if (DocLock.unlocked) VaultContent(insets) else LockedScreen(insets)
}

@Composable
private fun LockedScreen(insets: WindowInsets) {
    val context = LocalContext.current
    val canLock = remember {
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }
    RideColumn(insets = insets) {
        Title("Documents")
        Card {
            Text("Locked", fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface)
            Body("Your licence, RC, insurance and PUC, encrypted on this phone only. " +
                "Nothing is uploaded.", dim = true)
            if (!canLock) {
                Body("This phone has no screen lock, so the folder can't be protected. " +
                    "Set a PIN or fingerprint in Settings → Security for real protection.", dim = true)
            }
        }
        BigButton(if (canLock) "Unlock with fingerprint or PIN" else "Open", onClick = unlock@{
            if (!canLock) {
                DocLock.unlocked = true
                return@unlock
            }
            val activity = context.findActivity() as? FragmentActivity ?: return@unlock
            val prompt = BiometricPrompt(
                activity, ContextCompat.getMainExecutor(context),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        DocLock.unlocked = true
                    }
                }
            )
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock documents")
                    .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                    .build()
            )
        })
        OfficialAppsCard()
    }
}

@Composable
private fun VaultContent(insets: WindowInsets) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { DocVault.load(context) } }
    val items by DocVault.items.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var showing by remember { mutableStateOf<DocItem?>(null) }

    val open = showing
    if (open != null) {
        // Keep the shown document in sync (e.g. after adding a page).
        val current = items.firstOrNull { it.id == open.id } ?: open
        DocViewer(current, insets, onClose = { showing = null })
        return
    }

    RideColumn(insets = insets) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Documents", fontSize = 28.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            TextButton(onClick = { DocLock.unlocked = false; DocVault.forget() }) { Text("Lock") }
        }
        BigButton("+ Add document", onClick = { adding = true })
        if (items.isEmpty()) {
            Card { Body("Scan your licence, RC, insurance and PUC with the camera, or import a PDF or photo.", dim = true) }
        }
        items.forEach { d -> DocRow(d, onClick = { showing = d }) }
        OfficialAppsCard()
    }

    if (adding) AddDocDialog(onDone = { adding = false })
}

@Composable
private fun DocRow(d: DocItem, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(d.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(
                    listOfNotNull(d.type.label.takeIf { it != d.title }, d.number.ifEmpty { null },
                        "${d.files.size} file${if (d.files.size == 1) "" else "s"}").joinToString(" · "),
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            d.expiry?.let { exp ->
                val left = Days.daysFromToday(exp)
                Text(
                    if (left < 0) "Expired" else "Till ${Days.format(exp)}",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = if (left < 30) Stop else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ---------- Adding (scan or import) ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddDocDialog(onDone: () -> Unit) {
    val context = LocalContext.current
    var type by remember { mutableStateOf(DocType.LICENCE) }
    var title by remember { mutableStateOf(DocType.LICENCE.label) }
    var number by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf<Long?>(null) }

    fun save(files: List<DocFile>) {
        DocVault.save(context, DocVault.newItem(type, title.trim().ifEmpty { type.label }, number.trim(), expiry, files))
        onDone()
    }
    val pick = rememberFilePickers(onFile = { file -> save(listOf(file)) })

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Add document") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DocType.entries.forEach { t ->
                        FilterChip(selected = t == type, onClick = {
                            if (title == type.label) title = t.label
                            type = t
                        }, label = { Text(t.label) })
                    }
                }
                TextInput("Name", title, { title = it })
                TextInput("Number (optional)", number, { number = it }, KeyboardType.Ascii)
                DateInput("Valid till", expiry, { expiry = it }, clearable = true)
                BigButton("Scan with camera", onClick = { pick.scan() })
                BigButton("Import PDF or photo", secondary = true, onClick = { pick.import() })
            }
        },
        confirmButton = { TextButton(onClick = { save(emptyList()) }) { Text("Save without file") } },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } }
    )
}

/** Google's document scanner (auto-crop, PDF) and the system file picker. */
private class FilePickers(val scan: () -> Unit, val import: () -> Unit)

@Composable
private fun rememberFilePickers(onFile: (DocFile) -> Unit): FilePickers {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestOnFile by rememberUpdatedState(onFile)

    fun store(uri: Uri, mime: String?) {
        scope.launch {
            val file = withContext(Dispatchers.IO) { DocVault.importFile(context, uri, mime) }
            DocLock.picking = false
            if (file != null) latestOnFile(file)
            else Toast.makeText(context, "Couldn't read that file", Toast.LENGTH_LONG).show()
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val pdf = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pdf
        if (result.resultCode == Activity.RESULT_OK && pdf != null) store(pdf.uri, "application/pdf")
        else DocLock.picking = false
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) store(uri, null) else DocLock.picking = false
    }

    return remember {
        FilePickers(
            scan = scan@{
                val activity = context.findActivity() ?: return@scan
                val options = GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(true)
                    .setPageLimit(10)
                    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .build()
                GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                    .addOnSuccessListener { sender ->
                        DocLock.picking = true
                        scanLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Scanner not available: use Import instead", Toast.LENGTH_LONG).show()
                    }
            },
            import = {
                DocLock.picking = true
                importLauncher.launch(arrayOf("application/pdf", "image/*"))
            }
        )
    }
}

// ---------- Viewing ("show" mode) ----------

@Composable
private fun DocViewer(d: DocItem, insets: WindowInsets, onClose: () -> Unit) {
    val context = LocalContext.current
    var pages by remember(d.files) { mutableStateOf<List<ImageBitmap>?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val pick = rememberFilePickers(onFile = { file -> DocVault.save(context, d.copy(files = d.files + file)) })

    BackHandler(onBack = onClose)
    // Full brightness and screen on while showing a document (e.g. at a check).
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        val before = window?.attributes?.screenBrightness
        window?.let {
            it.attributes = it.attributes.apply { screenBrightness = 1f }
            it.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.let {
                it.attributes = it.attributes.apply {
                    screenBrightness = before ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
                it.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    LaunchedEffect(d.files) {
        pages = withContext(Dispatchers.IO) { d.files.flatMap { renderFile(context, it) } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(insets)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(d.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    listOfNotNull(d.number.ifEmpty { null }, d.expiry?.let { "Valid till ${Days.format(it)}" })
                        .joinToString(" · "),
                    fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f)
                )
            }
            TextButton(onClick = onClose) { Text("Close") }
        }
        Row(modifier = Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { pick.scan() }) { Text("+ Scan page") }
            TextButton(onClick = { pick.import() }) { Text("+ Import") }
            TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = Stop) }
        }
        val list = pages
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("No file yet. Use + Scan page or + Import.", color = Color.White.copy(alpha = 0.7f))
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(list) { page ->
                    Image(page, contentDescription = null, contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDelete("Delete ${d.title}?", onYes = {
            DocVault.delete(context, d)
            confirmDelete = false
            onClose()
        }, onNo = { confirmDelete = false })
    }
}

/** Decrypts a file and turns it into page images (PDF pages or one photo). */
private fun renderFile(context: Context, file: DocFile): List<ImageBitmap> = runCatching {
    val bytes = DocVault.read(context, file)
    if (file.mime == "application/pdf") {
        val tmp = File.createTempFile("doc", ".pdf", context.cacheDir)
        try {
            tmp.writeBytes(bytes)
            ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { pdf ->
                    (0 until minOf(pdf.pageCount, 20)).map { i ->
                        pdf.openPage(i).use { page ->
                            val width = 1400
                            val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(AndroidColor.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp.asImageBitmap()
                        }
                    }
                }
            }
        } finally {
            tmp.delete() // never leave a decrypted copy behind
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1400) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample })
        listOfNotNull(bmp?.asImageBitmap())
    }
}.getOrDefault(emptyList())

// ---------- Official apps ----------

@Composable
private fun OfficialAppsCard() {
    val context = LocalContext.current
    CollapsibleCard(
        title = "Official copies",
        stateKey = "official_docs",
        defaultOpen = false,
        summary = if (Build.MANUFACTURER.equals("samsung", ignoreCase = true))
            "Samsung Pass · DigiLocker · mParivahan" else "DigiLocker · mParivahan"
    ) {
        Body("Traffic police accept digital licences and RCs only from DigiLocker or mParivahan, " +
            "not photos. Keep the originals with you too.", dim = true)
        if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
            // Samsung Pass keeps DigiLocker documents on Galaxy phones (newer phones: Samsung Wallet).
            BigButton("Open Samsung Pass", onClick = {
                openFirst(context, "com.samsung.android.samsungpass", "com.samsung.android.spay")
            })
        }
        BigButton("Open DigiLocker", secondary = true, onClick = { openApp(context, "com.digilocker.android") })
        BigButton("Open mParivahan", secondary = true, onClick = { openApp(context, "com.nic.mparivahan") })
    }
}

/** Opens the first of these apps that's installed. */
private fun openFirst(context: Context, vararg packages: String) {
    val launch = packages.firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }
    if (launch != null) context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    else Toast.makeText(context, "Samsung Pass isn't installed on this phone", Toast.LENGTH_LONG).show()
}

private fun openApp(context: Context, pkg: String) {
    val launch = context.packageManager.getLaunchIntentForPackage(pkg)
        ?: Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$pkg".toUri())
    context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

// ---------- Backup (Settings, main phone) ----------

@Composable
fun BackupCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var asking by remember { mutableStateOf<String?>(null) } // "export" or "import"
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var pendingPassword by remember { mutableStateOf<CharArray?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val pw = pendingPassword
        pendingPassword = null
        if (uri == null || pw == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { Backup.export(context, pw, it) }
                }.isSuccess
            }
            pw.fill(' ')
            Toast.makeText(context, if (ok) "Backup saved" else "Backup failed", Toast.LENGTH_LONG).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val pw = pendingPassword
        pendingPassword = null
        if (uri == null || pw == null) return@rememberLauncherForActivityResult
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { Backup.import(context, pw, it) }
                }.exceptionOrNull()
            }
            pw.fill(' ')
            Toast.makeText(
                context,
                if (error == null) "Backup restored" else "Couldn't restore: wrong password or not a backup",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    CollapsibleCard(
        title = "Backup",
        stateKey = "backup",
        defaultOpen = false,
        summary = "Garage and Documents in one password-locked file"
    ) {
        Body("Everything lives only on this phone. Save a backup somewhere safe (e.g. your own " +
            "Google Drive) to restore it on a new phone. Without the password it can't be opened.", dim = true)
        BigButton("Save backup", secondary = true, onClick = { password = ""; confirm = ""; asking = "export" })
        BigButton("Restore backup", secondary = true, onClick = { password = ""; confirm = ""; asking = "import" })
    }

    asking?.let { mode ->
        val exporting = mode == "export"
        val valid = password.length >= 6 && (!exporting || password == confirm)
        AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text(if (exporting) "Backup password" else "Restore backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextInput("Password (6+ characters)", password, { password = it }, KeyboardType.Password)
                    if (exporting) {
                        TextInput("Same password again", confirm, { confirm = it }, KeyboardType.Password)
                        Body("Write it down: without it the backup can't be opened.", dim = true)
                    } else {
                        Body("This replaces the Garage and Documents on this phone.", dim = true)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    pendingPassword = password.toCharArray()
                    password = ""
                    confirm = ""
                    asking = null
                    if (exporting) exportLauncher.launch("DarksRide-backup-${Days.format(Days.today()).replace(' ', '-')}.drb")
                    else importLauncher.launch(arrayOf("*/*"))
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { asking = null }) { Text("Cancel") } }
        )
    }
}
