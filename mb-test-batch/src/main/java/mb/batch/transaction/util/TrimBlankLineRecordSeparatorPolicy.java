package mb.batch.transaction.util;


import org.springframework.batch.infrastructure.item.file.separator.SimpleRecordSeparatorPolicy;

public class TrimBlankLineRecordSeparatorPolicy extends SimpleRecordSeparatorPolicy {
    @Override
    public String postProcess(String record) {
        if (record == null) return null;
        String trimmed = record.trim();
        return trimmed.isEmpty() ? null : trimmed; // skip blank lines
    }
}
