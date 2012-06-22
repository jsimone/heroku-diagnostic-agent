# Heroku Diagnostic Agent

This Java agent allows you to take diagnostics via the built in java profiling commands (jstack, jmap) from a Java process running on a remote server that you cannot connect to directly. A Heroku dyno is an example of such a process.

It works by running a thread that periodically checks in with a diagnostic endpoint (defaulted to jstethescope.herokuapp.com). From the web console on that endpoint you can tell your app to run diagnostic commands and the output will be returned to the web console. 

## Usage

Build the agent with `mvn package`. After building you'll have `heroku-diagnostic-agent.jar` in the target directory.

Copy `heroku-diagnostic-agent.jar` to a folder called agent under the main directory of the application that you want to use the agent with.

Set the APP_NAME environment variable (config param if running on Heroku). This should hold a unique name for your application. If on Heroku it's a good idea to use the Heroku app name for this.

When launching your application (or creating your Procfile for Heroku) add the flag `-javaagent:agent/heroku-diagnostic-agent.jar`.

Note: The agent will use `jps` to find the process id of your Java process. It will use the first Java process that it finds (other than Jps itself). If running locally unsure that the process you want diagnostics for is the only Java application running.
