package com.attendance.fin.service.impl;

import com.attendance.fin.model.Attendance;
import com.attendance.fin.model.Employee;
import com.attendance.fin.repository.AttendanceRepository;
import com.attendance.fin.repository.EmployeeRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;

import java.time.*;
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

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        // Fetch all holidays once
        List<Attendance> holidayRecords = attendanceRepository.findByIsHolidayTrueAndDateBetween(startDate, endDate);
        Set<LocalDate> holidays = holidayRecords.stream()
                .map(Attendance::getDate)
                .collect(Collectors.toSet());

        // Process each employee
        for (Employee emp : employees) {
            List<Attendance> records = attendanceRepository.findByEmployeeAndDateBetween(emp, startDate, endDate);
            double totalSalary = calculateEmployeeSalary(emp, records, holidays, year, month);
            salaryList.add(new EmployeeSalary(emp.getFullName(), totalSalary));
        }

        return salaryList;
    }

    private double calculateEmployeeSalary(Employee emp, List<Attendance> monthlyRecords, Set<LocalDate> holidays, int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        int actualDaysInMonth = yearMonth.lengthOfMonth();

        // ✅ Always use 30 days for salary calculation (not actual days)
        double perDayRate = emp.getSalary() / 30.0;

        // Default shift hours
        LocalTime shiftStart = emp.getShiftStart() != null ? emp.getShiftStart() : LocalTime.of(9, 0);
        LocalTime shiftEnd = emp.getShiftEnd() != null ? emp.getShiftEnd() : LocalTime.of(17, 0);
        double shiftHours = Duration.between(shiftStart, shiftEnd).toMinutes() / 60.0;
        Duration tolerance = Duration.ofMinutes(5);

        double dailySalary = perDayRate;
        double perHourRate = dailySalary / shiftHours;
        boolean paidLeaveUsed = false;

        double totalSalary = 0.0;
        double totalOvertimePay = 0.0;

        // Group attendances by date
        Map<LocalDate, List<Attendance>> attendanceByDate = monthlyRecords.stream()
                .collect(Collectors.groupingBy(Attendance::getDate));

        // Iterate through each day in the month
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            List<Attendance> dayRecords = attendanceByDate.getOrDefault(date, Collections.emptyList());
            double daySalary = 0.0;

            // 🟠 No attendance record
            if (dayRecords.isEmpty()) {
                if (holidays.contains(date)) {
                    daySalary = dailySalary; // Holiday = paid
                } else if (!paidLeaveUsed) {
                    paidLeaveUsed = true;
                    daySalary = dailySalary; // Paid leave
                } else {
                    daySalary = 0.0; // Absent
                }
                totalSalary += daySalary;
                continue;
            }

            // Get the latest attendance record (most relevant one)
            Attendance a = dayRecords.stream()
                    .max(Comparator.comparing(Attendance::getClockIn, Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElse(dayRecords.get(0));

            LocalTime empShiftStart = a.getShiftStart() != null ? a.getShiftStart() : shiftStart;
            LocalTime empShiftEnd = a.getShiftEnd() != null ? a.getShiftEnd() : shiftEnd;
            double shiftHoursPerDay = Duration.between(empShiftStart, empShiftEnd).toMinutes() / 60.0;

            // ✅ Holiday
            if (a.isHoliday()) {
                daySalary = dailySalary;
            }
            // ✅ Absent or paid leave
            else if (!a.isPresent()) {
                if (!paidLeaveUsed) {
                    paidLeaveUsed = true;
                    daySalary = dailySalary;
                } else {
                    daySalary = 0.0;
                }
            }
            // ✅ Present cases
            else {
                LocalDateTime in = a.getClockIn();
                LocalDateTime out = a.getClockOut();
                if (in == null && out == null) {
                    daySalary = dailySalary;
                } else if (in != null && out == null) {
                    daySalary = dailySalary;
                } else if (in != null && out != null) {
                    Duration workDuration = Duration.between(in.toLocalTime(), out.toLocalTime());
                    double workedHours = workDuration.toMinutes() / 60.0;

                    boolean isLateBeyondTolerance = in.toLocalTime().isAfter(empShiftStart.plus(tolerance));

                    // 🌗 Half-day logic
                    if (a.isHalfDay()) {
                        double payableHours = Math.min(workedHours, shiftHoursPerDay / 2);
                        daySalary = payableHours * perHourRate;
                    }
                    // ✅ Full-day present
                    else {
                        double payableHours = Math.min(workedHours, shiftHoursPerDay);

                        // 💪 Handle overtime
                        if (a.isOvertimeAllowed() && out.toLocalTime().isAfter(empShiftEnd)) {
                            double overtimeHours = Duration.between(empShiftEnd, out.toLocalTime()).toMinutes() / 60.0;
                            double overtimePay = overtimeHours * perHourRate * 1; // normal multiplier
                            totalOvertimePay += overtimePay;
                            daySalary = dailySalary + overtimePay;
                        } else {
                            daySalary = Math.min(payableHours * perHourRate, dailySalary);
                        }
                    }
                }
            }

            totalSalary += daySalary;
        }

        // ✅ Add bonus and overtime
        totalSalary += emp.getBonus();
        totalSalary += totalOvertimePay;

        // ✅ Deduct 1 day’s salary if the month has 31 days
        if (actualDaysInMonth == 31) {
            totalSalary -= perDayRate;
        }

        return totalSalary;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    public static class EmployeeSalary {
        private String employeeName;
        private double salary;
    }


    // in SalaryService class

    /**
     * Public wrapper: calculate salary for single employee id (uses existing private logic).
     */
    public double calculateEmployeeSalaryPublic(String employeeId, int year, int month) {
        Employee emp = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found: " + employeeId));

        List<Attendance> records = attendanceRepository.findByEmployeeAndDateBetween(
                emp,
                YearMonth.of(year, month).atDay(1),
                YearMonth.of(year, month).atEndOfMonth()
        );

        // holidays set
        List<Attendance> holidayRecords = attendanceRepository.findByIsHolidayTrueAndDateBetween(
                YearMonth.of(year, month).atDay(1),
                YearMonth.of(year, month).atEndOfMonth()
        );
        Set<LocalDate> holidays = holidayRecords.stream().map(Attendance::getDate).collect(Collectors.toSet());

        return calculateEmployeeSalary(emp, records, holidays, year, month);
    }

}
