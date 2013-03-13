package mazestormer.game;

import java.io.IOException;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public enum ConnectionMode {

	LOCAL {
		@Override
		public ConnectionFactory createConnectionFactory() {
			ConnectionFactory factory = new ConnectionFactory();
			factory.setHost("localhost");
			return factory;
		}

		@Override
		public String toString() {
			return "Localhost";
		}
	},
	PENO {
		@Override
		public ConnectionFactory createConnectionFactory() {
			ConnectionFactory factory = new ConnectionFactory();
			factory.setUsername("guest");
			factory.setPassword("guest");
			factory.setVirtualHost("/");
			factory.setHost("leuven.cs.kotnet.kuleuven.be");
			factory.setPort(5672);
			factory.setRequestedHeartbeat(0);
			return factory;
		}

		@Override
		public String toString() {
			return "P&O";
		}
	};

	private final ConnectionFactory connectionFactory;

	private ConnectionMode() {
		this.connectionFactory = createConnectionFactory();
	}

	public Connection newConnection() throws IOException {
		return connectionFactory.newConnection();
	}

	protected abstract ConnectionFactory createConnectionFactory();

}