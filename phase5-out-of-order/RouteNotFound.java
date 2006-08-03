// Note: for the purposes of this simulation, RejectedLoop and RouteNotFound
// are equivalent

class RouteNotFound extends Message
{
	public final int id; // The unique ID of the request
	
	public RouteNotFound (int id)
	{
		this.id = id;
		size = Message.HEADER_SIZE + Message.ID_SIZE;
	}
	
	public String toString()
	{
		return new String ("route not found (" + id + ")");
	}
}
