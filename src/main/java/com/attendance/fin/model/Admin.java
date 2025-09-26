package com.attendance.fin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;


@Entity
@Table(name = "admin")
@Data
@Getter
@Setter
public class Admin {

    @Id
    private String AdminId;

    @Column(nullable = false)
    private String fullName;

    @Column(unique = true,nullable = false)
    private String phoneNumber;

    @Column( unique = true,nullable = false)
    private String companyEmail;

    @Column(nullable = false)
    private String password;


    @Column(nullable = false)
    private boolean emailVerified = false;

    @Column
    private String emailVerificationCode;


    @Column(name = "email_verification_expiry")
    private LocalDateTime emailVerificationExpiry;

    public Admin(String adminId, String fullName, String phoneNumber, String companyEmail, String password, boolean emailVerified, String emailVerificationCode, LocalDateTime emailVerificationExpiry) {
        AdminId = adminId;
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.companyEmail = companyEmail;
        this.password = password;
        this.emailVerified = emailVerified;
        this.emailVerificationCode = emailVerificationCode;
        this.emailVerificationExpiry = emailVerificationExpiry;
    }

    public Admin() {
    }



}
