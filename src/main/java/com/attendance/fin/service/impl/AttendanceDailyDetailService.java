package com.attendance.fin.service.impl;

import com.attendance.fin.model.Attendance;
import com.attendance.fin.model.Employee;
import com.attendance.fin.repository.AttendanceRepository;
import com.attendance.fin.repository.EmployeeRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class AttendanceDailyDetailService {

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;

    public AttendanceDailyDetailService(AttendanceRepository attendanceRepository,
                                        EmployeeRepository employeeRepository) {
        this.attendanceRepository = attendanceRepository;
        this.employeeRepository = employeeRepository;
    }

    public DailyAttendanceReport getDailyAttendance(String employeeId, LocalDate date) {
        Employee emp = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        Optional<Attendance> optionalAttendance = attendanceRepository.findByEmployeeAndDate(emp, date);

        DailyAttendanceReport report = new DailyAttendanceReport();
        report.setDate(date);

        if (optionalAttendance.isPresent()) {
            Attendance attendance = optionalAttendance.get();
            report.setStatus(attendance.isHoliday() ? "Holiday" :
                    attendance.isPresent() ? (attendance.isHalfDay() ? "Half-day" : "Present")
                            : "Absent");
            report.setClockIn(attendance.getClockIn());
            report.setClockOut(attendance.getClockOut());
            report.setAdminRemarks(attendance.getAdminRemarks());
            report.setTotalHours(attendance.getTotalHours());
            report.setOvertimeEnabled(attendance.isOvertimeAllowed());
        } else {
            report.setStatus("No Data");
        }

        return report;
    }

    // DTO for API response
    public static class DailyAttendanceReport {
        private LocalDate date;
        private String status;
        private java.time.LocalDateTime clockIn;
        private java.time.LocalDateTime clockOut;
        private String adminRemarks;
        private Double totalHours;
        private Boolean overtimeEnabled;

        // Getters and Setters
        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public java.time.LocalDateTime getClockIn() { return clockIn; }
        public void setClockIn(java.time.LocalDateTime clockIn) { this.clockIn = clockIn; }
        public java.time.LocalDateTime getClockOut() { return clockOut; }
        public void setClockOut(java.time.LocalDateTime clockOut) { this.clockOut = clockOut; }
        public String getAdminRemarks() { return adminRemarks; }
        public void setAdminRemarks(String adminRemarks) { this.adminRemarks = adminRemarks; }
        public Double getTotalHours() { return totalHours; }
        public void setTotalHours(Double totalHours) { this.totalHours = totalHours; }
        public Boolean getOvertimeEnabled() { return overtimeEnabled; }
        public void setOvertimeEnabled(Boolean overtimeEnabled) { this.overtimeEnabled = overtimeEnabled; }
    }
}
