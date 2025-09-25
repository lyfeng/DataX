package com.alibaba.datax.plugin.writer.smartmysqlwriter.util;

import com.alibaba.datax.common.exception.DataXException;
import com.alibaba.datax.common.util.Configuration;
import com.alibaba.datax.plugin.writer.smartmysqlwriter.SmartMysqlWriterErrorCode;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * SmartMysqlWriter工具类
 * 提供配置参数验证和其他辅助功能
 */
public class SmartMysqlWriterUtil {
    
    private static final Logger LOG = LoggerFactory.getLogger(SmartMysqlWriterUtil.class);
    
    /**
     * 验证配置参数
     * @param configuration 配置对象
     */
    public static void validateParameter(Configuration configuration) {
        // 验证必需的基本参数
        validateBasicParameters(configuration);
        
        // 验证连接配置
        validateConnectionParameters(configuration);
        
        // 验证列配置
        validateColumnParameters(configuration);
        
        // 验证写入模式相关参数
        validateWriteModeParameters(configuration);
        
        LOG.info("Configuration validation passed successfully.");
    }
    
    /**
     * 验证基本参数
     */
    private static void validateBasicParameters(Configuration configuration) {
        // 验证用户名
        String username = configuration.getString("username");
        if (StringUtils.isBlank(username)) {
            throw DataXException.asDataXException(
                SmartMysqlWriterErrorCode.REQUIRED_VALUE,
                "Parameter 'username' is required and cannot be empty.");
        }
        
        // 验证密码
        String password = configuration.getString("password");
        if (StringUtils.isBlank(password)) {
            throw DataXException.asDataXException(
                SmartMysqlWriterErrorCode.REQUIRED_VALUE,
                "Parameter 'password' is required and cannot be empty.");
        }
    }
    
    /**
     * 验证连接配置
     */
    private static void validateConnectionParameters(Configuration configuration) {
        List<Object> connections = configuration.getList("connection");
        if (connections == null || connections.isEmpty()) {
            throw DataXException.asDataXException(
                SmartMysqlWriterErrorCode.REQUIRED_VALUE,
                "Parameter 'connection' is required and cannot be empty.");
        }
        
        for (Object connectionObj : connections) {
            Configuration connection = Configuration.from(connectionObj.toString());
            
            // 验证JDBC URL
            String jdbcUrl = connection.getString("jdbcUrl");
            if (StringUtils.isBlank(jdbcUrl)) {
                throw DataXException.asDataXException(
                    SmartMysqlWriterErrorCode.REQUIRED_VALUE,
                    "Parameter 'jdbcUrl' in connection is required and cannot be empty.");
            }
            
            // 验证表名
            List<String> tables = connection.getList("table", String.class);
            if (tables == null || tables.isEmpty()) {
                throw DataXException.asDataXException(
                    SmartMysqlWriterErrorCode.REQUIRED_VALUE,
                    "Parameter 'table' in connection is required and cannot be empty.");
            }
            
            for (String table : tables) {
                if (StringUtils.isBlank(table)) {
                    throw DataXException.asDataXException(
                        SmartMysqlWriterErrorCode.REQUIRED_VALUE,
                        "Table name cannot be empty.");
                }
            }
        }
    }
    
    /**
     * 验证列配置
     */
    private static void validateColumnParameters(Configuration configuration) {
        List<String> columns = configuration.getList("column", String.class);
        if (columns == null || columns.isEmpty()) {
            throw DataXException.asDataXException(
                SmartMysqlWriterErrorCode.REQUIRED_VALUE,
                "Parameter 'column' is required and cannot be empty.");
        }
        
        for (String column : columns) {
            if (StringUtils.isBlank(column)) {
                throw DataXException.asDataXException(
                    SmartMysqlWriterErrorCode.REQUIRED_VALUE,
                    "Column name cannot be empty.");
            }
        }
    }
    
