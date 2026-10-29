package com.ruoyi.framework.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Prevents the application from starting with repository-level placeholder
 * credentials. Secrets should come from the process environment or a local
 * secrets file that is excluded from version control.
 */
@Component
public class StartupSecurityValidator
{
    private static final int MIN_TOKEN_SECRET_LENGTH = 32;

    @Value("${token.secret:}")
    private String tokenSecret;

    @Value("${file-storage.type:local}")
    private String storageType;

    @Value("${file-storage.minio.access-key:}")
    private String minioAccessKey;

    @Value("${file-storage.minio.secret-key:}")
    private String minioSecretKey;

    @Value("${spring.datasource.druid.master.password:}")
    private String databasePassword;

    @Value("${spring.datasource.druid.statViewServlet.login-password:}")
    private String druidPassword;

    @PostConstruct
    public void validate()
    {
        requireLength(tokenSecret, MIN_TOKEN_SECRET_LENGTH,
            "TOKEN_SECRET must be provided and contain at least 32 characters");
        requirePresent(databasePassword, "MYSQL_PASSWORD must be provided");
        requirePresent(druidPassword, "DRUID_LOGIN_PASSWORD must be provided");

        if ("minio".equalsIgnoreCase(storageType))
        {
            requirePresent(minioAccessKey, "MINIO_ACCESS_KEY must be provided when file-storage.type=minio");
            requirePresent(minioSecretKey, "MINIO_SECRET_KEY must be provided when file-storage.type=minio");
        }
    }

    private void requirePresent(String value, String message)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalStateException(message);
        }
    }

    private void requireLength(String value, int minimum, String message)
    {
        requirePresent(value, message);
        if (value.trim().length() < minimum)
        {
            throw new IllegalStateException(message);
        }
    }
}
