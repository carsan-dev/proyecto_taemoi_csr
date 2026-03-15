package com.taemoi.project;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class TaeMoiApplicationTests {

	@Test
	void applicationClassIsLoadable() {
		assertDoesNotThrow(() -> Class.forName("com.taemoi.project.TaeMoiApplication"));
	}

}
