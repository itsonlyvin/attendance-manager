package com.attendance.fin.repository;


import com.attendance.fin.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeRepository extends JpaRepository<Employee, String> {
    // Fin employees → finOrOpenArms = false
    List<Employee> findByFinOpenArmsTrue();

    // Open Arms employees → finOrOpenArms = true
    List<Employee> findByFinOpenArmsFalse();


    boolean existsByCompanyEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);
    Employee findByEmailVerificationCode(String code);

    Employee findByCompanyEmail(String email);
}
