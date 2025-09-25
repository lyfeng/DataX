package com.alibaba.datax.plugin.writer.smartmysqlwriter;

import com.alibaba.datax.common.element.Column;
import com.alibaba.datax.common.element.Record;
import com.alibaba.datax.common.exception.DataXException;
import com.alibaba.datax.common.util.Configuration;
import com.alibaba.datax.plugin.writer.smartmysqlwriter.util.SmartMysqlWriterUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * SmartMysqlWriterTask 处理具体的数据写入逻辑
 * 支持自定义更新字段，只更新指定的列
 */
public class SmartMysqlWriterTask {
    
    private static final Logger LOG = LoggerFactory.getLogger(SmartMysqlWriterTask.class);
    
    private Configuration writerSliceConfig;
    private Connection connection;
    private PreparedStatement preparedStatement;
    
    // 配置参数
    private String jdbcUrl;
    private String username;
    private String password;
    private String tableName;
    private List<String> columns;
    private List<String> updateKeys;
    private String writeMode;
    private int batchSize = 1000;
    
    // 批处理相关
    private List<Record> batchRecords;
    private int currentBatchSize = 0;
    
    public SmartMysqlWriterTask(Configuration writerSliceConfig) {
        this.writerSliceConfig = writerSliceConfig;
        this.batchRecords = new ArrayList<>();
        
        // 解析配置参数
        parseConfiguration();
    }
    
    private void parseConfiguration() {
        // 获取数据库连接信息
        this.username = this.writerSliceConfig.getString("username");
        this.password = this.writerSliceConfig.getString("password");
        
        // 获取连接和表信息
        Configuration connection = this.writerSliceConfig.getConfiguration("connection");
        this.jdbcUrl = connection.getString("jdbcUrl");
        this.tableName = connection.getList("table", String.class).get(0);
        
        // 获取列信息
        this.columns = this.writerSliceConfig.getList("column", String.class);
        
        // 获取更新键
        this.updateKeys = this.writerSliceConfig.getList("updateKey", String.class);
        
        // 获取写入模式
        this.writeMode = this.writerSliceConfig.getString("writeMode", "insert");
        
        // 获取批处理大小
        this.batchSize = this.writerSliceConfig.getInt("batchSize", 1000);
        
        LOG.info("SmartMysqlWriterTask configuration: table={}, writeMode={}, columns={}, updateKeys={}", 
                this.tableName, this.writeMode, this.columns, this.updateKeys);
    }
    
    public void prepare() {
        try {
            // 建立数据库连接
            this.connection = DriverManager.getConnection(this.jdbcUrl, this.username, this.password);
            this.connection.setAutoCommit(false);
            
            // 根据写入模式准备SQL语句
            String sql = buildSql();
            this.preparedStatement = this.connection.prepareStatement(sql);
            
            LOG.info("Database connection established and SQL prepared: {}", sql);
            
        } catch (SQLException e) {
            throw DataXException.asDataXException(
                SmartMysqlWriterErrorCode.CONNECT_DATABASE_ERROR,
                String.format("Failed to connect to database: %s", e.getMessage()), e);
        }
    }
    
    private String buildSql() {
        if ("update".equalsIgnoreCase(this.writeMode)) {
            return buildUpdateSql();
        } else {
            return buildInsertSql();
        }
    }
    
    private String buildInsertSql() {
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(this.tableName).append(" (");
        
        // 添加列名
        for (int i = 0; i < this.columns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(this.columns.get(i));
        }
        
        sql.append(") VALUES (");
        
        // 添加占位符
        for (int i = 0; i < this.columns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
        }
        
        sql.append(")");
        
        return sql.toString();
    }
    
