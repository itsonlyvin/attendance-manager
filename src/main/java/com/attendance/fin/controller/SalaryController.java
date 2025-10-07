package com.attendance.fin.controller;

import com.attendance.fin.service.impl.SalaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/salary")
public class SalaryController {

    private final SalaryService salaryService;

    public SalaryController(SalaryService salaryService) {
        this.salaryService = salaryService;
    }

    @GetMapping("/monthly/{year}/{month}")
    public List<SalaryService.EmployeeSalary> getMonthlySalary(@PathVariable int year, @PathVariable int month) {
        return salaryService.generateSalaryForAllEmployees(year, month);
    }
}
