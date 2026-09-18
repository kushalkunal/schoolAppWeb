package in.schoolapp.imports;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Outcome of a bulk import. The shape is identical for dry-run and commit; the only
 * difference is whether the {@code accepted} rows were actually persisted.
 *
 * <p>The contract for callers:
 * <ul>
 *   <li>{@code accepted}: rows that passed validation. In commit mode, also persisted.</li>
 *   <li>{@code errors}: per-row reason strings, with 1-based row numbers matching what
 *       the CSV upload UI shows the user.</li>
 *   <li>{@code skippedDuplicates}: rows that matched an existing record (e.g. same
 *       admission number) — treated as "already done" rather than errors.</li>
 * </ul>
 */
public class ImportResult<T> {

    private final boolean dryRun;
    private final List<T> accepted = new ArrayList<>();
    private final List<RowError> errors = new ArrayList<>();
    private final List<Integer> skippedDuplicates = new ArrayList<>();
    private int totalRowsRead;

    public ImportResult(boolean dryRun) { this.dryRun = dryRun; }

    public void recordRowRead()                          { totalRowsRead++; }
    public void recordAccepted(T item)                    { accepted.add(item); }
    public void recordError(int rowNumber, String message) { errors.add(new RowError(rowNumber, message)); }
    public void recordDuplicate(int rowNumber)             { skippedDuplicates.add(rowNumber); }

    @JsonProperty("dryRun")            public boolean dryRun()              { return dryRun; }
    @JsonProperty("totalRowsRead")     public int totalRowsRead()           { return totalRowsRead; }
    @JsonProperty("accepted")          public List<T> accepted()            { return List.copyOf(accepted); }
    @JsonProperty("errors")            public List<RowError> errors()       { return List.copyOf(errors); }
    @JsonProperty("skippedDuplicates") public List<Integer> skippedDuplicates() { return List.copyOf(skippedDuplicates); }
    @JsonProperty("acceptedCount")     public int acceptedCount()           { return accepted.size(); }
    @JsonProperty("errorCount")        public int errorCount()              { return errors.size(); }
    @JsonProperty("duplicateCount")    public int duplicateCount()          { return skippedDuplicates.size(); }

    /**
     * @param row 1-based row number (the header line is row 1 — first data row is row 2)
     */
    public record RowError(int row, String message) {}
}
