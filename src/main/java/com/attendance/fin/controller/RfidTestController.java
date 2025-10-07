//package com.attendance.fin.controller;
//
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//@RestController
//@RequestMapping("/api/attendance")
//public class RfidTestController {
//
//    @PostMapping("/rfid")
//    public ResponseEntity<String> receiveAttendance(@RequestBody AttendanceRequest request) {
//        System.out.println("===== ATTENDANCE POST RECEIVED =====");
//        System.out.println("Employee ID : " + request.getEmployeeId());
//        System.out.println("Latitude    : " + request.getLatitude());
//        System.out.println("Longitude   : " + request.getLongitude());
//        System.out.println("Device Type : " + request.getDeviceType());
//        System.out.println("Clock Type  : " + request.getClockType());
//        System.out.println("====================================");
//
//        // Differentiate handling
//        if ("rfid".equalsIgnoreCase(request.getDeviceType())) {
//            System.out.println("➡ Processing RFID card entry...");
//        } else if ("fingerprint".equalsIgnoreCase(request.getDeviceType())) {
//            System.out.println("➡ Processing Fingerprint entry...");
//        } else {
//            System.out.println("⚠ Unknown device type");
//        }
//
//        return ResponseEntity.ok("Data received successfully from " + request.getDeviceType());
//    }
//
//    public static class AttendanceRequest {
//        private String employeeId;
//        private double latitude;
//        private double longitude;
//        private String deviceType;   // "rfid" or "fingerprint"
//        private String clockType;    // "IN" or "OUT"
//
//        // Getters & setters
//        public String getEmployeeId() { return employeeId; }
//        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
//        public double getLatitude() { return latitude; }
//        public void setLatitude(double latitude) { this.latitude = latitude; }
//        public double getLongitude() { return longitude; }
//        public void setLongitude(double longitude) { this.longitude = longitude; }
//        public String getDeviceType() { return deviceType; }
//        public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
//        public String getClockType() { return clockType; }
//        public void setClockType(String clockType) { this.clockType = clockType; }
//    }
//}
