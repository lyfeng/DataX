package com.alibaba.datax.plugin.writer.smartmysqlwriter;

import com.alibaba.datax.common.element.Record;
import com.alibaba.datax.common.plugin.RecordReceiver;
import com.alibaba.datax.common.spi.Writer;
import com.alibaba.datax.common.util.Configuration;
import com.alibaba.datax.plugin.writer.smartmysqlwriter.util.SmartMysqlWriterUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * SmartMysqlWriter插件主类
 * 支持在update模式下自定义更新字段，只更新指定的列而不是全部列
 */
public class SmartMysqlWriter extends Writer {
    
    private static final Logger LOG = LoggerFactory.getLogger(SmartMysqlWriter.class);
    
    public static class Job extends Writer.Job {
        private Configuration originalConfig = null;
        
        @Override
        public void init() {
            this.originalConfig = this.getPluginJobConf();
            
            // 验证配置参数
            SmartMysqlWriterUtil.validateParameter(this.originalConfig);
            
            LOG.info("SmartMysqlWriter Job initialized successfully.");
        }
        
        @Override
        public void prepare() {
            // 预处理阶段，可以在这里进行表结构检查等操作
            LOG.info("SmartMysqlWriter Job prepare phase.");
        }
        
        @Override
        public List<Configuration> split(int mandatoryNumber) {
            List<Configuration> configurations = new ArrayList<Configuration>();
            
            // 根据表的数量进行任务分割
            List<Object> connections = this.originalConfig.getList("connection");
            
            for (int i = 0; i < mandatoryNumber; i++) {
                Configuration taskConfig = this.originalConfig.clone();
                
                // 为每个任务分配连接配置
                int connectionIndex = i % connections.size();
                Configuration connection = Configuration.from(connections.get(connectionIndex).toString());
                
                List<Object> tables = connection.getList("table");
                int tableIndex = (i / connections.size()) % tables.size();
                
                // 设置当前任务的表和连接信息
                taskConfig.set("connection", connection);
                taskConfig.set("table", tables.get(tableIndex));
                
                configurations.add(taskConfig);
            }
            
            LOG.info("SmartMysqlWriter Job split into {} tasks.", configurations.size());
            return configurations;
        }
        
        @Override
        public void post() {
            LOG.info("SmartMysqlWriter Job post phase completed.");
        }
        
        @Override
        public void destroy() {
            LOG.info("SmartMysqlWriter Job destroyed.");
        }
    }
    
    public static class Task extends Writer.Task {
        private Configuration writerSliceConfig;
        private SmartMysqlWriterTask smartMysqlWriterTask;
        
        @Override
        public void init() {
            this.writerSliceConfig = this.getPluginJobConf();
            this.smartMysqlWriterTask = new SmartMysqlWriterTask(this.writerSliceConfig);
            
            LOG.info("SmartMysqlWriter Task initialized.");
        }
        
        @Override
        public void prepare() {
            this.smartMysqlWriterTask.prepare();
            LOG.info("SmartMysqlWriter Task prepare phase completed.");
        }
        
        @Override
        public void startWrite(RecordReceiver recordReceiver) {
            LOG.info("SmartMysqlWriter Task start writing data.");
            
            Record record;
            while ((record = recordReceiver.getFromReader()) != null) {
                this.smartMysqlWriterTask.writeRecord(record);
            }
            
            // 刷新剩余的批次数据
            this.smartMysqlWriterTask.flush();
            
            LOG.info("SmartMysqlWriter Task finished writing data.");
        }
        
        @Override
        public void post() {
            this.smartMysqlWriterTask.post();
            LOG.info("SmartMysqlWriter Task post phase completed.");
        }
        
        @Override
        public void destroy() {
            if (this.smartMysqlWriterTask != null) {
                this.smartMysqlWriterTask.destroy();
            }
            LOG.info("SmartMysqlWriter Task destroyed.");
        }
    }
}