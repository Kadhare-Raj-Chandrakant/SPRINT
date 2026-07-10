package com.bot.bot.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bot.bot.config.AppProperties;
import com.bot.bot.persistence.Meta;
import com.bot.bot.persistence.MetaRepository;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

class TokenServiceTest {

    private TokenService newService(String baseUrl) {
        AppProperties props = new AppProperties();
        props.setActionSecret("test-secret");
        props.setBaseUrl(baseUrl);

        MetaRepository repo = Mockito.mock(MetaRepository.class);
        Map<String, Meta> store = new HashMap<>();
        Mockito.when(repo.save(Mockito.any(Meta.class))).thenAnswer(inv -> {
            Meta m = inv.getArgument(0);
            store.put(m.getKey(), m);
            return m;
        });
        Mockito.when(repo.findById(Mockito.anyString())).thenAnswer(inv ->
                Optional.ofNullable(store.get(inv.getArgument(0))));

        return new TokenService(props, repo);
    }

    private String sign(String b64, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(b64.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void roundTripVerifiesPayload() {
        TokenService svc = newService("https://bot.example.com");
        String token = svc.generate("o", "r", 42, "approve");
        TokenService.TokenPayload p = svc.verify(token);
        assertEquals("o", p.owner());
        assertEquals("r", p.repo());
        assertEquals(42, p.prNumber());
        assertEquals("approve", p.action());
    }

    @Test
    void rejectedOnTamperedSignature() {
        TokenService svc = newService(null);
        String token = svc.generate("o", "r", 1, "approve");
        String[] parts = token.split("\\.", 2);
        String tampered = parts[0] + ".AAAA" + parts[1].substring(4);
        assertThrows(TokenException.class, () -> svc.verify(tampered));
    }

    @Test
    void singleUseConsumedOnVerify() {
        TokenService svc = newService(null);
        String token = svc.generate("o", "r", 7, "approve");
        svc.verify(token); // first use succeeds
        assertThrows(TokenException.class, () -> svc.verify(token)); // second use fails
    }

    @Test
    void malformedAndBlankTokensRejected() {
        TokenService svc = newService(null);
        assertThrows(TokenException.class, () -> svc.verify("no-dot"));
        assertThrows(TokenException.class, () -> svc.verify(""));
        assertThrows(TokenException.class, () -> svc.verify(null));
    }

    @Test
    void expiredTokenRejected() throws Exception {
        TokenService svc = newService(null);
        String owner = "o", repo = "r", action = "approve";
        long expired = Instant.now().getEpochSecond() - 10;
        String payload = String.join("|", owner, repo, "1", action, String.valueOf(expired));
        String b64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String token = b64 + "." + sign(b64, "test-secret");
        TokenException ex = assertThrows(TokenException.class, () -> svc.verify(token));
        assertTrue(ex.getMessage().contains("expired"), ex.getMessage());
    }

    @Test
    void buildActionUrlIncludesTokenAndAction() {
        TokenService svc = newService("https://bot.example.com");
        String url = svc.buildActionUrl("o", "r", 9, "reject");
        assertTrue(url.startsWith("https://bot.example.com/action?token="));
        assertTrue(url.contains("do=reject"));
    }
}
