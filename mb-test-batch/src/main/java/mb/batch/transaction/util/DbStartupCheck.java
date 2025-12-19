package mb.batch.transaction.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Slf4j
@Component
public class DbStartupCheck implements ApplicationRunner {

	private final DataSource dataSource;
	private final JdbcTemplate jdbc;

	public DbStartupCheck(DataSource dataSource) {
		this.dataSource = dataSource;
		this.jdbc = new JdbcTemplate(dataSource);
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		try (Connection c = dataSource.getConnection()) {
			log.info("[DB-CHECK] jdbcUrl={}", c.getMetaData().getURL());
			log.info("[DB-CHECK] user={}", c.getMetaData().getUserName());
		}

		Integer tableCount = jdbc.queryForObject(
				"SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES",
				Integer.class
		);
		log.info("[DB-CHECK] information_schema.tables={}", tableCount);

		Integer up = jdbc.queryForObject(
				"SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='USER_PROFILE'",
				Integer.class
		);
		Integer acc = jdbc.queryForObject(
				"SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='ACCOUNT'",
				Integer.class
		);
		Integer tx = jdbc.queryForObject(
				"SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='TRANSACTION'",
				Integer.class
		);
		log.info("[DB-CHECK] USER_PROFILE={} ACCOUNT={} TRANSACTION={}", up, acc, tx);
	}
}
