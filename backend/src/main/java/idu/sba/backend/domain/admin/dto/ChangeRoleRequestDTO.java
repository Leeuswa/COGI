package idu.sba.backend.domain.admin.dto;

import idu.sba.backend.domain.user.entity.Role;
import jakarta.validation.constraints.NotNull;

// PATCH /api/admin/members/{userId}/role — 잘못된 값은 Jackson이 400으로 거른다.
public record ChangeRoleRequestDTO(@NotNull Role role) {}
