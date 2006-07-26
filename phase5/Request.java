class Request extends Message
{
	private static int nextId = 0;
	
	public final int id; // The unique ID of the request
	public final double key; // The requested key (as a routing location)
	
	// Start a new request
	public Request (double key)
	{
		id = nextId++;
		this.key = key;
		size = Message.HEADER_SIZE + Message.ID_SIZE + Message.KEY_SIZE;
	}
	
	// Forward a request
	public Request (int id, double key)
	{
		this.id = id;
		this.key = key;
		size = Message.HEADER_SIZE + Message.ID_SIZE + Message.KEY_SIZE;
	}
	
	public String toString()
	{
		return new String ("request (" + id + "," + key + ")");
	}
}
