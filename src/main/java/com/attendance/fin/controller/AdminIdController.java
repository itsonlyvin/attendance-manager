package com.attendance.fin.controller;

import com.attendance.fin.model.AdminId;
import com.attendance.fin.responseWrapperClasses.AdminIdResponse;
import com.attendance.fin.service.AdminIdService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin-ids")
public class AdminIdController {

    private final AdminIdService adminIdService;

    public AdminIdController(AdminIdService adminIdService) {
        this.adminIdService = adminIdService;
    }

    /// Create
    @PostMapping
    public ResponseEntity<String> createAdminId(@RequestBody AdminId adminId) {
        return adminIdService.createAdminId(adminId);
    }

    /// Get by ID
    @GetMapping("/{adminId}")
    public ResponseEntity<AdminIdResponse> getAdminIdDetails(@PathVariable String adminId) {
        return adminIdService.getAdminIdDetails(adminId);
    }

    /// Get all
    @GetMapping("/all")
    public ResponseEntity<List<AdminId>> getAllAdminIds() {
        return adminIdService.getAllAdminIds();
    }

    /// Update registered status
    @PutMapping("/{adminId}/update-registered")
    public ResponseEntity<String> updateRegisteredStatus(@PathVariable String adminId,
                                                         @RequestParam boolean status) {
        return adminIdService.updateRegisteredStatus(adminId, status);
    }

    /// Delete
    @DeleteMapping("/{adminId}")
    public ResponseEntity<String> deleteAdminId(@PathVariable String adminId) {
        return adminIdService.deleteAdminId(adminId);
    }
}
