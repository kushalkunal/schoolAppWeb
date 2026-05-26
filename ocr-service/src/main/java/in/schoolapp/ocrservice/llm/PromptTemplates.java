package in.schoolapp.ocrservice.llm;

import in.schoolapp.ocrservice.JobType;

/**
 * Per-type prompt templates. Verbatim from the backend's previous in-process version —
 * extracted only so all four LLM providers ship the same instructions.
 */
public final class PromptTemplates {

    private PromptTemplates() {}

    public static final String SYSTEM = """
        You are a precise OCR-to-structured-data extractor for school records. You receive raw
        OCR text (often from handwritten Indian school registers, possibly with errors) and
        return a strict JSON object: {"records": [...]}. Never include explanations or markdown
        — JSON only. If the OCR text is unreadable or has no records, return {"records": []}.
        Every record MUST include a numeric "confidence" between 0.0 and 1.0 reflecting your
        certainty in the extraction.
        """;

    public static String userPromptFor(JobType type, String ocrText) {
        return switch (type) {
            case FEE_RECEIPT     -> feeReceiptPrompt(ocrText);
            case ATTENDANCE      -> attendancePrompt(ocrText);
            case MARKS           -> marksPrompt(ocrText);
            case ADMISSION_FORM  -> admissionFormPrompt(ocrText);
        };
    }

    private static String feeReceiptPrompt(String ocrText) {
        return """
            Task: extract fee-payment records from this receipt-book OCR.
            Each record has these fields (any may be null if not present):
              studentName    string  — the student's name as written
              classHint      string  — class/section like "7B" or "Class 5", null if absent
              amountPaise    integer — amount in PAISE (multiply rupee value by 100)
              receiptNumber  string  — receipt # if printed
              date           string  — YYYY-MM-DD if a date is shown
              description    string  — e.g. "Term 2 Fees", null if generic
              confidence     number  — 0.0 to 1.0
            Multiple records per page are normal — return them all.

            OCR TEXT:
            ---
            %s
            ---
            Return JSON: {"records": [...]}
            """.formatted(ocrText);
    }

    private static String attendancePrompt(String ocrText) {
        return """
            Task: extract per-student attendance entries from this register page OCR.
            Each record (per student per date):
              studentName  string
              classHint    string  — class/section
              date         string  — YYYY-MM-DD
              description  string  — status: "PRESENT", "ABSENT", "LATE", "LEAVE", "HALF_DAY"
              confidence   number  — 0.0 to 1.0
            Group by student-day; one record per (student, date).

            OCR TEXT:
            ---
            %s
            ---
            Return JSON: {"records": [...]}
            """.formatted(ocrText);
    }

    private static String marksPrompt(String ocrText) {
        return """
            Task: extract marks from this exam mark-sheet OCR.
            Each record (per student):
              studentName    string
              classHint      string
              description    string  — exam name if shown
              rawFields      object  — map of {subjectName: marksObtained} for every subject
              confidence     number
            Use rawFields to capture per-subject marks: {"Math": 78, "English": 85, ...}.

            OCR TEXT:
            ---
            %s
            ---
            Return JSON: {"records": [...]}
            """.formatted(ocrText);
    }

    private static String admissionFormPrompt(String ocrText) {
        return """
            Task: extract one student-admission record from this handwritten form OCR.
            Fields:
              studentName    string  — child's full name
              classHint      string  — class being admitted to
              date           string  — date of birth, YYYY-MM-DD
              rawFields      object  — {parentName, parentPhone, address, gender, bloodGroup}
              confidence     number
            Usually one record per form; return as a single-element array.

            OCR TEXT:
            ---
            %s
            ---
            Return JSON: {"records": [...]}
            """.formatted(ocrText);
    }
}
