package com.attendance.fin.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "attendance")
@Data
@NoArgsConstructor
@Getter
@Setter
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Employee Reference
    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    // Attendance date
    @Column(nullable = false)
    private LocalDate date;

    // Clock-in and clock-out timestamps
    private LocalDateTime clockIn;
    private LocalDateTime clockOut;

    // QR codes scanned for in/out
    @Column(nullable = true)
    private String qrCodeIn;

    @Column(nullable = true)
    private String qrCodeOut;

    // Work timings
    private LocalTime shiftStart; // 9:00 or 9:30
    private LocalTime shiftEnd;   // 17:00 or 17:30

    // Calculated total worked hours
    private Double totalHours; // e.g., 7.5, 8.0

    // Flags
    private boolean isLate;       // true if late > allowed tolerance
    private boolean halfDay;      // true if late beyond 10 min
    private boolean overtimeAllowed; // admin allows extra
    private boolean isPresent;    // false if absent
    private boolean isHoliday;    // true if paid holiday

    // Employee location when punching
    private Double latitude;
    private Double longitude;

    // Admin remarks (optional)
    private String adminRemarks;

    public Attendance(Employee employee, LocalDate date, String qrCodeIn, String qrCodeOut, LocalTime shiftStart, LocalTime shiftEnd) {
        this.employee = employee;
        this.date = date;
        this.qrCodeIn = qrCodeIn;
        this.qrCodeOut = qrCodeOut;
        this.shiftStart = shiftStart;
        this.shiftEnd = shiftEnd;
        this.isPresent = true; // default present
    }


}
