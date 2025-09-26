package com.attendance.fin.controller;

import com.attendance.fin.model.EmployeeId;
import com.attendance.fin.responseWrapperClasses.EmployeeIdResponse;
import com.attendance.fin.service.EmployeeIdService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/employee-ids")
public class EmployeeIdController {

    private final EmployeeIdService employeeIdService;

    public EmployeeIdController(EmployeeIdService employeeIdService) {
        this.employeeIdService = employeeIdService;
    }

    /// Create
    @PostMapping
    public ResponseEntity<String> createEmployeeId(@RequestBody EmployeeId employeeId) {
        return employeeIdService.createEmployeeId(employeeId);
    }

    /// Get
    @GetMapping("/{employeeId}")
    public ResponseEntity<EmployeeIdResponse> getEmployeeIdDetails(@PathVariable String employeeId) {
        return employeeIdService.getEmployeeIdDetails(employeeId);
    }

    /// Get All
    @GetMapping("/all_employees")
    public ResponseEntity<List<EmployeeId>> getAllEmployeeIds() {
        return employeeIdService.getAllEmployeeIds();
    }

    /// Update Registered Status
    @PutMapping("/{employeeId}/update-registered")
    public ResponseEntity<String> updateRegisteredStatus(@PathVariable String employeeId,
                                                         @RequestParam boolean status) {
        return employeeIdService.updateRegisteredStatus(employeeId, status);
    }

    /// Delete EmployeeId
    @DeleteMapping("/{employeeId}")
    public ResponseEntity<String> deleteEmployeeId(@PathVariable String employeeId) {
        return employeeIdService.deleteEmployeeId(employeeId);
    }
}
