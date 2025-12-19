package mb.batch.transaction.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mb.batch.transaction.dto.TransactionFileRow;
import mb.batch.transaction.dto.TransactionInsertRow;
import mb.batch.transaction.exception.InvalidTransactionRecordException;
import mb.batch.transaction.util.TransactionImportSkipListener;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.batch.infrastructure.item.file.LineMapper;
import org.springframework.batch.infrastructure.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.infrastructure.item.file.transform.FieldSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class TransactionImportBatchConfig {

    @Value("${app.batch.import-transactions.chunk-size:100}")
    private int chunkSize;

    @Value("${app.batch.import-transactions.input-resource:file:/data/transactions-source.txt}")
    private String defaultInputResource;

    @Bean
    public Job importTransactionsJob(JobRepository jobRepository, Step importTransactionsStep) {
        return new JobBuilder("importTransactionsJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(importTransactionsStep)
                .build();
    }

    @Bean
    public Step importTransactionsStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            FlatFileItemReader<TransactionFileRow> transactionFileReader,
            TransactionImportProcessor processor,
            JdbcBatchItemWriter<TransactionInsertRow> writer,
            TransactionImportSkipListener skipListener,
            mb.batch.transaction.util.TransactionImportLoggingListener loggingListener
    ) {
        return new StepBuilder("importTransactionsStep", jobRepository)
                .<TransactionFileRow, TransactionInsertRow>chunk(chunkSize)
                .transactionManager(transactionManager)
                .reader(transactionFileReader)
                .processor(processor)
                .writer(writer)
                .listener(loggingListener)
                .faultTolerant()
                .skipLimit(500)
                .skip(FlatFileParseException.class)
                .skip(InvalidTransactionRecordException.class)
                .skipListener(skipListener)
                .build();
    }

    /**
     * One physical line = one record.
     * Blank lines are SKIPPED by throwing FlatFileParseException (so the reader never returns null early).
     */
    @Bean
    public LineMapper<TransactionFileRow> transactionLineMapper() {
        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
        tokenizer.setDelimiter("|");
        tokenizer.setNames("ACCOUNT_NUMBER", "TRX_AMOUNT", "DESCRIPTION", "TRX_DATE", "TRX_TIME", "CUSTOMER_ID");
        tokenizer.setStrict(false);

        DefaultLineMapper<TransactionFileRow> delegate = new DefaultLineMapper<>();
        delegate.setLineTokenizer(tokenizer);
        delegate.setFieldSetMapper(this::mapFieldSet);

        return (line, lineNumber) -> {
            if (line == null || line.trim().isEmpty()) {
                // blank line between records -> skip as read error
                throw new FlatFileParseException("Blank line", line, lineNumber);
            }
            return delegate.mapLine(line, lineNumber);
        };
    }

    @Bean
    @org.springframework.batch.core.configuration.annotation.StepScope
    public FlatFileItemReader<TransactionFileRow> transactionFileReader(
            ResourcePatternResolver resolver,
            LineMapper<TransactionFileRow> transactionLineMapper,
            @Value("#{jobParameters['inputResource']}") String inputResource
    ) {
        String location = (inputResource == null || inputResource.isBlank())
                ? defaultInputResource
                : inputResource;

        Resource resource = resolver.getResource(location);

        // ---- Resource diagnostics ----
        log.info("[READER] jobParam.inputResource='{}' default='{}' resolved='{}'",
                inputResource, defaultInputResource, location);
        log.info("[READER] resource='{}' exists={} readable={}",
                resource.getDescription(), resource.exists(), resource.isReadable());
        try {
            log.info("[READER] uri={}", resource.getURI());
        } catch (Exception e) {
            log.warn("[READER] cannot get URI: {}", e.toString());
        }
        try {
            if (resource.isFile()) {
                var f = resource.getFile();
                log.info("[READER] filePath={} sizeBytes={} lastModified={}",
                        f.getAbsolutePath(), f.length(), Instant.ofEpochMilli(f.lastModified()));
            }
        } catch (Exception e) {
            log.warn("[READER] cannot access file metadata: {}", e.toString());
        }
        previewFirstLines(resource, 10);
        // ----------------------------

        FlatFileItemReader<TransactionFileRow> reader = new FlatFileItemReader<>(resource, transactionLineMapper);
        reader.setName("transactionFileReader");

        // fail fast if file missing (default is true, keeping explicit)
        reader.setStrict(true);

        reader.setLinesToSkip(1);
        reader.setSkippedLinesCallback(line -> log.info("[READER] skippedHeader='{}'", line));

        // IMPORTANT FIX: DO NOT use RecordSeparatorPolicy for “blank lines between records”
        // reader.setRecordSeparatorPolicy(new TrimBlankLineRecordSeparatorPolicy());

        return reader;
    }

    private void previewFirstLines(Resource resource, int maxLines) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            for (int i = 1; i <= maxLines; i++) {
                String line = br.readLine();
                if (line == null) break;
                log.info("[READER-PREVIEW] {}: [{}]", i, line);
            }
        } catch (Exception e) {
            log.warn("[READER-PREVIEW] failed: {}", e.toString());
        }
    }

    private TransactionFileRow mapFieldSet(FieldSet fs) {
        if (fs == null) {
            // never return null here; treat as parse error (will be skipped via FlatFileParseException)
            throw new IllegalArgumentException("FieldSet is null");
        }

        String accountNumber = trimToNull(fs.readString("ACCOUNT_NUMBER"));
        String customerId    = trimToNull(fs.readString("CUSTOMER_ID"));
        String trxDateStr    = trimToNull(fs.readString("TRX_DATE"));
        String trxTimeStr    = trimToNull(fs.readString("TRX_TIME"));
        String desc          = trimToNull(fs.readString("DESCRIPTION"));

        if (accountNumber == null || customerId == null || trxDateStr == null || trxTimeStr == null) {
            throw new InvalidTransactionRecordException(
                    "Missing required fields. accountNumber=" + accountNumber +
                            ", customerId=" + customerId +
                            ", trxDate=" + trxDateStr +
                            ", trxTime=" + trxTimeStr
            );
        }

        return TransactionFileRow.builder()
                .accountNumber(accountNumber)
                .trxAmount(readBigDecimal(fs, "TRX_AMOUNT"))
                .description(desc)
                .trxDate(LocalDate.parse(trxDateStr))
                .trxTime(LocalTime.parse(trxTimeStr))
                .customerId(customerId)
                .build();
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static BigDecimal readBigDecimal(FieldSet fs, String name) {
        String raw = fs.readString(name);
        return raw == null || raw.isBlank() ? BigDecimal.ZERO : new BigDecimal(raw.trim());
    }

    @Bean
    public JdbcBatchItemWriter<TransactionInsertRow> transactionWriter(DataSource dataSource) {
        JdbcBatchItemWriter<TransactionInsertRow> writer = new JdbcBatchItemWriter<>();
        writer.setDataSource(dataSource);

        // Use uppercase quoted table name to match unquoted-created TRANSACTION in H2 and avoid keyword issues.
        writer.setSql("""
            INSERT INTO "TRANSACTION"
              (VERSION, ACCOUNT_ID, AMOUNT, DESCRIPTION, TRX_DATE, TRX_TIME, CUSTOMER_ID, CREATED_AT, UPDATED_AT)
            VALUES
              (:version, :accountId, :amount, :description, :trxDate, :trxTime, :customerId, :createdAt, :updatedAt)
        """);

        writer.setItemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>());
        return writer;
    }
}
