package com.alibaba.datax.plugin.writer.smartmysqlwriter;

import com.alibaba.datax.common.spi.ErrorCode;

/**
 * SmartMysqlWriter错误码定义
 */
public enum SmartMysqlWriterErrorCode implements ErrorCode {
    
    /**
     * 必需参数缺失
     */
    REQUIRED_VALUE("SmartMysqlWriter-00", "Required parameter is missing or empty."),
    
    /**
     * 参数值非法
     */
    ILLEGAL_VALUE("SmartMysqlWriter-01", "Parameter value is illegal or invalid."),
    
    /**
     * 数据库连接错误
     */
    CONNECT_DATABASE_ERROR("SmartMysqlWriter-02", "Failed to connect to database."),
    
    /**
     * 数据写入错误
     */
    WRITE_DATA_ERROR("SmartMysqlWriter-03", "Failed to write data to database."),
    
    /**
     * SQL执行错误
     */
    SQL_EXECUTE_ERROR("SmartMysqlWriter-04", "Failed to execute SQL statement."),
    
    /**
     * 表不存在
     */
    TABLE_NOT_EXISTS("SmartMysqlWriter-05", "Target table does not exist."),
    
    /**
     * 列不存在
     */
    COLUMN_NOT_EXISTS("SmartMysqlWriter-06", "Target column does not exist in table."),
    
    /**
     * 数据类型不匹配
     */
    DATA_TYPE_MISMATCH("SmartMysqlWriter-07", "Data type mismatch between source and target."),
    
    /**
     * 事务处理错误
     */
    TRANSACTION_ERROR("SmartMysqlWriter-08", "Transaction processing error."),
    
    /**
     * 配置解析错误
     */
    CONFIG_PARSE_ERROR("SmartMysqlWriter-09", "Failed to parse configuration."),
    
    /**
     * 资源清理错误
     */
    RESOURCE_CLEANUP_ERROR("SmartMysqlWriter-10", "Failed to cleanup resources.");
    
    private final String code;
    private final String description;
    
    SmartMysqlWriterErrorCode(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    @Override
    public String getCode() {
        return this.code;
    }
    
    @Override
    public String getDescription() {
        return this.description;
    }
    
    @Override
    public String toString() {
        return String.format("Code:[%s], Description:[%s]", this.code, this.description);
    }
}