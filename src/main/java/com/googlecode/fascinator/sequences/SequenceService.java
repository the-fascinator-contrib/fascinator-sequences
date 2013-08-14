/*
 * The Fascinator - Sequence Service
 * Copyright (C) 2008-2010 University of Southern Queensland
 * Copyright (C) 2012 Queensland Cyber Infrastructure Foundation (http://www.qcif.edu.au/)
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package com.googlecode.fascinator.sequences;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.fascinator.api.access.AccessControlException;
import com.googlecode.fascinator.common.JsonSimpleConfig;

public class SequenceService {

	/** Logging */
	private final Logger log = LoggerFactory
			.getLogger(SequenceService.class);

	/** JDBC Driver */
	private static String DERBY_DRIVER = "org.apache.derby.jdbc.EmbeddedDriver";

	/** Connection string prefix */
	private static String DERBY_PROTOCOL = "jdbc:derby:";

	/** Sequence database name */
	private static String SEQUENCE_DATABASE = "sequence";

	/** Records table */
	private static String SEQUENCE_TABLE = "sequences";


	/** Database home directory */
	private String derbyHome;

	/** Database connection */
	private Connection connection;


	/**
	 * Initialization of Sequences Service
	 * 
	 * @throws IOException 
	 * @throws SQLException 
	 * 
	 */
	public void init() throws IOException, SQLException {
		JsonSimpleConfig config = new JsonSimpleConfig();
		// Find data directory
		derbyHome = config.getString(null, "database-service", "derbyHome");
		String oldHome = System.getProperty("derby.system.home");

		// Derby's data directory has already been configured
		if (oldHome != null) {
			if (derbyHome != null) {
				// Use the existing one, but throw a warning
				log.warn("Using previously specified data directory:"
						+ " '{}', provided value has been ignored: '{}'",
						oldHome, derbyHome);
			} else {
				// This is ok, no configuration conflicts
				log.info("Using existing data directory: '{}'", oldHome);
			}

			// We don't have one, config MUST have one
		} else {
			if (derbyHome == null) {
				log.error("No database home directory configured!");
				return;
			} else {
				// Establish its validity and existance, create if necessary
				File file = new File(derbyHome);
				if (file.exists()) {
					if (!file.isDirectory()) {
						throw new IOException("Database home '"
								+ derbyHome + "' is not a directory!");
					}
				} else {
					file.mkdirs();
					if (!file.exists()) {
						throw new IOException("Database home '"
								+ derbyHome
								+ "' does not exist and could not be created!");
					}
				}
				System.setProperty("derby.system.home", derbyHome);
			}
		}

		// Database prep work
		try {
			checkTable(SEQUENCE_TABLE);
		} catch (SQLException ex) {
			log.error("Error during database preparation:", ex);
			throw new SQLException(
					"Error during database preparation:", ex);
		}
		log.debug("Derby security database online!");
	}

	private Connection connection() throws SQLException {
		if (connection == null || !connection.isValid(1)) {
			// At least try to close if not null... even though its not valid
			if (connection != null) {
				log.error("!!! Database connection has failed, recreating.");
				try {
					connection.close();
				} catch (SQLException ex) {
					log.error("Error closing invalid connection, ignoring: {}",
							ex.getMessage());
				}
			}

			// Open a new connection
			Properties props = new Properties();
			// Load the JDBC driver
			try {
				Class.forName(DERBY_DRIVER).newInstance();
			} catch (Exception ex) {
				log.error("Driver load failed: ", ex);
				throw new SQLException("Driver load failed: ", ex);
			}

			// Establish a database connection
			connection = DriverManager.getConnection(DERBY_PROTOCOL
					+ SEQUENCE_DATABASE + ";create=true", props);
		}
		return connection;
	}

	/**
	 * Shuts down the plugin
	 * 
	 * @throws AccessControlException
	 *             if there was an error during shutdown
	 */
	public void shutdown() throws SQLException {
		// Derby can only be shutdown from one thread,
		// we'll catch errors from the rest.
		String threadedShutdownMessage = DERBY_DRIVER
				+ " is not registered with the JDBC driver manager";
		try {
			// Tell the database to close
			DriverManager.getConnection(DERBY_PROTOCOL + ";shutdown=true");
			// Shutdown just this database (but not the engine)
			// DriverManager.getConnection(DERBY_PROTOCOL + SECURITY_DATABASE +
			// ";shutdown=true");
		} catch (SQLException ex) {
			// These test values are used if the engine is NOT shutdown
			// if (ex.getErrorCode() == 45000 &&
			// ex.getSQLState().equals("08006")) {

			// Valid response
			if (ex.getErrorCode() == 50000 && ex.getSQLState().equals("XJ015")) {
				// Error response
			} else {
				// Make sure we ignore simple thread issues
				if (!ex.getMessage().equals(threadedShutdownMessage)) {
					log.error("Error during database shutdown:", ex);
					throw new SQLException(
							"Error during database shutdown:", ex);
				}
			}
		} finally {
			try {
				// Close our connection
				if (connection != null) {
					connection.close();
					connection = null;
				}
			} catch (SQLException ex) {
				log.error("Error closing connection:", ex);
			}
		}
	}

	

	
	/**
	 * Check for the existence of a table and arrange for its creation if not
	 * found.
	 * 
	 * @param table
	 *            The table to look for and create.
	 * @throws SQLException
	 *             if there was an error.
	 */
	private void checkTable(String table) throws SQLException {
		boolean tableFound = findTable(table);

		// Create the table if we couldn't find it
		if (!tableFound) {
			log.debug("Table '{}' not found, creating now!", table);
			createTable(table);

			// Double check it was created
			if (!findTable(table)) {
				log.error("Unknown error creating table '{}'", table);
				throw new SQLException("Could not find or create table '"
						+ table + "'");
			}
		}
	}

	/**
	 * Check if the given table exists in the database.
	 * 
	 * @param table
	 *            The table to look for
	 * @return boolean flag if the table was found or not
	 * @throws SQLException
	 *             if there was an error accessing the database
	 */
	private boolean findTable(String table) throws SQLException {
		boolean tableFound = false;
		DatabaseMetaData meta = connection().getMetaData();
		ResultSet result = (ResultSet) meta.getTables(null, null, null, null);
		while (result.next() && !tableFound) {
			if (result.getString("TABLE_NAME").equalsIgnoreCase(table)) {
				tableFound = true;
			}
		}
		close(result);
		return tableFound;
	}

	/**
	 * Create the given table in the database.
	 * 
	 * @param table
	 *            The table to create
	 * @throws SQLException
	 *             if there was an error during creation, or an unknown table
	 *             was specified.
	 */
	private void createTable(String table) throws SQLException {
		if (table.equals(SEQUENCE_TABLE)) {
			Statement sql = connection().createStatement();
			sql.execute("CREATE TABLE " + SEQUENCE_TABLE
					+ "(sequence_name VARCHAR(255) NOT NULL, " 
					+ "counter INTEGER NOT NULL,"
					+ "PRIMARY KEY (sequence_name))");
			close(sql);
			return;
		}

		throw new SQLException("Unknown table '" + table + "' requested!");
	}

	/**
	 * Attempt to close a ResultSet. Basic wrapper for exception catching and
	 * logging
	 * 
	 * @param resultSet
	 *            The ResultSet to try and close.
	 */
	private void close(ResultSet resultSet) {
		if (resultSet != null) {
			try {
				resultSet.close();
			} catch (SQLException ex) {
				log.error("Error closing result set: ", ex);
			}
		}
		resultSet = null;
	}

	/**
	 * Attempt to close a Statement. Basic wrapper for exception catching and
	 * logging
	 * 
	 * @param statement
	 *            The Statement to try and close.
	 */
	private void close(Statement statement) {
		if (statement != null) {
			try {
				statement.close();
			} catch (SQLException ex) {
				log.error("Error closing statement: ", ex);
			}
		}
		statement = null;
	}
	
	public synchronized Integer getSequence(String sequenceName) throws SQLException {
		Integer sequenceCount = null;

		PreparedStatement sql = connection().prepareStatement(
				"SELECT * FROM " + SEQUENCE_TABLE + " WHERE sequence_name = ?");

		// Prepare and execute
		sql.setString(1, sequenceName);
		ResultSet result = sql.executeQuery();

		// Build response
		while (result.next()) {
			sequenceCount = result.getInt("count");
		}
		close(result);
		close(sql);
		
		if(sequenceCount == null) {
			sequenceCount = 1;
			createNewSequence(sequenceName);
		}
		
		incrementSequence(sequenceName, sequenceCount+1);
		
		return sequenceCount;
	}

	private void incrementSequence(String sequenceName, Integer sequenceCount) throws SQLException {
		PreparedStatement sql = connection().prepareStatement(
				"UPDATE " + SEQUENCE_TABLE + " SET count = ? WHERE sequence_name = ?)");

		// Prepare and execute
		sql.setInt(1, sequenceCount);
		sql.setString(2, sequenceName);
		
		sql.executeUpdate();
		close(sql);
		
	}

	private void createNewSequence(String sequenceName) throws SQLException {
		PreparedStatement sql = connection().prepareStatement(
				"INSERT INTO " + SEQUENCE_TABLE + " VALUES (?, ?)");

		// Prepare and execute
		sql.setString(1, sequenceName);
		sql.setInt(2, 1);
		sql.executeUpdate();
		close(sql);
		
	}

}