    /**
     * 验证写入模式相关参数
     */
    private static void validateWriteModeParameters(Configuration configuration) {
        String writeMode = configuration.getString("writeMode", "insert");
        
        // 验证写入模式值
        if (!"insert".equalsIgnoreCase(writeMode) && !"update".equalsIgnoreCase(writeMode)) {
            throw DataXException.asDataXException(
                SmartMysqlWriterErrorCode.ILLEGAL_VALUE,
                String.format("Unsupported writeMode: %s. Only 'insert' and 'update' are supported.", writeMode));
        }
        
        // 如果是update模式，验证updateKey参数
        if ("update".equalsIgnoreCase(writeMode)) {
            List<String> updateKeys = configuration.getList("updateKey", String.class);
            if (updateKeys == null || updateKeys.isEmpty()) {
                throw DataXException.asDataXException(
                    SmartMysqlWriterErrorCode.REQUIRED_VALUE,
                    "Parameter 'updateKey' is required when writeMode is 'update'.");
            }
            
            // 验证updateKey是否在column列表中
            List<String> columns = configuration.getList("column", String.class);
            for (String updateKey : updateKeys) {
                if (StringUtils.isBlank(updateKey)) {
                    throw DataXException.asDataXException(
                        SmartMysqlWriterErrorCode.REQUIRED_VALUE,
                        "UpdateKey cannot be empty.");
                }
                
                if (!columns.contains(updateKey)) {
                    throw DataXException.asDataXException(
                        SmartMysqlWriterErrorCode.ILLEGAL_VALUE,
                        String.format("UpdateKey '%s' is not found in column list: %s", updateKey, columns));
                }
            }
            
            // 验证至少有一个非updateKey的列用于更新
            long updateableColumns = columns.stream()
                .filter(column -> !updateKeys.contains(column))
                .count();
            
            if (updateableColumns == 0) {
                throw DataXException.asDataXException(
                    SmartMysqlWriterErrorCode.ILLEGAL_VALUE,
                    "At least one column should be available for update (not in updateKey list).");
            }
        }
        
        // 验证批处理大小
        int batchSize = configuration.getInt("batchSize", 1000);
        if (batchSize <= 0) {
            throw DataXException.asDataXException(
                SmartMysqlWriterErrorCode.ILLEGAL_VALUE,
                String.format("BatchSize must be greater than 0, but got: %d", batchSize));
        }
        
        if (batchSize > 10000) {
            LOG.warn("BatchSize {} is quite large, consider using a smaller value for better performance.", batchSize);
        }
    }
    
    /**
     * 格式化JDBC URL，确保包含必要的参数
     * @param jdbcUrl 原始JDBC URL
     * @return 格式化后的JDBC URL
     */
    public static String formatJdbcUrl(String jdbcUrl) {
        if (StringUtils.isBlank(jdbcUrl)) {
            return jdbcUrl;
        }
        
        // 确保包含字符编码参数
        if (!jdbcUrl.contains("useUnicode")) {
            String separator = jdbcUrl.contains("?") ? "&" : "?";
            jdbcUrl += separator + "useUnicode=true&characterEncoding=utf-8";
        }
        
        // 确保包含时区参数（MySQL 8.0+）
        if (!jdbcUrl.contains("serverTimezone") && jdbcUrl.contains("mysql")) {
            String separator = jdbcUrl.contains("?") ? "&" : "?";
            jdbcUrl += separator + "serverTimezone=Asia/Shanghai";
        }
        
        return jdbcUrl;
    }
    
    /**
     * 检查数据库连接是否可用
     * @param jdbcUrl JDBC URL
     * @param username 用户名
     * @param password 密码
     * @return 连接是否可用
     */
    public static boolean testConnection(String jdbcUrl, String username, String password) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            java.sql.Connection connection = java.sql.DriverManager.getConnection(
                formatJdbcUrl(jdbcUrl), username, password);
            connection.close();
            return true;
        } catch (Exception e) {
            LOG.error("Failed to test database connection: {}", e.getMessage());
            return false;
        }
    }
}