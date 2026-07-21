package com.orthodoxicons.storage;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Abstract storage backend. Concrete implementations (SQLite, and in the future
 * MySQL) provide pooled {@link Connection}s and schema management. Repositories
 * depend only on this interface, never on a concrete driver.
 */
public interface Database extends AutoCloseable {

    /**
     * Initializes the backend and creates the schema if needed.
     *
     * @throws SQLException if initialization fails
     */
    void initialize() throws SQLException;

    /**
     * Borrows a connection from the backend. Callers must close it (which
     * returns it to the pool where applicable).
     *
     * @return an open connection
     * @throws SQLException if a connection cannot be obtained
     */
    Connection connection() throws SQLException;

    /**
     * @return a short identifier of this backend (e.g. "SQLITE")
     */
    String kind();

    /**
     * Closes the backend and releases all resources.
     */
    @Override
    void close();
}
