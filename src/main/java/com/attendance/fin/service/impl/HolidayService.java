package com.attendance.fin.service.impl;

import com.attendance.fin.model.Attendance;
import com.attendance.fin.model.Employee;
import com.attendance.fin.repository.AttendanceRepository;
import com.attendance.fin.repository.EmployeeRepository;
import com.attendance.fin.responseWrapperClasses.HolidayDTO;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class HolidayService {

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    /**
     * ✅ Runs at midnight on the first day of every month
     * Cron: second, minute, hour, day, month, weekday
     * "0 0 0 1 * *" -> At 00:00 on day 1 of every month
     */
    @Scheduled(cron = "0 0 0 1 * *")
    public void generateSundayHolidaysForNewMonth() {
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();

        generateSundayHolidays(year, month);
    }

    /**
     * Generates all Sundays for the given month and marks them as holidays for all employees
     */
    public void generateSundayHolidays(int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        List<Employee> employees = employeeRepository.findAll();

        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            LocalDate date = LocalDate.of(year, month, day);

            // Check if the day is Sunday
            if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                for (Employee emp : employees) {
                    // Avoid duplicates
                    if (attendanceRepository.existsByEmployeeAndDate(emp, date)) {
                        continue;
                    }

                    Attendance attendance = new Attendance();
                    attendance.setEmployee(emp);
                    attendance.setDate(date);
                    attendance.setHoliday(true);
                    attendance.setPresent(false);
                    attendance.setAdminRemarks("Sunday Holiday");

                    attendanceRepository.save(attendance);
                }
            }
        }
    }


    public void markHolidayForAll(LocalDate holidayDate, String reason) {
        List<Employee> employees = employeeRepository.findAll();

        for (Employee emp : employees) {
            Attendance attendance = attendanceRepository.findByEmployeeAndDate(emp, holidayDate)
                    .orElseGet(() -> {
                        Attendance newAttendance = new Attendance();
                        newAttendance.setEmployee(emp);
                        newAttendance.setDate(holidayDate);
                        return newAttendance;
                    });

            attendance.setHoliday(true);
            attendance.setPresent(false);
            attendance.setAdminRemarks("Holiday: " + reason);

            attendanceRepository.save(attendance);
        }
    }



    @Transactional
    public void manageHoliday(LocalDate holidayDate, String newReason) {

        List<Employee> employees = employeeRepository.findAll();
        LocalDate today = LocalDate.now();

        for (Employee emp : employees) {
            Attendance attendance = attendanceRepository.findByEmployeeAndDate(emp, holidayDate)
                    .orElse(null);

            if (holidayDate.isAfter(today)) {
                // ✅ Future date → delete holiday if exists
                if (attendance != null && attendance.isHoliday()) {
                    attendanceRepository.delete(attendance);
                }
            } else {
                // ✅ Past or Today → mark/update holiday
                if (attendance == null) {
                    // Create new attendance
                    attendance = new Attendance();
                    attendance.setEmployee(emp);
                    attendance.setDate(holidayDate);
                }

                // Update or set holiday
                attendance.setHoliday(false);
                if(attendance.getClockIn()!= null){
                    attendance.setPresent(true);
                }else {
                    attendance.setPresent(false);

                }
                attendance.setAdminRemarks("Holiday: " + newReason);
                attendanceRepository.save(attendance);


            }
        }
    }




    public List<HolidayDTO> getHolidaysForMonth(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        List<Attendance> holidays = attendanceRepository.findByIsHolidayTrueAndDateBetween(start, end);

        return holidays.stream()
                .map(a -> new HolidayDTO(a.getDate(), "holiday", a.getAdminRemarks()))
                .collect(Collectors.toList());
    }



}





