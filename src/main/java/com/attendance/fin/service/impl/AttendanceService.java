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
     * Mark IN
     */
    public Attendance markIn(String employeeId, LocalDateTime clockInTime, double lat, double lon, String qrCode) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));

        LocalDate today = clockInTime.toLocalDate();

        if (!qrCodeService.validateQr(qrCode, true)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired IN QR Code");
        }

        if (!isWithinCompanyLocation(lat, lon)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not within company location to mark IN");
        }

        Attendance attendance = attendanceRepository.findByEmployeeAndDate(employee, today)
                .orElse(null);

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

        // Lateness calculation
        Duration lateDuration = Duration.between(shiftStart, clockInTime.toLocalTime());
        if (lateDuration.toMinutes() >= 4 * 60) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot mark in after 4 hours from shift start");
        } else if (lateDuration.toMinutes() > 10) {
            attendance.setHalfDay(true);
            attendance.setLate(true);
        } else if (lateDuration.toMinutes() > 0) {
            attendance.setLate(true);
            attendance.setHalfDay(false);
        } else {
            attendance.setLate(false);
            attendance.setHalfDay(false);
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

        if (!qrCodeService.validateQr(qrCode, false)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired OUT QR Code");
        }

        if (!isWithinCompanyLocation(lat, lon)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not within company location to mark OUT");
        }

        Duration workedSoFar = Duration.between(attendance.getClockIn(), clockOutTime);
        if (workedSoFar.toHours() < 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot clock out before 4 hours of work");
        }

        attendance.setClockOut(clockOutTime);
        attendance.setQrCodeOut(qrCode);
        attendance.setLatitude(lat);
        attendance.setLongitude(lon);

        double hoursWorked = workedSoFar.toMinutes() / 60.0;

        if (attendance.isLate() && !attendance.isHalfDay()) {
            LocalTime shiftStart = attendance.getShiftStart();
            double lateMinutes = Duration.between(shiftStart, attendance.getClockIn().toLocalTime()).toMinutes();
            hoursWorked = Math.min(hoursWorked + (lateMinutes / 60.0), 8.0);
        }

        if (hoursWorked > 8.0 && !attendance.isOvertimeAllowed()) {
            hoursWorked = 8.0;
        }

        attendance.setTotalHours(hoursWorked);

        return attendanceRepository.save(attendance);
    }

    // ✅ Utility method for location validation
    private boolean isWithinCompanyLocation(double lat, double lon) {
        // Replace with real implementation
        double companyLat = 8.506960; // example  8.506960, 76.933847
        double companyLon =76.933847; // example
        double maxDistanceMeters = 10.0;

        return distanceInMeters(lat, lon, companyLat, companyLon) <= maxDistanceMeters;
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




//    /**
//     * Validate if employee is within company location
//     */
//    private boolean isWithinCompanyLocation(double lat, double lon) {
//        // Example: company location fixed coordinates
//        double companyLat = 12.9716; // replace with actual
//        double companyLon = 77.5946; // replace with actual
//
//        double distance = distanceInMeters(lat, lon, companyLat, companyLon);
//        return distance <= DISTANCE_THRESHOLD_METERS;
//    }
//
//    /**
//     * Calculate distance between two points in meters
//     */
//    private double distanceInMeters(double lat1, double lon1, double lat2, double lon2) {
//        int R = 6371000; // radius of Earth in meters
//        double dLat = Math.toRadians(lat2 - lat1);
//        double dLon = Math.toRadians(lon2 - lon1);
//        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
//                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
//                        Math.sin(dLon/2) * Math.sin(dLon/2);
//        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
//        return R * c;
//    }


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
