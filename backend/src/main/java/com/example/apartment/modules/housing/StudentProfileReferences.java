package com.example.apartment.modules.housing;

import com.example.apartment.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Housing's reference check used by identity after it obtains the shared administrator lock. */
@Service
public class StudentProfileReferences {
    private final StudentProfileMapper profiles;
    public StudentProfileReferences(StudentProfileMapper profiles) { this.profiles = profiles; }
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireUnlinked(long userId) {
        if (profiles.countByUser(userId) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "STUDENT_PROFILE_LINKED", "该账号已关联学生资料，不能变更为非学生角色");
        }
    }
}
