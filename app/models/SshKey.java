package models;

import java.io.File;
import java.io.IOException;

import java.io.*;

public class SshKey {
	
	private File dir;
	private String filename;
	private String passPhrase;
	
	public SshKey(File dir) {
		this(dir, "id_rsa");
	}
	
	public SshKey(File dir, String filename) {
		this(dir, filename, "");
	}
	
	public SshKey(File dir, String filename, String passPhrase) {
		this.dir = dir;
		this.filename = filename;
		this.passPhrase = passPhrase;
	}
	
	public boolean exists() {
		File privateKey = new File(this.dir, this.filename);
		File publicKey = new File(this.dir, this.filename + ".pub");
		return privateKey.exists() && publicKey.exists();
	}
	
	public int generate() throws IOException {
		if (exists()) {
			return -1;
		}
		String[] commands = {
			"ssh-keygen",
			"-f",
			dir.toString() + File.separator + filename,
			"-N",
			passPhrase
		};
		Process p = Runtime.getRuntime().exec(commands);
		new ReadThread("out", p.getInputStream()).start();
		new ReadThread("err", p.getErrorStream()).start();
		try {
			p.waitFor();
		} catch (InterruptedException e) {
		}
		return p.exitValue();
	}
	
	private static class ReadThread extends Thread {
		
		private String name;
		private InputStream is;
		
		public ReadThread(String name, InputStream is) {
			this.name = name;
			this.is = is;
		}
		
		public void run() {
			try {
				BufferedReader reader = new BufferedReader(new InputStreamReader(is));
				try {
					String line = reader.readLine();
					while (line != null) {
						System.out.println(name + ": " + line);
						line = reader.readLine();
					}
				} finally {
					reader.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
