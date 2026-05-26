package in.schoolapp.imports;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;

/**
 * CSV parsing helper. Header-driven (first line must be column names), trims values,
 * skips blank rows. Wraps Commons-CSV so callers don't depend on it directly — alternative
 * parsers (e.g. opencsv, Univocity) can be slotted in later by changing this file alone.
 *
 * <p>Usage:
 * <pre>{@code
 * CsvImporter.parse(inputStream, (rowNumber, record) -> {
 *     String firstName = record.get("first_name");
 *     ...
 * });
 * }</pre>
 */
public final class CsvImporter {

    private CsvImporter() {}

    /**
     * @param consumer called for each non-blank data row with the 1-based row number
     *                 (matching what the user sees in Excel — row 2 is first data row)
     */
    public static void parse(InputStream input, BiConsumer<Integer, CSVRecord> consumer) {
        CSVFormat fmt = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .setIgnoreSurroundingSpaces(true)
            .build();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
             CSVParser parser = new CSVParser(reader, fmt)) {
            for (CSVRecord record : parser) {
                // record.getRecordNumber() is 1-based and counts data rows only; +1 for header.
                int displayRow = (int) record.getRecordNumber() + 1;
                consumer.accept(displayRow, record);
            }
        } catch (IOException e) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Could not read CSV: " + e.getMessage(), e);
        }
    }

    /** Safely fetch a column that might not exist. Returns null if header missing. */
    public static String optional(CSVRecord rec, String header) {
        try {
            return rec.isMapped(header) ? trimToNull(rec.get(header)) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Required column — throws a clear error if missing or blank. */
    public static String required(CSVRecord rec, String header) {
        String v = optional(rec, header);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Column \"" + header + "\" is required");
        }
        return v;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
