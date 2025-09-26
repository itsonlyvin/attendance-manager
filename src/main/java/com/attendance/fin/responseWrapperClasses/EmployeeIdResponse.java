package com.attendance.fin.responseWrapperClasses;


import com.attendance.fin.model.EmployeeId;

public class EmployeeIdResponse {
    private String message;
    private EmployeeId data;

    public EmployeeIdResponse(String message, EmployeeId data) {
        this.message = message;
        this.data = data;
    }

    // Getters and setters
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public EmployeeId getData() {
        return data;
    }

    public void setData(EmployeeId data) {
        this.data = data;
    }
}
