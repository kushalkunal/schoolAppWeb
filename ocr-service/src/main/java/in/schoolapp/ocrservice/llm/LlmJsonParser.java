package in.schoolapp.ocrservice.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.schoolapp.ocrservice.dto.ExtractedRecord;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Tolerant LLM-JSON → {@link ExtractedRecord} parser. Strips markdown fences some providers
 * emit despite being told not to. Returns {@code List.of()} on malformed JSON rather than
 * throwing — one bad row shouldn't crash the pipeline.
 */
@Slf4j
public final class LlmJsonParser {

    private LlmJsonParser() {}

    public static List<ExtractedRecord> parse(String llmJson, ObjectMapper objectMapper) {
        if (llmJson == null || llmJson.isBlank()) return List.of();
        String cleaned = stripMarkdownFences(llmJson.trim());
        try {
            JsonNode root = objectMapper.readTree(cleaned);
            JsonNode records = root.path("records");
            if (records.isMissingNode() || !records.isArray()) {
                log.warn("LLM response missing 'records' array — returning empty");
                return List.of();
            }
            List<ExtractedRecord> out = new ArrayList<>(records.size());
            for (JsonNode r : records) {
                out.add(toRecord(r));
            }
            return out;
        } catch (Exception e) {
            log.error("LLM JSON parse failed (preview={}): {}",
                cleaned.substring(0, Math.min(200, cleaned.length())), e.getMessage());
            return List.of();
        }
    }

    private static ExtractedRecord toRecord(JsonNode r) {
        return new ExtractedRecord(
            text(r, "studentName"),
            text(r, "classHint"),
            longOrNull(r, "amountPaise"),
            text(r, "receiptNumber"),
            dateOrNull(text(r, "date")),
            text(r, "description"),
            doubleOrNull(r, "confidence"),
            mapOf(r.get("rawFields"))
        );
    }

    private static String stripMarkdownFences(String s) {
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline > 0) s = s.substring(firstNewline + 1);
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
        }
        return s.trim();
    }

    private static String text(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return f == null || f.isNull() ? null : f.asText();
    }

    private static Long longOrNull(JsonNode node, String field) {
        JsonNode f = node.get(field);
        if (f == null || f.isNull()) return null;
        if (f.isNumber()) return f.asLong();
        try { return Long.parseLong(f.asText().trim()); } catch (NumberFormatException e) { return null; }
    }

    private static Double doubleOrNull(JsonNode node, String field) {
        JsonNode f = node.get(field);
        if (f == null || f.isNull()) return null;
        if (f.isNumber()) return f.asDouble();
        try { return Double.parseDouble(f.asText().trim()); } catch (NumberFormatException e) { return null; }
    }

    private static LocalDate dateOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s.trim()); } catch (DateTimeParseException e) { return null; }
    }

    private static Map<String, Object> mapOf(JsonNode node) {
        if (node == null || !node.isObject()) return null;
        Map<String, Object> out = new HashMap<>();
        Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            String f = fieldNames.next();
            JsonNode v = node.get(f);
            if (v.isNumber()) out.put(f, v.numberValue());
            else if (v.isBoolean()) out.put(f, v.booleanValue());
            else out.put(f, v.asText());
        }
        return out.isEmpty() ? null : out;
    }
}
