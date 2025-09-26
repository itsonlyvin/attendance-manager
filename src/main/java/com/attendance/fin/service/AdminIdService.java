package com.attendance.fin.service;

import com.attendance.fin.model.AdminId;
import com.attendance.fin.responseWrapperClasses.AdminIdResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface AdminIdService {

    // Create a new Admin ID
    ResponseEntity<String> createAdminId(AdminId adminId);

    // Delete an existing Admin ID
    ResponseEntity<String> deleteAdminId(String adminId);

    // Update the registered status
    ResponseEntity<String> updateRegisteredStatus(String adminId, boolean status);

    // Get Admin ID details
    ResponseEntity<AdminIdResponse> getAdminIdDetails(String adminId);

    // Get all Admin IDs
    ResponseEntity<List<AdminId>> getAllAdminIds();
}
