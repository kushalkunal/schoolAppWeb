package in.schoolapp.ocrservice.ocr;

import in.schoolapp.ocrservice.ocr.dto.OcrResult;

public interface OcrProvider {
    OcrResult extract(byte[] imageBytes);
}
