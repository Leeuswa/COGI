package idu.sba.backend.domain.admin.service;

import idu.sba.backend.domain.admin.dto.AdminAiUsageResponseDTO;

import java.time.LocalDate;
import java.util.List;

public interface AdminUsageService {
    List<AdminAiUsageResponseDTO> getAiUsage(LocalDate from ,LocalDate to);
}
