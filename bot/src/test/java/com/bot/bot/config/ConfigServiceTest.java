package com.bot.bot.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.bot.bot.persistence.MaintainerConfig;
import com.bot.bot.persistence.MaintainerConfigRepository;

@ExtendWith(MockitoExtension.class)
class ConfigServiceTest {

    @Mock
    private AppProperties appProperties;

    @Mock
    private MailProperties mailProperties;

    @Mock
    private MaintainerConfigRepository maintainerConfigRepository;

    @InjectMocks
    private ConfigService configService;

    @Test
    void missingRowReturnsGlobalDefaults() {
        when(mailProperties.getDigestCron()).thenReturn("0 0 18 * * *");
        when(mailProperties.isEnabled()).thenReturn(false);
        when(mailProperties.getMaintainerEmails()).thenReturn(List.of("admin@example.com"));
        when(appProperties.getActionSecret()).thenReturn("secret");
        when(maintainerConfigRepository.findById("42")).thenReturn(Optional.empty());

        ConfigService.ResolvedConfig result = configService.resolve(42L);

        assertThat(result.installationId()).isEqualTo("42");
        assertThat(result.digestCron()).isEqualTo("0 0 18 * * *");
        assertThat(result.thresholdTier()).isEqualTo("RED");
        assertThat(result.labelsEnabled()).isTrue();
        assertThat(result.emailEnabled()).isFalse();
        assertThat(result.actionsEnabled()).isTrue();
        assertThat(result.maintainerEmails()).containsExactly("admin@example.com");
    }

    @Test
    void perInstallationOverrideMergesWithDefaults() {
        when(mailProperties.getDigestCron()).thenReturn("0 0 18 * * *");
        when(mailProperties.isEnabled()).thenReturn(true);
        when(mailProperties.getMaintainerEmails()).thenReturn(List.of("admin@example.com"));
        when(appProperties.getActionSecret()).thenReturn(null);

        MaintainerConfig row = new MaintainerConfig();
        row.setInstallationId("99");
        row.setDigestCron("0 0 */2 * * *");
        row.setEmailEnabled(false);
        row.setMaintainerEmails(List.of("override@example.com"));
        when(maintainerConfigRepository.findById("99")).thenReturn(Optional.of(row));

        ConfigService.ResolvedConfig result = configService.resolve("99");

        assertThat(result.installationId()).isEqualTo("99");
        assertThat(result.digestCron()).isEqualTo("0 0 */2 * * *");
        assertThat(result.thresholdTier()).isEqualTo("RED"); // not overridden
        assertThat(result.labelsEnabled()).isTrue(); // not overridden
        assertThat(result.emailEnabled()).isFalse(); // overridden
        assertThat(result.actionsEnabled()).isFalse(); // global: secret null
        assertThat(result.maintainerEmails()).containsExactly("override@example.com");
    }
}