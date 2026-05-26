package in.schoolapp.ocrservice.llm;

import in.schoolapp.ocrservice.JobType;
import in.schoolapp.ocrservice.dto.ExtractedRecord;

import java.util.List;

public interface LlmExtractionProvider {
    List<ExtractedRecord> extract(String ocrText, JobType type);
}
