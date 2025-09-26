package com.attendance.fin.service.impl;

import com.attendance.fin.model.Attendance;
import com.attendance.fin.model.Employee;
import com.attendance.fin.repository.AttendanceRepository;
import com.attendance.fin.repository.EmployeeRepository;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class AttendanceReportService {

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;

    public AttendanceReportService(AttendanceRepository attendanceRepository, EmployeeRepository employeeRepository) {
        this.attendanceRepository = attendanceRepository;
        this.employeeRepository = employeeRepository;
    }

    public AttendanceReport generateMonthlyReport(String employeeId, int year, int month) {
        Employee emp = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        int totalDaysInMonth = yearMonth.lengthOfMonth();

        int presentDays = 0;
        int halfDays = 0;
        int absentDays = 0;
        int lateDays = 0;
        int paidLeaveCount = 0; // includes holidays + admin-approved leaves + first free leave
        int holidayCount = 0;
        boolean paidLeaveUsed = false; // track first leave

        double totalHoursWorked = 0.0;
        double totalOvertimeHours = 0.0;
        double overtimePay = 0.0;

        double dailySalary = emp.getSalary() / totalDaysInMonth;
        double hourlySalary = dailySalary / 8.0;

        List<DailyAttendance> dailyList = new ArrayList<>();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            Optional<Attendance> records = attendanceRepository.findByEmployeeAndDate(emp, date);

            DailyAttendance daily = new DailyAttendance();
            daily.setDate(date);

            if (records.isEmpty()) {
                // No attendance record
                boolean isHoliday = attendanceRepository.existsByIsHolidayTrueAndDate(date);
                if (isHoliday) {
                    holidayCount++;
                    paidLeaveCount++;
                    daily.setStatus("Holiday");
                    daily.setHoliday(true);
                } else {
                    if (!paidLeaveUsed) {
                        paidLeaveUsed = true;
                        paidLeaveCount++;
                        daily.setStatus("Paid Leave (Auto)");
                    } else {
                        absentDays++;
                        daily.setStatus("Absent");
                    }
                }
                dailyList.add(daily);
                continue;
            }

            // Pick latest record if multiple exist
            Attendance a = records.stream()
                    .max(Comparator.comparing(Attendance::getClockIn, Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElse(records.get());

            daily.setClockIn(a.getClockIn());
            daily.setClockOut(a.getClockOut());
            daily.setHalfDay(a.isHalfDay());
            daily.setLate(a.isLate());
            daily.setOvertimeAllowed(a.isOvertimeAllowed());
            daily.setAdminRemarks(a.getAdminRemarks());

            if (a.isHoliday()) {
                holidayCount++;
                paidLeaveCount++;
                daily.setStatus("Holiday");
                daily.setHoliday(true);
            } else if (!a.isPresent()) {
                if (!paidLeaveUsed) {
                    paidLeaveUsed = true;
                    paidLeaveCount++;
                    daily.setStatus("Paid Leave (Auto)");
                } else {
                    absentDays++;
                    daily.setStatus("Absent");
                }
            } else if (a.isHalfDay()) {
                halfDays++;
                daily.setStatus("Half-day");
            } else {
                presentDays++;
                daily.setStatus("Present");
            }

            if (a.isLate() && !a.isHalfDay()) {
                lateDays++;
            }

            if (a.getTotalHours() != null) {
                totalHoursWorked += a.getTotalHours();
                if (a.isOvertimeAllowed() && a.getTotalHours() > 8.0) {
                    double overtimeHours = a.getTotalHours() - 8.0;
                    totalOvertimeHours += overtimeHours;
                    overtimePay += hourlySalary * overtimeHours;
                }
            }

            dailyList.add(daily);
        }

        double salaryEarned = (dailySalary * presentDays)
                + (dailySalary * 0.5 * halfDays)
                + (dailySalary * paidLeaveCount) // includes holidays + first leave
                + emp.getBonus()
                + overtimePay;

        LocalDate today = LocalDate.now();
        int daysLeft = 0;
        if (today.getYear() == year && today.getMonthValue() == month) {
            daysLeft = endDate.getDayOfMonth() - today.getDayOfMonth();
        }

        AttendanceReport report = new AttendanceReport();
        report.setEmployeeId(employeeId);
        report.setEmployeeName(emp.getFullName());
        report.setMonth(month);
        report.setYear(year);
        report.setTotalDays(totalDaysInMonth);
        report.setDaysLeft(daysLeft);
        report.setPresentDays(presentDays);
        report.setHalfDays(halfDays);
        report.setAbsentDays(absentDays);
        report.setLateDays(lateDays);
        report.setPaidLeave(paidLeaveCount);
        report.setHolidayCount(holidayCount);
        report.setTotalHoursWorked(totalHoursWorked);
        report.setSalaryEarned(salaryEarned);
        report.setBonusEarned(emp.getBonus());
        report.setTotalOvertimeHours(totalOvertimeHours);
        report.setOvertimePay(overtimePay);
        report.setDailyAttendance(dailyList);

        return report;
    }

    @Getter
    @Setter
    public static class AttendanceReport {
        private String employeeId;
        private String employeeName;
        private int month;
        private int year;
        private int totalDays;
        private int daysLeft;
        private int presentDays;
        private int halfDays;
        private int absentDays;
        private int lateDays;
        private int paidLeave; // includes holidays + first free leave
        private int holidayCount;
        private double totalHoursWorked;
        private double salaryEarned;
        private double bonusEarned;
        private double totalOvertimeHours;
        private double overtimePay;
        private List<DailyAttendance> dailyAttendance;
    }

    @Getter
    @Setter
    public static class DailyAttendance {
        private LocalDate date;
        private String status; // Present, Half-day, Absent, Holiday, Paid Leave (Auto)
        private boolean halfDay;
        private boolean late;
        private boolean holiday;
        private boolean overtimeAllowed;
        private String adminRemarks;
        private java.time.LocalDateTime clockIn;
        private java.time.LocalDateTime clockOut;
    }
}
