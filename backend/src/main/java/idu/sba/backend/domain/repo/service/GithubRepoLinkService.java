package idu.sba.backend.domain.repo.service;

import idu.sba.backend.domain.repo.dto.GithubRepoResponseDTO;
import idu.sba.backend.domain.repo.dto.RepoLinkResponseDTO;

import java.util.List;

public interface GithubRepoLinkService {

    //내 GitHub 레포 목록 조회(API-022)
    List<GithubRepoResponseDTO> listMyGithubRepos(Long currentUserId);

    //레포 선택 및 저장(API-023) — Webhook 자동 등록은 별도 작업(Webhook 수신)에서 이어붙임
    RepoLinkResponseDTO linkRepo(Long currentUserId, String githubRepoId);

}
