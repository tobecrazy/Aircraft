package com.young.aircraft.gui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.SurfaceHolder
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.WriterException
import com.google.zxing.common.HybridBinarizer
import com.young.aircraft.R
import com.young.aircraft.databinding.ActivityQrCodeToolBinding
import androidx.core.graphics.toColorInt
import androidx.core.graphics.createBitmap

class QRCodeToolActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQrCodeToolBinding

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var isScanning = false
    private var isCameraOpening = false
    private var frameCounter = 0
    private val scanSurfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            if (isScanning) openCamera()
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            releaseCamera()
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startScanning() else Toast.makeText(
            this, R.string.qr_code_tool_camera_permission_denied, Toast.LENGTH_SHORT
        ).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        binding = ActivityQrCodeToolBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.surfaceCamera.holder.addCallback(scanSurfaceCallback)

        setupHeader()
        setupScanButton()
        setupGenerateButton()
        setupEditor()
        renderContentState()
    }

    private fun setupHeader() {
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun setupEditor() {
        binding.richEditor.setHint(getString(R.string.qr_code_tool_input_hint))
        binding.richEditor.setEditorBackground(R.drawable.qr_tool_preview_frame_bg)
        binding.richEditor.setEditorHeight(
            (192 * resources.displayMetrics.density).toInt()
        )
    }

    // ── Scan QR Code ───────────────────────────────────────

    private fun setupScanButton() {
        binding.btnScanQr.setOnClickListener {
            if (isScanning) {
                stopScanning()
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    startScanning()
                } else if (ActivityCompat.shouldShowRequestPermissionRationale(
                        this, Manifest.permission.CAMERA
                    )
                ) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.qr_code_tool_camera_rationale_title)
                        .setMessage(R.string.qr_code_tool_camera_rationale_message)
                        .setPositiveButton(R.string.qr_code_tool_camera_rationale_ok) { _, _ ->
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                        .setNegativeButton(R.string.history_cancel, null)
                        .show()
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        }
    }

    private fun startScanning() {
        isScanning = true
        frameCounter = 0
        renderScanningState()
        startBackgroundThread()

        // If surface is already available, open camera immediately
        if (binding.surfaceCamera.holder.surface.isValid) {
            openCamera()
        }
    }

    private fun stopScanning() {
        isScanning = false
        releaseCamera()
        stopBackgroundThread()
        renderContentState()
    }

    @Suppress("MissingPermission")
    private fun openCamera() {
        if (isCameraOpening || cameraDevice != null) return
        isCameraOpening = true
        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        val cameraId = findBackCamera(cameraManager)
        if (cameraId == null) {
            isCameraOpening = false
            runOnUiThread {
                Toast.makeText(
                    this,
                    R.string.qr_code_tool_camera_error,
                    Toast.LENGTH_SHORT
                ).show()
                stopScanning()
            }
            return
        }

        imageReader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    frameCounter++
                    if (frameCounter % 4 != 0) return@setOnImageAvailableListener

                    val yPlane = image.planes[0]
                    val yBuffer = yPlane.buffer
                    val yBytes = ByteArray(yBuffer.remaining())
                    yBuffer.get(yBytes)

                    val source = PlanarYUVLuminanceSource(
                        yBytes, image.width, image.height,
                        0, 0, image.width, image.height, false
                    )
                    val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                    val hints = mapOf(
                        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                        DecodeHintType.CHARACTER_SET to "UTF-8"
                    )
                    val result = MultiFormatReader().decode(binaryBitmap, hints)
                    runOnUiThread { onScanResult(result.text) }
                } catch (_: NotFoundException) {
                    // No QR code in this frame
                } finally {
                    image.close()
                }
            }, backgroundHandler)
        }

        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    isCameraOpening = false
                    cameraDevice = camera
                    createPreviewSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    isCameraOpening = false
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    isCameraOpening = false
                    camera.close()
                    cameraDevice = null
                    runOnUiThread {
                        Toast.makeText(
                            this@QRCodeToolActivity,
                            R.string.qr_code_tool_camera_error,
                            Toast.LENGTH_SHORT
                        ).show()
                        stopScanning()
                    }
                }
            }, backgroundHandler)
        } catch (_: CameraAccessException) {
            isCameraOpening = false
            runOnUiThread {
                Toast.makeText(
                    this,
                    R.string.qr_code_tool_camera_error,
                    Toast.LENGTH_SHORT
                ).show()
                stopScanning()
            }
        }
    }

    private fun findBackCamera(cameraManager: CameraManager): String? {
        for (cameraId in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_BACK) return cameraId
        }
        return cameraManager.cameraIdList.firstOrNull()
    }

    private fun createPreviewSession() {
        val camera = cameraDevice ?: return
        val reader = imageReader ?: return
        val previewSurface = binding.surfaceCamera.holder.surface
        if (!previewSurface.isValid) return

        try {
            val outputConfigs = listOf(
                OutputConfiguration(previewSurface),
                OutputConfiguration(reader.surface)
            )
            val stateCallback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice == null) return
                    captureSession = session
                    try {
                        val requestBuilder =
                            camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                addTarget(previewSurface)
                                addTarget(reader.surface)
                                set(
                                    CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                                )
                            }
                        session.setRepeatingRequest(
                            requestBuilder.build(), null, backgroundHandler
                        )
                    } catch (_: CameraAccessException) {
                        runOnUiThread { stopScanning() }
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    runOnUiThread {
                        Toast.makeText(
                            this@QRCodeToolActivity,
                            R.string.qr_code_tool_camera_error,
                            Toast.LENGTH_SHORT
                        ).show()
                        stopScanning()
                    }
                }
            }
            val sessionConfig = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                outputConfigs,
                mainExecutor,
                stateCallback
            )
            camera.createCaptureSession(sessionConfig)
        } catch (_: CameraAccessException) {
            runOnUiThread { stopScanning() }
        }
    }

    private fun onScanResult(result: String) {
        stopScanning()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.qr_code_tool_scan_result)
            .setMessage(result)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.qr_code_tool_copy) { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("QR Result", result))
                Toast.makeText(this, R.string.qr_code_tool_copied, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun releaseCamera() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
        isCameraOpening = false
    }

    private fun startBackgroundThread() {
        if (backgroundThread != null) return
        backgroundThread = HandlerThread("QRScanThread").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        backgroundThread?.join()
        backgroundThread = null
        backgroundHandler = null
    }

    private fun renderScanningState() {
        binding.scrollContent.visibility = View.GONE
        binding.cameraContainer.visibility = View.VISIBLE
        binding.surfaceCamera.visibility = View.VISIBLE
        binding.tvScanStatus.visibility = View.VISIBLE
        binding.tvScanStatus.text = getString(R.string.qr_code_tool_scanning)
        updateScanButton()
    }

    private fun renderContentState() {
        val hasQrPreview = binding.ivQrCode.drawable != null
        binding.cameraContainer.visibility = View.GONE
        binding.surfaceCamera.visibility = View.GONE
        binding.tvScanStatus.visibility = View.GONE
        binding.scrollContent.visibility = View.VISIBLE
        binding.tvHeroStatus.text = getString(
            if (hasQrPreview) R.string.qr_code_tool_status_generated
            else R.string.qr_code_tool_status_ready
        )
        binding.tvPreviewTitle.text = getString(
            if (hasQrPreview) R.string.qr_code_tool_preview_generated_title
            else R.string.qr_code_tool_preview_idle_title
        )
        binding.tvPreviewHint.text = getString(
            if (hasQrPreview) R.string.qr_code_tool_preview_generated_hint
            else R.string.qr_code_tool_preview_idle_hint
        )
        binding.qrPreviewContainer.visibility = if (hasQrPreview) View.VISIBLE else View.GONE
        binding.tvQrPlaceholder.visibility = if (hasQrPreview) View.GONE else View.VISIBLE
        updateScanButton()
    }

    private fun updateScanButton() {
        binding.btnScanQr.text = getString(
            if (isScanning) R.string.qr_code_tool_stop_scan else R.string.qr_code_tool_scan_button
        )
        binding.btnScanQr.setBackgroundResource(
            if (isScanning) R.drawable.qr_tool_stop_action_bg
            else R.drawable.qr_tool_secondary_action_bg
        )
        binding.btnGenerateQr.visibility =  if (isScanning) View.GONE else View.VISIBLE
    }

    // ── Generate QR Code ───────────────────────────────────

    private fun setupGenerateButton() {
        binding.btnGenerateQr.setOnClickListener {
            val content = binding.richEditor.plainText.trim()
            if (content.isEmpty()) {
                Toast.makeText(this, R.string.qr_code_tool_no_content, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            generateQrCode(content)
        }
    }

    private fun generateQrCode(content: String) {
        try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 2
            )
            val bitMatrix = MultiFormatWriter().encode(
                content, BarcodeFormat.QR_CODE, 512, 512, hints
            )
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            val bgColor = "#0F1118".toColorInt()
            for (y in 0 until height) {
                for (x in 0 until width) {
                    pixels[y * width + x] = if (bitMatrix[x, y]) Color.WHITE else bgColor
                }
            }
            val bitmap = createBitmap(width, height)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

            binding.ivQrCode.setImageBitmap(bitmap)
            renderContentState()
        } catch (_: WriterException) {
            Toast.makeText(this, R.string.qr_code_tool_content_too_long, Toast.LENGTH_SHORT).show()
        }
    }

    // ── Lifecycle ──────────────────────────────────────────

    override fun onPause() {
        super.onPause()
        if (isScanning) stopScanning()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseCamera()
        stopBackgroundThread()
    }
}