    private String buildUpdateSql() {
        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE ").append(this.tableName).append(" SET ");
        
        // 只更新非updateKey的列
        List<String> updateColumns = new ArrayList<>();
        for (String column : this.columns) {
            if (!this.updateKeys.contains(column)) {
                updateColumns.add(column);
            }
        }
        
        // 构建SET子句
        for (int i = 0; i < updateColumns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(updateColumns.get(i)).append(" = ?");
        }
        
        // 构建WHERE子句
        sql.append(" WHERE ");
        for (int i = 0; i < this.updateKeys.size(); i++) {
            if (i > 0) {
                sql.append(" AND ");
            }
            sql.append(this.updateKeys.get(i)).append(" = ?");
        }
        
        return sql.toString();
    }
    
    public void writeRecord(Record record) {
        this.batchRecords.add(record);
        this.currentBatchSize++;
        
        if (this.currentBatchSize >= this.batchSize) {
            flush();
        }
    }
    
    public void flush() {
        if (this.batchRecords.isEmpty()) {
            return;
        }
        
        try {
            for (Record record : this.batchRecords) {
                setStatementParameters(record);
                this.preparedStatement.addBatch();
            }
            
            int[] results = this.preparedStatement.executeBatch();
            this.connection.commit();
            
            LOG.info("Batch executed successfully, {} records processed.", results.length);
            
        } catch (SQLException e) {
            try {
                this.connection.rollback();
            } catch (SQLException rollbackException) {
                LOG.error("Failed to rollback transaction", rollbackException);
            }
            
            throw DataXException.asDataXException(
                SmartMysqlWriterErrorCode.WRITE_DATA_ERROR,
                String.format("Failed to write data: %s", e.getMessage()), e);
        } finally {
            // 清空批次数据
            this.batchRecords.clear();
            this.currentBatchSize = 0;
        }
    }
    
    private void setStatementParameters(Record record) throws SQLException {
        int parameterIndex = 1;
        
        if ("update".equalsIgnoreCase(this.writeMode)) {
            // UPDATE模式：先设置SET子句的参数，再设置WHERE子句的参数
            
            // 设置非updateKey列的值
            for (int i = 0; i < this.columns.size(); i++) {
                String columnName = this.columns.get(i);
                if (!this.updateKeys.contains(columnName)) {
                    Column column = record.getColumn(i);
                    setParameter(this.preparedStatement, parameterIndex++, column);
                }
            }
            
            // 设置WHERE子句的updateKey值
            for (String updateKey : this.updateKeys) {
                int columnIndex = this.columns.indexOf(updateKey);
                if (columnIndex >= 0) {
                    Column column = record.getColumn(columnIndex);
                    setParameter(this.preparedStatement, parameterIndex++, column);
                }
            }
            
        } else {
            // INSERT模式：按列顺序设置参数
            for (int i = 0; i < this.columns.size(); i++) {
                Column column = record.getColumn(i);
                setParameter(this.preparedStatement, parameterIndex++, column);
            }
        }
    }
    
    private void setParameter(PreparedStatement ps, int index, Column column) throws SQLException {
        if (column.getRawData() == null) {
            ps.setNull(index, Types.NULL);
        } else {
            switch (column.getType()) {
                case STRING:
                    ps.setString(index, column.asString());
                    break;
                case LONG:
                    ps.setLong(index, column.asLong());
                    break;
                case DOUBLE:
                    ps.setDouble(index, column.asDouble());
                    break;
                case DATE:
                    ps.setTimestamp(index, new Timestamp(column.asDate().getTime()));
                    break;
                case BOOL:
                    ps.setBoolean(index, column.asBoolean());
                    break;
                case BYTES:
                    ps.setBytes(index, column.asBytes());
                    break;
                default:
                    ps.setString(index, column.asString());
                    break;
            }
        }
    }
    
    public void post() {
        // 确保所有数据都已写入
        flush();
    }
    
    public void destroy() {
        try {
            if (this.preparedStatement != null) {
                this.preparedStatement.close();
            }
            if (this.connection != null) {
                this.connection.close();
            }
            LOG.info("Database resources closed successfully.");
        } catch (SQLException e) {
            LOG.error("Failed to close database resources", e);
        }
    }
}