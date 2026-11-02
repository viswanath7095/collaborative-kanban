package com.kanban.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kanban.entity.Organization;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
}