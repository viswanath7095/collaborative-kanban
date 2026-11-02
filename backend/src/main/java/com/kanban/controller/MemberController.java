package com.kanban.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.kanban.dto.AddMemberRequest;
import com.kanban.dto.MemberResponse;
import com.kanban.service.MemberService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/organizations/{organizationId}/tenants/{tenantId}/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse add(Authentication auth,
                              @PathVariable UUID organizationId,
                              @PathVariable UUID tenantId,
                              @Valid @RequestBody AddMemberRequest request) {
        return memberService.add((UUID) auth.getPrincipal(), organizationId, tenantId,
                request.email(), request.role());
    }

    @GetMapping
    public List<MemberResponse> list(Authentication auth,
                                     @PathVariable UUID organizationId,
                                     @PathVariable UUID tenantId) {
        return memberService.list((UUID) auth.getPrincipal(), organizationId, tenantId);
    }
}