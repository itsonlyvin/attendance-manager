package com.attendance.fin.responseWrapperClasses;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

@Data
@AllArgsConstructor
public class HolidayDTO {
    private LocalDate date;
    private String status;       // e.g., "holiday"
    private String adminRemarks; // e.g., "Onam"
}
