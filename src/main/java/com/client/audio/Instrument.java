package com.client.audio;

import com.client.io.Buffer;

public final class Instrument {

	public static void initialise() {
		noise = new int[32768];
		for (int i = 0; i < 32768; i++)
			if (Math.random() > 0.5D)
				noise[i] = 1;
			else
				noise[i] = -1;

		sine = new int[32768];
		for (int j = 0; j < 32768; j++)
			sine[j] = (int) (Math.sin(j / 5215.1903000000002D) * 16384D);

		output = new int[0x35d54];
	}

	public int[] synthesize(int steps, int j) {
		for (int position = 0; position < steps; position++)
			output[position] = 0;

		if (j < 10)
			return output;
		double d = steps / (j + 0.0D);
		pitchEnvelope.resetValues();
		volumeEnvelope.resetValues();
		int pitchModulationStep = 0;
		int pitchModulationBaseStep = 0;
		int pitchModulationPhase = 0;
		if (pitchModulationEnvelope != null) {
			pitchModulationEnvelope.resetValues();
			pitchModulationAmplitudeEnvelope.resetValues();
			pitchModulationStep = (int) (((pitchModulationEnvelope.end - pitchModulationEnvelope.start) * 32.768000000000001D) / d);
			pitchModulationBaseStep = (int) ((pitchModulationEnvelope.start * 32.768000000000001D) / d);
		}
		int volumeModulationStep = 0;
		int volumeModulationBaseStep = 0;
		int volumeModulationPhase = 0;
		if (volumeModulationEnvelope != null) {
			volumeModulationEnvelope.resetValues();
			volumeModulationAmplitude.resetValues();
			volumeModulationStep = (int) (((volumeModulationEnvelope.end - volumeModulationEnvelope.start) * 32.768000000000001D) / d);
			volumeModulationBaseStep = (int) ((volumeModulationEnvelope.start * 32.768000000000001D) / d);
		}
		for (int oscillationVolumeId = 0; oscillationVolumeId < 5; oscillationVolumeId++)
			if (oscillationVolume[oscillationVolumeId] != 0) {
				phases[oscillationVolumeId] = 0;
				delays[oscillationVolumeId] = (int) (oscillationDelay[oscillationVolumeId] * d);
				volumeStep[oscillationVolumeId] = (oscillationVolume[oscillationVolumeId] << 14) / 100;
				pitchStep[oscillationVolumeId] = (int) (((pitchEnvelope.end - pitchEnvelope.start) * 32.768000000000001D * Math
						.pow(1.0057929410678534D, oscillationPitch[oscillationVolumeId])) / d);
				pitchBaseStep[oscillationVolumeId] = (int) ((pitchEnvelope.start * 32.768000000000001D) / d);
			}

		for (int offset = 0; offset < steps; offset++) {
			int pitchChange = pitchEnvelope.step(steps);
			int volumeChange = volumeEnvelope.step(steps);
			if (pitchModulationEnvelope != null) {
				int modulation = pitchModulationEnvelope.step(steps);
				int modulationAmplitude = pitchModulationAmplitudeEnvelope.step(steps);
				pitchChange += evaluateWave(modulationAmplitude, pitchModulationPhase, pitchModulationEnvelope.form) >> 1;
				pitchModulationPhase += (modulation * pitchModulationStep >> 16) + pitchModulationBaseStep;
			}
			if (volumeModulationEnvelope != null) {
				int modulation = volumeModulationEnvelope.step(steps);
				int modulationAmplitude = volumeModulationAmplitude.step(steps);
				volumeChange = volumeChange
						* ((evaluateWave(modulationAmplitude, volumeModulationPhase, volumeModulationEnvelope.form) >> 1) + 32768) >> 15;
				volumeModulationPhase += (modulation * volumeModulationStep >> 16) + volumeModulationBaseStep;
			}
			for (int oscillationId = 0; oscillationId < 5; oscillationId++)
				if (oscillationVolume[oscillationId] != 0) {
					int position = offset + delays[oscillationId];
					if (position < steps) {
						output[position] += evaluateWave(
								volumeChange * volumeStep[oscillationId] >> 15,
								phases[oscillationId], pitchEnvelope.form);
						phases[oscillationId] += (pitchChange * pitchStep[oscillationId] >> 16)
								+ pitchBaseStep[oscillationId];
					}
				}

		}

		if (gatingReleaseEnvelope != null) {
			gatingReleaseEnvelope.resetValues();
			gatingAttackEnvelope.resetValues();
			int counter = 0;
			boolean muted = true;
			for (int position = 0; position < steps; position++) {
				int stepOn = gatingReleaseEnvelope.step(steps);
				int stepOff = gatingAttackEnvelope.step(steps);
				int threshold;
				if (muted)
					threshold = gatingReleaseEnvelope.start
							+ ((gatingReleaseEnvelope.end - gatingReleaseEnvelope.start)
									* stepOn >> 8);
				else
					threshold = gatingReleaseEnvelope.start
							+ ((gatingReleaseEnvelope.end - gatingReleaseEnvelope.start)
									* stepOff >> 8);
				if ((counter += 256) >= threshold) {
					counter = 0;
					muted = !muted;
				}
				if (muted)
					output[position] = 0;
			}

		}
		if (delayTime > 0 && delayFeedback > 0) {
			int delay = (int) (delayTime * d);
			for (int position = delay; position < steps; position++)
				output[position] += (output[position - delay] * delayFeedback) / 100;

		}
		if (filter.pairCount[0] > 0
				|| filter.pairCount[1] > 0) {
			filterEnvelope.resetValues();
			int t = filterEnvelope.step(steps + 1);
			int M = filter.compute(0, t / 65536F);
			int N = filter.compute(1, t / 65536F);
			if (steps >= M + N) {
				int n = 0;
				int delay = N;
				if (delay > steps - M)
					delay = steps - M;
				for (; n < delay; n++) {
					int y = (int) ((long) output[n + M]
							* (long) SoundFilter.invUnity >> 16);
					for (int k8 = 0; k8 < M; k8++)
						y += (int) ((long) output[(n + M) - 1 - k8]
								* (long) SoundFilter.coefficient[0][k8] >> 16);

					for (int j9 = 0; j9 < n; j9++)
						y -= (int) ((long) output[n - 1 - j9]
								* (long) SoundFilter.coefficient[1][j9] >> 16);

					output[n] = y;
					t = filterEnvelope.step(steps + 1);
				}

				char offset = '\200';
				delay = offset;
				do {
					if (delay > steps - M)
						delay = steps - M;
					for (; n < delay; n++) {
						int y = (int) ((long) output[n + M]
								* (long) SoundFilter.invUnity >> 16);
						for (int k9 = 0; k9 < M; k9++)
							y += (int) ((long) output[(n + M) - 1
									- k9]
									* (long) SoundFilter.coefficient[0][k9] >> 16);

						for (int i10 = 0; i10 < N; i10++)
							y -= (int) ((long) output[n - 1 - i10]
									* (long) SoundFilter.coefficient[1][i10] >> 16);

						output[n] = y;
						t = filterEnvelope.step(steps + 1);
					}

					if (n >= steps - M)
						break;
					M = filter.compute(0, t / 65536F);
					N = filter.compute(1, t / 65536F);
					delay += offset;
				} while (true);
				for (; n < steps; n++) {
					int y = 0;
					for (int l9 = (n + M) - steps; l9 < M; l9++)
						y += (int) ((long) output[(n + M) - 1 - l9]
								* (long) SoundFilter.coefficient[0][l9] >> 16);

					for (int j10 = 0; j10 < N; j10++)
						y -= (int) ((long) output[n - 1 - j10]
								* (long) SoundFilter.coefficient[1][j10] >> 16);

					output[n] = y;
					filterEnvelope.step(steps + 1);
				}

			}
		}
		for (int position = 0; position < steps; position++) {
			if (output[position] < -32768)
				output[position] = -32768;
			if (output[position] > 32767)
				output[position] = 32767;
		}

		return output;
	}

