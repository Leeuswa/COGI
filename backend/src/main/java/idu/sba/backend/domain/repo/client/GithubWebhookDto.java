package idu.sba.backend.domain.repo.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

//GitHub REST API 웹훅 생성 응답 중 필요한 필드만 매핑(나머지는 무시)
@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubWebhookDto(Long id) {}
