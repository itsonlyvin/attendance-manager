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
@Table (name = "admin_ids")
@Getter
@Setter
public class AdminId {

    @Id
    private String adminId;


    @Column(nullable = false)
    private boolean isRegistered = false;


    public AdminId(String adminId, boolean isRegistered) {
        this.adminId = adminId;
        this.isRegistered = isRegistered;
    }

    public AdminId() {
    }
}

