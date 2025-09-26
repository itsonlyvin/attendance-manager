package com.attendance.fin.repository;

import com.attendance.fin.model.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRepository extends JpaRepository<Admin, String> {
    Admin findByCompanyEmail(String email);

    boolean existsByCompanyEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);

    Admin findByEmailVerificationCode(String code);
}
