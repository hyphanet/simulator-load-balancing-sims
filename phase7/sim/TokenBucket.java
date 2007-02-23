package sim;

class TokenBucket
{
	public final double rate, size;
	private double tokens, lastUpdated;
	
	public TokenBucket (double rate, double size)
	{
		this.rate = rate; // Bandwidth limit in bytes per second
		this.size = size; // Size of maximum burst in bytes
		tokens = size;
		lastUpdated = 0.0; // Time
	}
	
	public int available()
	{
		double now = Event.time();
		double elapsed = now - lastUpdated;
		lastUpdated = now;
		tokens += elapsed * rate;
		if (tokens > size) tokens = size;
		return (int) tokens;
	}
	
	public void remove (int t)
	{
		tokens -= t; // Counter can go negative
	}
}
