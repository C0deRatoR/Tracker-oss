package com.yash.tracker.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.yash.tracker.ui.theme.EyebrowStyle
import com.yash.tracker.ui.theme.Motion
import java.io.File
import java.util.concurrent.Executor

/**
 * In-app capture, fixed to the back camera.
 *
 * TRD §1 specified CameraX and this build originally used the system camera instead, to avoid
 * a permission and a preview screen. That turned out to cost the thing that matters: an
 * `ACTION_IMAGE_CAPTURE` intent cannot choose a lens, and Samsung's camera ignores every extra
 * that supposedly asks — so it opened on whichever lens took the last selfie. For an app whose
 * whole interaction is photographing a plate or a label, a front-facing camera is not a small
 * annoyance.
 *
 * This is the one screen that does not use the app's light palette: a viewfinder has to be the
 * photograph and nothing else, so the chrome is white-on-black and kept to the edges.
 */
@Composable
fun CameraCapture(
    target: File,
    onCaptured: (Uri) -> Unit,
    onCancel: () -> Unit,
    title: String = "Photograph the plate",
    hint: String = "Fill the frame and hold still — tap to focus.",
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var failure by remember { mutableStateOf<String?>(null) }
    var capturing by remember { mutableStateOf(false) }

    val askPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { allowed ->
        granted = allowed
        if (!allowed) failure = "Camera access is off. You can turn it on in Android settings."
    }

    LaunchedEffect(Unit) {
        if (!granted) askPermission.launch(Manifest.permission.CAMERA)
    }

    // LifecycleCameraController rather than binding a Preview and ImageCapture by hand: it
    // brings continuous autofocus and tap-to-focus with it. Without them the first real attempt
    // at a jar of peanut butter came back too blurry to read, and the model returned nothing.
    val controller = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (granted) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).also { view ->
                        view.scaleType = PreviewView.ScaleType.FILL_CENTER
                        // TextureView rather than the default SurfaceView: the hardware path
                        // rendered a blue-cast preview on this phone.
                        view.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        view.controller = controller
                    }
                },
            )

            LaunchedEffect(Unit) {
                runCatching { controller.bindToLifecycle(lifecycleOwner) }
                    .onFailure { failure = "The camera could not be opened." }
            }

            DisposableEffect(Unit) {
                onDispose { controller.unbind() }
            }
        }

        // Top chrome: what you are shooting, and the way out.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(
                icon = Icons.Outlined.Close,
                contentDescription = "Cancel",
                onClick = onCancel,
                size = 44.dp,
                background = Color.White.copy(alpha = 0.14f),
                tint = Color.White,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "VIEWFINDER",
                    style = EyebrowStyle,
                    color = Color.White.copy(alpha = 0.6f),
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(44.dp))
        }

        failure?.let { message ->
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Bottom chrome: the shutter, with the hint above it rather than floating over the frame.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (granted && failure == null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 40.dp),
                )
                Spacer(Modifier.height(20.dp))
                Shutter(enabled = !capturing) {
                    capturing = true
                    controller.takeInto(target, context.mainExecutor()) { saved ->
                        capturing = false
                        if (saved) onCaptured(target.toUri()) else failure = "That shot didn't save."
                    }
                }
            } else {
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onCancel)
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                )
            }
        }
    }
}

/**
 * The ring-and-disc shutter every phone camera has trained people to expect. Built here rather
 * than from the kit because the kit's buttons carry the light palette with them.
 */
@Composable
private fun Shutter(enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.88f else 1f,
        animationSpec = Motion.press,
        label = "shutter",
    )

    Box(
        modifier = Modifier
            .size(78.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .border(3.dp, Color.White.copy(alpha = 0.9f), CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = "Take the photo",
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (enabled) 1f else 0.45f)),
        )
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

private fun Context.mainExecutor(): Executor = ContextCompat.getMainExecutor(this)

private fun LifecycleCameraController.takeInto(
    file: File,
    executor: Executor,
    onDone: (Boolean) -> Unit,
) {
    takePicture(
        ImageCapture.OutputFileOptions.Builder(file).build(),
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) = onDone(true)
            override fun onError(exception: ImageCaptureException) = onDone(false)
        },
    )
}
