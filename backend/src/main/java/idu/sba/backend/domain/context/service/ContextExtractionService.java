package idu.sba.backend.domain.context.service;

import idu.sba.backend.domain.context.dto.CodeContextResponseDTO;
import idu.sba.backend.domain.context.dto.ContextExtractionRequestDTO;

public interface ContextExtractionService {

    //코드에서 AI 리뷰 프롬프트용 컨텍스트(클래스/메서드/필드/import 등) 추출
    CodeContextResponseDTO extract(ContextExtractionRequestDTO request);

}
