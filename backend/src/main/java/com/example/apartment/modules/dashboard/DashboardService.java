package com.example.apartment.modules.dashboard;

import com.example.apartment.modules.hygiene.*;
import com.example.apartment.modules.identity.*;
import com.example.apartment.modules.housing.*;
import com.example.apartment.modules.repair.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only composition of domain queries; never prepares batches or stores derived facts. */
@Service
public class DashboardService {
    private final BatchService batches;
    private final RectificationService rectifications;
    private final RepairService repairs;
    private final CurrentResidencyQuery residency;
    private final StudentResultsService results;
    public DashboardService(BatchService batches, RectificationService rectifications, RepairService repairs, CurrentResidencyQuery residency, StudentResultsService results) {
        this.batches=batches; this.rectifications=rectifications; this.repairs=repairs; this.residency=residency; this.results=results;
    }
    public record Queue(RepairViews.Status status, RepairViews.Page page) {}
    public record StudentSummary(ResultViews.Period period, ResultViews.Sample summary) {}
    public record Dashboard(Role role, String title, BatchViews.Current inspection,
            RectificationViews.Page rectifications, ResidencyView residency, StudentSummary results, List<Queue> repairs) {}
    @Transactional(readOnly=true)
    public Dashboard read(AccountPrincipal actor) {
        if(actor.role()==Role.STUDENT) {
            var current=residency.findForStudent(actor);
            var own=current==null ? null : results.results(actor,ResultViews.Kind.WEEK,null,null,null,1,1);
            return new Dashboard(actor.role(),actor.role().label()+"工作台",null,null,current,
                    own==null?null:new StudentSummary(own.period(),own.summary()),List.of(queue(actor,null)));
        }
        if(actor.role()==Role.MAINTAINER)
            return new Dashboard(actor.role(),actor.role().label()+"工作台",null,null,null,null,
                    List.of(queue(actor,RepairViews.Status.PENDING),queue(actor,RepairViews.Status.IN_PROGRESS)));
        if(actor.role()!=Role.SYSTEM_ADMIN && actor.role()!=Role.DORM_MANAGER)
            throw new org.springframework.security.access.AccessDeniedException("没有管理工作台权限");
        return new Dashboard(actor.role(),actor.role().label()+"工作台",batches.current(),
                rectifications.list(RectificationViews.Status.OPEN,null,"",1,5),null,null,
                List.of(queue(actor,RepairViews.Status.PENDING_ASSIGNMENT),queue(actor,RepairViews.Status.PENDING),queue(actor,RepairViews.Status.IN_PROGRESS)));
    }
    private Queue queue(AccountPrincipal actor,RepairViews.Status status) {
        return new Queue(status,repairs.list(actor,"",status,1,5));
    }
}
