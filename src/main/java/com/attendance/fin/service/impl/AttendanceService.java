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
import java.util.Optional;

@Service
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final QrCodeService qrCodeService; // ✅ Inject QR validation service

    public AttendanceService(AttendanceRepository attendanceRepository,
                             EmployeeRepository employeeRepository,
                             QrCodeService qrCodeService) {
        this.attendanceRepository = attendanceRepository;
        this.employeeRepository = employeeRepository;
        this.qrCodeService = qrCodeService;
    }

    // Distance threshold in meters (for location validation)
    private static final double DISTANCE_THRESHOLD_METERS = 10.0;
    private static final int LATE_LIMIT_MINUTES = 10;

    /**
     * Employee marks clock-in
     */
    public Attendance markIn(String employeeId, LocalDateTime clockInTime, double lat, double lon, String qrCode) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));

        LocalDate today = clockInTime.toLocalDate();

        // Check if already clocked in today
        Attendance attendance = attendanceRepository.findByEmployeeAndDate(employee, today)
                .orElse(new Attendance());

        attendance.setEmployee(employee);
        attendance.setDate(today);
        attendance.setClockIn(clockInTime);
        attendance.setLatitude(lat);
        attendance.setLongitude(lon);
        attendance.setQrCodeIn(qrCode);

        // Shift timings
        LocalTime shiftStart = employee.isFinOpenArms() ? LocalTime.of(9, 0) : LocalTime.of(9, 30);
        LocalTime shiftEnd   = employee.isFinOpenArms() ? LocalTime.of(17, 0) : LocalTime.of(17, 30);

        attendance.setShiftStart(shiftStart);
        attendance.setShiftEnd(shiftEnd);

        // Calculate how late the employee is
        Duration lateDuration = Duration.between(shiftStart, clockInTime.toLocalTime());

        if (lateDuration.toMinutes() >= 4 * 60) {
            // 4 hours late or more => cannot mark in
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Cannot mark in after 4 hours from shift start");
        } else if (lateDuration.toMinutes() > 10) {
            // More than 10 minutes late => auto half-day
            attendance.setHalfDay(true);
            attendance.setLate(true);
        } else if (lateDuration.toMinutes() > 0) {
            // Late but within 10 min => mark as late only
            attendance.setLate(true);
            attendance.setHalfDay(false);
        } else {
            // On-time
            attendance.setLate(false);
            attendance.setHalfDay(false);
        }

        attendance.setPresent(true);
        attendance.setOvertimeAllowed(false); // default, admin can override
        attendance.setAdminRemarks(attendance.getAdminRemarks()); // keep existing remarks if any

        return attendanceRepository.save(attendance);
    }








    /**
     * Employee marks clock-out
     */
    public Attendance markOut(String employeeId, LocalDateTime clockOutTime, double lat, double lon, String qrCode) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        LocalDate date = clockOutTime.toLocalDate();

        Attendance attendance = attendanceRepository.findByEmployeeAndDate(employee, date)
                .orElseThrow(() -> new RuntimeException("You need to mark IN first"));

        // ✅ Validate QR code (OUT QR only)
        if (!qrCodeService.validateQr(qrCode, false)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired OUT QR Code");
        }

        // Validate location
        if (!isWithinCompanyLocation(lat, lon)) {
            throw new RuntimeException("You are not within company location to mark out");
        }

        attendance.setClockOut(clockOutTime);
        attendance.setQrCodeOut(qrCode);
        attendance.setLatitude(lat);
        attendance.setLongitude(lon);

        // Calculate total hours
        Duration worked = Duration.between(attendance.getClockIn(), clockOutTime);
        double hoursWorked = worked.toMinutes() / 60.0;

        // If late, must make up time (max 8 hours)
        if (attendance.isLate() && !attendance.isHalfDay()) {
            LocalTime shiftStart = attendance.getShiftStart();
            double lateMinutes = Duration.between(shiftStart, attendance.getClockIn().toLocalTime()).toMinutes();
            hoursWorked = Math.min(hoursWorked + (lateMinutes / 60.0), 8.0);
        }

        // If overtime > 8, cap unless admin allows
        if (hoursWorked > 8.0 && !attendance.isOvertimeAllowed()) {
            hoursWorked = 8.0;
        }

        attendance.setTotalHours(hoursWorked);

        return attendanceRepository.save(attendance);
    }

    /**
     * Admin override to allow overtime or mark absent/present
     */
    public Attendance adminOverrideByEmp(String employeeId, int year, int month, int day,
                                         boolean allowOvertime, boolean isPresent, boolean halfDay, String remarks) {

        LocalDate date = LocalDate.of(year, month, day);

        Employee emp = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        // Try to get existing attendance OR create a new one
        Attendance attendance = attendanceRepository.findByEmployeeAndDate(emp, date)
                .orElseGet(() -> {
                    Attendance newAttendance = new Attendance();
                    newAttendance.setEmployee(emp);
                    newAttendance.setDate(date);
                    return newAttendance;
                });

        // Apply admin overrides
        attendance.setOvertimeAllowed(allowOvertime);
        attendance.setPresent(isPresent);
        attendance.setHalfDay(halfDay);
        attendance.setAdminRemarks(remarks);

        return attendanceRepository.save(attendance);
    }




    /**
     * Validate if employee is within company location
     */
    private boolean isWithinCompanyLocation(double lat, double lon) {
        // Example: company location fixed coordinates
        double companyLat = 12.9716; // replace with actual
        double companyLon = 77.5946; // replace with actual

        double distance = distanceInMeters(lat, lon, companyLat, companyLon);
        return distance <= DISTANCE_THRESHOLD_METERS;
    }

    /**
     * Calculate distance between two points in meters
     */
    private double distanceInMeters(double lat1, double lon1, double lat2, double lon2) {
        int R = 6371000; // radius of Earth in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
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
