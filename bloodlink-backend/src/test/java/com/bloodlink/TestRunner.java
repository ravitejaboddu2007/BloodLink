package com.bloodlink;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class TestRunner {
    public static void main(String[] args) {
        System.setProperty("net.bytebuddy.experimental", "true");
        int totalPassed = 0;
        int totalFailed = 0;
        List<Class<?>> testClasses = Arrays.asList(
                BloodRequestServiceMatchingTest.class,
                BloodBankRequestServiceMatchingTest.class,
                UserServiceTest.class,
                DonorServiceSearchTest.class,
                DonationHistoryServiceTest.class,
                ReviewServiceTest.class,
                BloodRequestServiceTest.class,
                InventoryServiceTest.class,
                AuthAndUserPhase8Test.class,
                FinalMigrationAuditPhase9Test.class,
                BloodBankConfirmationWorkflowTest.class,
                com.bloodlink.security.JwtAndSpringSecurityTest.class,
                com.bloodlink.security.RoleBasedAuthorizationTest.class,
                com.bloodlink.security.ResourceOwnershipSecurityTest.class
        );

        for (Class<?> testClass : testClasses) {
            System.out.println("Running test suite: " + testClass.getSimpleName());
            for (Method method : testClass.getDeclaredMethods()) {
                if (method.isAnnotationPresent(org.junit.jupiter.api.Test.class)) {
                    try {
                        Object testInstance = testClass.getDeclaredConstructor().newInstance();
                        try {
                            Method setUp = testClass.getDeclaredMethod("setUp");
                            setUp.invoke(testInstance);
                        } catch (NoSuchMethodException ignored) {}

                        method.invoke(testInstance);
                        System.out.println("  [PASS] " + method.getName());
                        totalPassed++;
                    } catch (Throwable t) {
                        System.err.println("  [FAIL] " + method.getName() + " -> " + (t.getCause() != null ? t.getCause().getMessage() : t.getMessage()));
                        if (t.getCause() != null) {
                            t.getCause().printStackTrace();
                        }
                        totalFailed++;
                    }
                }
            }
        }

        System.out.println("\nTest Summary: " + totalPassed + " passed, " + totalFailed + " failed.");
        if (totalFailed > 0) {
            System.exit(1);
        }
    }
}