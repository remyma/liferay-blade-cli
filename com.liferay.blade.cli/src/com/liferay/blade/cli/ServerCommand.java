package com.liferay.blade.cli;

import aQute.lib.getopt.Description;
import aQute.lib.getopt.Options;

import java.io.File;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * @author David Truong
 */
public class ServerCommand {

	public ServerCommand(blade blade, ServerOptions options) {
		_blade = blade;
		_options = options;
	}

	@Description("Start server defined by your project")
	public void _start(ServerStartOptions options) throws Exception {
		executeCommand("start", options);
	}

	@Description("Stop server defined by your project")
	public void _stop(ServerStopOptions options) throws Exception {
		executeCommand("stop", options);
	}

	@Description("Start or stop server defined by your project")
	public interface ServerOptions extends Options {
	}

	@Description("Start server defined by your project")
	public interface ServerStartOptions extends ServerOptions {

		@Description("Start server in background")
		public boolean background();

		@Description("Start server in debug mode")
		public boolean debug();

		@Description("Tail a running server")
		public boolean tail();

	}

	@Description("Stop server defined by your project")
	public interface ServerStopOptions extends ServerOptions {
	}

	private void commandServer(
			String cmd, File dir, String serverType, ServerOptions options)
		throws Exception {

		for (File file : dir.listFiles()) {
			String fileName = file.getName();

			if (fileName.startsWith(serverType) && file.isDirectory()) {
				if (serverType.equals("tomcat")) {
					commmandTomcat(cmd, file, options);

					return;
				}
				else if (serverType.equals("jboss") ||
						 serverType.equals("wildfly")) {

					commmandJBossWildfly(cmd, file, options);

					return;
				}
			}
		}

		_blade.error(serverType + " not supported");
	}

	private void commmandJBossWildfly(
			String cmd, File dir, ServerOptions options)
		throws Exception {

		Map<String, String> enviroment = new HashMap<>();

		String executable = "./standalone.sh";

		if (Util.isWindows()) {
			executable = "standalone.bat";
		}

		if (cmd.equals("start")) {
			ServerStartOptions startOptions = (ServerStartOptions)options;

			String debug = "";

			if (startOptions.debug()) {
				debug = " --debug";
			}

			Process process = Util.startProcess(
				_blade, executable + debug, new File(dir, "bin"), enviroment);

			process.waitFor();
		}
		else {
			_blade.error("JBoss/Wildfly supports start command and debug flag");
		}
	}

	private void commmandTomcat(String cmd, File dir, ServerOptions options)
		throws Exception {

		Map<String, String> enviroment = new HashMap<>();

		enviroment.put("CATALINA_PID", "catalina.pid");

		String executable = "./catalina.sh";

		if (Util.isWindows()) {
			executable = "catalina.bat";
		}

		if (cmd.equals("start")) {
			ServerStartOptions startOptions = (ServerStartOptions)options;

			String startCommand = " run";

			if (startOptions.background()) {
				startCommand = " start";
			}
			else if (startOptions.debug()) {
				startCommand = " jpda " + startCommand;
			}

			Process process = Util.startProcess(
				_blade, executable + startCommand, new File(dir, "bin"),
				enviroment);

			process.waitFor();

			if (startOptions.background() && startOptions.tail()) {
				process = Util.startProcess(
					_blade, "tail -f catalina.out", new File(dir, "logs"),
					enviroment);

				process.waitFor();
			}
		}
		else if (cmd.equals("stop")) {
			Process process = Util.startProcess(
				_blade, executable + " stop 60 -force", new File(dir, "bin"),
				enviroment);

			process.waitFor();
		}
	}

	private void executeCommand(String cmd, ServerOptions options)
		throws Exception {

		File gradleWrapper = Util.getGradleWrapper(_blade.getBase());

		File rootDir = gradleWrapper.getParentFile();

		String serverType = null;

		if (Util.isWorkspace(rootDir)) {
			Properties properties = Util.getGradleProperties(rootDir);

			String liferayHomePath = properties.getProperty(
				Workspace.DEFAULT_LIFERAY_HOME_DIR_PROPERTY);

			if ((liferayHomePath == null) || liferayHomePath.equals("")) {
				liferayHomePath = Workspace.DEFAULT_LIFERAY_HOME_DIR;
			}

			serverType = properties.getProperty(
				Workspace.DEFAULT_BUNDLE_ARTIFACT_NAME_PROPERTY);

			if (serverType == null) {
				serverType = Workspace.DEFAULT_BUNDLE_ARTIFACT_NAME;
			}

			serverType = serverType.replace("portal-", "");

			serverType = serverType.replace("-bundle", "");

			commandServer(
				cmd, new File(rootDir, liferayHomePath), serverType, options);
		}
		else {
			try {
				List<Properties> propertiesList = Util.getAppServerProperties(
					rootDir);

				String appServerParentDir = "";

				for (Properties properties : propertiesList) {
					if (appServerParentDir.equals("")) {
						String appServerParentDirTemp = properties.getProperty(
							Util.APP_SERVER_PARENT_DIR_PROPERTY);

						if ((appServerParentDirTemp != null) &&
							!appServerParentDirTemp.equals("")) {

							appServerParentDirTemp =
								appServerParentDirTemp.replace(
									"${project.dir}",
									rootDir.getCanonicalPath());

							appServerParentDir = appServerParentDirTemp;
						}
					}

					if ((serverType == null) || serverType.equals("")) {
						String serverTypeTemp = properties.getProperty(
							Util.APP_SERVER_TYPE_PROPERTY);

						if ((serverTypeTemp != null) &&
							!serverTypeTemp.equals("")) {

							serverType = serverTypeTemp;
						}
					}
				}

				if (appServerParentDir.startsWith("/") ||
					appServerParentDir.contains(":")) {

					commandServer(
						cmd, new File(appServerParentDir), serverType, options);
				}
				else {
					commandServer(
						cmd, new File(rootDir, appServerParentDir), serverType,
						options);
				}
			}
			catch (Exception e) {
				_blade.error(
					"Please execute this command from a Liferay project");
			}
		}
	}

	private blade _blade;
	private ServerOptions _options;

}