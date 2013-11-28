package com.anthavio.sink;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

/**
 * 
 * @author martin.vanek
 *
 */
public class GenericThrows {

	public static void main(String[] args) {
		StrategyContext context = new StrategyContext();

		ReaderLoader readerLoader = new ReaderLoader(new StringReader("Blah! Blah! Blah!"));
		try {
			context.perform(readerLoader);
		} catch (IOException iox) {
			//ReaderLoader thrown IOException is propagated through StrategyContext and delivered here
		} catch (ContextException cx) {
			//StrategyContext thrown exception wrapper - All sorts of non ReaderLoader originated Exceptions
		}

		DataSource dataSource = null; //get it from somewhere
		JdbcLoader jdbcLoader = new JdbcLoader(dataSource, "SELECT something FROM somewhere WHERE column = ?");
		try {
			context.perform(jdbcLoader);
		} catch (InvalidRecordCountException ircx) {
			//JdbcLoader thrown InvalidRecordCountException is propagated through StrategyContext and delivered here
			long badRecordId = ircx.getRecordId(); //we can take some action when knowing failing record id
		} catch (ContextException cx) {
			//StrategyContext thrown exception wrapper - SQLException will be cause propably...
			Throwable cause = cx.getCause();
		}

	}

}

/**
 * Strategy interface - To be implemented by client
 */
interface LoaderStrategy<X extends Exception> {

	public String load(long id) throws X;

}

/**
 * Lazy but nice client can pick this LoaderStrategy subtype
 */
interface NiceLoaderStrategy extends LoaderStrategy<RuntimeException> {

	@Override
	public String load(long id);

}

/**
 * Lazy and nasty client can pick this LoaderStrategy subtype
 */
interface NastyLoaderStrategy extends LoaderStrategy<Exception> {

	@Override
	public String load(long id) throws Exception;

}

/**
 * Context exception wrapper - delivers other then LoaderStrategy exceptions to client 
 */
class ContextException extends RuntimeException {

	public ContextException(String string, Exception cause) {
		super(string, cause);
	}

}

/**
 * Context using LoaderStrategy, passing selected exceptions (X)
 */
class StrategyContext {

	public <X extends Exception> void perform(LoaderStrategy<X> loader) throws X {

		long resourceId = getIdFromSomewhere();

		String data = loader.load(resourceId); //throws X - don't have to hadle exception here

		try {
			sendDataToRemoteSystem(data);
		} catch (RemoteException rx) {
			//Other checked Exception must be wrapped inside ContextException 
			throw new ContextException("Failed to send data for resourceId " + resourceId, rx);
		}
	}

	private long getIdFromSomewhere() {
		return System.currentTimeMillis(); //CurrentTimeMillis is best id ever!
	}

	private void sendDataToRemoteSystem(String data) throws RemoteException {
		//invoke some remoting operation here...
	}

}

/**
 * Client provided LoaderStrategy implementation throwing IOException
 */
class ReaderLoader implements LoaderStrategy<IOException> {

	private Reader reader;

	public ReaderLoader(Reader reader) {
		this.reader = reader;
	}

	public String load(long id) throws IOException {
		String sid = String.valueOf(id);
		BufferedReader br = new BufferedReader(reader);
		String line = null;
		while ((line = br.readLine()) != null) {
			if (line.startsWith(sid)) {
				return line;
			}
		}
		return null;
	}
}

/**
 * Client provided custom Exception
 */
class InvalidRecordCountException extends Exception {

	private long recordId;

	public InvalidRecordCountException(long recordId, String message) {
		super(message);
		this.recordId = recordId;
	}

	public InvalidRecordCountException(long recordId, SQLException exception) {
		super(exception);
		this.recordId = recordId;
	}

	public long getRecordId() {
		return recordId;
	}

}

/**
 * Client provided LoaderStrategy implementation throwing custom InvalidRecordCountException
 */
class JdbcLoader implements LoaderStrategy<InvalidRecordCountException> {

	private DataSource dataSource;

	private String select;

	public JdbcLoader(DataSource dataSource, String select) {
		this.dataSource = dataSource;
		this.select = select;
	}

	@Override
	public String load(long id) throws InvalidRecordCountException {
		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet resultSet = null;
		try {
			connection = dataSource.getConnection();
			statement = connection.prepareStatement(select);
			statement.setLong(1, id);
			resultSet = statement.executeQuery();
			if (resultSet.next()) {
				String string = resultSet.getString(1);
				if (resultSet.next()) {
					//here we go...
					throw new InvalidRecordCountException(id, "Too many somethings in somewhere!");
				}
				return string;
			} else {
				//here we go...
				throw new InvalidRecordCountException(id, "Not a single something in somewhere!");
			}
		} catch (SQLException sqlx) {
			//here we go...
			throw new InvalidRecordCountException(id, sqlx);
		} finally {
			//TODO close resultSet, statement, connection
		}

	}
}
