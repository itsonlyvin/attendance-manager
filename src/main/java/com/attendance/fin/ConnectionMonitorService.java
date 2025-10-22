package com.attendance.fin;

import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class ConnectionMonitorService {

    private final JdbcTemplate jdbcTemplate;

    @Value("${sendgrid.api-key}")
    private String sendGridApiKey;

    @Value("${sendgrid.from-email}")
    private String fromEmail;

    private final String toEmail = "vinayak448v@gamil.com"; // recipient

    private final int WARNING_THRESHOLD = 50; // adjust based on your Supabase tier

    public ConnectionMonitorService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedDelay = 60000) // every 1 min
    public void checkConnections() {
        Integer activeConnections = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_stat_activity;", Integer.class
        );

        if (activeConnections != null && activeConnections >= WARNING_THRESHOLD) {
            System.out.println("⚠️ Warning: Active connections = " + activeConnections);
            sendAlertEmail(activeConnections);
        }
    }

    private void sendAlertEmail(int activeConnections) {
        Email from = new Email(fromEmail);
        String subject = "⚠️ Supabase Connection Warning";
        Email to = new Email(toEmail);
        Content content = new Content("text/plain",
                "Warning: Your Supabase database has " + activeConnections +
                        " active connections, nearing the limit.");
        Mail mail = new Mail(from, subject, to, content);

        SendGrid sg = new SendGrid(sendGridApiKey);
        Request request = new Request();

        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sg.api(request);
            System.out.println("Email sent! Status Code: " + response.getStatusCode());
        } catch (IOException ex) {
            System.err.println("Failed to send email alert: " + ex.getMessage());
        }
    }
}
