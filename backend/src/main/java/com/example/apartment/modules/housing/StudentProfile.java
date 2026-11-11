package com.example.apartment.modules.housing;

public record StudentProfile(long id, long userId, String studentNo, String fullName, String college,
        String major, String className, boolean enabled, long version) {}
