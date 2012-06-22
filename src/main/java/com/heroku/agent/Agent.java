package com.heroku.agent;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.instrument.Instrumentation;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class Agent {

    Timer timer = new Timer("Heroku Agent Timer",/* daemon */true);
    static Agent agent;

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        agent = new Agent();
    }

    public Agent() {
        timer.scheduleAtFixedRate(new Reporter(), 5000, 5000);
    }

    public static class Reporter extends TimerTask {

        //private final String AWS_ACCESS_KEY = System.getenv("S3_KEY");
        //private final String AWS_SECRET = System.getenv("S3_SECRET");
        private final String APP_NAME = System.getenv("APP_NAME");
        private final String API_BASE = System.getenv("API_BASE") != null ? System.getenv("APP_NAME") : "https://jstethoscope.herokuapp.com";
        private final String BUCKET_NAME = APP_NAME + "-java-diagnostics";

        public Reporter() {
        }

        @Override
        public void run() {
            checkIn();
            String command = getCommand();
            if (command != null && !"none".equals(command)) {
                execCommand(command);
            }
        }

        private String checkIn() {
            String response = null;
            try {
                formatAndOutput("calling: " + API_BASE + "/api/"
                        + APP_NAME);
                response = hitUrl(API_BASE + "/api/" + APP_NAME,
                        false);
                formatAndOutput("response: " + response);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return response;
        }

        private String getCommand() {
            String response = null;
            try {
                formatAndOutput("calling " + API_BASE + "/api/" + APP_NAME
                        + "/command");
                response = hitUrl(API_BASE + "/api/" + APP_NAME
                        + "/command", false);
                formatAndOutput("response: " + response);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return response;

        }

        private String ackCommand(String command) {
            try {
                formatAndOutput("calling " + API_BASE + "/api/" + APP_NAME
                        + "/ack/");
                return hitUrl(API_BASE + "/api/" + APP_NAME + "/ack/" + command, true);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return "none";
        }
        
        private void errorCommand(String command) {
            try {
                formatAndOutput("calling " + API_BASE + "/api/" + APP_NAME
                        + "/error/" + command);
                hitUrl(API_BASE + "/api/" + APP_NAME + "/error/" + command, true);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        private void execCommand(String command) {
            try {
                if ("jmap".equals(command)) {
                    String commandId = ackCommand(command);
                    formatAndOutput("response from ack: " + commandId);
                    if(!"none".equals(commandId)) {
                        runJmap(Long.valueOf(commandId));
                    }
                    
                } else if ("jstack".equals(command)) {
                    String commandId = ackCommand(command);
                    formatAndOutput("response from ack: " + commandId);
                    if(!"none".equals(commandId)) {
                        runJstack(Long.valueOf(commandId));
                    }
                } else {
                    errorCommand(command);
                }
            } catch (Exception e) {
                formatAndOutput("Execution of " + command + " command failed");
                e.printStackTrace();
            }
        }

        private String hitUrl(String urlLoc, boolean post)
                throws MalformedURLException, IOException {
            BufferedReader in = null;
            try {
                URL url = new URL(urlLoc);
                HttpURLConnection conn = (HttpURLConnection) url
                        .openConnection();
                if (post) {
                    conn.setRequestMethod("POST");
                }
                in = new BufferedReader(new InputStreamReader(
                        conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                return response.toString();
            } catch (MalformedURLException e) {
                throw e;
            } catch (IOException e) {
                throw e;
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        formatAndOutput("Error closing URL input stream");
                        e.printStackTrace();
                    }
                }
            }
        }

        private final static String getTimeStamp() {
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd:hh-mm-ss");
            return (df.format(new Date()));
        }

        private void runStackDump(int pid, String filename) throws IOException {
            String command = "jmap -dump:file=" + filename + " " + pid;
            formatAndOutput("running " + command);
            Process p = Runtime.getRuntime().exec(command);

            String line = null;
            BufferedReader bri = new BufferedReader(new InputStreamReader(
                    p.getInputStream()));
            while ((line = bri.readLine()) != null) {
                formatAndOutput(line);
            }
        }
        
        private void runJmap(Long commandId) throws Exception {
            int pid = findPid();
            if (pid != 0) {
                BufferedReader reader = runJmap(pid);
                try {
                    sendBackToApi(reader, commandId);
                } finally {
                    reader.close();
                }
            }            
        }
        
        private BufferedReader runJmap(int pid) throws IOException {
            String command = "jmap -heap " + pid;
            formatAndOutput("running " + command);
            Process p = Runtime.getRuntime().exec(command);
            
            return new BufferedReader(new InputStreamReader(
                    p.getInputStream()));
        }

        private void runJstack(Long commandId) throws Exception {
            int pid = findPid();
            if (pid != 0) {
                BufferedReader reader = runJstack(pid);
                try {
                    sendBackToApi(reader, commandId);
                } finally {
                    reader.close();
                }
            }
        }

        private void runJstack(int pid, String filename) throws IOException {
            String command = "jstack -l " + pid;
            formatAndOutput("running " + command);
            Process p = Runtime.getRuntime().exec(command);

            File file = new File(filename);
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            String line = null;
            BufferedReader bri = new BufferedReader(new InputStreamReader(
                    p.getInputStream()));
            while ((line = bri.readLine()) != null) {
                writer.write(line);
                writer.newLine();
            }
            writer.flush();
            writer.close();
        }
        
        private BufferedReader runJstack(int pid) throws IOException {
            String command = "jstack -l " + pid;
            formatAndOutput("running " + command);
            Process p = Runtime.getRuntime().exec(command);
            
            return new BufferedReader(new InputStreamReader(
                    p.getInputStream()));
        }

        private int findPid() throws IOException {
            Process p = Runtime.getRuntime().exec("jps");

            String line = null;
            BufferedReader bri = new BufferedReader(new InputStreamReader(
                    p.getInputStream()));
            while ((line = bri.readLine()) != null) {
                int pid = getPid(line);
                if (pid != 0) {
                    return pid;
                }
            }
            return 0;
        }

        private int getPid(String line) {
            if (!line.contains("Jps")) {
                String[] procInfo = line.split(" ");
                int pid = 0;
                try {
                    pid = Integer.valueOf(procInfo[0]);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
                return pid;
            }
            return 0;
        }

        private void formatAndOutput(String out) {
            System.out.print("heroku-diagnostic-agent: ");
            System.out.println(out);
        }

//        private void uploadToS3(String filename) {
//            AmazonS3 s3 = new AmazonS3Client(new BasicAWSCredentials(
//                    AWS_ACCESS_KEY, AWS_SECRET));
//            File file = new File(filename);
//            formatAndOutput("saving " + filename + " to " + BUCKET_NAME
//                    + " bucket at key: " + file.getName());
//            s3.putObject(new PutObjectRequest(BUCKET_NAME, file.getName(), file));
//        }
        
        
        
        private URLConnection openUrlForSend(String urlLoc, String boundary) throws IOException {
            URL url = new URL(urlLoc);
            URLConnection conn = url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            return conn;
        }
        
        private void sendBackToApi(BufferedReader reader, Long commandId) throws Exception {
            formatAndOutput("sending data to api for command: " + commandId);
            String boundary = Long.toHexString(System.currentTimeMillis()); // Just generate some unique random value.
            String CRLF = "\r\n"; // Line separator required by multipart/form-data.
            String charset = "UTF-8";

            URLConnection connection = null;
            PrintWriter writer = null;
            try {
                connection = openUrlForSend(API_BASE + "/api/cmd/" + commandId, boundary);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                OutputStream output = connection.getOutputStream();
                writer = new PrintWriter(new OutputStreamWriter(output, charset), true); // true = autoFlush, important!

                // Send command param.
                writer.append("--" + boundary).append(CRLF);
                writer.append("Content-Disposition: form-data; name=\"command\"").append(CRLF);
                writer.append("Content-Type: text/plain; charset=" + charset).append(CRLF);
                writer.append(CRLF);
                writer.append("jstack").append(CRLF).flush();

                // Send text file.
                writer.append("--" + boundary).append(CRLF);
                writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"commandOutput\"").append(CRLF);
                writer.append("Content-Type: text/plain; charset=" + charset).append(CRLF);
                writer.append(CRLF).flush();
                for (String line; (line = reader.readLine()) != null;) {
                    writer.append(line).append(CRLF);
                }
                writer.flush();

                // End of multipart/form-data.
                writer.append("--" + boundary + "--").append(CRLF);
                
            } finally {
                if (writer != null) writer.close();
            }      
            
            BufferedReader in = null;
            try {
                in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            } finally {
                if (in != null) in.close();
            }
        }
    }

}