	private int evaluateWave(int amplitude, int phase, int table) {
		if (table == 1)
			if ((phase & 0x7fff) < 16384)
				return amplitude;
			else
				return -amplitude;
		if (table == 2)
			return sine[phase & 0x7fff] * amplitude >> 14;
		if (table == 3)
			return ((phase & 0x7fff) * amplitude >> 14) - amplitude;
		if (table == 4)
			return noise[phase / 2607 & 0x7fff] * amplitude;
		else
			return 0;
	}

	public void decode(Buffer stream) {
		pitchEnvelope = new Envelope();
		pitchEnvelope.decode(stream);
		volumeEnvelope = new Envelope();
		volumeEnvelope.decode(stream);
		int option = stream.readUnsignedByte();
		if (option != 0) {
			stream.position--;
			pitchModulationEnvelope = new Envelope();
			pitchModulationEnvelope.decode(stream);
			pitchModulationAmplitudeEnvelope = new Envelope();
			pitchModulationAmplitudeEnvelope.decode(stream);
		}
		option = stream.readUnsignedByte();
		if (option != 0) {
			stream.position--;
			volumeModulationEnvelope = new Envelope();
			volumeModulationEnvelope.decode(stream);
			volumeModulationAmplitude = new Envelope();
			volumeModulationAmplitude.decode(stream);
		}
		option = stream.readUnsignedByte();
		if (option != 0) {
			stream.position--;
			gatingReleaseEnvelope = new Envelope();
			gatingReleaseEnvelope.decode(stream);
			gatingAttackEnvelope = new Envelope();
			gatingAttackEnvelope.decode(stream);
		}
		for (int oscillationId = 0; oscillationId < 10; oscillationId++) {
			int volume = stream.readUSmart();
			if (volume == 0)
				break;
			oscillationVolume[oscillationId] = volume;
			oscillationPitch[oscillationId] = stream.method421();
			oscillationDelay[oscillationId] = stream.readUSmart();
		}

		delayTime = stream.readUSmart();
		delayFeedback = stream.readUSmart();
		duration = stream.readUShort();
		begin = stream.readUShort();
		filter = new SoundFilter();
		filterEnvelope = new Envelope();
		filter.decode(stream, filterEnvelope);
	}

	public Instrument() {
		oscillationVolume = new int[5];
		oscillationPitch = new int[5];
		oscillationDelay = new int[5];
		delayFeedback = 100;
		duration = 500;
	}

	private Envelope pitchEnvelope;
	private Envelope volumeEnvelope;
	private Envelope pitchModulationEnvelope;
	private Envelope pitchModulationAmplitudeEnvelope;
	private Envelope volumeModulationEnvelope;
	private Envelope volumeModulationAmplitude;
	private Envelope gatingReleaseEnvelope;
	private Envelope gatingAttackEnvelope;
	private final int[] oscillationVolume;
	private final int[] oscillationPitch;
	private final int[] oscillationDelay;
	private int delayTime;
	private int delayFeedback;
	private SoundFilter filter;
	private Envelope filterEnvelope;
	int duration;
	int begin;
	private static int[] output;
	private static int[] noise;
	private static int[] sine;
	private static final int[] phases = new int[5];
	private static final int[] delays = new int[5];
	private static final int[] volumeStep = new int[5];
	private static final int[] pitchStep = new int[5];
	private static final int[] pitchBaseStep = new int[5];

}
