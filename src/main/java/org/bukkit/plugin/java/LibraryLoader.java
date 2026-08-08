// CHECKSTYLE:OFF
package org.bukkit.plugin.java;

import com.mohistmc.bukkit.PluginsLibrarySource;
import com.mohistmc.bukkit.remapping.RemappingURLClassLoader;
import com.mohistmc.org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.PluginDescriptionFile;
import com.mohistmc.org.eclipse.aether.DefaultRepositorySystemSession;
import com.mohistmc.org.eclipse.aether.RepositorySystem;
import com.mohistmc.org.eclipse.aether.artifact.Artifact;
import com.mohistmc.org.eclipse.aether.artifact.DefaultArtifact;
import com.mohistmc.org.eclipse.aether.collection.CollectRequest;
import com.mohistmc.org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import com.mohistmc.org.eclipse.aether.graph.Dependency;
import com.mohistmc.org.eclipse.aether.impl.DefaultServiceLocator;
import com.mohistmc.org.eclipse.aether.repository.LocalRepository;
import com.mohistmc.org.eclipse.aether.repository.RemoteRepository;
import com.mohistmc.org.eclipse.aether.repository.RepositoryPolicy;
import com.mohistmc.org.eclipse.aether.resolution.ArtifactResult;
import com.mohistmc.org.eclipse.aether.resolution.DependencyRequest;
import com.mohistmc.org.eclipse.aether.resolution.DependencyResolutionException;
import com.mohistmc.org.eclipse.aether.resolution.DependencyResult;
import com.mohistmc.org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import com.mohistmc.org.eclipse.aether.spi.connector.transport.TransporterFactory;
import com.mohistmc.org.eclipse.aether.transfer.AbstractTransferListener;
import com.mohistmc.org.eclipse.aether.transfer.TransferEvent;
import com.mohistmc.org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LibraryLoader {

    private final Logger logger;
    private final RepositorySystem repository;
    private final DefaultRepositorySystemSession session;
    private final List<RemoteRepository> repositories;

    public LibraryLoader(@NotNull Logger logger) {
        this.logger = logger;

        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override
            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
                logger.log(Level.SEVERE, "Service initialization failed - Type: " + type + ", Implementation: " + impl, exception);
            }
        });

        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

        this.repository = locator.getService(RepositorySystem.class);
        this.session = MavenRepositorySystemUtils.newSession();

        session.setChecksumPolicy(RepositoryPolicy.CHECKSUM_POLICY_FAIL);
        session.setLocalRepositoryManager(repository.newLocalRepositoryManager(session, new LocalRepository("libraries")));
        session.setTransferListener(new AbstractTransferListener() {
            @Override
            public void transferStarted(@NotNull TransferEvent event) {
                logger.log(Level.INFO, "Downloading {0}", event.getResource().getRepositoryUrl() + event.getResource().getResourceName());
            }
        });
        session.setReadOnly();

        this.repositories = repository.newResolutionRepositories(session, Arrays.asList(new RemoteRepository.Builder("central", "default", PluginsLibrarySource.DEFAULT).build()));
    }

    @Nullable
    public ClassLoader createLoader(@NotNull PluginDescriptionFile desc) {
        if (desc.getLibraries().isEmpty()) {
            return null;
        }
        logger.log(Level.INFO, "[{0}] Loading {1} libraries... please wait", new Object[]
                {
                        desc.getName(), desc.getLibraries().size()
                });

        List<Dependency> dependencies = new ArrayList<>();
        for (String library : desc.getLibraries()) {
            Artifact artifact = new DefaultArtifact(library);
            Dependency dependency = new Dependency(artifact, null);

            dependencies.add(dependency);
        }

        DependencyResult result;
        try {
            result = repository.resolveDependencies(session, new DependencyRequest(new CollectRequest((Dependency) null, dependencies, repositories), null));
        } catch (DependencyResolutionException ex) {
            throw new RuntimeException("Error resolving libraries", ex);
        }

        List<URL> jarFiles = new ArrayList<>();
        for (ArtifactResult artifact : result.getArtifactResults()) {
            File file = artifact.getArtifact().getFile();

            URL url;
            try {
                url = file.toURI().toURL();
            } catch (MalformedURLException ex) {
                throw new AssertionError(ex);
            }

            jarFiles.add(url);
            logger.log(Level.INFO, "[{0}] Loaded library {1}", new Object[]
                    {
                            desc.getName(), file
                    });
        }

        return new RemappingURLClassLoader(jarFiles.toArray(new URL[0]), getClass().getClassLoader());
    }
}
