# Heroku Diagnostic Agent

This Java agent will run jmap and jstack every 10 seconds, dump the output to files, and upload those files to S3.

## Usage

Build the agent with `mvn package`. After building you'll have `heroku-diagnostic-agent.jar` in the target directory.

Copy `heroku-diagnostic-agent.jar` to a folder called agent under the main directory of the application that you want to use the agent with.

Set 3 environment variables (config params if running on Heroku): S3_KEY, S3_SECRET, APP_NAME. These should hold the S3 credentials and the name of your application respectively.

In the S3 account pointed to by the above credentials make sure there's a bucket called `[app name]-java-diagnostics`.

When launching your application (or creating your Procfile for Heroku) add the flag `-javaagent:agent/heroku-diagnostic-agent.jar`.

Now when you run your application locally or on Heroku the agent will pull diagnsotics for you.

Note: The agent will use `jps` to find the process id of your Java process. It will use the first Java process that it finds (other than Jps itself). If running locally unsure that the process you want diagnostics for is the only Java application running.
