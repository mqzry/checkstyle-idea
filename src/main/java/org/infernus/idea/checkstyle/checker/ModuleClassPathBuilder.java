package org.infernus.idea.checkstyle.checker;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import org.infernus.idea.checkstyle.config.PluginConfigurationManager;
import org.infernus.idea.checkstyle.util.ModulePaths;
import org.infernus.idea.checkstyle.util.TempDirProvider;
import org.jetbrains.annotations.NotNull;


public class ModuleClassPathBuilder {
    private static final Logger LOG = Logger.getInstance(ModuleClassPathBuilder.class);

    private final PluginConfigurationManager pluginConfigurationManager;

    public ModuleClassPathBuilder(@NotNull final PluginConfigurationManager pluginConfigurationManager) {
        this.pluginConfigurationManager = pluginConfigurationManager;
    }

    public ClassLoader build(final Module baseModule) {
        if (baseModule == null) {
            return getClass().getClassLoader();
        }

        final Project project = baseModule.getProject();
        final List<URL> outputPaths = new ArrayList<>();

        final Set<Module> transitiveDependencies = new HashSet<>();
        ModuleUtil.getDependencies(baseModule, transitiveDependencies);
        for (Module moduleInScope : transitiveDependencies) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Adding module to classpath: " + moduleInScope.getName());
            }
            outputPaths.addAll(ModulePaths.compilerOutputPathsFor(moduleInScope));
            outputPaths.addAll(ModulePaths.libraryPathsFor(moduleInScope));
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating class-loader with URLs: " + outputPaths);
        }

        URL[] effectiveClasspath = outputPaths.toArray(new URL[outputPaths.size()]);
        if (wantsCopyLibs()) {
            final Optional<File> tempDir = new TempDirProvider().forCopiedLibraries(project);
            if (tempDir.isPresent()) {
                final Path t = Paths.get(tempDir.get().toURI());
                effectiveClasspath = new ClasspathStabilizer(project, t).stabilize(outputPaths);
            }
        }
        return new URLClassLoader(effectiveClasspath, getClass().getClassLoader());
    }

    private boolean wantsCopyLibs() {
        return pluginConfigurationManager.getCurrent().isCopyLibs();
    }
}
