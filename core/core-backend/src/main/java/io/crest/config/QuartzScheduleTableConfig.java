package io.crest.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Quartz JDBC JobStore 使用 Crest 统一后的调度表名。
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.quartz", name = "job-store-type", havingValue = "jdbc")
public class QuartzScheduleTableConfig {

    @Bean
    public SchedulerFactoryBeanCustomizer quartzScheduleTableNameCustomizer(DataSource dataSource) {
        return schedulerFactoryBean -> schedulerFactoryBean.setDataSource(
                new QuartzScheduleTableNameDataSource(dataSource)
        );
    }
}
