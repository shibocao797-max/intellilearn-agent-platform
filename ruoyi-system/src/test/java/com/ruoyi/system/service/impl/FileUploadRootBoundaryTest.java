package com.ruoyi.system.service.impl;

import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.utils.file.FileUploadUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileUploadRootBoundaryTest
{
    @TempDir
    Path tempDirectory;

    private String previousProfile;

    @AfterEach
    void restoreProfile()
    {
        new RuoYiConfig().setProfile(previousProfile);
    }

    @Test
    void allowsUploadDirectoriesUnderConfiguredProfile() throws Exception
    {
        configureProfile();
        Path file = FileUploadUtils.getAbsoluteFile(
                tempDirectory.resolve("profile/upload").toString(), "2026/notes.txt").toPath();

        assertTrue(file.startsWith(tempDirectory.resolve("profile")));
    }

    @Test
    void rejectsCanonicalUploadDirectoryOutsideConfiguredProfile()
    {
        configureProfile();
        String escapedDirectory = tempDirectory.resolve("profile/../../outside").toString();

        assertThrows(IOException.class,
                () -> FileUploadUtils.getAbsoluteFile(escapedDirectory, "notes.txt"));
    }

    private void configureProfile()
    {
        previousProfile = RuoYiConfig.getProfile();
        new RuoYiConfig().setProfile(tempDirectory.resolve("profile").toString());
    }
}
