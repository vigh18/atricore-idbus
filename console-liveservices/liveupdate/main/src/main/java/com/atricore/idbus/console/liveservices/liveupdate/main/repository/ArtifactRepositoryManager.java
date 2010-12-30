package com.atricore.idbus.console.liveservices.liveupdate.main.repository;

import com.atricore.idbus.console.liveservices.liveupdate.main.LiveUpdateException;
import com.atricore.liveservices.liveupdate._1_0.md.ArtifactKeyType;

import java.io.InputStream;
import java.util.Collection;

/**
 * Manages a set of LiveUpdate Artifact repositories.
 *
 * It retrieves Artifacts for actual update services and stores it in the local repository representation.
 * Different transports are supported
 *
 * @author <a href=mailto:sgonzalez@atricore.org>Sebastian Gonzalez Oyuela</a>
 */
public interface ArtifactRepositoryManager extends RepositoryManager {

    InputStream getArtifactStream(ArtifactKeyType artifact) throws LiveUpdateException;

    void clearRepository(String repoName) throws LiveUpdateException;

    void clearAllRepositories() throws LiveUpdateException;

    void addRepository(ArtifactRepository repo) throws LiveUpdateException;
}