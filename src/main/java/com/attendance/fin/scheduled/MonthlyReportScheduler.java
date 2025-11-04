package com.attendance.fin.scheduled;

import com.attendance.fin.model.Employee;
import com.attendance.fin.repository.EmployeeRepository;
import com.attendance.fin.service.impl.AttendanceReportService;
import com.attendance.fin.service.impl.SalaryService;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.property.TextAlignment;
import com.itextpdf.layout.property.UnitValue;
import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Attachments;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MonthlyReportScheduler {

    private final EmployeeRepository employeeRepository;
    private final SalaryService salaryService;
    private final AttendanceReportService attendanceReportService;

    private static final String HR_EMAIL = "openarmsautismschool@gmail.com";
    private static final String FROM_EMAIL = "openarmsautismschool@gmail.com";
    private static final String SENDGRID_API_KEY = System.getenv("SENDGRID_API_KEY");

    /**
     * ✅ Automatically run every month on the 1st at 7 AM IST
     */
    @Scheduled(cron = "0 0 7 1 * *", zone = "Asia/Kolkata")
    public void sendMonthlyReports() {
        System.out.println("🕒 Scheduled monthly report triggered...");
        generateAndSendReports();
    }

    /**
     * ✅ Automatically runs once after deployment/startup (for Render)
     */
    @PostConstruct
    public void sendReportOnStartup() {
        System.out.println("🚀 App started — sending monthly attendance reports automatically...");
        generateAndSendReports();
    }

    /**
     * ✅ Core function: generate and send both PDFs
     */
    private void generateAndSendReports() {
        LocalDate now = LocalDate.now();
        YearMonth previousMonth = YearMonth.from(now.minusMonths(1));
        int year = previousMonth.getYear();

        try {
            List<Employee> allEmployees = employeeRepository.findAll();

            // Split employees by finOpenArms flag
            List<Employee> finEmployees = allEmployees.stream()
                    .filter(Employee::isFinOpenArms)
                    .collect(Collectors.toList());

            List<Employee> openarmsEmployees = allEmployees.stream()
                    .filter(e -> !e.isFinOpenArms())
                    .collect(Collectors.toList());

            // Create PDFs
            byte[] finPdf = createReportPdfForGroup(finEmployees, previousMonth, "FIN");
            byte[] openarmsPdf = createReportPdfForGroup(openarmsEmployees, previousMonth, "Openarms");

            // Send email via SendGrid
            sendEmailWithAttachments(finPdf, openarmsPdf, previousMonth, year);

            System.out.println("✅ Monthly attendance reports sent successfully via SendGrid.");

        } catch (Exception ex) {
            System.err.println("❌ Error generating or sending monthly report: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * ✅ SendGrid email with two PDF attachments
     */
    private void sendEmailWithAttachments(byte[] finPdf, byte[] openarmsPdf, YearMonth month, int year) throws Exception {
        String subject = "Attendance Reports - FIN & Openarms (" +
                month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + year + ")";

        String body = "Dear HR,\n\n" +
                "Please find attached the attendance reports for both FIN and Openarms for " +
                month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + year + ".\n\n" +
                "Regards,\nOpenarms Attendance System";

        Email from = new Email(FROM_EMAIL, "Openarms Attendance System");
        Email to = new Email(HR_EMAIL);
        Content content = new Content("text/plain", body);
        Mail mail = new Mail(from, subject, to, content);

        // Attach FIN report
        Attachments finAttachment = new Attachments();
        finAttachment.setFilename("FIN_Attendance_Report_" + month + ".pdf");
        finAttachment.setType("application/pdf");
        finAttachment.setDisposition("attachment");
        finAttachment.setContent(Base64.getEncoder().encodeToString(finPdf));
        mail.addAttachments(finAttachment);

        // Attach Openarms report
        Attachments openarmsAttachment = new Attachments();
        openarmsAttachment.setFilename("Openarms_Attendance_Report_" + month + ".pdf");
        openarmsAttachment.setType("application/pdf");
        openarmsAttachment.setDisposition("attachment");
        openarmsAttachment.setContent(Base64.getEncoder().encodeToString(openarmsPdf));
        mail.addAttachments(openarmsAttachment);

        SendGrid sg = new SendGrid(SENDGRID_API_KEY);
        Request request = new Request();

        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sg.api(request);
            System.out.println("📧 SendGrid Response: " + response.getStatusCode());
        } catch (Exception e) {
            System.err.println("❌ SendGrid Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * ✅ Generate a detailed PDF for a specific employee group (FIN / Openarms)
     */
    private byte[] createReportPdfForGroup(List<Employee> employees, YearMonth month, String groupName) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);

        // Header
        Paragraph header = new Paragraph("OPENARMS — " + groupName + " Employee Attendance Report")
                .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD))
                .setFontSize(14)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(header);

        Paragraph sub = new Paragraph(month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear())
                .setFontSize(11)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(sub);

        document.add(new Paragraph("\n"));

        // Table
        Table table = new Table(UnitValue.createPercentArray(new float[]{3, 1, 1, 1, 1, 1, 1}))
                .useAllAvailableWidth();

        table.addHeaderCell(createHeaderCell("Employee Name"));
        table.addHeaderCell(createHeaderCell("Present"));
        table.addHeaderCell(createHeaderCell("Half"));
        table.addHeaderCell(createHeaderCell("Absent"));
        table.addHeaderCell(createHeaderCell("Total Hrs"));
        table.addHeaderCell(createHeaderCell("OT Hrs"));
        table.addHeaderCell(createHeaderCell("Salary (₹)"));

        double grandTotalSalary = 0.0;
        double grandTotalHours = 0.0;
        double grandTotalOvertime = 0.0;

        for (Employee emp : employees) {
            AttendanceReportService.AttendanceReport report =
                    attendanceReportService.generateMonthlyReport(emp.getEmployeeId(), month.getYear(), month.getMonthValue());

            double salary = salaryService.calculateEmployeeSalaryPublic(emp.getEmployeeId(), month.getYear(), month.getMonthValue());

            grandTotalSalary += salary;
            grandTotalHours += report.getTotalHoursWorked();
            grandTotalOvertime += report.getTotalOvertimeHours();

            table.addCell(createBodyCell(emp.getFullName()));
            table.addCell(createBodyCell(String.valueOf(report.getPresentDays())));
            table.addCell(createBodyCell(String.valueOf(report.getHalfDays())));
            table.addCell(createBodyCell(String.valueOf(report.getAbsentDays())));
            table.addCell(createBodyCell(String.format("%.2f", report.getTotalHoursWorked())));
            table.addCell(createBodyCell(String.format("%.2f", report.getTotalOvertimeHours())));
            table.addCell(createBodyCell(String.format("%.2f", salary)));
        }

        // Add total row
        Cell totalLabelCell = new Cell(1, 6)
                .add(new Paragraph("TOTAL"))
                .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                .setBold();
        table.addCell(totalLabelCell);

        table.addCell(new Cell()
                .add(new Paragraph(String.format("%.2f", grandTotalSalary)))
                .setBackgroundColor(ColorConstants.LIGHT_GRAY));

        document.add(table);
        document.add(new Paragraph("\n"));

        // Summary section
        Paragraph summary = new Paragraph()
                .add("Group: " + groupName + "\n")
                .add("Employees Count: " + employees.size() + "\n")
                .add("Total Hours (All Employees): " + String.format("%.2f", grandTotalHours) + "\n")
                .add("Total Overtime Hours: " + String.format("%.2f", grandTotalOvertime) + "\n")
                .add("Total Salary (₹): " + String.format("%.2f", grandTotalSalary) + "\n");

        document.add(summary);
        document.add(new Paragraph("\nGenerated on: " + LocalDate.now()).setFontSize(9));

        document.close();
        return baos.toByteArray();
    }

    private Cell createHeaderCell(String text) {
        return new Cell().add(new Paragraph(text))
                .setBackgroundColor(ColorConstants.GRAY)
                .setFontColor(ColorConstants.WHITE)
                .setBold();
    }

    private Cell createBodyCell(String text) {
        return new Cell().add(new Paragraph(text));
    }
}
