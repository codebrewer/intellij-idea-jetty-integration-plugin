/*
 * Copyright 2007 Mark Scott
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codebrewer.idea.jetty;

import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.util.EnvironmentVariable;
import com.intellij.javaee.run.configuration.CommonModel;
import com.intellij.javaee.run.localRun.CommandLineExecutableObject;
import com.intellij.javaee.run.localRun.EnvironmentHelper;
import com.intellij.javaee.run.localRun.ExecutableObject;
import com.intellij.javaee.run.localRun.ExecutableObjectStartupPolicy;
import com.intellij.javaee.run.localRun.ScriptsHelper;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.EnvironmentUtil;
import static org.codebrewer.idea.jetty.JettyConstants.JETTY_CONTEXT_DEPLOYER_CONFIG_FILE_NAME;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Mark Scott
 * @version $Id$
 */
public class JettyStartupPolicy implements ExecutableObjectStartupPolicy
{
  @NonNls
  private static final String BIN_DIR = "bin";
  @NonNls
  private static final String JAVA_HOME_ENV_PROPERTY = "JAVA_HOME";
  @NonNls
  private static final String JAVA_VM_ENV_VARIABLE = "JAVA_OPTS";
  @NonNls
  private static final String JETTY_STOP_COMMAND_TEMPLATE = "-DSTOP.PORT={0,number,#####} -DSTOP.KEY={1} -jar start.jar --stop";
  @NonNls
  private static final String JETTY_START_COMMAND = "-DSTOP.PORT=0 -jar start.jar";

  @NonNls
  private static String getDefaultJettyLauncherFileName()
  {
    return SystemInfo.isWindows ? "jetty.bat" : "jetty.sh";
  }

  private static File getJettyLauncherFile()
  {
    final String binDir =
      PathManager.getPluginsPath() + File.separator + JettyManager.PLUGIN_NAME + File.separator + BIN_DIR;
    return new File(binDir, getDefaultJettyLauncherFileName());
  }

  public static void ensureExecutable()
  {
    if (!SystemInfo.isWindows) {
      final Logger logger = Logger.getInstance(JettyManager.class.getName());
      final File jettyLauncherFile = getJettyLauncherFile();
      final ProcessBuilder processBuilder = new ProcessBuilder("/bin/chmod", "+x", jettyLauncherFile.getAbsolutePath());
      final String errorMessage = "Couldn't set executable bit on " + jettyLauncherFile.getAbsolutePath();

      try {
        final Process process = processBuilder.start();
        final int exitValue = process.waitFor();

        if (exitValue != 0) {
          logger.warn(errorMessage);
        }
      }
      catch (IOException e) {
        logger.error(errorMessage, e);
      }
      catch (InterruptedException e) {
        logger.error(errorMessage, e);
      }
    }
  }

  public ScriptsHelper getStartupHelper()
  {
    return new ScriptsHelper()
    {
      public ExecutableObject getDefaultScript(final CommonModel model)
      {
        final File jettyLauncherFile = getJettyLauncherFile();
        return new CommandLineExecutableObject(new String[]{ jettyLauncherFile.getAbsolutePath() }, null);
      }
    };
  }

  public ScriptsHelper getShutdownHelper()
  {
    return new ScriptsHelper()
    {
      public ExecutableObject getDefaultScript(final CommonModel model)
      {
        final File jettyLauncherFile = getJettyLauncherFile();
        return new CommandLineExecutableObject(new String[]{ jettyLauncherFile.getAbsolutePath() }, null);
      }
    };
  }

  public EnvironmentHelper getEnvironmentHelper()
  {
    final EnvironmentHelper helper = new EnvironmentHelper()
    {
      @Override
      public List<EnvironmentVariable> getAdditionalEnvironmentVariables(final CommonModel model)
      {
        final List<EnvironmentVariable> vars = new ArrayList<EnvironmentVariable>();

        try {
          final ProjectJdk projectJdk = ProjectRootManager.getInstance(model.getProject()).getProjectJdk();

          if (projectJdk != null) {
            vars.add(new EnvironmentVariable(
              JAVA_HOME_ENV_PROPERTY, projectJdk.getHomePath().replace('/', File.separatorChar), true));
          }
          else {
            final String javaHome = EnvironmentUtil.getEnviromentProperties().get(JAVA_HOME_ENV_PROPERTY);

            if (javaHome != null) {
              vars.add(new EnvironmentVariable(JAVA_HOME_ENV_PROPERTY, javaHome, true));
            }
          }

          final JettyModel jettyModel = (JettyModel) model.getServerModel();
          vars.add(new EnvironmentVariable(JettyConstants.JETTY_HOME_ENV_VAR, jettyModel.getHomeDirectory(), true));
          final int stopPort = jettyModel.getStopPort();

          if (stopPort == 0) {
            final String[] configFilePaths = jettyModel.getActiveConfigFilePaths();
            final File scratchDirectory = jettyModel.getScratchDirectory();
            final StringBuilder sb = new StringBuilder(JETTY_START_COMMAND);

            for (final String configFilePath : configFilePaths) {
              sb.append(' ').append(configFilePath);
            }

            sb.append(' ').append(new File(scratchDirectory, JETTY_CONTEXT_DEPLOYER_CONFIG_FILE_NAME));
            vars.add(new EnvironmentVariable(JettyConstants.JETTY_OPTS_ENV_VAR, sb.toString(), true));
          }
          else {
            final String stopKey = jettyModel.getStopKey();
            final String jettyOptsEnvVar = MessageFormat.format(JETTY_STOP_COMMAND_TEMPLATE, stopPort, stopKey);

            vars.add(new EnvironmentVariable(JettyConstants.JETTY_OPTS_ENV_VAR, jettyOptsEnvVar, true));
            jettyModel.setStopPort(0);
          }
        }
        catch (RuntimeConfigurationException e) {
          // Ignore
        }

        return vars;
      }

      @Override
      public String getDefaultJavaVmEnvVariableName(final CommonModel model)
      {
        return JAVA_VM_ENV_VARIABLE;
      }
    };

    return helper;
  }
}
