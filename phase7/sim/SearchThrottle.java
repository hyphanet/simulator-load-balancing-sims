// An AIMD token bucket

package sim;

public class SearchThrottle
{
	public final static double INITIAL_RATE = 5.0; // Searches per second
	public final static double MAX_RATE = 50.0;
	public final static double MIN_RATE = 1.0 / 300.0;
	public final static double ALPHA = 0.082; // AIMD increase parameter
	public final static double BETA = 0.969; // AIMD decrease parameter
	
	private double rate = INITIAL_RATE;
	private double tokens = INITIAL_RATE, size = INITIAL_RATE;
	private double lastUpdated = 0.0; // Time
	
	public void increaseRate()
	{
		rate += ALPHA;
		if (rate > MAX_RATE) rate = MAX_RATE;
		Event.log ("rate increased to " + rate);
	}
	
	public void decreaseRate()
	{
		rate *= BETA;
		if (rate < MIN_RATE) rate = MIN_RATE;
		Event.log ("rate decreased to " + rate);
	}
	
	// Return the time when the next search can be sent
	public double nextSearchTime (double now)
	{
		tokens += (now - lastUpdated) * rate;
		if (tokens > size) tokens = size;
		lastUpdated = now;
		if (tokens >= 1.0) return now;
		else return now + (1.0 - tokens) * rate;
	}
	
	public void searchSent()
	{
		tokens--;
	}
}
