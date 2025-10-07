package com.attendance.fin.service.impl;

import com.attendance.fin.model.Attendance;
import com.attendance.fin.model.Employee;
import com.attendance.fin.repository.AttendanceRepository;
import com.attendance.fin.repository.EmployeeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.List;

@Service
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;

    // Distance threshold in meters (for location validation)
    private static final double DISTANCE_THRESHOLD_METERS = 10.0;
    private static final int LATE_LIMIT_MINUTES = 5;

    private static final String FIXED_QR = "FIXEDQR123"; // same for IN and OUT

    public AttendanceService(AttendanceRepository attendanceRepository, EmployeeRepository employeeRepository) {
        this.attendanceRepository = attendanceRepository;
        this.employeeRepository = employeeRepository;
    }

    /**
     * Mark IN
     */
    public Attendance markIn(String employeeId, LocalDateTime clockInTime, double lat, double lon, String qrCode) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));

        LocalDate today = clockInTime.toLocalDate();

        // ✅ Validate fixed QR
        if (!qrCode.equals(FIXED_QR)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid QR Code");
        }

        // ✅ Validate location
        if (!isWithinCompanyLocation(lat, lon)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not within company location to mark IN");
        }

        // Fetch existing attendance or create new
        Attendance attendance = attendanceRepository.findByEmployeeAndDate(employee, today).orElse(null);
        if (attendance != null && attendance.getClockIn() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Already clocked in today");
        }
        if (attendance == null) {
            attendance = new Attendance();
            attendance.setEmployee(employee);
            attendance.setDate(today);
        }

        attendance.setClockIn(clockInTime);
        attendance.setLatitude(lat);
        attendance.setLongitude(lon);
        attendance.setQrCodeIn(qrCode);

        // Shift timings
        LocalTime shiftStart = employee.isFinOpenArms() ? LocalTime.of(9, 30) : LocalTime.of(9, 0);
        LocalTime shiftEnd = employee.isFinOpenArms() ? LocalTime.of(17, 30) : LocalTime.of(17, 0);
        attendance.setShiftStart(shiftStart);
        attendance.setShiftEnd(shiftEnd);

        // ✅ Lateness calculation
        Duration lateDuration = Duration.between(shiftStart, clockInTime.toLocalTime());
        if (lateDuration.toMinutes() >= 4 * 60) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot mark in after 4 hours from shift start");
        } else if (lateDuration.toMinutes() > LATE_LIMIT_MINUTES) {
            // More than 5 mins late → Half day
            attendance.setHalfDay(true);
            attendance.setLate(true);
        } else if (lateDuration.toMinutes() > 0) {
            // Between 1 and 5 mins late → Late only
            attendance.setLate(true);
            attendance.setHalfDay(false);
        } else {
            // On time → Not late, not half-day
            attendance.setHalfDay(false);
            attendance.setLate(false);
        }

        attendance.setPresent(true);
        attendance.setOvertimeAllowed(false);
        return attendanceRepository.save(attendance);
    }

    /**
     * Mark OUT
     */
    public Attendance markOut(String employeeId, LocalDateTime clockOutTime, double lat, double lon, String qrCode) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));

        LocalDate date = clockOutTime.toLocalDate();

        Attendance attendance = attendanceRepository.findByEmployeeAndDate(employee, date)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "You need to mark IN first"));

        if (attendance.getClockOut() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Already clocked out today");
        }

        // Validate QR
        if (!qrCode.equals(FIXED_QR)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid QR Code");
        }

        // Validate location
        if (!isWithinCompanyLocation(lat, lon)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not within company location to mark OUT");
        }

        // Worked duration
        Duration workedSoFar = Duration.between(attendance.getClockIn(), clockOutTime);
        if (workedSoFar.toMinutes() < 4 * 60) { // cannot work less than 4 hours
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot clock out before 4 hours of work");
        }

        attendance.setClockOut(clockOutTime);
        attendance.setQrCodeOut(qrCode);
        attendance.setLatitude(lat);
        attendance.setLongitude(lon);

        // Total hours in decimals
        double hoursWorked = workedSoFar.toMinutes() / 60.0;
        attendance.setTotalHours(hoursWorked);

        // If late > 5 mins → Half-day
        if (attendance.isLate() && !attendance.isHalfDay()) {
            LocalTime shiftStart = attendance.getShiftStart();
            double lateMinutes = Duration.between(shiftStart, attendance.getClockIn().toLocalTime()).toMinutes();
            hoursWorked = Math.min(hoursWorked + (lateMinutes / 60.0), 8.0);
        }

        attendance.setTotalHours(hoursWorked);

        return attendanceRepository.save(attendance);
    }

    // ✅ Utility method for location validation
    private boolean isWithinCompanyLocation(double lat, double lon) {
        // Replace with real implementation
        double companyLat = 8.506960;
        double companyLon = 76.933847;
        return distanceInMeters(lat, lon, companyLat, companyLon) <= DISTANCE_THRESHOLD_METERS;
    }

    private double distanceInMeters(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // Earth radius in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /**
     * Admin override
     */
    public Attendance adminOverrideByEmp(
            String employeeId,
            int year,
            int month,
            int day,
            boolean allowOvertime,
            boolean isPresent,
            boolean halfDay,
            String remarks,
            java.time.LocalDateTime clockIn,
            java.time.LocalDateTime clockOut
    ) {

        LocalDate date = LocalDate.of(year, month, day);

        // Fetch the employee
        Employee emp = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        // Fetch existing attendance or create new
        Attendance attendance = attendanceRepository.findByEmployeeAndDate(emp, date)
                .orElseGet(() -> {
                    Attendance newAttendance = new Attendance();
                    newAttendance.setEmployee(emp);
                    newAttendance.setDate(date);
                    return newAttendance;
                });

        // Update attendance details
        attendance.setOvertimeAllowed(allowOvertime);
        attendance.setPresent(isPresent); // explicitly set
        attendance.setHalfDay(halfDay); // explicitly set half-day if passed
        attendance.setAdminRemarks(remarks);

        // Update clock-in/out if provided
        if (clockIn != null) {
            attendance.setClockIn(clockIn);
        }
        if (clockOut != null) {
            attendance.setClockOut(clockOut);
        }

        // Recalculate total hours and adjust half-day/presence
        if (attendance.getClockIn() != null && attendance.getClockOut() != null) {
            double hoursWorked = java.time.Duration.between(attendance.getClockIn(), attendance.getClockOut()).toMinutes() / 60.0;
            attendance.setTotalHours(hoursWorked);

            // If not explicitly half-day, automatically set based on hours
            if (!halfDay) {
                attendance.setHalfDay(hoursWorked < 8.0);
            }

            // If explicitly marked present/absent, honor that
            if (!isPresent) {
                attendance.setHalfDay(false);
                attendance.setTotalHours(0.0);
            }
        }

        return attendanceRepository.save(attendance);
    }

    /**
     * generateMonthlyReport
     */
    public MonthlyAttendanceReport generateMonthlyReport(Employee employee, YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();

        List<Attendance> attendances = attendanceRepository.findAllByEmployeeAndDateBetween(employee, start, end);

        double totalHours = attendances.stream().mapToDouble(a -> a.getTotalHours() != null ? a.getTotalHours() : 0).sum();
        long totalLateDays = attendances.stream().filter(Attendance::isLate).count();
        long totalHalfDays = attendances.stream().filter(Attendance::isHalfDay).count();
        long totalHolidays = attendances.stream().filter(Attendance::isHoliday).count();
        long totalPresentDays = attendances.stream().filter(Attendance::isPresent).count();

        return new MonthlyAttendanceReport(employee.getEmployeeId(), month, totalPresentDays, totalLateDays, totalHalfDays, totalHolidays, totalHours);
    }

    public static class MonthlyAttendanceReport {
        public String employeeId;
        public YearMonth month;
        public long presentDays;
        public long lateDays;
        public long halfDays;
        public long holidays;
        public double totalHours;

        public MonthlyAttendanceReport(String employeeId, YearMonth month, long presentDays, long lateDays, long halfDays, long holidays, double totalHours) {
            this.employeeId = employeeId;
            this.month = month;
            this.presentDays = presentDays;
            this.lateDays = lateDays;
            this.halfDays = halfDays;
            this.holidays = holidays;
            this.totalHours = totalHours;
        }
    }

    /**
     * delete attendance
     */
    public void deleteAttendance(String employeeId, LocalDate date) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));

        Attendance attendance = attendanceRepository.findByEmployeeAndDate(employee, date)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attendance not found for this date"));

        attendanceRepository.delete(attendance);
    }
}
