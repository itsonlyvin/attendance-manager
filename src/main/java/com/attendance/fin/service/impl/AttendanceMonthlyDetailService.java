package com.attendance.fin.service.impl;

import com.attendance.fin.model.Attendance;
import com.attendance.fin.model.Employee;
import com.attendance.fin.repository.AttendanceRepository;
import com.attendance.fin.repository.EmployeeRepository;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

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

        // 1. Fetch Data & Holidays
        List<Attendance> allRecords = attendanceRepository.findByEmployeeAndDateBetween(emp, startDate, endDate);
        List<Attendance> allHolidays = attendanceRepository.findByIsHolidayTrueAndDateBetween(startDate, endDate);

        // Group by Date to handle multiple punches efficiently
        Map<LocalDate, List<Attendance>> attendanceByDate = allRecords.stream()
                .collect(Collectors.groupingBy(Attendance::getDate));

        // 2. Setup Configuration (Shift & Tolerance)
        LocalTime shiftStart = emp.getShiftStart() != null ? emp.getShiftStart() : LocalTime.of(9, 0);
        Duration tolerance = Duration.ofMinutes(5);

        List<DailyAttendance> monthlyAttendance = new ArrayList<>();

        // Counters
        int presentCount = 0;
        int absentCount = 0;
        int halfDayCount = 0;
        int holidayCount = 0;
        int paidLeaveCount = 0;
        int noClockOutCount = 0;
        boolean paidLeaveUsed = false; // To track the "Auto Paid Leave" logic

        // 3. Iterate through every day of the month
        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            LocalDate date = yearMonth.atDay(day);
            DailyAttendance daily = new DailyAttendance();
            daily.setDate(date);

            List<Attendance> dayRecords = attendanceByDate.getOrDefault(date, Collections.emptyList());

            // --- CASE A: NO DATA (Absent / Holiday) ---
            if (dayRecords.isEmpty()) {
                LocalDate finalDate = date;
                boolean isHoliday = allHolidays.stream().anyMatch(h -> h.getDate().equals(finalDate));

                if (isHoliday) {
                    daily.setStatus("Holiday");
                    holidayCount++;
                } else if (!paidLeaveUsed) {
                    // Logic: First non-holiday absence is Auto Paid Leave
                    paidLeaveUsed = true;
                    paidLeaveCount++;
                    daily.setStatus("Paid Leave (Auto)");
                } else {
                    daily.setStatus("Absent");
                    absentCount++;
                }
                monthlyAttendance.add(daily);
                continue;
            }

            // --- CASE B: DATA EXISTS ---
            // Get the relevant record (Earliest Clock In logic)
            Attendance a = dayRecords.stream()
                    .max(Comparator.comparing(Attendance::getClockIn, Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElse(dayRecords.get(0));

            daily.setClockIn(a.getClockIn());
            daily.setClockOut(a.getClockOut());
            daily.setAdminRemarks(a.getAdminRemarks());
            daily.setOvertimeEnabled(a.isOvertimeAllowed());

            // 1. Holiday Record
            if (a.isHoliday()) {
                daily.setStatus("Holiday");
                holidayCount++;
            }
            // 2. Leave Record (Present = false)
            else if (!a.isPresent()) {
                if (!paidLeaveUsed) {
                    paidLeaveUsed = true;
                    paidLeaveCount++;
                    daily.setStatus("Paid Leave (Auto)");
                } else {
                    daily.setStatus("Absent");
                    absentCount++;
                }
            }
            // 3. Present Record
            else {
                // Manual Entry Check
                if (a.getClockIn() == null && a.getClockOut() == null) {
                    daily.setStatus("Present (Manual)");
                    presentCount++;
                }
                // No Clock Out Check
                else if (a.getClockIn() != null && a.getClockOut() == null) {
                    daily.setStatus("No Clock-Out");
                    noClockOutCount++;
                    // Note: In detailed view, we often count them as present visually,
                    // but the salary service gives them 0 pay.
                    presentCount++;
                }
                // Normal Punch
                else {
                    LocalTime actualIn = a.getClockIn().toLocalTime();
                    LocalTime actualOut = a.getClockOut().toLocalTime();

                    // --- TOLERANCE LOGIC ---
                    LocalTime effectiveIn = actualIn;
                    boolean isLate = false;

                    if (actualIn.isAfter(shiftStart)) {
                        // If within 5 mins, snap to start time
                        if (actualIn.isBefore(shiftStart.plus(tolerance).plusSeconds(1))) {
                            effectiveIn = shiftStart;
                            isLate = false;
                        } else {
                            isLate = true;
                        }
                    } else {
                        effectiveIn = shiftStart; // Early arrival
                    }

                    daily.setLate(isLate); // Update DTO

                    // Calculate Hours based on EFFECTIVE time (matches Salary Report)
                    Duration workDuration = Duration.between(effectiveIn, actualOut);
                    double totalHours = Math.max(0, workDuration.toMinutes() / 60.0);
                    daily.setTotalHours(totalHours);

                    if (a.isHalfDay()) {
                        daily.setStatus("Half-day");
                        halfDayCount++;
                    } else {
                        daily.setStatus("Present");
                        presentCount++;
                    }
                }
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
        report.setTotalNoClockOut(noClockOutCount);

        // Working days usually excludes holidays
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
        private Boolean late; // Added this field for UI


        public String getWorkedDurationFormatted() {
            if (totalHours == null || totalHours == 0) return "-";

            int hours = totalHours.intValue();
            int minutes = (int) Math.round((totalHours - hours) * 60);

            if (minutes == 60) {
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

        public Boolean getLate() { return late; }
        public void setLate(Boolean late) { this.late = late; }
    }

    public static class MonthlyAttendanceReport {
        private List<DailyAttendance> days;
        private int totalPresent;
        private int totalAbsent;
        private int totalHalfDay;
        private int totalHoliday;
        private int totalPaidLeave;
        private int totalWorkingDays;
        private int totalNoClockOut; // Added for summary

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

        public int getTotalNoClockOut() { return totalNoClockOut; }
        public void setTotalNoClockOut(int totalNoClockOut) { this.totalNoClockOut = totalNoClockOut; }
    }
}