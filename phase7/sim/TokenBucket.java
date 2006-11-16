package sim;

class TokenBucket
{
	public final double rate, size, poll;
	private double tokens, lastUpdated;
	
	public TokenBucket (double rate, double size)
	{
		this.rate = rate; // Bandwidth limit in bytes per second
		this.size = size; // Size of maximum burst in bytes
		double poll = Packet.MAX_SIZE / rate;
		if (poll < Peer.MIN_SLEEP) poll = Peer.MIN_SLEEP;
		if (poll > Peer.MAX_DELAY) poll = Peer.MAX_DELAY;
		this.poll = poll; // Polling interval in seconds
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
