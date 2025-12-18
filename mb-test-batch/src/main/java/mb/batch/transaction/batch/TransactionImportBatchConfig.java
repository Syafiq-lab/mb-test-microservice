package mb.batch.transaction.batch;

import lombok.RequiredArgsConstructor;
import mb.batch.transaction.dto.TransactionFileRow;
import mb.batch.transaction.dto.TransactionInsertRow;
import mb.batch.transaction.exception.InvalidTransactionRecordException;
import mb.batch.transaction.util.TransactionImportSkipListener;
import mb.batch.transaction.util.TrimBlankLineRecordSeparatorPolicy;
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
import org.springframework.lang.NonNull;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

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
                .skip(org.springframework.batch.infrastructure.item.file.FlatFileParseException.class)
                .skip(InvalidTransactionRecordException.class)
                .skipListener(skipListener)
                .build();
    }


    @Bean
    public LineMapper<TransactionFileRow> transactionLineMapper() {
        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
        tokenizer.setDelimiter("|");
        tokenizer.setNames("ACCOUNT_NUMBER", "TRX_AMOUNT", "DESCRIPTION", "TRX_DATE", "TRX_TIME", "CUSTOMER_ID");
        tokenizer.setStrict(false);  // Changed to false to handle blank lines gracefully

        DefaultLineMapper<TransactionFileRow> mapper = new DefaultLineMapper<>();
        mapper.setLineTokenizer(tokenizer);
        mapper.setFieldSetMapper(fieldSet -> {
            // Skip blank lines completely
            if (fieldSet == null || fieldSet.getFieldCount() == 0) {
                return null;
            }
            return mapFieldSet(fieldSet);
        });
        return mapper;
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

        FlatFileItemReader<TransactionFileRow> reader = new FlatFileItemReader<>(resource, transactionLineMapper);
        reader.setName("transactionFileReader");
        reader.setLinesToSkip(1);  // Skip header
        reader.setRecordSeparatorPolicy(new TrimBlankLineRecordSeparatorPolicy());
        return reader;
    }

    private TransactionFileRow mapFieldSet(FieldSet fs) {
        if (fs == null) return null;
        
        String accountNumber = trimToNull(fs.readString("ACCOUNT_NUMBER"));
        String customerId    = trimToNull(fs.readString("CUSTOMER_ID"));
        String trxDateStr    = trimToNull(fs.readString("TRX_DATE"));
        String trxTimeStr    = trimToNull(fs.readString("TRX_TIME"));
        String desc          = trimToNull(fs.readString("DESCRIPTION"));

        if (accountNumber == null || customerId == null || trxDateStr == null || trxTimeStr == null) {
            throw new mb.batch.transaction.exception.InvalidTransactionRecordException(
                    "Missing required fields. accountNumber=" + accountNumber +
                            ", customerId=" + customerId + ", trxDate=" + trxDateStr + ", trxTime=" + trxTimeStr
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

        // Quote table name because "transaction" can be problematic in SQL dialects
        writer.setSql("""
            INSERT INTO "transaction"
              (version, account_id, amount, description, trx_date, trx_time, customer_id, created_at, updated_at)
            VALUES
              (:version, :accountId, :amount, :description, :trxDate, :trxTime, :customerId, :createdAt, :updatedAt)
        """);

        writer.setItemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>());
        return writer;
    }
}
