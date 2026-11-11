package com.example.apartment.modules.housing;

import com.example.apartment.modules.identity.AccountSummary;

public record StudentView(long id, long userId, String username, boolean accountEnabled, String studentNo,
        String fullName, String college, String major, String className, boolean enabled, long version) {
    static StudentView of(StudentProfile profile, AccountSummary account) {
        return new StudentView(profile.id(), profile.userId(), account.username(), account.enabled(), profile.studentNo(),
                profile.fullName(), profile.college(), profile.major(), profile.className(), profile.enabled(), profile.version());
    }
}
