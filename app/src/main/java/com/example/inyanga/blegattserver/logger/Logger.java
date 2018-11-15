package com.example.inyanga.blegattserver.logger;

public class Logger {
    private LoggerCallback loggerCallback;



    public Logger(LoggerCallback loggerCallback) {
        this.loggerCallback = loggerCallback;
    }

    public void log(String msg, boolean newLine, int emptyLines) {
        if (loggerCallback == null) throw new NullPointerException("Logger Callback does not set");
        for (int i = 0; i <= emptyLines; i++) {
            loggerCallback.onLog("", true);
        }

        loggerCallback.onLog(msg, newLine);
    }

    public void setStatus(String status) {
        loggerCallback.onStatus(status);
    }

    public void startUpdatingStatus() {
        loggerCallback.statusUpdate();
    }


}
