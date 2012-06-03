package com.heroku.agent;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.instrument.Instrumentation;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.PutObjectRequest;


public class Agent {

    Timer timer = new Timer("Heroku Agent Timer",/*daemon*/true);
    static Agent agent;


    public static void premain(String agentArgs, Instrumentation instrumentation) {
        agent = new Agent();
    }

    public Agent() {
        timer.scheduleAtFixedRate(new Reporter(), 5000, 60000);
    }


    public static class Reporter extends TimerTask {

    	private final String AWS_ACCESS_KEY = System.getenv("S3_KEY");
    	private final String AWS_SECRET = System.getenv("S3_SECRET");
    	private final String BUCKET_NAME = System.getenv("APP_NAME") + "-java-diagnostics";

    	public Reporter() {
        }

        @Override
        public void run() {
        	try {
        		diagdump();
			} catch (IOException e) {
				e.printStackTrace();
			}
        }
        
        private void diagdump() throws IOException{
    		int pid = findPid();
			if(pid != 0) {
				String jstackFile = "jstack-" + getTimeStamp() + ".dump";
				String heapFile = "heap-" + getTimeStamp() + ".bin";
				runJstack(pid, jstackFile);
				runJmap(pid, heapFile);
				uploadToS3(jstackFile);
				uploadToS3(heapFile);
			}
        }
        
        private  final static String getTimeStamp() {  
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd:hh-mm-ss");  
            return (df.format(new Date()));  
        }  
        
        private void runJmap(int pid, String filename) throws IOException {
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
        
        private void runJstack(int pid, String filename) throws IOException {
        	String command = "jstack -l " + pid;
        	formatAndOutput("running " + command);
        	Process p = Runtime.getRuntime().exec(command);
        	
        	File file = new File(filename);
        	BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        	String line = null;
        	BufferedReader bri = new BufferedReader(new InputStreamReader(p.getInputStream()));
        	while ((line = bri.readLine()) != null) {
        		writer.write(line);
        		writer.newLine();
        	}
        	writer.flush();
        	writer.close();
        }
        
        private int findPid() throws IOException{
        	Process p = Runtime.getRuntime().exec("jps");
        	
        	String line = null;
        	BufferedReader bri = new BufferedReader(new InputStreamReader(
        			p.getInputStream()));
        	while ((line = bri.readLine()) != null) {
        		int pid = getPid(line);
        		if(pid != 0) {
        			return pid;
        		}
        	}
        	return 0;
        }

        private int getPid(String line) {
        	if(!line.contains("Jps")) {
        		String[] procInfo = line.split(" ");
        		int pid = 0;
        		try {
        			pid = Integer.valueOf(procInfo[0]);
        		} catch(NumberFormatException e) {
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
        
        private void uploadToS3(String filename) {
            AmazonS3 s3 = new AmazonS3Client(new BasicAWSCredentials(AWS_ACCESS_KEY, AWS_SECRET));
            File file = new File(filename);
            formatAndOutput("saving " + filename + " to " + BUCKET_NAME + " bucket at key: " + file.getName());
            s3.putObject(new PutObjectRequest(BUCKET_NAME, file.getName(), file));
        }
    }


}
