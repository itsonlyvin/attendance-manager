package com.attendance.fin.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "employees")
@Data
public class Employee {

    @Id
    private String employeeId;

    @Column(nullable = false)
    private String fullName;

    @Column(unique = true,nullable = false)
    private String phoneNumber;


    @Column(unique = true,  nullable = false)
    private String companyEmail;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private boolean finOpenArms = false;  // true is fin and false is open arms


    @Column(nullable = false)
    private boolean emailVerified = false;

    @Column
    private String emailVerificationCode;


    @Column(name = "email_verification_expiry")
    private LocalDateTime emailVerificationExpiry;

    @Column()
    private double salary =  0;

    @Column()
    private double bonus = 0;


    public Employee(String employeeId, String fullName, String phoneNumber, String companyEmail, String password, boolean finOpenArms, boolean emailVerified, String emailVerificationCode, LocalDateTime emailVerificationExpiry, double salary, double bonus) {
        this.employeeId = employeeId;
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.companyEmail = companyEmail;
        this.password = password;
        this.finOpenArms = finOpenArms;
        this.emailVerified = emailVerified;
        this.emailVerificationCode = emailVerificationCode;
        this.emailVerificationExpiry = emailVerificationExpiry;
        this.salary = salary;
        this.bonus = bonus;
    }

    public Employee() {
    }
}

