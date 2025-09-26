package com.attendance.fin.controller;

import com.attendance.fin.responseWrapperClasses.HolidayDTO;
import com.attendance.fin.service.impl.HolidayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/admin/holiday")
public class HolidayController {

    @Autowired
    private HolidayService holidayService;

    @PostMapping
    public ResponseEntity<String> markHoliday(
            @RequestParam String date,
            @RequestParam String reason) {

        LocalDate holidayDate = LocalDate.parse(date); // yyyy-MM-dd format
        holidayService.markHolidayForAll(holidayDate, reason);

        return ResponseEntity.ok("Holiday set for all employees on " + holidayDate + " (" + reason + ")");
    }


    @GetMapping("/{year}/{month}")
    public List<HolidayDTO> getHolidays(@PathVariable int year, @PathVariable int month) {
        return holidayService.getHolidaysForMonth(year, month);
    }

    // ✅ Mark, update, or delete holiday based on date logic
    @PutMapping("/manage")
    public ResponseEntity<String> manageHoliday(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String reason) {

        holidayService.manageHoliday(date, reason);
        return ResponseEntity.ok("Holiday management completed for " + date);
    }

}
