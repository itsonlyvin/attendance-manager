package com.attendance.fin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Entity
@Table (name = "employee_ids")
@Getter
@Setter
public class EmployeeId {

    @Id
    private String employeeId;

    @Column(nullable = false)
    private boolean isRegistered = false;

    public EmployeeId(String employeeId, boolean isRegistered) {
        this.employeeId = employeeId;
        this.isRegistered = isRegistered;
    }

    public EmployeeId() {
    }


}

