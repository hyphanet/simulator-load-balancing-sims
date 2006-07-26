class Response extends Message
{
	public final int id; // The unique ID of the request
	public final double key; // The requested key (as a routing location)
	
	public Response (int id, double key)
	{
		this.id = id;
		this.key = key;
		size = Message.HEADER_SIZE + Message.ID_SIZE +
			Message.KEY_SIZE + Message.DATA_SIZE;
	}
	
	public String toString()
	{
		return new String ("response (" + id + "," + key + ")");
	}
}
