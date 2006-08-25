class TokenBucket
{
	private double tokens, rate, size, lastUpdated;
	
	public TokenBucket (double rate, double size)
	{
		tokens = size;
		this.rate = rate;
		this.size = size;
		lastUpdated = 0.0; // Clock time
	}
	
	public int available()
	{
		double now = Event.time();
		double elapsed = now - lastUpdated;
		lastUpdated = now;
		tokens += elapsed * rate;
		if (tokens > size) tokens = size;
		// Event.log (tokens + " tokens available");
		return (int) tokens;
	}
	
	public void remove (int t)
	{
		tokens -= t; // Counter can go negative
		Event.log (t + " tokens removed, " + tokens + " available");
	}
}
