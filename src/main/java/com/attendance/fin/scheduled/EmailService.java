package com.attendance.fin.scheduled;

import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Attachments;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;

@Service
public class EmailService {

    @Value("${sendgrid.api-key}")
    private String sendGridApiKey;

    @Value("${sendgrid.from-email}")
    private String fromEmail;

    public void sendEmailWithMultipleAttachments(String to, String subject, String body, Map<String, byte[]> attachments) throws IOException {
        Email from = new Email(fromEmail);
        Email toEmail = new Email(to);
        Content content = new Content("text/plain", body);
        Mail mail = new Mail(from, subject, toEmail, content);

        // add attachments
        for (Map.Entry<String, byte[]> e : attachments.entrySet()) {
            Attachments a = new Attachments();
            a.setContent(Base64.getEncoder().encodeToString(e.getValue()));
            a.setType("application/pdf");
            a.setFilename(e.getKey());
            a.setDisposition("attachment");
            mail.addAttachments(a);
        }

        SendGrid sg = new SendGrid(sendGridApiKey);
        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(mail.build());
        Response response = sg.api(request);

        System.out.println("Email sent. Status code: " + response.getStatusCode());
    }
}
