package kz.tcloud.dcinv.scanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlin.math.abs

/**
 * CameraX analyzer that decodes QR codes via ML Kit and reports the first one
 * whose center falls inside the central scan frame, so codes elsewhere on screen
 * are ignored (matches the on-screen reticle). Prefix validation (`DCQR-`) is the
 * caller's responsibility.
 *
 * @param centerFraction side of the accepted centered square as a fraction of the
 *   upright image width. ~0.7 roughly matches the 250.dp reticle.
 */
class QrCodeAnalyzer(
    private val centerFraction: Float = 0.7f,
    private val onQrDetected: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build(),
    )

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val rotation = imageProxy.imageInfo.rotationDegrees
        // Upright (rotation-applied) image dimensions — the space ML Kit reports
        // bounding boxes in.
        val uprightWidth: Int
        val uprightHeight: Int
        if (rotation == 90 || rotation == 270) {
            uprightWidth = imageProxy.height
            uprightHeight = imageProxy.width
        } else {
            uprightWidth = imageProxy.width
            uprightHeight = imageProxy.height
        }
        val halfSide = centerFraction * uprightWidth / 2f
        val centerX = uprightWidth / 2f
        val centerY = uprightHeight / 2f

        val image = InputImage.fromMediaImage(mediaImage, rotation)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val inFrame = barcodes.firstOrNull { barcode ->
                    val box = barcode.boundingBox ?: return@firstOrNull false
                    abs(box.exactCenterX() - centerX) <= halfSide &&
                        abs(box.exactCenterY() - centerY) <= halfSide
                }
                inFrame?.rawValue?.let(onQrDetected)
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}
