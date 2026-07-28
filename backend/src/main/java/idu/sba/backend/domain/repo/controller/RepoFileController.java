package idu.sba.backend.domain.repo.controller;

import idu.sba.backend.domain.repo.client.GithubContentDto;
import idu.sba.backend.domain.repo.client.GithubFileClient;
import idu.sba.backend.domain.repo.dto.RepoFileResponseDTO;
import idu.sba.backend.domain.repo.entity.GithubRepository;
import idu.sba.backend.domain.repo.repository.GithubRepositoryRepository;
import idu.sba.backend.domain.repo.repository.RepoMemberRepository;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.repository.UserRepository;
import idu.sba.backend.global.common.ApiResponse;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

//미리보기 도크가 부족한 로컬 참조(CSS/JS/이미지)를 GitHub에서 가져올 때 쓰는 신규 엔드포인트.
//RepoController(홍성찬 소유)는 그대로 두고 파일 하나만 별도 컨트롤러로 분리했다.
@RestController
@RequestMapping("/api/repos")
@RequiredArgsConstructor
public class RepoFileController {

    //미리보기용이라 큰 파일은 굳이 받을 필요 없음 — 1MB 초과는 거절
    private static final long MAX_FILE_SIZE = 1_000_000;

    private final GithubRepositoryRepository githubRepositoryRepository;
    private final RepoMemberRepository repoMemberRepository;
    private final UserRepository userRepository;
    private final GithubFileClient githubFileClient;

    //승인 버튼을 누른 뒤에만 프론트가 호출 — 파일 하나씩 조회
    @GetMapping("/{repoId}/file-content")
    public ApiResponse<RepoFileResponseDTO> getFileContent(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long repoId,
            @RequestParam String path) {
        if (path == null || path.isBlank() || path.contains("..")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        GithubRepository repo = githubRepositoryRepository.findById(repoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPO_NOT_FOUND));
        if (!repoMemberRepository.existsByRepoIdAndUserId(repoId, userId)) {
            throw new BusinessException(ErrorCode.NOT_REPO_MEMBER);
        }
        String accessToken = requireAccessToken(userId);

        GithubContentDto file = githubFileClient.getFileContent(accessToken, repo.getFullName(), path);
        if (file.getContent() == null || file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT); //디렉터리이거나 미리보기 한도 초과
        }

        String raw = file.getContent().replaceAll("\\s", ""); //GitHub base64는 60자마다 개행이 섞여 있음
        if (isTextAsset(path)) {
            String text = new String(Base64.getDecoder().decode(raw), StandardCharsets.UTF_8);
            return ApiResponse.ok(RepoFileResponseDTO.ofText(file.getPath(), text, file.getSize()));
        }
        return ApiResponse.ok(RepoFileResponseDTO.ofBase64(file.getPath(), raw, file.getSize()));
    }

    //CSS/JS만 텍스트로 디코딩해서 내려주고, 나머지(이미지 등)는 base64 그대로 — 프론트가 data URI로 씀
    private boolean isTextAsset(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith(".css") || lower.endsWith(".js") || lower.endsWith(".mjs");
    }

    private String requireAccessToken(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getGithubAccessToken() == null) {
            throw new BusinessException(ErrorCode.GITHUB_NOT_LINKED);
        }
        return user.getGithubAccessToken();
    }

}
