package com.example.apartment.modules.housing;

import com.example.apartment.common.ApiException;
import com.example.apartment.modules.identity.AccountPrincipal;
import com.example.apartment.modules.identity.Role;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Other modules derive a student's scope from the authenticated principal, never a client-supplied student/room ID. */
@Service
public class CurrentResidencyQuery {
    private final ResidencyMapper residencies;
    public CurrentResidencyQuery(ResidencyMapper residencies) { this.residencies = residencies; }
    @Transactional(readOnly = true)
    public ResidencyView findForStudent(AccountPrincipal actor) {
        if (actor.role() != Role.STUDENT) throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可查看本人住宿");
        return residencies.currentForUser(actor.id());
    }
    @Transactional(readOnly = true)
    public ResidencyView requireForStudent(AccountPrincipal actor) {
        var current = findForStudent(actor);
        if (current == null) throw new ApiException(HttpStatus.CONFLICT, "NOT_RESIDENT", "尚未入住，请先联系系统管理员办理入住");
        return current;
    }
}
