package com.attendance.fin.controller;

import com.attendance.fin.model.Employee;
import com.attendance.fin.service.EmployeeService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/employee")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    /// Create
    @PostMapping
    public ResponseEntity<String> createEmployee(@RequestBody Employee employee) {
        return employeeService.createEmployee(employee);
    }

    /// Password update
    @PutMapping("/update-registered")
    public ResponseEntity<String> updateEmployeePassword(@RequestBody Map<String, String> body) {
        String employeeId = body.get("employeeId");
        String password = body.get("password");
        String newPassword = body.get("newPassword");
        return employeeService.updateEmployee(employeeId, password, newPassword);
    }

    /// Get employee by ID
    @GetMapping("/{employee}")
    public ResponseEntity<?> getEmployeeDetails(@PathVariable String employee) {
        return employeeService.getEmployeeDetails(employee);
    }

    /// Get all employees
    @GetMapping("/all_employees")
    public ResponseEntity<List<Employee>> getAllEmployees() {
        return employeeService.getAllEmployees();
    }

    /// Get all Fin employees
    @GetMapping("/all_employees_fin")
    public ResponseEntity<List<Employee>> getFinEmployees() {
        return employeeService.getFinEmployees();
    }

    /// Get all OpenArms employees
    @GetMapping("/all_employees_openarms")
    public ResponseEntity<List<Employee>> getOpenArmsEmployees() {
        return employeeService.getOpenArmsEmployees();
    }

    /// Login
    @PutMapping("/login")
    public ResponseEntity<String> loginEmployee(@RequestBody Map<String, String> body) {
        String employeeId = body.get("employeeId");
        String password = body.get("password");
        return employeeService.loginEmployee(employeeId, password);
    }

    /// 🔹 Verify Email
    @PostMapping("/verify-email")
    public ResponseEntity<String> verifyEmail(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String code = body.get("code");
        return employeeService.verifyEmail(email, code);
    }

    /// 🔹 Resend Verification Code
    @PostMapping("/resend-verification")
    public ResponseEntity<String> resendVerification(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        return employeeService.resendVerificationCode(email);
    }

    /// 🔹 Forget Password
    @PostMapping("/forget-password")
    public ResponseEntity<String> forgetPassword(@RequestBody Map<String, String> body) {
        String employeeId = body.get("employeeId");
        return employeeService.forgetPassword(employeeId);
    }

    /// 🔹 Add New Password - verify code and update password
    @PostMapping("/add-new-password")
    public ResponseEntity<String> addNewPassword(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        String newPassword = body.get("newPassword");
        return employeeService.addNewPassword(code, newPassword);
    }

    /// 🔹 Endpoint to set salary
    @PutMapping("/salary")
    public ResponseEntity<String> updateSalary(@RequestBody Map<String, String> body) {
        String employeeId = body.get("employeeId");
        double salary = Double.parseDouble(body.get("salary"));
        return employeeService.setSalary(employeeId, salary);
    }

    /// 🔹 Endpoint to set bonus
    @PutMapping("/bonus")
    public ResponseEntity<String> updateBonus(@RequestBody Map<String, String> body) {
        String employeeId = body.get("employeeId");
        double bonus = Double.parseDouble(body.get("bonus"));
        return employeeService.setBonus(employeeId, bonus);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteEmployee(@PathVariable("id") String employeeId) {
        try {
            employeeService.deleteEmployeeById(employeeId);
            return ResponseEntity.ok("Employee deleted successfully.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }


    // API to set shift start and end times
    @PutMapping("/{id}/shift")
    public Employee setShiftTimes(
            @PathVariable("id") String employeeId,
            @RequestParam("start") @DateTimeFormat(pattern = "HH:mm") LocalTime shiftStart,
            @RequestParam("end") @DateTimeFormat(pattern = "HH:mm") LocalTime shiftEnd) {

        return employeeService.setShiftTimes(employeeId, shiftStart, shiftEnd);
    }

    // API to get employee shift times
    @GetMapping("/{id}/shift")
    public Employee getShiftTimes(@PathVariable("id") String employeeId) {
        return employeeService.getShiftTimes(employeeId);
    }

}
