package com.attendance.fin.service.impl;

import com.attendance.fin.model.EmployeeId;
import com.attendance.fin.repository.EmployeeIdRepository;
import com.attendance.fin.responseWrapperClasses.EmployeeIdResponse;
import com.attendance.fin.service.EmployeeIdService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EmployeeIdServiceImpl implements EmployeeIdService {

    private final EmployeeIdRepository employeeIdRepository;

    public EmployeeIdServiceImpl(EmployeeIdRepository employeeIdRepository) {
        this.employeeIdRepository = employeeIdRepository;
    }

    @Override
    public ResponseEntity<String> createEmployeeId(EmployeeId employeeId) {
        if (employeeIdRepository.existsById(employeeId.getEmployeeId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Employee ID already exists");
        }

        employeeIdRepository.save(employeeId);
        return ResponseEntity.status(HttpStatus.CREATED).body("Employee ID created successfully");
    }

    @Override
    public ResponseEntity<String> updateRegisteredStatus(String employeeId, boolean status) {
        Optional<EmployeeId> optionalEmployeeId = employeeIdRepository.findById(employeeId);
        if (optionalEmployeeId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Employee ID not found");
        }

        EmployeeId employee = optionalEmployeeId.get();
        employee.setRegistered(status);
        employeeIdRepository.save(employee);

        return ResponseEntity.ok("Updated registered status to " + status);
    }

    @Override
    public ResponseEntity<String> deleteEmployeeId(String employeeId) {
        if (!employeeIdRepository.existsById(employeeId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Employee ID not found");
        }

        employeeIdRepository.deleteById(employeeId);
        return ResponseEntity.ok("Employee ID deleted successfully");
    }

    @Override
    public ResponseEntity<EmployeeIdResponse> getEmployeeIdDetails(String employeeId) {
        Optional<EmployeeId> optionalEmployeeId = employeeIdRepository.findById(employeeId);
        if (optionalEmployeeId.isPresent()) {
            return ResponseEntity.ok(new EmployeeIdResponse("Employee ID found", optionalEmployeeId.get()));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new EmployeeIdResponse("Employee ID not found", null));
        }
    }

    @Override
    public ResponseEntity<List<EmployeeId>> getAllEmployeeIds() {
        List<EmployeeId> employeeIds = employeeIdRepository.findAll();
        return ResponseEntity.ok(employeeIds);
    }
}
