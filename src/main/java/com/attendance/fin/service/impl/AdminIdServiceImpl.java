package com.attendance.fin.service.impl;

import com.attendance.fin.model.AdminId;
import com.attendance.fin.repository.AdminIdRepository;
import com.attendance.fin.responseWrapperClasses.AdminIdResponse;
import com.attendance.fin.service.AdminIdService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminIdServiceImpl implements AdminIdService {

    private final AdminIdRepository adminIdRepository;

    public AdminIdServiceImpl(AdminIdRepository adminIdRepository) {
        this.adminIdRepository = adminIdRepository;
    }

    @Override
    public ResponseEntity<String> createAdminId(AdminId adminId) {
        if (adminIdRepository.existsById(adminId.getAdminId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Admin ID already exists");
        }
        adminIdRepository.save(adminId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Admin ID created successfully");
    }

    @Override
    public ResponseEntity<String> deleteAdminId(String adminId) {
        if (!adminIdRepository.existsById(adminId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Admin ID not found");
        }
        adminIdRepository.deleteById(adminId);
        return ResponseEntity.ok("Admin ID deleted successfully");
    }

    @Override
    public ResponseEntity<String> updateRegisteredStatus(String adminId, boolean status) {
        AdminId admin = adminIdRepository.findById(adminId).orElse(null);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Admin ID not found");
        }
        admin.setRegistered(status);
        adminIdRepository.save(admin);
        return ResponseEntity.ok("Updated registered status to " + status);
    }

    @Override
    public ResponseEntity<AdminIdResponse> getAdminIdDetails(String adminId) {
        AdminId admin = adminIdRepository.findById(adminId).orElse(null);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new AdminIdResponse("Admin ID not found", null));
        }
        return ResponseEntity.ok(new AdminIdResponse("Admin ID found", admin));
    }

    @Override
    public ResponseEntity<List<AdminId>> getAllAdminIds() {
        List<AdminId> allAdmins = adminIdRepository.findAll();
        return ResponseEntity.ok(allAdmins);
    }
}
