package com.sprint.sprint.persistence;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "maintainer_config")
@Data
public class MaintainerConfig {

    @Id
    @Column(name = "installation_id")
    private String installationId;

    @Column(name = "digest_cron")
    private String digestCron;

    @Column(name = "threshold_tier")
    private String thresholdTier;

    @Column(name = "labels_enabled")
    private Boolean labelsEnabled;

    @Column(name = "email_enabled")
    private Boolean emailEnabled;

    @Column(name = "actions_enabled")
    private Boolean actionsEnabled;

    @Lob
    @Column(name = "maintainer_emails", columnDefinition = "TEXT")
    @Convert(converter = StringListConverter.class)
    private List<String> maintainerEmails;

    @Converter(autoApply = false)
    public static class StringListConverter implements jakarta.persistence.AttributeConverter<List<String>, String> {

        private static final Gson GSON = new Gson();
        private static final Type LIST_TYPE = new TypeToken<List<String>>(){}.getType();

        @Override
        public String convertToDatabaseColumn(List<String> list) {
            if (list == null || list.isEmpty()) {
                return null;
            }
            return GSON.toJson(list);
        }

        @Override
        public List<String> convertToEntityAttribute(String json) {
            if (json == null || json.isBlank()) {
                return new ArrayList<>();
            }
            return GSON.fromJson(json, LIST_TYPE);
        }
    }
}