package com.github.cecchisandrone.vc.audio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Mixer;

import com.github.cecchisandrone.vc.wit.WitClient;

public class MicrophoneTest {

	// record duration, in milliseconds
	static final long RECORD_TIME = 5000;

	private static AudioFormat audioFormat = new AudioFormat(32000, 8, 1, true,
			false);

	/**
	 * Entry to run the program
	 * 
	 * @throws URISyntaxException
	 * @throws IOException
	 */
	public static void main(String[] args) throws URISyntaxException,
			IOException {

		int i = 0;
		for (Mixer.Info info : AudioSystem.getMixerInfo()) {
			System.out.println("[" + i++ + "] " + info);
		}

		String indexString;
		if (args == null || args.length == 0) {
			System.out.print("Please enter the line number: ");
			BufferedReader br = new BufferedReader(new InputStreamReader(
					System.in));
			indexString = br.readLine();
		} else {
			indexString = args[0];
		}

		final Microphone recorder = new Microphone(audioFormat,
				Integer.parseInt(indexString));
		recorder.open();

		WitClient witClient = new WitClient("https://api.wit.ai/speech",
				audioFormat);

		// creates a new thread that waits for a specified
		// of time before stopping
		Thread stopper = new Thread(new Runnable() {
			public void run() {
				try {
					Thread.sleep(RECORD_TIME);
				} catch (InterruptedException ex) {
					ex.printStackTrace();
				}
				recorder.stop();
			}
		});

		stopper.start();

		// start recording
		MicrophoneInputStream inputStream = recorder.start();
		witClient.sendChunkedAudio(inputStream);

		recorder.close();
	}
}
