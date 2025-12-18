package mb.batch.transaction.util;

import org.springframework.batch.infrastructure.item.file.separator.RecordSeparatorPolicy;

public class TrimBlankLineRecordSeparatorPolicy implements RecordSeparatorPolicy {

    @Override
    public boolean isEndOfRecord(String line) {
        // Blank lines are considered end of record (skip them)
        if (line == null || line.trim().isEmpty()) {
            return true;
        }
        // Non-blank lines are not end of record yet
        return false;
    }

    @Override
    public String postProcess(String record) {
        if (record == null) return null;
        String trimmed = record.trim();
        // Return null for blank lines so they're completely skipped
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    public String preProcess(String record) {
        // No preprocessing needed
        return record;
    }
}
