package idu.sba.backend.domain.webhook.service;

import idu.sba.backend.domain.webhook.dto.GithubPullRequestEventPayload;

public interface GithubWebhookService {

    void handlePullRequestEvent(GithubPullRequestEventPayload payload);

}
