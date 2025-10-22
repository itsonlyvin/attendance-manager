package com.attendance.fin;

import jakarta.annotation.PreDestroy;
import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class DataSourceShutdown {
    @Autowired
    private ApplicationContext context;

    @PreDestroy
    public void closeDataSource() {
        try {
            DataSource dataSource = context.getBean(DataSource.class);
            if (dataSource instanceof HikariDataSource hikari) {
                hikari.close();
                System.out.println("✅ HikariDataSource closed on shutdown");
            }
        } catch (Exception e) {
            System.err.println("⚠️ Failed to close DataSource: " + e.getMessage());
        }
    }
}
