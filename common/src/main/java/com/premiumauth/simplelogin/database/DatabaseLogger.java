package com.premiumauth.simplelogin.database;

public interface DatabaseLogger {
    void info(String msg);
    void warning(String msg);
    void severe(String msg, Throwable t);
}
