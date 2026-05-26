package in.schoolapp.ocrservice.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import in.schoolapp.ocrservice.dto.ExtractedRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmJsonParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void parsesStandardEnvelope() {
        String json = """
            {
              "records": [
                {"studentName": "Rohan Sharma", "classHint": "7B", "amountPaise": 450000,
                 "receiptNumber": "1845", "date": "2025-04-12", "description": "Term 2 Fees",
                 "confidence": 0.94}
              ]
            }
            """;
        List<ExtractedRecord> out = LlmJsonParser.parse(json, objectMapper);
        assertThat(out).hasSize(1);
        ExtractedRecord r = out.get(0);
        assertThat(r.studentName()).isEqualTo("Rohan Sharma");
        assertThat(r.classHint()).isEqualTo("7B");
        assertThat(r.amountPaise()).isEqualTo(450000L);
        assertThat(r.receiptNumber()).isEqualTo("1845");
        assertThat(r.date()).isEqualTo(LocalDate.of(2025, 4, 12));
        assertThat(r.confidence()).isEqualTo(0.94);
    }

    @Test
    void stripsMarkdownFences() {
        String json = """
            ```json
            {"records": [{"studentName": "X", "confidence": 0.5}]}
            ```
            """;
        List<ExtractedRecord> out = LlmJsonParser.parse(json, objectMapper);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).studentName()).isEqualTo("X");
    }

    @Test
    void emptyArrayReturnsEmptyList() {
        assertThat(LlmJsonParser.parse("{\"records\": []}", objectMapper)).isEmpty();
    }

    @Test
    void missingRecordsKeyReturnsEmptyList() {
        assertThat(LlmJsonParser.parse("{}", objectMapper)).isEmpty();
    }

    @Test
    void malformedJsonReturnsEmptyListNoThrow() {
        assertThat(LlmJsonParser.parse("not json at all", objectMapper)).isEmpty();
    }

    @Test
    void invalidDateBecomesNull() {
        String json = """
            {"records": [{"studentName": "X", "date": "12/4/2025", "confidence": 0.5}]}
            """;
        assertThat(LlmJsonParser.parse(json, objectMapper).get(0).date()).isNull();
    }

    @Test
    void rawFieldsPreserved() {
        String json = """
            {"records": [{
              "studentName": "X",
              "rawFields": {"Math": 78, "English": 85}
            }]}
            """;
        assertThat(LlmJsonParser.parse(json, objectMapper).get(0).rawFields())
            .containsKeys("Math", "English");
    }
}
