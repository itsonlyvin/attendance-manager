package com.attendance.fin.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CronController {
    @GetMapping("/cron")
    public ResponseEntity<String> runTask() {
        // put your scheduled task logic here
        return ResponseEntity.ok("Cron task executed!");
    }
}
