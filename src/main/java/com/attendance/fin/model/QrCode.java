package com.attendance.fin.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class QrCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean inQr;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public boolean isInQr() { return inQr; }
    public void setInQr(boolean inQr) { this.inQr = inQr; }
}
