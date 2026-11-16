package com.example.apartment.modules.hygiene;

import com.example.apartment.modules.identity.AccountPrincipal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hygiene/student/results")
public class StudentResultsController {
    private final StudentResultsService service;
    public StudentResultsController(StudentResultsService service) { this.service=service; }
    @GetMapping
    public ResultViews.Results results(@AuthenticationPrincipal AccountPrincipal actor,
            @RequestParam(defaultValue="WEEK") ResultViews.Kind period,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required=false) Long roomId,@RequestParam(required=false) Long buildingId,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size) {
        return service.results(actor,period,date,roomId,buildingId,page,size);
    }
}
