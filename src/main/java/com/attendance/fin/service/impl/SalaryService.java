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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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

        for (Employee emp : employees) {
            double totalSalary = calculateSalary(emp, year, month);
            salaryList.add(new EmployeeSalary(emp.getFullName(), totalSalary));
        }

        return salaryList;
    }

    private double calculateSalary(Employee emp, int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        int totalDaysInMonth = yearMonth.lengthOfMonth();

        double dailySalary = emp.getSalary() / totalDaysInMonth;
        double perHourRate = dailySalary / 8.0;
        boolean paidLeaveUsed = false;
        double totalSalary = 0.0;

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            Optional<Attendance> records = attendanceRepository.findByEmployeeAndDate(emp, date);
            double daySalary = 0.0;

            if (records.isEmpty()) {
                boolean isHoliday = attendanceRepository.existsByIsHolidayTrueAndDate(date);
                if (isHoliday || !paidLeaveUsed) {
                    daySalary = dailySalary;
                    if (!isHoliday) paidLeaveUsed = true;
                } // else Absent, daySalary = 0
            } else {
                Attendance a = records.stream()
                        .max(Comparator.comparing(Attendance::getClockIn, Comparator.nullsLast(Comparator.naturalOrder())))
                        .orElse(records.get());

                double hoursWorked = a.getTotalHours() != null ? a.getTotalHours() : (a.isHalfDay() ? 4.0 : 8.0);

                if (a.isHoliday()) {
                    daySalary = dailySalary;
                } else if (!a.isPresent()) {
                    if (!paidLeaveUsed) {
                        daySalary = dailySalary;
                        paidLeaveUsed = true;
                    }
                } else if (a.isHalfDay()) {
                    daySalary = Math.max((hoursWorked - 4) * perHourRate, 0); // half-day rule
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
