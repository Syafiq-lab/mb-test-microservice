package mb.batch.transaction.util;

import lombok.extern.slf4j.Slf4j;
import mb.batch.transaction.dto.TransactionFileRow;
import mb.batch.transaction.dto.TransactionInsertRow;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TransactionImportSkipListener implements SkipListener<TransactionFileRow, TransactionInsertRow> {

	@Override
	public void onSkipInRead(Throwable t) {
		if (t instanceof FlatFileParseException fpe) {
			log.warn("[SKIP-READ] line={} input={} reason={}",
					fpe.getLineNumber(), fpe.getInput(), fpe.getMessage(), t);
		} else {
			log.warn("[SKIP-READ] reason={}", t.getMessage(), t);
		}
	}

	@Override
	public void onSkipInProcess(TransactionFileRow item, Throwable t) {
		log.warn("[SKIP-PROCESS] account={} customerId={} reason={}",
				item == null ? null : item.getAccountNumber(),
				item == null ? null : item.getCustomerId(),
				t.getMessage(), t);
	}

	@Override
	public void onSkipInWrite(TransactionInsertRow item, Throwable t) {
		log.warn("[SKIP-WRITE] accountId={} customerId={} reason={}",
				item == null ? null : item.getAccountId(),
				item == null ? null : item.getCustomerId(),
				t.getMessage(), t);
	}
}
