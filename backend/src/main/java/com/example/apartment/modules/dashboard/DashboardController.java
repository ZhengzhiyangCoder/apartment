package com.example.apartment.modules.dashboard;

import com.example.apartment.modules.identity.AccountPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboard;
    public DashboardController(DashboardService dashboard) { this.dashboard=dashboard; }
    @GetMapping
    public DashboardService.Dashboard read(@AuthenticationPrincipal AccountPrincipal actor) { return dashboard.read(actor); }
}
