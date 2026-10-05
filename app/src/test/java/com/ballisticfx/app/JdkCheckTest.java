package com.ballisticfx.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JdkCheckTest {

    @Test
    void problemIsReportedExactlyWhenTheJdkLacksJsObject() {
        // Holds on every JDK: 21 has jdk.jsobject (no problem), 27 does not (problem is reported).
        assertEquals(!JdkCheck.hasJsObject(), JdkCheck.problem().isPresent());
    }

    @Test
    void messageNamesTheJavaVersionTheModuleAndTheFix() {
        String message = JdkCheck.message("27");
        assertTrue(message.contains("Java 27"));
        assertTrue(message.contains("jdk.jsobject"));
        assertTrue(message.contains("JDK 21"));
        assertTrue(message.contains("Project Structure"));
    }
}
