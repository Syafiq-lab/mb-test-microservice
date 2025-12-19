package mb.be.config;

import org.h2.tools.Server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.SQLException;

@Configuration
public class H2TcpServerConfig {

	@Bean(initMethod = "start", destroyMethod = "stop")
	public Server h2TcpServer() throws SQLException {
		return Server.createTcpServer("-tcp", "-tcpAllowOthers", "-tcpPort", "9092");
	}
}