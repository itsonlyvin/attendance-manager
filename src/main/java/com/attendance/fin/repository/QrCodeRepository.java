package com.attendance.fin.repository;

import com.attendance.fin.model.QrCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface QrCodeRepository extends JpaRepository<QrCode, Long> {

    // Find the most recent active QR
    Optional<QrCode> findFirstByStartTimeBeforeAndEndTimeAfterOrderByStartTimeDesc(
            LocalDateTime start,
            LocalDateTime end
    );

    // Find active QR based on inQr flag
    Optional<QrCode> findByInQrAndStartTimeBeforeAndEndTimeAfter(
            boolean inQr,
            LocalDateTime start,
            LocalDateTime end
    );

    // Just find any active QR
    Optional<QrCode> findFirstByStartTimeBeforeAndEndTimeAfter(
            LocalDateTime start,
            LocalDateTime end
    );

    Optional<QrCode> findFirstByOrderByStartTimeDesc();
}
