/**
 * 
 */
package marytts.util.data.text;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author marc
 * 
 */
public class PraatPitchTier implements PraatTier {

	protected static final String FIRSTLINE = "File type = \"ooTextFile\"";
	protected static final String SECONDLINE = "Object class = \"PitchTier\"";
	protected double xmin;
	protected double xmax;
	protected int numTargets;
	protected PitchTarget[] targets;

	public PraatPitchTier(Reader input) throws IOException {
		BufferedReader lineReader = new BufferedReader(input);
		try {
			String filetype = lineReader.readLine();
			if (!filetype.equals(FIRSTLINE)) {
				throw new IllegalArgumentException("First line expected to be '" + FIRSTLINE + "' but was '" + filetype + "'");
			}
			String subtype = lineReader.readLine();
			if (!subtype.equals(SECONDLINE)) {
				throw new IllegalArgumentException("Second line expected to be '" + SECONDLINE + "' but was '" + subtype + "'");
			}

			lineReader.readLine(); // skip empty line
			String xminLine = lineReader.readLine();
			String xmaxLine = lineReader.readLine();
			String numTargetsLine = lineReader.readLine();
			try {
				xmin = Double.parseDouble(xminLine);
				xmax = Double.parseDouble(xmaxLine);
				numTargets = Integer.parseInt(numTargetsLine);
			} catch (NumberFormatException nfe) {
				String[] fields = xminLine.split("\\s+");
				xmin = Double.parseDouble(fields[fields.length - 1]);
				fields = xmaxLine.split("\\s+");
				xmax = Double.parseDouble(fields[fields.length - 1]);
				fields = numTargetsLine.split("\\s+");
				numTargets = Integer.parseInt(fields[fields.length - 1]);
			}
			targets = new PitchTarget[numTargets];
			for (int i = 0; i < numTargets; i++) {
				String timeLine = lineReader.readLine();
				String freqLine = lineReader.readLine();
				try {
					double time = Double.parseDouble(timeLine);
					double freq = Double.parseDouble(freqLine);
					targets[i] = new PitchTarget(time, freq);
				} catch (NumberFormatException nfe) {
					// default format has an extra line indexing each pitch
					// point
					timeLine = freqLine;
					freqLine = lineReader.readLine();
					String[] fields = timeLine.split("\\s+");
					double time = Double.parseDouble(fields[fields.length - 1]);
					fields = freqLine.split("\\s+");
					double freq = Double.parseDouble(fields[fields.length - 1]);
					targets[i] = new PitchTarget(time, freq);
				}
			}
		} finally {
			lineReader.close();
		}
	}

	public PraatPitchTier(double xmin, double[] frames, double step) {
		this.xmin = xmin;
		this.xmax = xmin + (frames.length - 1) * step;
		importFrames(frames, step);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see marytts.util.data.text.PraatTier#getName()
	 */
	@Override
	public String getName() {
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see marytts.util.data.text.PraatTier#getXmax()
	 */
	@Override
	public double getXmax() {
		return xmax;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see marytts.util.data.text.PraatTier#getXmin()
	 */
	@Override
	public double getXmin() {
		return xmin;
	}

	public int getNumTargets() {
		return numTargets;
	}

	public PitchTarget[] getPitchTargets() {
		return targets;
	}

	public void writeTo(Writer out) {
		PrintWriter pw = new PrintWriter(out);
		try {
			pw.println(FIRSTLINE);
			pw.println(SECONDLINE);
			pw.println();
			pw.println(xmin);
			pw.println(xmax);
			pw.println(numTargets);
			for (int i = 0; i < numTargets; i++) {
				pw.println(targets[i].time);
				pw.println(targets[i].frequency);
			}
		} finally {
			pw.close();
		}
	}

	/**
	 * Convert this sequence of pitch targets into an array of frame values.
	 * Values before the first target are NaN, values after the last target are
	 * NaN; values between targets are linearly interpolated.
	 * 
	 * @param step
	 *            the constant time distance between two frames, in seconds.
	 * @return an array of doubles representing the equally spaced frequency
	 *         values, from xmin to xmax.
	 */
	public double[] toFrames(double step) {
		int numFrames = (int) ((xmax - xmin) / step) + 1;
		assert xmin + (numFrames - 1) * step <= xmax;
		double[] frames = new double[numFrames];
		double t = xmin;
		PitchTarget prev = null;
		int j = 0;
		for (int i = 0; i < frames.length; i++) {
			frames[i] = getFrequency(t);
			t += step;
		}
		return frames;
	}

	public double getFrequency(double time) {
		PitchTarget prev = null;
		PitchTarget current = null;
		for (int j = 0; j < targets.length; j++) {
			if (time <= targets[j].time) {
				current = targets[j];
				if (j > 0) {
					prev = targets[j - 1];
				}
				break;
			}
		}
		if (current == null)
			return Double.NaN;
		if (Math.abs(time - current.time) < 1.e-7)
			return current.frequency;
		if (prev == null)
			return Double.NaN;
		// need to interpolate:
		assert prev != null;
		double deltaT = current.time - prev.time;
		double deltaF = current.frequency - prev.frequency;
		return prev.frequency + (time - prev.time) / deltaT * deltaF;
	}

	/**
	 * For every frame that is not NaN, create a pitch-time target.
	 * 
	 * @param frames
	 */
	protected void importFrames(double[] frames, double step) {
		ArrayList<PitchTarget> newTargets = new ArrayList<PitchTarget>();
		double t = xmin;
		for (int i = 0; i < frames.length; i++) {
			if (!Double.isNaN(frames[i])) {
				newTargets.add(new PitchTarget(t, frames[i]));
			}
			t += step;
		}
		targets = newTargets.toArray(new PitchTarget[0]);
		numTargets = targets.length;
	}

	@Override
	public boolean equals(Object otherObj) {
		if (this == otherObj) {
			return true;
		}
		if (!(otherObj instanceof PraatPitchTier)) {
			return false;
		}
		PraatPitchTier other = (PraatPitchTier) otherObj;
		if (this.xmin == other.xmin && this.xmax == other.xmax && this.numTargets == other.numTargets) {
			return Arrays.equals(this.targets, other.targets);
		}
		return false;
	}

	@Override
	public int hashCode() {
		int hash = 31 + Integer.parseInt(Double.toString(xmin + xmax)) + numTargets + Arrays.hashCode(targets);
		return hash;
	}

	public static class PitchTarget {
		public final double time;
		public final double frequency;

		public PitchTarget(double time, double frequency) {
			this.time = time;
			this.frequency = frequency;
		}

		@Override
		public boolean equals(Object otherObj) {
			if (this == otherObj) {
				return true;
			}
			if (!(otherObj instanceof PitchTarget)) {
				return false;
			}
			PitchTarget other = (PitchTarget) otherObj;
			if (this.time == other.time && this.frequency == other.frequency) {
				return true;
			}
			return false;
		}

		@Override
		public int hashCode() {
			int hash = 31 + Integer.parseInt(Double.toString(time + frequency));
			return hash;
		}
	}

}
