package in.schoolapp.imports;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvImporterTest {

    @Test
    void parsesHeaderAndRows() {
        String csv = """
            first_name,last_name,class,section
            Asha,Patil,5,A
            Rohan,Sharma,5,B
            """;

        List<String> rows = new ArrayList<>();
        CsvImporter.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), (n, rec) -> {
            rows.add(rec.get("first_name") + "|" + rec.get("class") + rec.get("section"));
        });

        assertThat(rows).containsExactly("Asha|5A", "Rohan|5B");
    }

    @Test
    void skipsBlankLines() {
        String csv = """
            first_name,phone
            A,9876543210

            B,9876543211
            """;

        List<String> rows = new ArrayList<>();
        CsvImporter.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
            (n, rec) -> rows.add(rec.get("first_name")));

        assertThat(rows).containsExactly("A", "B");
    }

    @Test
    void optionalReturnsNullForMissingColumn() {
        String csv = "first_name\nAsha\n";

        CsvImporter.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), (n, rec) -> {
            assertThat(CsvImporter.optional(rec, "missing_column")).isNull();
            assertThat(CsvImporter.optional(rec, "first_name")).isEqualTo("Asha");
        });
    }

    @Test
    void rowNumbersStartAtTwoForFirstDataRow() {
        String csv = """
            first_name
            Asha
            Rohan
            """;

        List<Integer> seen = new ArrayList<>();
        CsvImporter.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
            (n, rec) -> seen.add(n));

        // Header is row 1 in the user's mental model; first data row is row 2.
        assertThat(seen).containsExactly(2, 3);
    }
}
