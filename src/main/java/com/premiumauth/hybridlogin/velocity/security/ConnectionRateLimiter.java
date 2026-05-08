package com.premiumauth.hybridlogin.velocity.security;

import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ConnectionRateLimiter {

    private final Map<String, Queue<Long>> ipAttempts = new ConcurrentHashMap<>();
    private final Map<String, Queue<Long>> nameAttempts = new ConcurrentHashMap<>();
    private final Map<String, Long> blockedIps = new ConcurrentHashMap<>();

    public boolean recordAndIsBlocked(String ip, String username, int maxPerIp, int windowSeconds, int cooldownSeconds, int maxNameAttempts) {
        long now = System.currentTimeMillis();
        Long blockedUntil = blockedIps.get(ip);
        if (blockedUntil != null && blockedUntil > now) {
            return true;
        }
        blockedIps.remove(ip);

        boolean ipExceeded = record(ipAttempts, ip, maxPerIp, windowSeconds, now);
        boolean nameExceeded = record(nameAttempts, username.toLowerCase(), maxNameAttempts, windowSeconds, now);
        if (ipExceeded || nameExceeded) {
            blockedIps.put(ip, now + cooldownSeconds * 1000L);
            return true;
        }
        return false;
    }

    public void cleanup(int windowSeconds) {
        long now = System.currentTimeMillis();
        cleanupQueues(ipAttempts, windowSeconds, now);
        cleanupQueues(nameAttempts, windowSeconds, now);
        blockedIps.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private boolean record(Map<String, Queue<Long>> attempts, String key, int maxAttempts, int windowSeconds, long now) {
        Queue<Long> queue = attempts.computeIfAbsent(key, ignored -> new ConcurrentLinkedQueue<>());
        prune(queue, windowSeconds, now);
        queue.add(now);
        return queue.size() > maxAttempts;
    }

    private void cleanupQueues(Map<String, Queue<Long>> attempts, int windowSeconds, long now) {
        Iterator<Map.Entry<String, Queue<Long>>> iterator = attempts.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Queue<Long>> entry = iterator.next();
            prune(entry.getValue(), windowSeconds, now);
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
    }

    private void prune(Queue<Long> queue, int windowSeconds, long now) {
        long cutoff = now - windowSeconds * 1000L;
        while (!queue.isEmpty() && queue.peek() < cutoff) {
            queue.poll();
        }
    }
}
