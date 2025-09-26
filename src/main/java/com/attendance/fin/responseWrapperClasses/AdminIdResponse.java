package com.attendance.fin.responseWrapperClasses;


import com.attendance.fin.model.AdminId;

public class AdminIdResponse {
    private String message;
    private AdminId data;

    public AdminIdResponse(String message, AdminId data) {
        this.message = message;
        this.data = data;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public AdminId getData() {
        return data;
    }

    public void setData(AdminId data) {
        this.data = data;
    }
}
