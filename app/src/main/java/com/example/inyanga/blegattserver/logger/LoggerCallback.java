package com.example.inyanga.blegattserver.logger;

public interface LoggerCallback {
    void onLog(String msg, boolean newLine);
    void onStatus(String status);
    void statusUpdate();
}
