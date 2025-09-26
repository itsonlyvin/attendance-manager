package com.attendance.fin.service;

import com.attendance.fin.model.Admin;
import org.springframework.http.ResponseEntity;

public interface AdminService {



    ResponseEntity<String> createAdmin(Admin admin);

    ResponseEntity<String> updateAdmin(String adminId, String password, String newPassword);

    ResponseEntity<Admin> getAdminDetails(String adminId);

    ResponseEntity<String> loginAdmin(String adminId, String password);

    ResponseEntity<String> verifyEmail(String email, String code);

    ResponseEntity<String> resendVerificationCode(String email);

    ResponseEntity<String> forgetPassword(String adminId);



    ResponseEntity<String> addNewPassword(String password, String code);




}
