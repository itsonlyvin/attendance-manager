package com.attendance.fin.service.impl;

import com.attendance.fin.model.Attendance;
import com.attendance.fin.model.Employee;
import com.attendance.fin.repository.AttendanceRepository;
import com.attendance.fin.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AutoLeaveService {

    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;

    /**
     * Runs every day at 12.00 PM
     *  Marks absent for employees who didn't mark in by 12:00
     */
    @Scheduled(cron = "0 0 12 * * ?") //
    public void markAbsentForMissedIn() {
        LocalDate today = LocalDate.now();
        List<Employee> employees = employeeRepository.findAll();

        for (Employee emp : employees) {
            boolean alreadyMarked = attendanceRepository.existsByEmployeeAndDate(emp, today);
            if (!alreadyMarked) {
                Attendance absent = new Attendance();
                absent.setEmployee(emp);
                absent.setDate(today);
                absent.setPresent(false);
                absent.setHalfDay(false);
                absent.setOvertimeAllowed(false);
                absent.setAdminRemarks("Auto-marked absent (did not mark in before 12:00 PM)");
                absent.setHoliday(false);
                // Optional: set default shift times
//                absent.setShiftStart(emp.isFinOpenArms() ? LocalTime.of(9,30) : LocalTime.of(9,0));
//                absent.setShiftEnd(emp.isFinOpenArms() ? LocalTime.of(17,30) : LocalTime.of(17,0));

                attendanceRepository.save(absent);
            }
        }
    }
}
