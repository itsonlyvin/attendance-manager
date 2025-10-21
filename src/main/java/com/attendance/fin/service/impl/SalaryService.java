package com.attendance.fin.service.impl;

import com.attendance.fin.model.Attendance;
import com.attendance.fin.model.Employee;
import com.attendance.fin.repository.AttendanceRepository;
import com.attendance.fin.repository.EmployeeRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SalaryService {

    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;

    public SalaryService(EmployeeRepository employeeRepository, AttendanceRepository attendanceRepository) {
        this.employeeRepository = employeeRepository;
        this.attendanceRepository = attendanceRepository;
    }

    public List<EmployeeSalary> generateSalaryForAllEmployees(int year, int month) {
        List<Employee> employees = employeeRepository.findAll();
        List<EmployeeSalary> salaryList = new ArrayList<>();

        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        // Fetch all holidays once
        Set<LocalDate> holidays = attendanceRepository.findByIsHolidayTrueAndDateBetween(start, end)
                .stream()
                .map(Attendance::getDate)
                .collect(Collectors.toSet());

        // Process each employee
        for (Employee emp : employees) {
            List<Attendance> monthlyRecords = attendanceRepository.findByEmployeeAndDateBetween(emp, start, end);
            double totalSalary = calculateSalary(emp, monthlyRecords, holidays, year, month);
            salaryList.add(new EmployeeSalary(emp.getFullName(), totalSalary));
        }

        return salaryList;
    }

    private double calculateSalary(Employee emp, List<Attendance> monthlyRecords, Set<LocalDate> holidays, int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        int totalDaysInMonth = yearMonth.lengthOfMonth();

        double dailySalary = emp.getSalary() / totalDaysInMonth;
        double perHourRate = dailySalary / 8.0;
        boolean paidLeaveUsed = false;
        double totalSalary = 0.0;

        // Group attendances by date
        Map<LocalDate, List<Attendance>> attendanceByDate = monthlyRecords.stream()
                .collect(Collectors.groupingBy(Attendance::getDate));

        // Iterate through each day
        for (LocalDate date = yearMonth.atDay(1); !date.isAfter(yearMonth.atEndOfMonth()); date = date.plusDays(1)) {
            double daySalary = 0.0;
            List<Attendance> records = attendanceByDate.getOrDefault(date, Collections.emptyList());

            if (records.isEmpty()) {
                if (holidays.contains(date)) {
                    daySalary = dailySalary;
                } else if (!paidLeaveUsed) {
                    daySalary = dailySalary;
                    paidLeaveUsed = true;
                }
            } else {
                // Pick the latest attendance record
                Attendance a = records.stream()
                        .max(Comparator.comparing(Attendance::getClockIn, Comparator.nullsLast(Comparator.naturalOrder())))
                        .orElse(records.get(0));

                double hoursWorked = a.getTotalHours() != null ? a.getTotalHours() : (a.isHalfDay() ? 4.0 : 8.0);

                if (a.isHoliday()) {
                    daySalary = dailySalary;
                } else if (!a.isPresent()) {
                    if (!paidLeaveUsed) {
                        daySalary = dailySalary;
                        paidLeaveUsed = true;
                    }
                } else if (a.isHalfDay()) {
                    daySalary = Math.max((hoursWorked - 4) * perHourRate, 0);
                } else {
                    daySalary = hoursWorked * perHourRate;

                    if (a.isOvertimeAllowed() && hoursWorked > 8.0) {
                        daySalary += (hoursWorked - 8.0) * perHourRate;
                    }
                }
            }

            totalSalary += daySalary;
        }

        totalSalary += emp.getBonus();
        return totalSalary;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    public static class EmployeeSalary {
        private String employeeName;
        private double salary;
    }
}
