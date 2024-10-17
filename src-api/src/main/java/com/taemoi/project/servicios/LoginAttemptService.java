package com.taemoi.project.servicios;

public interface LoginAttemptService {
	void loginSucceeded(String key);

	void loginFailed(String key);

	boolean isBlocked(String key);
}
