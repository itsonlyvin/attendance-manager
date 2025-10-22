package com.attendance.fin;

import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import jakarta.annotation.PostConstruct;
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

    private final String toEmail = "vinayak448v@gmail.com"; // recipient

    private final int WARNING_THRESHOLD = 50; // adjust based on your Supabase tier

    public ConnectionMonitorService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Test email on startup
    @PostConstruct
    public void testEmail() {
        System.out.println("Testing SendGrid email...");
        sendAlertEmail(99); // test value
    }

    // Scheduled task to check active connections every 1 minute
    @Scheduled(fixedDelay = 60000)
    public void checkConnections() {
        try {
            Integer activeConnections = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity;", Integer.class
            );

            if (activeConnections != null && activeConnections >= WARNING_THRESHOLD) {
                System.out.println("⚠️ Warning: Active connections = " + activeConnections);
                sendAlertEmail(activeConnections);
            } else {
                System.out.println("✅ Active connections = " + activeConnections);
            }
        } catch (Exception ex) {
            System.err.println("Failed to query database: " + ex.getMessage());
        }
    }

    // Send email via SendGrid with retries and logging
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

        int maxRetries = 3;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                request.setMethod(Method.POST);
                request.setEndpoint("mail/send");
                request.setBody(mail.build());

                Response response = sg.api(request);

                System.out.println("Attempt " + attempt + " - Status Code: " + response.getStatusCode());
                System.out.println("Body: " + response.getBody());
                System.out.println("Headers: " + response.getHeaders());

                if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                    System.out.println("✅ Email successfully sent!");
                    break;
                } else {
                    System.err.println("⚠️ Failed to send email on attempt " + attempt);
                }

            } catch (IOException ex) {
                System.err.println("❌ Exception on attempt " + attempt + ": " + ex.getMessage());
            }

            try {
                Thread.sleep(2000); // wait 2 seconds before retrying
            } catch (InterruptedException ignored) {}
        }
    }
}
