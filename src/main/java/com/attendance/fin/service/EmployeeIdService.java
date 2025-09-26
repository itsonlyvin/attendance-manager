package com.attendance.fin.service;

import com.attendance.fin.model.EmployeeId;
import com.attendance.fin.responseWrapperClasses.EmployeeIdResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface EmployeeIdService {

    ResponseEntity<String> createEmployeeId(EmployeeId employeeId);

    ResponseEntity<String> updateRegisteredStatus(String employeeId, boolean status);

    ResponseEntity<String> deleteEmployeeId(String employeeId);

    ResponseEntity<EmployeeIdResponse> getEmployeeIdDetails(String employeeId);

    ResponseEntity<List<EmployeeId>> getAllEmployeeIds();
}
