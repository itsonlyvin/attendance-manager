package com.attendance.fin.controller;

import com.attendance.fin.model.QrCode;
import com.attendance.fin.service.impl.QrCodeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
public class QrCodeController {

    private final QrCodeService qrCodeService;

    public QrCodeController(QrCodeService qrCodeService) {
        this.qrCodeService = qrCodeService;
    }

    @GetMapping("/active-qr")
    public QrCode getActiveQr() {
        Optional<QrCode> activeQr = qrCodeService.getActiveQrCode();
        return activeQr.orElseThrow(() -> new RuntimeException("No active QR code found"));
    }
}