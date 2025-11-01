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

// ✅ Use actual shift duration (default to 9:00–17:00 if missing)
        LocalTime defaultShiftStart = emp.getShiftStart() != null ? emp.getShiftStart() : LocalTime.of(9, 0);
        LocalTime defaultShiftEnd = emp.getShiftEnd() != null ? emp.getShiftEnd() : LocalTime.of(17, 0);
        double shiftHours1 = Duration.between(defaultShiftStart, defaultShiftEnd).toMinutes() / 60.0;

        double dailySalary = emp.getSalary() / totalDaysInMonth;
        double perHourRate = dailySalary / shiftHours1;

        int presentDays = 0;
        int halfDays = 0;
        int absentDays = 0;
        int holidayCount = 0;
        int paidLeaveCount = 0;
        int noClockOutDays = 0;
        boolean paidLeaveUsed = false;
        double totalHoursWorked = 0.0;
        double totalOvertimeHours = 0.0;
        double overtimePay = 0.0;

        List<DailyAttendance> dailyList = new ArrayList<>();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            DailyAttendance daily = new DailyAttendance();
            daily.setDate(date);

            List<Attendance> dayRecords = attendanceByDate.getOrDefault(date, Collections.emptyList());

            // 🟠 No attendance found for this date
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

            // ✅ Get latest attendance of the day
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
            LocalTime shiftStart1 = a.getShiftStart() != null ? a.getShiftStart() : LocalTime.of(9, 0);
            LocalTime shiftEnd1 = a.getShiftEnd() != null ? a.getShiftEnd() : LocalTime.of(17, 0);
            double shiftHours = Duration.between(shiftStart1, shiftEnd1).toHours();


            if (a.getShiftStart() != null && a.getShiftEnd() != null) {
                shiftHours = Duration.between(a.getShiftStart(), a.getShiftEnd()).toHours();
            }

            // ✅ Handle holidays and presence
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

            } else {
                // ✅ Employee was marked present
                if (a.getClockIn() == null && a.getClockOut() == null) {
                    // 🟢 Admin manually marked as present
                    presentDays++;
                    daily.setStatus("Present");
                    daySalary = dailySalary;
                }
                else if (a.getClockIn() != null && a.getClockOut() == null) {
                    // 🟠 Employee forgot to clock out
                    presentDays++;
                    noClockOutDays++;
                    daily.setStatus("No Clock-Out");
                    daySalary = dailySalary; // ✅ still pay full-day salary
                }
                else if (a.isHalfDay()) {
                    // 🌗 Half-Day logic
                    halfDays++;
                    daily.setStatus("Half-day");

                    LocalTime shiftStart = a.getShiftStart() != null ? a.getShiftStart() : LocalTime.of(9, 0);
                    LocalTime shiftEnd = a.getShiftEnd() != null ? a.getShiftEnd() : LocalTime.of(17, 0);
                    double totalShiftHours = Duration.between(shiftStart, shiftEnd).toHours();
                    LocalTime halfDayStart = shiftStart.plusHours((long) (totalShiftHours / 2));
                    LocalTime halfDayEnd = shiftEnd;

                    double halfDayHours = Duration.between(halfDayStart, halfDayEnd).toMinutes() / 60.0;

                    double actualHoursWorked = Duration.between(
                            a.getClockIn().toLocalTime(),
                            a.getClockOut().toLocalTime()
                    ).toMinutes() / 60.0;

                    double payableHours = Math.min(actualHoursWorked, halfDayHours);
                    daySalary = payableHours * perHourRate;

                }
                else {
                    // ✅ Full Day normal case
                    presentDays++;
                    daily.setStatus("Present");

                    LocalTime shiftStart = a.getShiftStart() != null ? a.getShiftStart() : LocalTime.of(9, 0);
                    LocalTime shiftEnd = a.getShiftEnd() != null ? a.getShiftEnd() : LocalTime.of(17, 0);

                    double workedHours = Duration.between(
                            a.getClockIn().toLocalTime(),
                            a.getClockOut().toLocalTime()
                    ).toMinutes() / 60.0;

                    double payableHours = Math.min(workedHours, shiftHours);

                    // 💪 Add overtime if allowed
                    if (a.isOvertimeAllowed() && a.getClockOut().toLocalTime().isAfter(shiftEnd)) {
                        double overtimeHours = Duration.between(shiftEnd, a.getClockOut().toLocalTime()).toMinutes() / 60.0;
                        payableHours += overtimeHours;
                        totalOvertimeHours += overtimeHours;
                    }

                    daySalary = payableHours * perHourRate;
                }
            }

            daily.setSalary(daySalary);
            totalHoursWorked += hoursWorked;
            dailyList.add(daily);
        }

        // ✅ Total monthly summary
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
        report.setOvertimePay(overtimePay);
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
