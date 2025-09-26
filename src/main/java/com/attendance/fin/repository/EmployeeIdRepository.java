package com.attendance.fin.repository;

import com.attendance.fin.model.EmployeeId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeIdRepository extends JpaRepository<EmployeeId, String> {

}