//package com.attendance.fin.scheduled;
//
//import com.attendance.fin.model.QrCode;
//import com.attendance.fin.repository.QrCodeRepository;
//import org.springframework.beans.factory.annotation.Autowired;
//    import org.springframework.scheduling.annotation.EnableScheduling;
//    import org.springframework.scheduling.annotation.Scheduled;
//    import org.springframework.stereotype.Service;
//
//    import java.time.LocalDateTime;
//    import java.time.LocalTime;
//    import java.util.UUID;
//
//    @Service
//    @EnableScheduling
//    public class QrCodeScheduler {
//
//        @Autowired
//        private QrCodeRepository qrCodeRepository;
//
//        private String generateRandomCode() {
//            return UUID.randomUUID().toString().substring(0, 8);
//        }
//
//
//
//
//        // Generate IN QR at 8:45 AM daily
//        @Scheduled(cron = "0 45 8 * * *")
//            public void generateInQr() {
//            LocalDateTime now = LocalDateTime.now();
//            QrCode qr = new QrCode();
//            qr.setCode(generateRandomCode());
//            qr.setStartTime(now);
//            qr.setEndTime(LocalDateTime.of(now.toLocalDate(), LocalTime.of(12, 0)));
//            qr.setInQr(true);  // ✅ updated field
//            qrCodeRepository.save(qr);
//            System.out.println("IN QR generated: " + qr.getCode());
//        }
//
//        // Generate OUT QR at 12:00 PM daily  @Scheduled(cron = "0 */3 * * * *")
//        @Scheduled(cron = "0 0 12 * * *")
//        public void generateOutQr() {
//            LocalDateTime now = LocalDateTime.now();
//            QrCode qr = new QrCode();
//            qr.setCode(generateRandomCode());
//            qr.setStartTime(now);
//            qr.setEndTime(now.plusDays(1).withHour(8).withMinute(45));
//            qr.setInQr(false);  // ✅ updated field
//            qrCodeRepository.save(qr);
//            System.out.println("OUT QR generated: " + qr.getCode());
//        }
//
//    }
