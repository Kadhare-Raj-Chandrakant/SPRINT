package com.sprint.sprint.web;

import com.sprint.sprint.actions.TokenService;
import com.sprint.sprint.config.AppProperties;
import com.sprint.sprint.github.GitHubApiClient;
import com.sprint.sprint.persistence.Meta;
import com.sprint.sprint.persistence.MetaRepository;
import com.sprint.sprint.persistence.PrAnalysis;
import com.sprint.sprint.persistence.PrAnalysisRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static reactor.core.publisher.Mono.empty;

/**
 * End-to-end action flow (SRS §8/§9): signed token → /action → GitHub call →
 * PrAnalysis marked ACTIONED. GitHubApiClient is mocked; repositories are
 * in-memory fakes so the full controller + token path is exercised.
 */
@ExtendWith(MockitoExtension.class)
class ActionFlowIntegrationTest {

    @Mock
    private GitHubApiClient gitHubApiClient;
    @Mock
    private PrAnalysisRepository prAnalysisRepository;
    @Mock
    private MetaRepository metaRepository;

    private TokenService tokenService;
    private MockMvc mockMvc;
    private final Map<String, Meta> metaStore = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.setActionSecret("test-secret");
        props.setBaseUrl("https://bot.test");
        tokenService = new TokenService(props, metaRepository);

        ActionController controller =
                new ActionController(tokenService, prAnalysisRepository, gitHubApiClient);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        when(metaRepository.save(any(Meta.class))).thenAnswer(inv -> {
            Meta m = inv.getArgument(0);
            metaStore.put(m.getKey(), m);
            return m;
        });
        when(metaRepository.findById(anyString())).thenAnswer(
                inv -> Optional.ofNullable(metaStore.get(inv.getArgument(0))));
    }

    private PrAnalysis seeded(String owner, String repo, int pr, String action) {
        PrAnalysis a = new PrAnalysis();
        a.setOwner(owner);
        a.setRepo(repo);
        a.setPrNumber(pr);
        a.setInstallationId("123");
        when(prAnalysisRepository.findLatest(owner, repo, pr)).thenReturn(Optional.of(a));
        return a;
    }

    @Test
    void closeAction_callsGitHubAndMarksActioned() throws Exception {
        PrAnalysis a = seeded("o", "r", 1, "close");
        when(gitHubApiClient.closePullRequest(anyString(), anyString(), anyInt(), anyLong()))
                .thenReturn(empty());

        mockMvc.perform(get("/action").param("token", tokenService.generate("o", "r", 1, "close"))
                        .param("do", "close"))
                .andExpect(status().isOk());

        verify(gitHubApiClient).closePullRequest("o", "r", 1, 123L);
        verify(prAnalysisRepository).save(a);
        assertThat(a.getStatus()).isEqualTo("ACTIONED");
        assertThat(a.getActionTaken()).isTrue();
    }

    @Test
    void approveAction_submitsApproveReview() throws Exception {
        PrAnalysis a = seeded("o", "r", 2, "approve");
        when(gitHubApiClient.submitReview(anyString(), anyString(), anyInt(), anyString(),
                anyString(), any(), anyLong())).thenReturn(empty());

        mockMvc.perform(get("/action").param("token", tokenService.generate("o", "r", 2, "approve"))
                        .param("do", "approve"))
                .andExpect(status().isOk());

        verify(gitHubApiClient).submitReview(eq("o"), eq("r"), eq(2), anyString(), eq("APPROVE"), any(), eq(123L));
        assertThat(a.getStatus()).isEqualTo("ACTIONED");
    }

    @Test
    void requestChangesAction_submitsRequestChangesReview() throws Exception {
        PrAnalysis a = seeded("o", "r", 3, "request-changes");
        when(gitHubApiClient.submitReview(anyString(), anyString(), anyInt(), anyString(),
                anyString(), any(), anyLong())).thenReturn(empty());

        mockMvc.perform(get("/action").param("token", tokenService.generate("o", "r", 3, "request-changes"))
                        .param("do", "request-changes"))
                .andExpect(status().isOk());

        verify(gitHubApiClient).submitReview(eq("o"), eq("r"), eq(3), anyString(), eq("REQUEST_CHANGES"), any(), eq(123L));
        assertThat(a.getStatus()).isEqualTo("ACTIONED");
    }

    @Test
    void doubleUse_secondAttemptReturns410() throws Exception {
        seeded("o", "r", 4, "close");
        when(gitHubApiClient.closePullRequest(anyString(), anyString(), anyInt(), anyLong()))
                .thenReturn(empty());
        String token = tokenService.generate("o", "r", 4, "close");

        mockMvc.perform(get("/action").param("token", token).param("do", "close"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/action").param("token", token).param("do", "close"))
                .andExpect(status().isGone());

        verify(gitHubApiClient, never()).closePullRequest("o", "r", 4, 999L);
    }
}
