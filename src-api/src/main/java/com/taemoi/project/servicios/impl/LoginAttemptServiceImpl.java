package com.taemoi.project.servicios.impl;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.taemoi.project.servicios.LoginAttemptService;

@Service
public class LoginAttemptServiceImpl implements LoginAttemptService {
    private final int MAX_ATTEMPTS = 5;
    private final long LOCK_TIME_DURATION = 15 * 60 * 1000; // 15 minutos

    private Map<String, Integer> attemptsCache = new HashMap<>();
    private Map<String, Long> lockTimeCache = new HashMap<>();

    public void loginSucceeded(String key) {
        attemptsCache.remove(key);
        lockTimeCache.remove(key);
    }

    public void loginFailed(String key) {
        int attempts = attemptsCache.getOrDefault(key, 0);
        attempts++;
        attemptsCache.put(key, attempts);
        if (attempts >= MAX_ATTEMPTS) {
            lockTimeCache.put(key, System.currentTimeMillis());
        }
    }

    public boolean isBlocked(String key) {
        if (lockTimeCache.containsKey(key)) {
            long lockTime = lockTimeCache.get(key);
            if ((System.currentTimeMillis() - lockTime) < LOCK_TIME_DURATION) {
                return true;
            } else {
                lockTimeCache.remove(key);
                attemptsCache.remove(key);
                return false;
            }
        }
        return false;
    }
}
