package com.attendance.fin.controller;


import com.attendance.fin.service.impl.AttendanceDailyDetailService;
import com.attendance.fin.service.impl.AttendanceMonthlyDetailService;
import com.attendance.fin.service.impl.AttendanceReportService;
import com.attendance.fin.service.impl.AttendanceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceDailyDetailService dailyService;
    private final AttendanceService attendanceService;
    private final AttendanceReportService reportService;
    private final AttendanceMonthlyDetailService attendanceMonthlyDetailService;
    public AttendanceController(AttendanceService attendanceService,
                                AttendanceReportService reportService, AttendanceMonthlyDetailService attendanceMonthlyDetailService, AttendanceDailyDetailService dailyService) {
        this.attendanceService = attendanceService;
        this.reportService = reportService;
        this.attendanceMonthlyDetailService = attendanceMonthlyDetailService;
        this.dailyService = dailyService;
    }

    @PostMapping("/in/{employeeId}")
    public ResponseEntity<?> markIn(@PathVariable String employeeId,
                                    @RequestParam double latitude,
                                    @RequestParam double longitude,
                                    @RequestParam String qrCode) {

        return ResponseEntity.ok(
                attendanceService.markIn(employeeId, LocalDateTime.now(), latitude, longitude, qrCode)
        );
    }

    @PostMapping("/out/{employeeId}")
    public ResponseEntity<?> markOut(@PathVariable String employeeId,
                                     @RequestParam double latitude,
                                     @RequestParam double longitude,
                                     @RequestParam String qrCode) {

        return ResponseEntity.ok(
                attendanceService.markOut(employeeId, LocalDateTime.now(), latitude, longitude, qrCode)
        );
    }

    @PostMapping("/admin/override")
    public ResponseEntity<?> adminOverrideByEmp(
            @RequestParam String employeeId,
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam int day,
            @RequestParam boolean allowOvertime,
            @RequestParam boolean isPresent,
            @RequestParam(required = false) String remarks,
            @RequestParam(required = false) String clockIn,   // format: "yyyy-MM-ddTHH:mm"
            @RequestParam(required = false) String clockOut   // format: "yyyy-MM-ddTHH:mm"
    ) {
        java.time.LocalDateTime clockInTime = null;
        java.time.LocalDateTime clockOutTime = null;

        if (clockIn != null && !clockIn.isEmpty()) {
            clockInTime = java.time.LocalDateTime.parse(clockIn);
        }
        if (clockOut != null && !clockOut.isEmpty()) {
            clockOutTime = java.time.LocalDateTime.parse(clockOut);
        }

        return ResponseEntity.ok(
                attendanceService.adminOverrideByEmp(
                        employeeId,
                        year,
                        month,
                        day,
                        allowOvertime,
                        isPresent,
                        remarks,
                        clockInTime,
                        clockOutTime
                )
        );
    }



    @GetMapping("/report/{employeeId}")
    public ResponseEntity<?> getMonthlyReport(@PathVariable String employeeId,
                                              @RequestParam int year,
                                              @RequestParam int month) {

        var report = reportService.generateMonthlyReport(employeeId, year, month);
        return ResponseEntity.ok(report);
    }


    @GetMapping("/admin/monthly-attendance")
    public ResponseEntity<?> getMonthlyAttendance(@RequestParam String employeeId,
                                                  @RequestParam int year,
                                                  @RequestParam int month) {

        List<AttendanceMonthlyDetailService.DailyAttendance> monthly =
                attendanceMonthlyDetailService.getMonthlyAttendance(employeeId, year, month).getDays();

        return ResponseEntity.ok(monthly);
    }


    @DeleteMapping("/delete/{employeeId}")
    public ResponseEntity<String> deleteAttendance(
            @PathVariable String employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        attendanceService.deleteAttendance(employeeId, date);
        return ResponseEntity.ok("Attendance deleted successfully for " + date);
    }




    @GetMapping("/daily/{employeeId}")
    public AttendanceDailyDetailService.DailyAttendanceReport getDailyAttendance(
            @PathVariable String employeeId,
            @RequestParam String date) {

        try {
            LocalDate attendanceDate = LocalDate.parse(date);
            return dailyService.getDailyAttendance(employeeId, attendanceDate);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format. Use yyyy-MM-dd");
        }
    }

}
