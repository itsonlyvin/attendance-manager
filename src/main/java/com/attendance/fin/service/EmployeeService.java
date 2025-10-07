package com.attendance.fin.service;

import com.attendance.fin.model.Employee;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface EmployeeService {

    ResponseEntity<String> createEmployee(Employee employee);

    ResponseEntity<String> resendVerificationCode(String email);

    ResponseEntity<String> updateEmployee(String employee, String password, String newPassword);

    ResponseEntity<?> getEmployeeDetails(String employee);

    ResponseEntity<String> loginEmployee(String employeeId, String password);

    ResponseEntity<List<Employee>> getAllEmployees();

    ResponseEntity<List<Employee>> getFinEmployees();

    ResponseEntity<List<Employee>> getOpenArmsEmployees();

    ResponseEntity<String> verifyEmail(String email, String code);

    ResponseEntity<String> forgetPassword(String employeeId);


    ResponseEntity<String> addNewPassword(String newPassword, String code);



    ResponseEntity<String> setSalary(String employeeId, double salary);

    ResponseEntity<String> setBonus(String employeeId, double bonus);

    void deleteEmployeeById(String employeeId);
}
