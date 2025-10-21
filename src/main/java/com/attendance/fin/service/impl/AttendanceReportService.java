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
import java.util.*;
import java.util.stream.Collectors;

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

        List<Attendance> allRecords = attendanceRepository.findByEmployeeAndDateBetween(emp, startDate, endDate);
        List<Attendance> allHolidays = attendanceRepository.findByIsHolidayTrueAndDateBetween(startDate, endDate);

        Map<LocalDate, List<Attendance>> attendanceByDate = allRecords.stream()
                .collect(Collectors.groupingBy(Attendance::getDate));

        double dailySalary = emp.getSalary() / totalDaysInMonth;
        double perHourRate = dailySalary / 8.0;

        int presentDays = 0;
        int halfDays = 0;
        int absentDays = 0;
        int holidayCount = 0;
        int paidLeaveCount = 0;
        boolean paidLeaveUsed = false;

        double totalHoursWorked = 0.0;
        double totalOvertimeHours = 0.0;
        double overtimePay = 0.0;

        List<DailyAttendance> dailyList = new ArrayList<>();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            DailyAttendance daily = new DailyAttendance();
            daily.setDate(date);

            List<Attendance> dayRecords = attendanceByDate.getOrDefault(date, Collections.emptyList());

            if (dayRecords.isEmpty()) {
                LocalDate finalDate = date;
                boolean isHoliday = allHolidays.stream().anyMatch(h -> h.getDate().equals(finalDate));

                if (isHoliday) {
                    holidayCount++;
                    paidLeaveCount++;
                    daily.setStatus("Holiday");
                    daily.setHoliday(true);
                    daily.setSalary(dailySalary);
                } else if (!paidLeaveUsed) {
                    paidLeaveUsed = true;
                    paidLeaveCount++;
                    daily.setStatus("Paid Leave (Auto)");
                    daily.setSalary(dailySalary);
                } else {
                    absentDays++;
                    daily.setStatus("Absent");
                    daily.setSalary(0.0);
                }

                dailyList.add(daily);
                continue;
            }

            // Find the latest attendance record for the day safely
            Attendance a = dayRecords.stream()
                    .max(Comparator.comparing(Attendance::getClockIn, Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElse(dayRecords.get(0));

            daily.setClockIn(a.getClockIn());
            daily.setClockOut(a.getClockOut());
            daily.setHalfDay(a.isHalfDay());
            daily.setLate(a.isLate());
            daily.setOvertimeAllowed(a.isOvertimeAllowed());
            daily.setAdminRemarks(a.getAdminRemarks());

            double hoursWorked = a.getTotalHours() != null ? a.getTotalHours() : (a.isHalfDay() ? 4.0 : 8.0);
            double daySalary;

            if (a.isHoliday()) {
                holidayCount++;
                paidLeaveCount++;
                daily.setStatus("Holiday");
                daily.setHoliday(true);
                daySalary = dailySalary;
            } else if (!a.isPresent()) {
                if (!paidLeaveUsed) {
                    paidLeaveUsed = true;
                    paidLeaveCount++;
                    daily.setStatus("Paid Leave (Auto)");
                    daySalary = dailySalary;
                } else {
                    absentDays++;
                    daily.setStatus("Absent");
                    daySalary = 0.0;
                }
            } else if (a.isHalfDay()) {
                halfDays++;
                daily.setStatus("Half-day");
                daySalary = Math.max((hoursWorked - 4) * perHourRate, 0);
            } else {
                presentDays++;
                daily.setStatus("Present");
                daySalary = hoursWorked * perHourRate;

                if (a.isOvertimeAllowed() && hoursWorked > 8.0) {
                    double overtimeHours = hoursWorked - 8.0;
                    totalOvertimeHours += overtimeHours;
                    overtimePay += overtimeHours * perHourRate;
                }
            }

            daily.setSalary(daySalary);
            totalHoursWorked += hoursWorked;
            dailyList.add(daily);
        }

        double totalSalaryEarned = dailyList.stream().mapToDouble(DailyAttendance::getSalary).sum()
                + emp.getBonus() + overtimePay;

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
        report.setPaidLeave(paidLeaveCount);
        report.setHolidayCount(holidayCount);
        report.setTotalHoursWorked(totalHoursWorked);
        report.setSalaryEarned(totalSalaryEarned);
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
        private int paidLeave;
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
        private String status;
        private boolean halfDay;
        private boolean late;
        private boolean holiday;
        private boolean overtimeAllowed;
        private String adminRemarks;
        private java.time.LocalDateTime clockIn;
        private java.time.LocalDateTime clockOut;
        private double salary;
    }
}
