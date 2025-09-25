# SmartMysqlWriter

SmartMysqlWriter是一个增强版的DataX MySQL写入插件，支持在update模式下自定义更新字段，只更新指定的列而不是全部列，提供更智能和高效的数据更新功能。

## 功能特性

- **智能更新模式**：在update模式下，只更新指定的列，而不是更新表的所有字段
- **自定义更新键**：支持通过updateKey参数指定用于匹配记录的键字段
- **批量处理**：支持批量写入，提高数据处理效率
- **完整的错误处理**：提供详细的错误码和异常信息
- **配置验证**：严格的参数验证，确保配置正确性

## 与原生mysqlwriter的区别

| 特性 | 原生mysqlwriter | SmartMysqlWriter |
|------|----------------|------------------|
| update模式 | 更新所有字段 | 只更新指定字段 |
| 性能 | 较低（更新不必要的字段） | 较高（只更新需要的字段） |
| 灵活性 | 有限 | 高度可配置 |
| 网络开销 | 较大 | 较小 |

## 配置参数

### 基本参数

| 参数名 | 描述 | 必选 | 默认值 |
|--------|------|------|--------|
| username | 数据库用户名 | 是 | 无 |
| password | 数据库密码 | 是 | 无 |
| column | 要操作的列名列表 | 是 | 无 |
| connection | 数据库连接配置 | 是 | 无 |
| writeMode | 写入模式：insert/update | 否 | insert |
| updateKey | 更新键（update模式必需） | 否 | 无 |
| batchSize | 批处理大小 | 否 | 1000 |

### connection配置

| 参数名 | 描述 | 必选 |
|--------|------|------|
| jdbcUrl | JDBC连接URL | 是 |
| table | 目标表名列表 | 是 |

## 使用示例

### 示例1：INSERT模式

```json
{
    "name": "smartmysqlwriter",
    "parameter": {
        "username": "${mysql_username}",
        "password": "${mysql_password}",
        "column": ["id", "name", "email", "created_time"],
        "connection": [
            {
                "table": ["user_info"],
                "jdbcUrl": "jdbc:mysql://localhost:3306/test?useUnicode=true&characterEncoding=utf-8"
            }
        ],
        "writeMode": "insert",
        "batchSize": 1000
    }
}
```

### 示例2：UPDATE模式（智能更新）

```json
{
    "name": "smartmysqlwriter",
    "parameter": {
        "username": "${mysql_username}",
        "password": "${mysql_password}",
        "column": ["device_id", "pe", "updated_time"],
        "connection": [
            {
                "table": ["device_info_tmp"],
                "jdbcUrl": "jdbc:goldendb://134.84.61.13:8880/sd_iot?useUnicode=true&characterEncoding=utf-8"
            }
        ],
        "writeMode": "update",
        "updateKey": ["device_id"],
        "batchSize": 500
    }
}
```

在上述示例中：
- 只会更新`pe`和`updated_time`字段
- 使用`device_id`作为匹配条件
- 不会更新`device_id`字段本身

生成的SQL类似于：
```sql
UPDATE device_info_tmp SET pe = ?, updated_time = ? WHERE device_id = ?
```

### 示例3：多键更新

```json
{
    "name": "smartmysqlwriter",
    "parameter": {
        "username": "${mysql_username}",
        "password": "${mysql_password}",
        "column": ["user_id", "product_id", "quantity", "price", "updated_time"],
        "connection": [
            {
                "table": ["order_items"],
                "jdbcUrl": "jdbc:mysql://localhost:3306/ecommerce?useUnicode=true&characterEncoding=utf-8"
            }
        ],
        "writeMode": "update",
        "updateKey": ["user_id", "product_id"],
        "batchSize": 800
    }
}
```

生成的SQL类似于：
```sql
UPDATE order_items SET quantity = ?, price = ?, updated_time = ? 
WHERE user_id = ? AND product_id = ?
```

## 编译和部署

### 前置条件

确保您的环境中已安装DataX，并且可以访问DataX的相关依赖。

### 编译

```bash
# 进入项目目录
cd smartmysqlwriter

# 编译项目
mvn clean package
```

### 部署

1. 将编译生成的jar包复制到DataX的插件目录：
```bash
cp target/smartmysqlwriter-1.0.0-jar-with-dependencies.jar $DATAX_HOME/plugin/writer/smartmysqlwriter/
```

2. 复制插件配置文件：
```bash
cp src/main/resources/plugin.json $DATAX_HOME/plugin/writer/smartmysqlwriter/
```

3. 在DataX环境中测试插件功能

## 性能优化建议

1. **合理设置批处理大小**：根据数据量和网络情况调整batchSize，建议值在500-2000之间
2. **使用索引**：确保updateKey字段上有适当的索引，提高更新性能
3. **网络优化**：在JDBC URL中添加适当的连接参数，如连接池配置
4. **监控资源使用**：监控数据库连接数和内存使用情况

## 错误处理

插件提供详细的错误码和错误信息：

| 错误码 | 描述 |
|--------|------|
| SmartMysqlWriter-00 | 必需参数缺失 |
| SmartMysqlWriter-01 | 参数值非法 |
| SmartMysqlWriter-02 | 数据库连接错误 |
| SmartMysqlWriter-03 | 数据写入错误 |
| SmartMysqlWriter-04 | SQL执行错误 |

## 注意事项

1. **DataX环境依赖**：本插件需要在DataX环境中运行，确保DataX相关依赖可用
2. **updateKey必须存在**：在update模式下，updateKey指定的字段必须在column列表中
3. **至少一个更新字段**：除了updateKey之外，必须至少有一个字段用于更新
4. **数据类型匹配**：确保源数据类型与目标表字段类型兼容
5. **事务处理**：插件使用批量事务，如果批次中有错误，整个批次会回滚
6. **连接管理**：插件会自动管理数据库连接的创建和释放

## 版本历史

- **v1.0.0**：初始版本，支持智能更新模式和自定义更新字段

## 许可证

本项目基于Apache License 2.0开源协议。

## 贡献

欢迎提交Issue和Pull Request来改进这个插件。

## 联系方式

如有问题或建议，请通过以下方式联系：
- 提交GitHub Issue
- 发送邮件至项目维护者

---

**注意**：本插件是对DataX原生mysqlwriter的增强，保持了与DataX框架的完全兼容性。