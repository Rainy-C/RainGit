package me.rain.git

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import me.rain.git.github.Artifact
import me.rain.git.ui.RainApp
import me.rain.git.ui.RainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val model: RainViewModel = viewModel()
            var pendingArtifact by remember { mutableStateOf<Artifact?>(null) }
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                uri?.let(model::chooseRoot)
            }
            val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
                val artifact = pendingArtifact
                if (uri != null && artifact != null) model.saveArtifact(artifact, uri)
                pendingArtifact = null
            }
            val createApk = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")) { uri ->
                val artifact = pendingArtifact
                if (uri != null && artifact != null) model.saveArtifact(artifact, uri, apkOnly = true)
                pendingArtifact = null
            }
            val grantStorage = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
            RainApp(
                model = model,
                onChooseDirectory = { picker.launch(null) },
                onGrantStorage = {
                    if (!Environment.isExternalStorageManager()) {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:$packageName"))
                        grantStorage.launch(intent)
                    } else picker.launch(null)
                },
                onExportArtifact = { artifact, apkOnly ->
                    pendingArtifact = artifact
                    if (apkOnly) createApk.launch("${artifact.name}.apk")
                    else createFile.launch("${artifact.name}.zip")
                },
                onOpenUrl = { url -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            )
        }
    }
}
