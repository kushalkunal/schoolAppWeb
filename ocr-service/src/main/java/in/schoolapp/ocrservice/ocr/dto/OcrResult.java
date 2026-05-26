package in.schoolapp.ocrservice.ocr.dto;

public record OcrResult(String text, double confidence) {
    public static OcrResult of(String text) {
        return new OcrResult(text, 1.0);
    }
}
