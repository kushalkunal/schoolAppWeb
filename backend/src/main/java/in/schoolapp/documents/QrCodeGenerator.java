package in.schoolapp.documents;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;

/**
 * Turns an arbitrary string (a verification URL, in our case) into a QR code.
 *
 * <p>Two outputs, because we feed two different PDF pipelines:
 * <ul>
 *   <li>{@link #pngBytes} — raw PNG, for the OpenPDF/iText report-card path which embeds
 *       {@code Image.getInstance(byte[])}.</li>
 *   <li>{@link #pngDataUri} — {@code data:image/png;base64,…}, for the Thymeleaf +
 *       openhtmltopdf path where the template just sets {@code <img th:src>}.</li>
 * </ul>
 *
 * <p>Error-correction is set to {@code M} (~15%) so the code still scans after the slight
 * blurring that PDF rasterisation introduces.
 */
@Component
public class QrCodeGenerator {

    public byte[] pngBytes(String text, int size) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = Map.of(
                EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN, 1,
                EncodeHintType.CHARACTER_SET, "UTF-8");
            BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size, hints);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "QR generation failed: " + e.getMessage(), e);
        }
    }

    public String pngDataUri(String text, int size) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(pngBytes(text, size));
    }
}
