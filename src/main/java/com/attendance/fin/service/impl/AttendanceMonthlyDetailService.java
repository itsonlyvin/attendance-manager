package com.attendance.fin.service.impl;

import com.attendance.fin.model.Attendance;
import com.attendance.fin.model.Employee;
import com.attendance.fin.repository.AttendanceRepository;
import com.attendance.fin.repository.EmployeeRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
public class AttendanceMonthlyDetailService {

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;

    public AttendanceMonthlyDetailService(AttendanceRepository attendanceRepository,
                                          EmployeeRepository employeeRepository) {
        this.attendanceRepository = attendanceRepository;
        this.employeeRepository = employeeRepository;
    }

    public MonthlyAttendanceReport getMonthlyAttendance(String employeeId, int year, int month) {
        Employee emp = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        // Fetch all attendance records for that employee in the month
        List<Attendance> attendances = attendanceRepository.findByEmployeeAndDateBetween(emp, startDate, endDate);

        List<DailyAttendance> monthlyAttendance = new ArrayList<>();

        int presentCount = 0;
        int absentCount = 0;
        int halfDayCount = 0;
        int holidayCount = 0;
        int paidLeaveCount = 0;

        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            LocalDate date = yearMonth.atDay(day);
            Attendance attendance = attendances.stream()
                    .filter(a -> a.getDate().equals(date))
                    .findFirst()
                    .orElse(null);

            DailyAttendance daily = new DailyAttendance();
            daily.setDate(date);

            if (attendance == null) {
                daily.setStatus("No Data");
            } else if (attendance.isHoliday()) {
                daily.setStatus("Holiday");
                holidayCount++;
            } else if (attendance.isHalfDay()) {
                daily.setStatus("Half-day");
                halfDayCount++;
            } else if (attendance.isPresent()) {
                daily.setStatus("Present");
                presentCount++;
            } else {
                daily.setStatus("Absent");
                absentCount++;
            }

            // ✅ Fill extended details if record exists
            if (attendance != null) {
                daily.setClockIn(attendance.getClockIn());
                daily.setClockOut(attendance.getClockOut());
                daily.setAdminRemarks(attendance.getAdminRemarks());
                daily.setTotalHours(attendance.getTotalHours());
                daily.setOvertimeEnabled(attendance.isOvertimeAllowed());
            }

            monthlyAttendance.add(daily);
        }

        // Prepare summary
        MonthlyAttendanceReport report = new MonthlyAttendanceReport();
        report.setDays(monthlyAttendance);
        report.setTotalPresent(presentCount);
        report.setTotalAbsent(absentCount);
        report.setTotalHalfDay(halfDayCount);
        report.setTotalHoliday(holidayCount);
        report.setTotalPaidLeave(paidLeaveCount);
        report.setTotalWorkingDays(yearMonth.lengthOfMonth() - holidayCount);

        return report;
    }

    // --- DTO Classes ---
    public static class DailyAttendance {
        private LocalDate date;
        private String status;
        private LocalDateTime clockIn;
        private LocalDateTime clockOut;
        private String adminRemarks;
        private Double totalHours;
        private Boolean overtimeEnabled;

        // ✅ Derived human-readable duration
        public String getWorkedDurationFormatted() {
            if (totalHours == null) return null;

            int hours = totalHours.intValue();
            int minutes = (int) Math.round((totalHours - hours) * 60);

            if (minutes == 60) { // edge case
                hours += 1;
                minutes = 0;
            }

            return String.format("%dh %02dm", hours, minutes);
        }

        // --- Getters & Setters ---
        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public LocalDateTime getClockIn() { return clockIn; }
        public void setClockIn(LocalDateTime clockIn) { this.clockIn = clockIn; }

        public LocalDateTime getClockOut() { return clockOut; }
        public void setClockOut(LocalDateTime clockOut) { this.clockOut = clockOut; }

        public String getAdminRemarks() { return adminRemarks; }
        public void setAdminRemarks(String adminRemarks) { this.adminRemarks = adminRemarks; }

        public Double getTotalHours() { return totalHours; }
        public void setTotalHours(Double totalHours) { this.totalHours = totalHours; }

        public Boolean getOvertimeEnabled() { return overtimeEnabled; }
        public void setOvertimeEnabled(Boolean overtimeEnabled) { this.overtimeEnabled = overtimeEnabled; }
    }

    public static class MonthlyAttendanceReport {
        private List<DailyAttendance> days;
        private int totalPresent;
        private int totalAbsent;
        private int totalHalfDay;
        private int totalHoliday;
        private int totalPaidLeave;
        private int totalWorkingDays;

        public List<DailyAttendance> getDays() { return days; }
        public void setDays(List<DailyAttendance> days) { this.days = days; }

        public int getTotalPresent() { return totalPresent; }
        public void setTotalPresent(int totalPresent) { this.totalPresent = totalPresent; }

        public int getTotalAbsent() { return totalAbsent; }
        public void setTotalAbsent(int totalAbsent) { this.totalAbsent = totalAbsent; }

        public int getTotalHalfDay() { return totalHalfDay; }
        public void setTotalHalfDay(int totalHalfDay) { this.totalHalfDay = totalHalfDay; }

        public int getTotalHoliday() { return totalHoliday; }
        public void setTotalHoliday(int totalHoliday) { this.totalHoliday = totalHoliday; }

        public int getTotalPaidLeave() { return totalPaidLeave; }
        public void setTotalPaidLeave(int totalPaidLeave) { this.totalPaidLeave = totalPaidLeave; }

        public int getTotalWorkingDays() { return totalWorkingDays; }
        public void setTotalWorkingDays(int totalWorkingDays) { this.totalWorkingDays = totalWorkingDays; }
    }
}
