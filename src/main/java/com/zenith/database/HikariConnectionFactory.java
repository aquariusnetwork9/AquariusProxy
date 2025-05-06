package com.zenith.database;

import lombok.Data;
import org.jdbi.v3.core.ConnectionFactory;

import java.sql.Connection;

@Data
public class HikariConnectionFactory implements ConnectionFactory {
    private final ConnectionPool connectionPool;

    @Override
    public Connection openConnection() {
        return connectionPool.getWriteConnection();
    }
}
