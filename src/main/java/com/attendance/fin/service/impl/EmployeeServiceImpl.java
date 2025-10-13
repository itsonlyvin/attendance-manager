package com.attendance.fin.service.impl;

import com.attendance.fin.model.Employee;
import com.attendance.fin.model.EmployeeId;
import com.attendance.fin.repository.EmployeeIdRepository;
import com.attendance.fin.repository.EmployeeRepository;
import com.attendance.fin.service.EmployeeService;
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
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeIdRepository employeeIdRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SendGrid sendGrid;

    @Value("${sendgrid.from-email}")
    private String fromEmail;

    public EmployeeServiceImpl(EmployeeRepository employeeRepository,
                               EmployeeIdRepository employeeIdRepository,
                               BCryptPasswordEncoder passwordEncoder,
                               SendGrid sendGrid) {
        this.employeeRepository = employeeRepository;
        this.employeeIdRepository = employeeIdRepository;
        this.passwordEncoder = passwordEncoder;
        this.sendGrid = sendGrid;
    }

    @Override
    public ResponseEntity<String> createEmployee(Employee employee) {
        EmployeeId status = employeeIdRepository.findById(employee.getEmployeeId()).orElse(null);
        if (status == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid Employee ID. Not found in employee_ids table.");
        }

        if (status.isRegistered()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Employee already registered.");
        }

        if (employeeRepository.existsByCompanyEmail(employee.getCompanyEmail())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Email already registered.");
        }

        if (employeeRepository.existsByPhoneNumber(employee.getPhoneNumber())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Phone number already registered.");
        }

        try {
            employee.setPassword(passwordEncoder.encode(employee.getPassword()));

            int code = 1000 + new Random().nextInt(9000);
            employee.setEmailVerificationCode(String.valueOf(code));
            employee.setEmailVerified(false);
            employee.setEmailVerificationExpiry(LocalDateTime.now().plusMinutes(10));

            employeeRepository.save(employee);

            sendVerificationEmail(employee.getCompanyEmail(), String.valueOf(code));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error saving employee: " + e.getMessage());
        }

        status.setRegistered(false);
        employeeIdRepository.save(status);

        return ResponseEntity.status(HttpStatus.CREATED).body("Employee registered successfully. Verification email sent.");
    }

    private void sendVerificationEmail(String email, String code) throws IOException {
        Email from = new Email(fromEmail);
        Email to = new Email(email);
        String subject = "Verify Your Email";
        Content content = new Content("text/plain", "Your verification code is: " + code);
        Mail mail = new Mail(from, subject, to, content);

        Request request = new Request();
        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sendGrid.api(request);
            System.out.println("SendGrid Response: " + response.getStatusCode());
        } catch (IOException e) {
            throw e;
        }
    }

    @Override
    public ResponseEntity<String> verifyEmail(String email, String code) {
        Employee employee = employeeRepository.findByCompanyEmail(email);
        if (employee == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Employee not found.");
        }

        if (employee.isEmailVerified()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Email already verified.");
        }

        if (employee.getEmailVerificationExpiry() == null ||
                employee.getEmailVerificationExpiry().isBefore(LocalDateTime.now())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Verification code expired.");
        }

        if (!employee.getEmailVerificationCode().equals(code)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid verification code.");
        }

        EmployeeId status = employeeIdRepository.findById(employee.getEmployeeId()).orElse(null);
        assert status != null;
        status.setRegistered(true);

        employee.setEmailVerified(true);
        employee.setEmailVerificationCode(null);
        employee.setEmailVerificationExpiry(null);

        employeeRepository.save(employee);

        return ResponseEntity.ok("Email verified successfully.");
    }

    @Override
    public ResponseEntity<String> forgetPassword(String employeeId) {
        Employee employee = employeeRepository.findById(employeeId).orElse(null);
        if (employee == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Employee ID not found.");
        }

        int code = 1000 + new Random().nextInt(9000);
        employee.setEmailVerificationCode(String.valueOf(code));
        employee.setEmailVerificationExpiry(LocalDateTime.now().plusMinutes(10));
        employeeRepository.save(employee);

        try {
            Email from = new Email(fromEmail);
            Email to = new Email(employee.getCompanyEmail());
            String subject = "Password Reset Verification";
            String body = "Your password reset code is: " + code + "\nIt expires in 10 minutes.";
            Content content = new Content("text/plain", body);
            Mail mail = new Mail(from, subject, to, content);

            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sendGrid.api(request);
            System.out.println("SendGrid Response: " + response.getStatusCode());

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to send email: " + e.getMessage());
        }

        return ResponseEntity.ok("Password reset code sent to your email.");
    }

    @Override
    public ResponseEntity<String> addNewPassword(String code, String newPassword) {
        Employee employee = employeeRepository.findByEmailVerificationCode(code);
        if (employee == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid reset code.");
        }

        if (employee.getEmailVerificationExpiry() == null ||
                employee.getEmailVerificationExpiry().isBefore(LocalDateTime.now())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Reset code expired.");
        }

        employee.setPassword(passwordEncoder.encode(newPassword));
        employee.setEmailVerificationCode(null);
        employee.setEmailVerificationExpiry(null);
        employeeRepository.save(employee);

        return ResponseEntity.ok("Password updated successfully.");
    }

    @Override
    public ResponseEntity<String> resendVerificationCode(String email) {
        Employee employee = employeeRepository.findByCompanyEmail(email);

        if (employee == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Employee not found.");
        }

        if (employee.isEmailVerified()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Email is already verified.");
        }

        int code = 1000 + new Random().nextInt(9000);
        employee.setEmailVerificationCode(String.valueOf(code));
        employee.setEmailVerificationExpiry(LocalDateTime.now().plusMinutes(10));
        employeeRepository.save(employee);

        try {
            sendVerificationEmail(employee.getCompanyEmail(), String.valueOf(code));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to send email: " + e.getMessage());
        }

        return ResponseEntity.ok("A new verification code has been sent to your email.");
    }

    @Override
    public ResponseEntity<String> updateEmployee(String employeeId, String password, String newPassword) {
        Employee data = employeeRepository.findById(employeeId).orElse(null);
        if (data == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Employee ID not found");

        if (!passwordEncoder.matches(password, data.getPassword()))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");

        if (passwordEncoder.matches(newPassword, data.getPassword()))
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Password is already the same");

        data.setPassword(passwordEncoder.encode(newPassword));
        employeeRepository.save(data);

        return ResponseEntity.ok("Password updated successfully");
    }

    @Override
    public ResponseEntity<?> getEmployeeDetails(String employeeId) {
        return employeeRepository.findById(employeeId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("Employee not found"));
    }

    @Override
    public ResponseEntity<String> loginEmployee(String employeeId, String password) {
        Employee data = employeeRepository.findById(employeeId).orElse(null);
        if (data == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Employee ID not found");

//        if (!data.isEmailVerified())
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Email not verified. Please check your inbox.");

        if (passwordEncoder.matches(password, data.getPassword())) {
            return ResponseEntity.ok("Login successful");
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        }
    }

    @Override
    public ResponseEntity<List<Employee>> getAllEmployees() {
        return ResponseEntity.ok(employeeRepository.findAll());
    }

    @Override
    public ResponseEntity<List<Employee>> getFinEmployees() {
        return ResponseEntity.ok(employeeRepository.findByFinOpenArmsTrue());
    }

    @Override
    public ResponseEntity<List<Employee>> getOpenArmsEmployees() {
        return ResponseEntity.ok(employeeRepository.findByFinOpenArmsFalse());
    }

    @Override
    public ResponseEntity<String> setSalary(String employeeId, double salary) {
        Optional<Employee> employeeOpt = employeeRepository.findById(employeeId);
        if (employeeOpt.isPresent()) {
            Employee employee = employeeOpt.get();
            employee.setSalary(salary);
            employeeRepository.save(employee);
            return ResponseEntity.ok("Salary updated successfully.");
        } else {
            return ResponseEntity.status(404).body("Employee not found.");
        }
    }

    @Override
    public ResponseEntity<String> setBonus(String employeeId, double bonus) {
        Optional<Employee> employeeOpt = employeeRepository.findById(employeeId);
        if (employeeOpt.isPresent()) {
            Employee employee = employeeOpt.get();
            employee.setBonus(bonus);
            employeeRepository.save(employee);
            return ResponseEntity.ok("Bonus updated successfully.");
        } else {
            return ResponseEntity.status(404).body("Employee not found.");
        }
    }



    @Override
    public void deleteEmployeeById(String employeeId) {
        if (employeeRepository.existsById(employeeId)) {
            employeeRepository.deleteById(employeeId);
        } else {
            throw new RuntimeException("Employee with ID " + employeeId + " not found");
        }
    }


    // Set or update shift timings
    @Override
    public Employee setShiftTimes(String employeeId, LocalTime shiftStart, LocalTime shiftEnd) {
        Optional<Employee> optionalEmployee = employeeRepository.findById(employeeId);

        if (optionalEmployee.isEmpty()) {
            throw new RuntimeException("Employee not found with ID: " + employeeId);
        }

        Employee employee = optionalEmployee.get();
        employee.setShiftStart(shiftStart);
        employee.setShiftEnd(shiftEnd);

        return employeeRepository.save(employee);
    }

    // Get shift timings
    @Override
    public Employee getShiftTimes(String employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found with ID: " + employeeId));
    }
}

