package mb.batch.transaction.util;

import lombok.extern.slf4j.Slf4j;
import mb.batch.transaction.dto.TransactionFileRow;
import mb.batch.transaction.dto.TransactionInsertRow;

import org.springframework.batch.core.ExitStatus;

import org.springframework.batch.core.listener.ItemProcessListener;
import org.springframework.batch.core.listener.ItemReadListener;
import org.springframework.batch.core.listener.ItemWriteListener;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class TransactionImportLoggingListener implements
		ItemReadListener<TransactionFileRow>,
		ItemProcessListener<TransactionFileRow, TransactionInsertRow>,
		ItemWriteListener<TransactionInsertRow>,
		StepExecutionListener {

	// ===== STEP =====
	@Override
	public void beforeStep(StepExecution stepExecution) {
		log.info("[STEP-START] name={} jobExecutionId={} params={}",
				stepExecution.getStepName(),
				stepExecution.getJobExecutionId(),
				stepExecution.getJobParameters());
	}

	@Override
	public ExitStatus afterStep(StepExecution stepExecution) {
		log.info("[STEP-END] name={} status={} read={} filter={} written={} readSkips={} processSkips={} writeSkips={} commits={}",
				stepExecution.getStepName(),
				stepExecution.getStatus(),
				stepExecution.getReadCount(),
				stepExecution.getFilterCount(),
				stepExecution.getWriteCount(),
				stepExecution.getReadSkipCount(),
				stepExecution.getProcessSkipCount(),
				stepExecution.getWriteSkipCount(),
				stepExecution.getCommitCount());

		return stepExecution.getExitStatus();
	}

	// ===== READ =====
	@Override
	public void afterRead(TransactionFileRow item) {
		log.debug("[READ] account={} customerId={} trxDate={} trxTime={}",
				safe(item.getAccountNumber()),
				safe(item.getCustomerId()),
				item.getTrxDate(),
				item.getTrxTime());
	}

	@Override
	public void onReadError(Exception ex) {
		if (ex instanceof FlatFileParseException fpe) {
			log.error("[READ-ERROR] line={} input={} msg={}",
					fpe.getLineNumber(),
					fpe.getInput(),
					fpe.getMessage(), ex);
		} else {
			log.error("[READ-ERROR] msg={}", ex.getMessage(), ex);
		}
	}

	// ===== PROCESS =====
	@Override
	public void beforeProcess(TransactionFileRow item) {
		log.debug("[PROCESS] start account={} customerId={}",
				safe(item.getAccountNumber()),
				safe(item.getCustomerId()));
	}

	@Override
	public void afterProcess(TransactionFileRow item, TransactionInsertRow result) {
		if (result == null) {
			log.warn("[PROCESS] filtered/null account={} customerId={}",
					safe(item.getAccountNumber()),
					safe(item.getCustomerId()));
		} else {
			log.debug("[PROCESS] ok accountId={} amount={} customerId={}",
					result.getAccountId(),
					result.getAmount(),
					safe(result.getCustomerId()));
		}
	}

	@Override
	public void onProcessError(TransactionFileRow item, Exception ex) {
		log.error("[PROCESS-ERROR] account={} customerId={} msg={}",
				item == null ? null : safe(item.getAccountNumber()),
				item == null ? null : safe(item.getCustomerId()),
				ex.getMessage(), ex);
	}

	// ===== WRITE =====
	@Override
	public void beforeWrite(Chunk<? extends TransactionInsertRow> items) {
		log.info("[WRITE] start batchSize={}", items == null ? 0 : items.size());
	}

	@Override
	public void afterWrite(Chunk<? extends TransactionInsertRow> items) {
		log.info("[WRITE] ok batchSize={}", items == null ? 0 : items.size());
	}

	@Override
	public void onWriteError(Exception ex, Chunk<? extends TransactionInsertRow> items) {
		log.error("[WRITE-ERROR] batchSize={} msg={}",
				items == null ? 0 : items.size(),
				ex.getMessage(), ex);
	}


	private static String safe(String s) {
		return s == null ? "" : s.trim();
	}
}
