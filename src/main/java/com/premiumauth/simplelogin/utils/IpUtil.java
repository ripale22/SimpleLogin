package com.premiumauth.simplelogin.utils;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class IpUtil {

    public static String normalize(String rawIp) {
        if (rawIp == null || rawIp.isBlank()) return rawIp;
        try {
            String cleaned = rawIp.replace("%eth0", "").replace("%wlan0", "").replaceFirst("%\\d+$", "");
            InetAddress addr = InetAddress.getByName(cleaned);
            if (addr instanceof Inet6Address ipv6) {
                byte[] bytes = ipv6.getAddress();
                if (isIpv4MappedIpv6(bytes)) {
                    byte[] ipv4Bytes = new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]};
                    return InetAddress.getByAddress(ipv4Bytes).getHostAddress();
                }
                if (isLoopback(bytes)) return "127.0.0.1";
            }
            if (addr instanceof Inet4Address && addr.isLoopbackAddress()) return "127.0.0.1";
            return addr.getHostAddress();
        } catch (UnknownHostException e) {
            return rawIp;
        }
    }

    public static boolean matches(String storedIp, String currentIp) {
        if (storedIp == null || currentIp == null) return false;
        return normalize(storedIp).equals(normalize(currentIp));
    }

    private static boolean isIpv4MappedIpv6(byte[] bytes) {
        for (int i = 0; i < 10; i++) if (bytes[i] != 0) return false;
        return bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
    }

    private static boolean isLoopback(byte[] bytes) {
        for (int i = 0; i < 15; i++) if (bytes[i] != 0) return false;
        return bytes[15] == 1;
    }
}
