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
        int actualDaysInMonth = yearMonth.lengthOfMonth();

        //  Standard Accounting: Salary is always based on 30 days
        int totalDaysForSalary = 30;

        List<Attendance> allRecords = attendanceRepository.findByEmployeeAndDateBetween(emp, startDate, endDate);
        List<Attendance> allHolidays = attendanceRepository.findByIsHolidayTrueAndDateBetween(startDate, endDate);

        Map<LocalDate, List<Attendance>> attendanceByDate = allRecords.stream()
                .collect(Collectors.groupingBy(Attendance::getDate));

        // Define Shift Timings
        LocalTime shiftStart = emp.getShiftStart() != null ? emp.getShiftStart() : LocalTime.of(9, 0);
        LocalTime shiftEnd = emp.getShiftEnd() != null ? emp.getShiftEnd() : LocalTime.of(17, 0);
        double shiftHours = Duration.between(shiftStart, shiftEnd).toMinutes() / 60.0;

        // Salary Calculations
        double dailySalary = emp.getSalary() / totalDaysForSalary;
        double perHourRate = dailySalary / shiftHours;

        // Tolerance Configuration
        Duration tolerance = Duration.ofMinutes(5);

        // Report Counters
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

            // ---------------------------------------------------
            // CASE 1: NO RECORDS (Absent / Holiday / Auto Leave)
            // ---------------------------------------------------
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
                    paidLeaveUsed = true; // First absence is Paid Leave
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

            // ---------------------------------------------------
            // CASE 2: RECORDS EXIST (Present / Leave / etc)
            // ---------------------------------------------------
            // Get the earliest check-in (or max logic based on your previous code)
            Attendance a = dayRecords.stream()
                    .max(Comparator.comparing(Attendance::getClockIn, Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElse(dayRecords.get(0));

            daily.setClockIn(a.getClockIn());
            daily.setClockOut(a.getClockOut());
            daily.setHalfDay(a.isHalfDay());
            daily.setOvertimeAllowed(a.isOvertimeAllowed());
            daily.setAdminRemarks(a.getAdminRemarks());

            double daySalary = 0.0;

            // Handle Explicit Holidays/Leaves in records
            if (a.isHoliday()) {
                holidayCount++;
                paidLeaveCount++;
                daily.setStatus("Holiday");
                daily.setHoliday(true);
                daySalary = dailySalary;
            }
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
            // Handle PRESENT records
            else {
                // --- SUB-CASE: Manual Entry (No times) ---
                if (a.getClockIn() == null && a.getClockOut() == null) {
                    presentDays++;
                    daily.setStatus("Present (Manual)");
                    daySalary = dailySalary;
                }

                // --- SUB-CASE: No Clock Out -> ZERO PAY ---
                else if (a.getClockIn() != null && a.getClockOut() == null) {
                    presentDays++; // Count as present for attendance
                    noClockOutDays++;
                    daily.setStatus("No Clock-Out (Zero Pay)");
                    daySalary = 0.0; // STRICT RULE: 0 Salary
                }

                // --- SUB-CASE: Normal Logic (Clock In & Out exist) ---
                else {
                    LocalTime actualIn = a.getClockIn().toLocalTime();
                    LocalTime actualOut = a.getClockOut().toLocalTime();

                    // --- TOLERANCE LOGIC START ---
                    // Determine Effective Start Time
                    LocalTime effectiveIn = actualIn;
                    boolean isLate = false;

                    // Logic: If ActualIn is AFTER ShiftStart
                    if (actualIn.isAfter(shiftStart)) {
                        // Check if within 5 mins tolerance (e.g., 9:32 <= 9:35)
                        if (actualIn.isBefore(shiftStart.plus(tolerance).plusSeconds(1))) {
                            // WAIVE LATENESS: Snap time back to 9:30
                            effectiveIn = shiftStart;
                            isLate = false;
                        } else {
                            // REAL LATE: Use actual time (9:40 stays 9:40)
                            // Note: You can choose to use 'shiftStart' here too if you pay for full day despite lateness,
                            // but usually lateness affects worked hours calculation.
                            isLate = true;
                        }
                    } else {
                        // Early arrival (9:15) -> Count from 9:30
                        effectiveIn = shiftStart;
                    }

                    daily.setLate(isLate); // Set the flag for UI
                    // --- TOLERANCE LOGIC END ---

                    // Calculate Duration using EFFECTIVE In time
                    Duration workDuration = Duration.between(effectiveIn, actualOut);
                    double workedHours = Math.max(0, workDuration.toMinutes() / 60.0); // Prevent negative
                    totalHoursWorked += workedHours;

                    if (a.isHalfDay()) {
                        halfDays++;
                        daily.setStatus("Half-day");
                        double payableHours = Math.min(workedHours, shiftHours / 2);
                        daySalary = payableHours * perHourRate;
                    } else {
                        presentDays++;
                        daily.setStatus("Present");

                        double payableHours = Math.min(workedHours, shiftHours); // Cap normal hours

                        // Overtime Logic
                        if (a.isOvertimeAllowed() && actualOut.isAfter(shiftEnd)) {
                            double overtimeHours = Duration.between(shiftEnd, actualOut).toMinutes() / 60.0;
                            totalOvertimeHours += overtimeHours;

                            double overtimePay = overtimeHours * perHourRate; // 1x Rate
                            totalOvertimePay += overtimePay;

                            daySalary = dailySalary + overtimePay;
                        } else {
                            // Normal Day Pay (Capped at Daily Salary)
                            daySalary = Math.min(payableHours * perHourRate, dailySalary);
                        }
                    }
                }
            }

            daily.setSalary(daySalary);
            dailyList.add(daily);
        }

        // ---------------------------------------------------
        // FINAL TOTALS & 31-DAY ADJUSTMENT
        // ---------------------------------------------------

        double totalSalaryEarned = dailyList.stream()
                .mapToDouble(DailyAttendance::getSalary)
                .sum() + emp.getBonus();

        // Count effectively paid days (Present + Leaves + Holidays + HalfDays)
        double totalPaidDaysCount = presentDays + paidLeaveCount + holidayCount + (halfDays * 0.5);

        // SMART 31-DAY DEDUCTION:
        // Only deduct if it's a 31-day month AND the employee earned > 30 days pay.
        // This protects new joiners or unpaid leave takers from unfair deductions.
        if (actualDaysInMonth == 31 && totalPaidDaysCount > 30) {
            if (holidayCount > 0) {
                holidayCount--;             // Reduce 1 holiday from the count
                totalSalaryEarned -= dailySalary; // Reduce 1 day salary
            }
        }

        // Days left in current month (for current month report)
        LocalDate today = LocalDate.now();
        int daysLeft = 0;
        if (today.getYear() == year && today.getMonthValue() == month) {
            daysLeft = Math.max(0, endDate.getDayOfMonth() - today.getDayOfMonth());
        }

        AttendanceReport report = new AttendanceReport();
        report.setEmployeeId(employeeId);
        report.setEmployeeName(emp.getFullName());
        report.setMonth(month);
        report.setYear(year);
        report.setTotalDays(totalDaysForSalary);
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