package com.attendance.fin.service.impl;

import com.attendance.fin.model.Admin;
import com.attendance.fin.model.AdminId;
import com.attendance.fin.repository.AdminIdRepository;
import com.attendance.fin.repository.AdminRepository;
import com.attendance.fin.service.AdminService;
import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@Service
public class AdminServiceImpl implements AdminService {

    private final AdminRepository adminRepository;
    private final AdminIdRepository adminIdRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SendGrid sendGrid;

    @Value("${sendgrid.from-email}")
    private String fromEmail;

    public AdminServiceImpl(AdminRepository adminRepository,
                            AdminIdRepository adminIdRepository,
                            BCryptPasswordEncoder passwordEncoder,
                            SendGrid sendGrid) {
        this.adminRepository = adminRepository;
        this.adminIdRepository = adminIdRepository;
        this.passwordEncoder = passwordEncoder;
        this.sendGrid = sendGrid;
    }

    @Override
    public ResponseEntity<String> createAdmin(Admin admin) {
        AdminId status = adminIdRepository.findById(admin.getAdminId()).orElse(null);
        if (status == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid Admin ID. Not found in admin_ids table.");
        }

        if (status.isRegistered()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Admin already registered.");
        }

        if (adminRepository.existsByCompanyEmail(admin.getCompanyEmail())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Email already registered.");
        }

        if (adminRepository.existsByPhoneNumber(admin.getPhoneNumber())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Phone number already registered.");
        }

        try {
            admin.setPassword(passwordEncoder.encode(admin.getPassword()));

            // Generate verification code
            int code = 1000 + new Random().nextInt(9000);
            admin.setEmailVerificationCode(String.valueOf(code));
            admin.setEmailVerificationExpiry(LocalDateTime.now().plusMinutes(10));
            admin.setEmailVerified(false);

            adminRepository.save(admin);

            // send email via SendGrid
            sendVerificationEmail(admin.getCompanyEmail(), String.valueOf(code));

            status.setRegistered(true);
            adminIdRepository.save(status);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error while saving admin.");
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Admin registered successfully. Verification email sent.");
    }

    @Override
    public ResponseEntity<String> updateAdmin(String adminId, String password, String newPassword) {
        Admin data = adminRepository.findById(adminId).orElse(null);
        if (data == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Admin ID not found");
        }

        if (!passwordEncoder.matches(password, data.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        }

        if (passwordEncoder.matches(newPassword, data.getPassword())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Password is already the same");
        }

        data.setPassword(passwordEncoder.encode(newPassword));
        adminRepository.save(data);
        return ResponseEntity.ok("Password updated successfully");
    }

    @Override
    public ResponseEntity<Admin> getAdminDetails(String adminId) {
        return adminRepository.findById(adminId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(null));
    }

    @Override
    public ResponseEntity<String> loginAdmin(String adminId, String password) {
        Admin data = adminRepository.findById(adminId).orElse(null);
        if (data == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Admin ID not found");
        }

//        if (!data.isEmailVerified()) {
//            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Please verify your email first.");
//        }

        if (passwordEncoder.matches(password, data.getPassword())) {
            return ResponseEntity.ok("Login successful");
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        }
    }

    @Override
    public ResponseEntity<String> verifyEmail(String email, String code) {
        Admin admin = adminRepository.findByCompanyEmail(email);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Admin not found");
        }

        if (admin.isEmailVerified()) {
            return ResponseEntity.ok("Email already verified");
        }

        if (admin.getEmailVerificationCode() != null &&
                admin.getEmailVerificationCode().equals(code) &&
                admin.getEmailVerificationExpiry().isAfter(LocalDateTime.now())) {

            admin.setEmailVerified(true);
            admin.setEmailVerificationCode(null);
            admin.setEmailVerificationExpiry(null);
            adminRepository.save(admin);
            return ResponseEntity.ok("Email verified successfully");
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid or expired code");
    }

    @Override
    public ResponseEntity<String> resendVerificationCode(String email) {
        Admin admin = adminRepository.findByCompanyEmail(email);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Admin not found");
        }

        if (admin.isEmailVerified()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Email already verified");
        }

        int code = 1000 + new Random().nextInt(9000);
        admin.setEmailVerificationCode(String.valueOf(code));
        admin.setEmailVerificationExpiry(LocalDateTime.now().plusMinutes(10));

        adminRepository.save(admin);

        sendVerificationEmail(admin.getCompanyEmail(), String.valueOf(code));
        return ResponseEntity.ok("New verification code sent to email.");
    }

    @Override
    public ResponseEntity<String> forgetPassword(String adminId) {
        Admin admin = adminRepository.findById(adminId).orElse(null);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Admin ID not found.");
        }

        int code = 1000 + new Random().nextInt(9000);
        admin.setEmailVerificationCode(String.valueOf(code));
        admin.setEmailVerificationExpiry(LocalDateTime.now().plusMinutes(10));
        adminRepository.save(admin);

        try {
            sendEmail(admin.getCompanyEmail(),
                    "Password Reset Verification",
                    "Your password reset code is: " + code + "\nIt expires in 10 minutes.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to send email: " + e.getMessage());
        }

        return ResponseEntity.ok("Password reset code sent to your email.");
    }

    @Override
    public ResponseEntity<String> addNewPassword(String newPassword, String code) {
        Admin admin = adminRepository.findByEmailVerificationCode(code);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid reset code.");
        }

        if (admin.getEmailVerificationExpiry() == null || admin.getEmailVerificationExpiry().isBefore(LocalDateTime.now())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Reset code expired.");
        }

        admin.setPassword(passwordEncoder.encode(newPassword));
        admin.setEmailVerificationCode(null);
        admin.setEmailVerificationExpiry(null);
        adminRepository.save(admin);

        return ResponseEntity.ok("Password updated successfully.");
    }

    // 🔹 Common method to send verification email
    private void sendVerificationEmail(String to, String code) {
        String subject = "Admin Email Verification";
        String body = "Your verification code is: " + code + "\nIt expires in 10 minutes.";
        try {
            sendEmail(to, subject, body);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 🔹 Generic SendGrid Email Sender
    private void sendEmail(String to, String subject, String body) throws IOException {
        Email from = new Email(fromEmail);
        Email recipient = new Email(to);
        Content content = new Content("text/plain", body);
        Mail mail = new Mail(from, subject, recipient, content);

        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(mail.build());

        Response response = sendGrid.api(request);
        System.out.println("SendGrid Response: " + response.getStatusCode());
    }
}
