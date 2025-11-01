package com.attendance.fin.service.impl;

import com.attendance.fin.model.Attendance;
import com.attendance.fin.model.Employee;
import com.attendance.fin.repository.AttendanceRepository;
import com.attendance.fin.repository.EmployeeRepository;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;

import java.time.*;
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

        // ✅ Default shift time
        LocalTime defaultShiftStart = emp.getShiftStart() != null ? emp.getShiftStart() : LocalTime.of(9, 0);
        LocalTime defaultShiftEnd = emp.getShiftEnd() != null ? emp.getShiftEnd() : LocalTime.of(17, 0);
        double shiftHours = Duration.between(defaultShiftStart, defaultShiftEnd).toMinutes() / 60.0;

        double dailySalary = emp.getSalary() / totalDaysInMonth;
        double perHourRate = dailySalary / shiftHours;
        Duration tolerance = Duration.ofMinutes(5); // ✅ 5-min tolerance

        int presentDays = 0;
        int halfDays = 0;
        int absentDays = 0;
        int holidayCount = 0;
        int paidLeaveCount = 0;
        int noClockOutDays = 0;
        boolean paidLeaveUsed = false;
        double totalHoursWorked = 0.0;
        double totalOvertimeHours = 0.0;
        double totalOvertimePay = 0.0;

        List<DailyAttendance> dailyList = new ArrayList<>();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            DailyAttendance daily = new DailyAttendance();
            daily.setDate(date);

            List<Attendance> dayRecords = attendanceByDate.getOrDefault(date, Collections.emptyList());

            // 🟠 No attendance found
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

            Attendance a = dayRecords.stream()
                    .max(Comparator.comparing(Attendance::getClockIn, Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElse(dayRecords.get(0));

            daily.setClockIn(a.getClockIn());
            daily.setClockOut(a.getClockOut());
            daily.setHalfDay(a.isHalfDay());
            daily.setLate(a.isLate());
            daily.setOvertimeAllowed(a.isOvertimeAllowed());
            daily.setAdminRemarks(a.getAdminRemarks());

            double daySalary = 0.0;
            double hoursWorked = a.getTotalHours() != null ? a.getTotalHours() : 0.0;

            LocalTime shiftStart = a.getShiftStart() != null ? a.getShiftStart() : defaultShiftStart;
            LocalTime shiftEnd = a.getShiftEnd() != null ? a.getShiftEnd() : defaultShiftEnd;
            double shiftHoursPerDay = Duration.between(shiftStart, shiftEnd).toMinutes() / 60.0;

            // ✅ Handle holidays
            if (a.isHoliday()) {
                holidayCount++;
                paidLeaveCount++;
                daily.setStatus("Holiday");
                daily.setHoliday(true);
                daySalary = dailySalary;
            }

            // ✅ Absent or Paid Leave
            else if (!a.isPresent()) {
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
            }

            // ✅ Present cases
            else {
                if (a.getClockIn() == null && a.getClockOut() == null) {
                    presentDays++;
                    daily.setStatus("Present");
                    daySalary = dailySalary;
                } else if (a.getClockIn() != null && a.getClockOut() == null) {
                    presentDays++;
                    noClockOutDays++;
                    daily.setStatus("No Clock-Out");
                    daySalary = dailySalary;
                } else {
                    // ✅ Calculate work duration
                    LocalTime inTime = a.getClockIn().toLocalTime();
                    LocalTime outTime = a.getClockOut().toLocalTime();

                    Duration workDuration = Duration.between(inTime, outTime);
                    double workedHours = workDuration.toMinutes() / 60.0;
                    totalHoursWorked += workedHours;

                    boolean isLateBeyondTolerance = inTime.isAfter(shiftStart.plus(tolerance));

                    // 🌗 Half-day logic
                    if (a.isHalfDay()) {
                        halfDays++;
                        daily.setStatus("Half-day");
                        double payableHours = Math.min(workedHours, shiftHoursPerDay / 2);
                        daySalary = payableHours * perHourRate;
                    }
                    // ✅ Full-day present
                    else {
                        presentDays++;
                        daily.setStatus("Present");

                        // Late tolerance check
                        if (isLateBeyondTolerance) {
                            daily.setLate(true);
                        }

                        double payableHours = Math.min(workedHours, shiftHoursPerDay);

                        // 💪 Handle overtime if allowed
                        if (a.isOvertimeAllowed() && outTime.isAfter(shiftEnd)) {
                            double overtimeHours = Duration.between(shiftEnd, outTime).toMinutes() / 60.0;
                            totalOvertimeHours += overtimeHours;
                            double overtimePay = overtimeHours * perHourRate * 1;
                            totalOvertimePay += overtimePay;
                            payableHours = shiftHoursPerDay; // keep base salary capped
                            daySalary = dailySalary + overtimePay;
                        } else {
                            // cap salary to full-day max
                            daySalary = Math.min(payableHours * perHourRate, dailySalary);
                        }
                    }
                }
            }

            daily.setSalary(daySalary);
            dailyList.add(daily);
        }

        // ✅ Total salary
        double totalSalaryEarned = dailyList.stream()
                .mapToDouble(DailyAttendance::getSalary)
                .sum() + emp.getBonus();

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
        report.setNoClockOutDays(noClockOutDays);
        report.setTotalHoursWorked(totalHoursWorked);
        report.setSalaryEarned(totalSalaryEarned);
        report.setBonusEarned(emp.getBonus());
        report.setTotalOvertimeHours(totalOvertimeHours);
        report.setOvertimePay(totalOvertimePay);
        report.setDailyAttendance(dailyList);

        return report;
    }

    // ============================ INNER CLASSES ============================

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
        private int noClockOutDays;
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
        private LocalDateTime clockIn;
        private LocalDateTime clockOut;
        private double salary;
    }
}
