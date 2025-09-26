package com.attendance.fin.controller;

import com.attendance.fin.model.Admin;
import com.attendance.fin.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /// Create Admin
    @PostMapping
    public ResponseEntity<String> createAdmin(@RequestBody Admin admin) {
        return adminService.createAdmin(admin);
    }

    /// Update Admin Password
    @PutMapping("/update-password")
    public ResponseEntity<String> updateAdminPassword(@RequestBody Map<String, String> body) {
        String adminId = body.get("adminId");
        String password = body.get("password");
        String newPassword = body.get("newPassword");
        return adminService.updateAdmin(adminId, password, newPassword);
    }

    /// Get Admin Details
    @GetMapping("/{adminId}")
    public ResponseEntity<Admin> getAdminDetails(@PathVariable String adminId) {
        return adminService.getAdminDetails(adminId);
    }

    /// Admin Login
    @PutMapping("/login")
    public ResponseEntity<String> loginAdmin(@RequestBody Map<String, String> body) {
        String adminId = body.get("adminId");
        String password = body.get("password");
        return adminService.loginAdmin(adminId, password);
    }


    /// 🔹 Forget Password
    @PostMapping("/forget-password")
    public ResponseEntity<String> forgetPassword(@RequestBody Map<String, String> body) {
        return adminService.forgetPassword(body.get("adminId"));
    }

    /// 🔹 Add New Password
    @PostMapping("/add-new-password")
    public ResponseEntity<String> addNewPassword(@RequestBody Map<String, String> body) {
        return adminService.addNewPassword(body.get("newPassword"), body.get("code"));
    }

    /// Verify Email
    @PostMapping("/verify-email")
    public ResponseEntity<String> verifyAdminEmail(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String code = body.get("code");
        return adminService.verifyEmail(email, code);
    }

    /// Resend Verification Email
    @PostMapping("/resend-verification")
    public ResponseEntity<String> resendAdminVerification(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        return adminService.resendVerificationCode(email);
    }
}
