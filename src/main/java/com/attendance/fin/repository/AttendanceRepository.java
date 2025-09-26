package com.attendance.fin.repository;

import com.attendance.fin.model.Attendance;
import com.attendance.fin.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {





    List<Attendance> findAllByEmployeeAndDateBetween(Employee employee, LocalDate start, LocalDate end);

    List<Attendance> findAllByDate(LocalDate date);


    Optional<Attendance> findByEmployeeAndDate(Employee employee, LocalDate date);

    List<Attendance> findByEmployeeAndDateBetween(Employee employee, LocalDate startDate, LocalDate endDate);

    boolean existsByEmployeeAndDate(Employee emp, LocalDate date);


    Optional<Object> findTopByEmployeeAndDateBeforeOrderByDateDesc(Employee employee, LocalDate today);

    boolean existsByIsHolidayTrueAndDate(LocalDate datePointer);

    List<Attendance> findByIsHolidayTrueAndDateBetween(LocalDate start, LocalDate end);

    List<Attendance> findByDate(LocalDate holidayDate);
}
