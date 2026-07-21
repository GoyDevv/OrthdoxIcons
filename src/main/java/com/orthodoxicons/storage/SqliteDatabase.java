package com.orthodoxicons.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

/**
 * SQLite-backed {@link Database}. SQLite is single-file and does not need a
 * connection pool; instead every call opens a short-lived connection with WAL
 * enabled for good concurrent read performance. All access is serialized by the
 * repositories through a single-threaded executor, guaranteeing thread safety.
 */
public final class SqliteDatabase implements Database {

    private final File file;
    private final Logger logger;
    private final String url;

    /**
     * @param file   the database file location
     * @param logger plugin logger
     */
    public SqliteDatabase(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
        this.url = "jdbc:sqlite:" + file.getAbsolutePath();
    }

    @Override
    public void initialize() throws SQLException {
        try {
            // Load the shaded driver explicitly so it registers on all servers.
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Bundled SQLite driver missing", e);
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            logger.warning("Could not create database directory: " + parent);
        }
        try (Connection conn = connection(); Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=NORMAL");
            st.execute("PRAGMA foreign_keys=ON");
            createSchema(st);
        }
        logger.info("SQLite storage ready at " + file.getName());
    }

    private void createSchema(Statement st) throws SQLException {
        st.executeUpdate("""
            CREATE TABLE IF NOT EXISTS icons (
                id           TEXT PRIMARY KEY,
                name         TEXT NOT NULL,
                saint        TEXT NOT NULL DEFAULT '',
                feast        TEXT NOT NULL DEFAULT '',
                category     TEXT NOT NULL DEFAULT 'Uncategorized',
                description  TEXT NOT NULL DEFAULT '',
                source_url   TEXT NOT NULL DEFAULT '',
                image_url    TEXT NOT NULL,
                attribution  TEXT NOT NULL DEFAULT '',
                license      TEXT NOT NULL DEFAULT 'Unknown',
                license_free INTEGER NOT NULL DEFAULT 0,
                tags         TEXT NOT NULL DEFAULT '',
                provider     TEXT NOT NULL,
                image_hash   TEXT NOT NULL DEFAULT '',
                etag         TEXT,
                last_modified TEXT,
                date_added   INTEGER NOT NULL
            )
            """);
        st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_icons_category ON icons(category)");
        st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_icons_saint ON icons(saint)");
        st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_icons_provider ON icons(provider)");

        st.executeUpdate("""
            CREATE TABLE IF NOT EXISTS placed_icons (
                entity_id  TEXT PRIMARY KEY,
                icon_id    TEXT NOT NULL,
                owner      TEXT,
                world      TEXT NOT NULL,
                x          INTEGER NOT NULL,
                y          INTEGER NOT NULL,
                z          INTEGER NOT NULL,
                block_face TEXT NOT NULL,
                rotation   INTEGER NOT NULL DEFAULT 0,
                map_id     INTEGER NOT NULL DEFAULT -1
            )
            """);
        st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_placed_owner ON placed_icons(owner)");
        st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_placed_world ON placed_icons(world)");

        st.executeUpdate("""
            CREATE TABLE IF NOT EXISTS favorites (
                player  TEXT NOT NULL,
                icon_id TEXT NOT NULL,
                PRIMARY KEY (player, icon_id)
            )
            """);
    }

    @Override
    public Connection connection() throws SQLException {
        return DriverManager.getConnection(url);
    }

    @Override
    public String kind() {
        return "SQLITE";
    }

    @Override
    public void close() {
        // Nothing pooled; connections are closed by their callers.
    }
}
