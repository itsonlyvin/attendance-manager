package com.attendance.fin.service.impl;

import com.attendance.fin.model.QrCode;
import com.attendance.fin.repository.QrCodeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;


@Service
public class QrCodeService {

    @Autowired
    private QrCodeRepository qrCodeRepository;

    public boolean validateQr(String scannedCode, boolean inQr) {
        LocalDateTime now = LocalDateTime.now();
        return qrCodeRepository.findByInQrAndStartTimeBeforeAndEndTimeAfter(inQr, now, now)
                .map(qr -> qr.getCode().equals(scannedCode))
                .orElse(false);
    }



    // Get the currently active QR code
//    public Optional<QrCode> getActiveQrCode() {
//        LocalDateTime now = LocalDateTime.now();
//        return qrCodeRepository.findFirstByStartTimeBeforeAndEndTimeAfter(now, now);
//    }

    public Optional<QrCode> getActiveOrLastQr() {
        LocalDateTime now = LocalDateTime.now();

        // Try to find an active QR first
        Optional<QrCode> activeQr = qrCodeRepository.findFirstByStartTimeBeforeAndEndTimeAfter(now, now);
        if (activeQr.isPresent()) {
            return activeQr;
        } else {
            // Return the last generated QR regardless of current time
            return qrCodeRepository.findFirstByOrderByStartTimeDesc();
        }
    }


}
