package idu.sba.backend.domain.context.controller;

import idu.sba.backend.domain.context.dto.CodeContextResponseDTO;
import idu.sba.backend.domain.context.dto.ContextExtractionRequestDTO;
import idu.sba.backend.domain.context.service.ContextExtractionService;
import idu.sba.backend.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/context")
@RequiredArgsConstructor
public class ContextExtractionController {

    private final ContextExtractionService contextExtractionService;

    //AST 기반 컨텍스트 추출
    @PostMapping("/extract")
    public ApiResponse<CodeContextResponseDTO> extract(
            @Valid @RequestBody ContextExtractionRequestDTO request) {
        CodeContextResponseDTO context = contextExtractionService.extract(request);
        return ApiResponse.ok(context);
    }

}
