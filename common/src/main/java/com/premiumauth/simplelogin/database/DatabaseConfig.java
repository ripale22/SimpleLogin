package com.premiumauth.simplelogin.database;

public interface DatabaseConfig {
    String getDatabaseType();
    String getSqliteFile();
    String getMysqlHost();
    int getMysqlPort();
    String getMysqlDatabase();
    String getMysqlUsername();
    String getMysqlPassword();
    boolean getMysqlUseSSL();
    boolean getMysqlRequireSSL();
    int getDatabasePoolSize();
}
